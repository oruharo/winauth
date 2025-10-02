package com.example.adauth.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Simple NTLM Authentication Filter
 * Extracts username from NTLM token without password validation
 */
public class SimpleNtlmAuthenticationFilter extends OncePerRequestFilter {

    private static final String NTLM_HEADER = "NTLM";
    private static final byte[] TYPE2_MESSAGE = createType2Message();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        System.out.println("=== NTLM Filter Debug ===");
        System.out.println("URI: " + request.getRequestURI());
        System.out.println("Auth Header: " + (authHeader != null ? authHeader.substring(0, Math.min(50, authHeader.length())) : "null"));

        // No authorization header - send 401 with NTLM challenge
        if (authHeader == null) {
            sendNtlmChallenge(response);
            return;
        }

        // Check if NTLM authentication
        if (authHeader.startsWith("NTLM ")) {
            String token = authHeader.substring(5);
            byte[] decoded = Base64.getDecoder().decode(token);

            // Check message type
            if (decoded.length >= 9) {
                int messageType = decoded[8] & 0xFF;
                System.out.println("NTLM Message Type: " + messageType);

                if (messageType == 1) {
                    // Type 1 - Send Type 2 challenge
                    sendType2Challenge(response);
                    return;

                } else if (messageType == 3) {
                    // Type 3 - Extract username and authenticate
                    NtlmTokenParser.NtlmUserInfo userInfo = NtlmTokenParser.parseNtlmToken(authHeader);

                    if (userInfo != null && userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
                        // Create authentication token
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                userInfo.getFullUsername(),
                                null,
                                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                        // Set to security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        System.out.println("NTLM Authentication successful: " + userInfo.getFullUsername());

                        // Continue filter chain
                        filterChain.doFilter(request, response);
                        return;
                    } else {
                        System.err.println("Failed to extract user info from Type 3 message");
                        sendNtlmChallenge(response);
                        return;
                    }
                }
            }
        }

        // If not NTLM or invalid, send challenge
        sendNtlmChallenge(response);
    }

    /**
     * Send 401 with NTLM challenge
     */
    private void sendNtlmChallenge(HttpServletResponse response) throws IOException {
        System.out.println("Sending NTLM challenge (401)");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "NTLM");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"NTLM Authentication Required\"}");
    }

    /**
     * Send Type 2 challenge message
     */
    private void sendType2Challenge(HttpServletResponse response) throws IOException {
        System.out.println("Sending NTLM Type 2 challenge");
        String challenge = Base64.getEncoder().encodeToString(TYPE2_MESSAGE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "NTLM " + challenge);
    }

    /**
     * Create NTLM Type 2 challenge message
     */
    private static byte[] createType2Message() {
        // Minimal NTLM Type 2 message
        byte[] message = new byte[48];

        // Signature "NTLMSSP\0"
        message[0] = 'N';
        message[1] = 'T';
        message[2] = 'L';
        message[3] = 'M';
        message[4] = 'S';
        message[5] = 'S';
        message[6] = 'P';
        message[7] = 0;

        // Type 2
        message[8] = 2;
        message[9] = 0;
        message[10] = 0;
        message[11] = 0;

        // Target Name (empty)
        message[12] = 0;
        message[13] = 0;
        message[14] = 0;
        message[15] = 0;
        message[16] = 40; // offset
        message[17] = 0;
        message[18] = 0;
        message[19] = 0;

        // Flags
        message[20] = 0x01; // Negotiate Unicode
        message[21] = (byte) 0x82; // Negotiate NTLM + Request Target
        message[22] = 0;
        message[23] = 0;

        // Challenge (8 bytes) - random would be better but fixed for simplicity
        for (int i = 24; i < 32; i++) {
            message[i] = (byte) (i * 2);
        }

        // Context (8 bytes)
        for (int i = 32; i < 40; i++) {
            message[i] = 0;
        }

        // Target Information (empty)
        message[40] = 0;
        message[41] = 0;
        message[42] = 0;
        message[43] = 0;
        message[44] = 40; // offset
        message[45] = 0;
        message[46] = 0;
        message[47] = 0;

        return message;
    }
}
