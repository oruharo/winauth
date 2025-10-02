package com.example.adauth.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NTLM Token Parser
 * Extracts username and domain from NTLM Type 3 message
 */
public class NtlmTokenParser {

    /**
     * Parse NTLM token and extract user information
     */
    public static NtlmUserInfo parseNtlmToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("NTLM ")) {
            return null;
        }

        try {
            String token = authHeader.substring(5);
            byte[] decoded = Base64.getDecoder().decode(token);

            // Check NTLM signature
            if (decoded.length < 8 || !isNtlmSignature(decoded)) {
                return null;
            }

            // Get message type (offset 8)
            int messageType = decoded[8] & 0xFF;

            if (messageType == 3) {
                // Type 3 message - contains username and domain
                return parseType3Message(decoded);
            }

            return null;
        } catch (Exception e) {
            System.err.println("Failed to parse NTLM token: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if token has valid NTLM signature
     */
    private static boolean isNtlmSignature(byte[] data) {
        if (data.length < 8) return false;
        // NTLMSSP signature: "NTLMSSP\0"
        return data[0] == 'N' && data[1] == 'T' && data[2] == 'L' &&
               data[3] == 'M' && data[4] == 'S' && data[5] == 'S' &&
               data[6] == 'P' && data[7] == 0;
    }

    /**
     * Parse NTLM Type 3 message to extract username and domain
     */
    private static NtlmUserInfo parseType3Message(byte[] data) {
        try {
            // NTLM Type 3 message structure:
            // 0-7: Signature "NTLMSSP\0"
            // 8: Message Type (3)
            // 12-19: LM Response Security Buffer
            // 20-27: NTLM Response Security Buffer
            // 28-35: Target Name (Domain) Security Buffer
            // 36-43: User Name Security Buffer
            // 44-51: Workstation Name Security Buffer

            // Domain Name - offset 28
            int domainLen = readShort(data, 28);
            int domainOffset = readInt(data, 32);
            String domain = readString(data, domainOffset, domainLen);

            // User Name - offset 36
            int userLen = readShort(data, 36);
            int userOffset = readInt(data, 40);
            String username = readString(data, userOffset, userLen);

            // Workstation Name - offset 44
            int workstationLen = readShort(data, 44);
            int workstationOffset = readInt(data, 48);
            String workstation = readString(data, workstationOffset, workstationLen);

            System.out.println("NTLM Parse Result:");
            System.out.println("  Domain: " + domain);
            System.out.println("  Username: " + username);
            System.out.println("  Workstation: " + workstation);

            return new NtlmUserInfo(domain, username, workstation);

        } catch (Exception e) {
            System.err.println("Failed to parse Type 3 message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Read 2-byte little-endian short
     */
    private static int readShort(byte[] data, int offset) {
        if (offset + 1 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Read 4-byte little-endian int
     */
    private static int readInt(byte[] data, int offset) {
        if (offset + 3 >= data.length) return 0;
        return (data[offset] & 0xFF) |
               ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) |
               ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Read Unicode string (UTF-16LE)
     */
    private static String readString(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            return "";
        }
        byte[] stringBytes = new byte[length];
        System.arraycopy(data, offset, stringBytes, 0, length);
        return new String(stringBytes, StandardCharsets.UTF_16LE);
    }

    /**
     * NTLM User Information
     */
    public static class NtlmUserInfo {
        private final String domain;
        private final String username;
        private final String workstation;

        public NtlmUserInfo(String domain, String username, String workstation) {
            this.domain = domain != null ? domain : "";
            this.username = username != null ? username : "";
            this.workstation = workstation != null ? workstation : "";
        }

        public String getDomain() {
            return domain;
        }

        public String getUsername() {
            return username;
        }

        public String getWorkstation() {
            return workstation;
        }

        public String getFullUsername() {
            if (domain != null && !domain.isEmpty()) {
                return domain + "\\" + username;
            }
            return username;
        }

        @Override
        public String toString() {
            return "NtlmUserInfo{domain='" + domain + "', username='" + username +
                   "', workstation='" + workstation + "'}";
        }
    }
}
