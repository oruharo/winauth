# NTLM認証統一実装ガイド

## 概要

現在のKerberos認証からNTLM認証に統一することで、クロスドメイン環境での認証問題を解決します。
NTLM認証でも統合Windows認証（自動ログイン）が可能で、ユーザー情報も完全に取得できます。

## 背景と動機

### 現在の問題
- WIN1 (DOMAIN1) → Kerberos認証成功 ✅
- WIN2 (DOMAIN2) → Kerberos認証失敗、500エラー ❌
- 原因: SPNがDOMAIN1にのみ存在、クロスドメインKerberos制約

### NTLM統一のメリット
1. **設定簡素化**: keytab、SPN登録、krb5.conf設定が不要
2. **クロスドメイン対応**: 信頼関係があれば自動的に両ドメイン対応
3. **DOMAIN2作業不要**: 海外拠点での作業が一切不要
4. **Java 17互換**: RC4暗号化問題を完全回避
5. **自動ログイン**: パスワード入力不要（統合Windows認証）
6. **ユーザー情報取得**: ドメイン、グループ、属性情報を完全取得

## KerberosとNTLMの設定要件比較

| 設定項目 | Kerberos | NTLM | 備考 |
|---------|----------|------|------|
| **ドメインコントローラー設定** |
| SPN登録 | ✅ 必要 | ❌ 不要 | setspn -A HTTP/service user |
| サービスアカウント作成 | ✅ 必要 | ❌ 不要 | Kerberosのみ専用アカウント必要 |
| keytab生成 | ✅ 必要 | ❌ 不要 | ktpassコマンドで生成 |
| RC4/AES暗号化設定 | ✅ 必要 | ❌ 不要 | allow_weak_crypto = true |
| **クロスドメイン設定** |
| 両ドメインでのSPN | ✅ 必要 | ❌ 不要 | 各ドメインで個別設定 |
| 両ドメインでのkeytab | ✅ 必要 | ❌ 不要 | マージ作業が複雑 |
| 信頼関係 | ✅ 必要 | ✅ 必要 | 両方式で必要（既存） |
| DNS設定 | ✅ 必要 | ❌ 不要 | conditional forwarder |
| **アプリケーション設定** |
| krb5.conf | ✅ 必要 | ❌ 不要 | レルム、KDC設定 |
| JVM RC4設定 | ✅ 必要 | ❌ 不要 | Java 17制約回避 |
| Spring Security設定 | 🔄 複雑 | ✅ シンプル | SPNEGO vs NTLM |
| keytabファイル配置 | ✅ 必要 | ❌ 不要 | セキュアな配置必要 |
| **クライアント設定** |
| ブラウザ設定 | ✅ 必要 | ✅ 必要 | 統合認証有効化（同じ設定） |
| イントラネットゾーン | ✅ 必要 | ✅ 必要 | ドメイン追加（同じ設定） |
| **運用・保守** |
| keytab更新 | ✅ 必要 | ❌ 不要 | パスワード変更時 |
| 証明書管理 | ✅ 必要 | ❌ 不要 | keytab暗号化証明書 |
| DOMAIN2作業 | ✅ 必要 | ❌ 不要 | 海外拠点協力が不要 |

## なぜNTLMでドメインコントローラー設定が不要なのか

### **認証フローの比較**

**Kerberos認証の場合**:
```
1. クライアント → KDC: TGT要求
2. KDC → クライアント: TGT発行
3. クライアント → KDC: サービスチケット要求（SPN必要）
4. KDC → クライアント: サービスチケット発行
5. クライアント → サーバー: サービスチケット送信
6. サーバー: keytabで検証
```

**NTLM認証の場合**:
```
1. クライアント → サーバー: Type1メッセージ（認証開始）
2. サーバー → クライアント: Type2メッセージ（チャレンジ）
3. クライアント → サーバー: Type3メッセージ（レスポンス）
4. サーバー → DC: ユーザー検証要求（既存の仕組み）
5. DC → サーバー: 検証結果
```

**重要な違い**:
- **Kerberos**: 事前にSPNとkeytabをDCに設定する必要がある
- **NTLM**: DCの既存のユーザー認証機能をそのまま利用

### 既存のDC設定で十分な理由

1. **ユーザーアカウント**: 既にDOMAIN1\user1、DOMAIN2\user2が存在
2. **信頼関係**: 既にDOMAIN1⟷DOMAIN2間で設定済み
3. **DNS解決**: 既存のDNS設定で十分
4. **認証プロトコル**: NTLMはWindowsの標準機能

### **必要な確認項目**

DCで以下が設定済みであることを確認（通常は既に設定済み）：

```powershell
# 信頼関係の確認
nltest /domain_trusts

# ユーザーアカウントの確認
Get-ADUser user1  # DC1で実行
Get-ADUser user2  # DC2で実行

# 信頼関係の詳細確認
netdom trust DOMAIN1.LAB /domain:DOMAIN2.LAB /verify
netdom trust DOMAIN2.LAB /domain:DOMAIN1.LAB /verify
```

## 実装手順

### 1. Maven依存関係の追加

`pom.xml`に以下を追加：

```xml
<dependencies>
    <!-- 既存のSpring Boot依存関係 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- NTLM認証用ライブラリ -->
    <dependency>
        <groupId>com.github.waffle</groupId>
        <artifactId>waffle-spring-boot3</artifactId>
        <version>3.3.0</version>
    </dependency>

    <!-- JCIFSライブラリ（Linux環境でのNTLM検証用） -->
    <dependency>
        <groupId>org.samba.jcifs</groupId>
        <artifactId>jcifs</artifactId>
        <version>2.1.30</version>
    </dependency>
</dependencies>
```

### 2. Spring Security設定

#### NTLMセキュリティ設定クラス

```java
package com.example.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import waffle.servlet.NegotiateSecurityFilter;
import waffle.servlet.NegotiateSecurityFilterProvider;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

@Configuration
@EnableWebSecurity
@Profile("ntlm")
public class NtlmSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions().deny())
            .addFilterBefore(negotiateSecurityFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public NegotiateSecurityFilter negotiateSecurityFilter() {
        NegotiateSecurityFilter filter = new NegotiateSecurityFilter();
        filter.setProvider(windowsAuthProvider());
        return filter;
    }

    @Bean
    public WindowsAuthProviderImpl windowsAuthProvider() {
        WindowsAuthProviderImpl provider = new WindowsAuthProviderImpl();
        provider.setAllowGuestLogin(false);
        return provider;
    }
}
```

#### Linux環境用のJCIFS設定

```java
package com.example.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import jcifs.http.NtlmSsp;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbSession;

@Configuration
@EnableWebSecurity
@Profile("linux-ntlm")
public class LinuxNtlmConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(jcifsNtlmFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JcifsNtlmAuthenticationFilter jcifsNtlmFilter() {
        JcifsNtlmAuthenticationFilter filter = new JcifsNtlmAuthenticationFilter();
        filter.setDomainController("DC1.DOMAIN1.LAB");  // プライマリDC
        filter.setDomain("DOMAIN1.LAB");
        filter.setEnableCrossDomainAuth(true);  // 信頼関係使用
        return filter;
    }
}
```

### 3. カスタムNTLMフィルター実装

```java
package com.example.adauth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Base64;

public class JcifsNtlmAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private String domainController;
    private String domain;
    private boolean enableCrossDomainAuth;

    public JcifsNtlmAuthenticationFilter() {
        super(new AntPathRequestMatcher("/**"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                              HttpServletResponse response)
            throws AuthenticationException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            // NTLM認証チャレンジを送信
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "NTLM");
            return null;
        }

        if (authHeader.startsWith("NTLM ")) {
            String ntlmToken = authHeader.substring(5);
            return processNtlmToken(ntlmToken, request, response);
        }

        if (authHeader.startsWith("Negotiate ")) {
            String negotiateToken = authHeader.substring(10);
            return processNegotiateToken(negotiateToken, request, response);
        }

        return null;
    }

    private Authentication processNtlmToken(String token, HttpServletRequest request,
                                          HttpServletResponse response) {
        try {
            byte[] tokenBytes = Base64.getDecoder().decode(token);

            // NTLMトークンの種類を判定
            if (isType1Message(tokenBytes)) {
                // Type 1: 認証開始
                return handleType1Message(tokenBytes, response);
            } else if (isType3Message(tokenBytes)) {
                // Type 3: 認証情報送信
                return handleType3Message(tokenBytes, request);
            }

        } catch (Exception e) {
            throw new RuntimeException("NTLM authentication failed", e);
        }

        return null;
    }

    private Authentication handleType1Message(byte[] message, HttpServletResponse response) {
        // Type 2メッセージ（チャレンジ）を生成して送信
        byte[] challengeMessage = generateNtlmChallenge();
        String challengeB64 = Base64.getEncoder().encodeToString(challengeMessage);

        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "NTLM " + challengeB64);

        return null;
    }

    private Authentication handleType3Message(byte[] message, HttpServletRequest request) {
        // Type 3メッセージからユーザー情報を抽出
        NtlmMessage ntlmMessage = parseNtlmType3Message(message);

        String username = ntlmMessage.getUsername();
        String domain = ntlmMessage.getDomain();
        String workstation = ntlmMessage.getWorkstation();

        // ドメインコントローラーで認証検証
        boolean isAuthenticated = validateWithDomainController(
            username, domain, ntlmMessage.getNtlmResponse()
        );

        if (isAuthenticated) {
            return createAuthenticationToken(username, domain, workstation);
        }

        throw new RuntimeException("Authentication failed");
    }

    // ヘルパーメソッド
    private boolean validateWithDomainController(String username, String domain, byte[] ntlmResponse) {
        if (domain.equals("DOMAIN1")) {
            // DOMAIN1の場合は直接DC1で検証
            return validateWithDC1(username, ntlmResponse);
        } else if (domain.equals("DOMAIN2") && enableCrossDomainAuth) {
            // DOMAIN2の場合は信頼関係経由で検証
            return validateWithTrustRelationship(username, domain, ntlmResponse);
        }
        return false;
    }

    // その他のヘルパーメソッド...
}
```

### 4. ユーザー情報取得とレスポンス

#### ユーザー情報コントローラー

```java
package com.example.adauth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import waffle.windows.auth.WindowsPrincipal;
import waffle.windows.auth.WindowsAccount;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AuthenticationController {

    @GetMapping("/whoami")
    public Map<String, Object> getCurrentUser(Authentication auth) {
        if (auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();

            return Map.of(
                "username", extractUsername(principal.getName()),
                "domain", extractDomain(principal.getName()),
                "fullName", principal.getName(),
                "sid", principal.getSid(),
                "groups", getGroups(principal),
                "authenticated", auth.isAuthenticated(),
                "authMethod", "NTLM",
                "workstation", getWorkstation(auth)
            );
        }

        return Map.of(
            "username", auth.getName(),
            "authenticated", auth.isAuthenticated(),
            "authMethod", "NTLM"
        );
    }

    @GetMapping("/userinfo")
    public Map<String, Object> getUserDetails(Authentication auth) {
        WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();

        return Map.of(
            "user", Map.of(
                "name", extractUsername(principal.getName()),
                "domain", extractDomain(principal.getName()),
                "sid", principal.getSid(),
                "displayName", getDisplayName(principal)
            ),
            "groups", getDetailedGroups(principal),
            "permissions", auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList()),
            "session", Map.of(
                "authTime", System.currentTimeMillis(),
                "method", "NTLM",
                "secure", true
            )
        );
    }

    // ヘルパーメソッド
    private String extractUsername(String fqn) {
        return fqn.contains("\\") ? fqn.split("\\\\")[1] : fqn;
    }

    private String extractDomain(String fqn) {
        return fqn.contains("\\") ? fqn.split("\\\\")[0] : "UNKNOWN";
    }

    private List<String> getGroups(WindowsPrincipal principal) {
        return principal.getGroups().stream()
            .map(WindowsAccount::getFqn)
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getDetailedGroups(WindowsPrincipal principal) {
        return principal.getGroups().stream()
            .map(group -> Map.of(
                "name", group.getName(),
                "domain", group.getDomain(),
                "fqn", group.getFqn(),
                "sid", group.getSid()
            ))
            .collect(Collectors.toList());
    }
}
```

### 5. 設定ファイル

#### application-ntlm.properties

```properties
# NTLM認証プロファイル設定
spring.profiles.active=ntlm

# CORS設定
spring.security.cors.allowed-origins=http://localhost:5173
spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.security.cors.allowed-headers=*
spring.security.cors.allow-credentials=true

# NTLM設定
ntlm.domain-controller=DC1.DOMAIN1.LAB
ntlm.primary-domain=DOMAIN1.LAB
ntlm.enable-cross-domain=true
ntlm.trust-relationship=true

# ログ設定
logging.level.com.example.adauth.security=DEBUG
logging.level.waffle=DEBUG
logging.level.jcifs=DEBUG
logging.level.org.springframework.security=DEBUG

# セッション設定
server.servlet.session.timeout=30m
server.servlet.session.cookie.secure=false
server.servlet.session.cookie.http-only=true
```

#### Linux環境用設定

```properties
# Linux NTLM設定
spring.profiles.active=linux-ntlm

# JCIFS設定
jcifs.smb.client.domain=DOMAIN1.LAB
jcifs.smb.client.username=svcapp
jcifs.smb.client.password=ServicePass123!
jcifs.smb.client.laddr=10.0.30.10
jcifs.netbios.wins=10.0.10.10,10.0.20.10
jcifs.smb.client.dfs.disabled=false

# ドメインコントローラー設定
domain.dc1.host=DC1.DOMAIN1.LAB
domain.dc1.ip=10.0.10.10
domain.dc2.host=DC2.DOMAIN2.LAB
domain.dc2.ip=10.0.20.10
```

### 6. デプロイと起動

#### Maven実行

```bash
# NTLM プロファイルで起動
mvn spring-boot:run -Dspring.profiles.active=ntlm

# Linux環境での起動
mvn spring-boot:run -Dspring.profiles.active=linux-ntlm

# デバッグモード
mvn spring-boot:run -Dspring.profiles.active=ntlm -Ddebug=true
```

#### Docker実行

```dockerfile
FROM openjdk:17-jdk-slim

COPY target/winauth-*.jar app.jar

# NTLM用の環境変数
ENV SPRING_PROFILES_ACTIVE=linux-ntlm
ENV NTLM_DOMAIN_CONTROLLER=DC1.DOMAIN1.LAB
ENV NTLM_PRIMARY_DOMAIN=DOMAIN1.LAB

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 7. クライアント側設定

#### WIN1とWIN2の共通ブラウザ設定

既存のPlaybook `configure-browser-kerberos.yml`を流用可能：

```yaml
# NTLM認証でも同じ設定が有効
- name: Enable integrated Windows authentication for Edge
  win_regedit:
    path: 'HKCU:\SOFTWARE\Policies\Microsoft\Edge'
    name: AuthServerAllowlist
    data: "{{ alb_dns_name }}"
    type: string

- name: Enable automatic logon for Intranet zone
  win_regedit:
    path: 'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Internet Settings\Zones\1'
    name: '1A00'
    data: 0
    type: dword
```

## テストと検証

### 1. 基本認証テスト

```bash
# WIN1からのテスト
curl -v --negotiate -u : https://alb.amazonaws.com/api/whoami

# WIN2からのテスト
curl -v --negotiate -u : https://alb.amazonaws.com/api/whoami

# 期待される結果（両方とも成功）
{
  "username": "user1",  // or "user2"
  "domain": "DOMAIN1",  // or "DOMAIN2"
  "authenticated": true,
  "authMethod": "NTLM"
}
```

### 2. ログ確認

```bash
# 認証成功ログ
INFO  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication successful for DOMAIN1\user1
INFO  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication successful for DOMAIN2\user2

# 認証失敗時のログ
WARN  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication failed for user: unknown
ERROR c.e.a.s.NtlmAuthenticationFilter - Domain controller unreachable: DC1.DOMAIN1.LAB
```

### 3. 統合テスト

```java
@SpringBootTest
@ActiveProfiles("ntlm")
class NtlmAuthenticationTest {

    @Test
    void testDomain1Authentication() {
        // DOMAIN1ユーザーの認証テスト
    }

    @Test
    void testDomain2CrossDomainAuthentication() {
        // DOMAIN2ユーザーの信頼関係経由認証テスト
    }

    @Test
    void testUserInfoRetrieval() {
        // ユーザー情報取得テスト
    }
}
```

## 移行手順

### 1. 段階的移行

```bash
# Step 1: 現在のKerberos設定をバックアップ
cp application-kerberos.properties application-kerberos.properties.backup

# Step 2: NTLM設定でテスト環境起動
mvn spring-boot:run -Dspring.profiles.active=ntlm

# Step 3: WIN1とWIN2で動作確認

# Step 4: 本番環境への適用
```

### 2. 設定ファイルの更新

```bash
# Ansible Playbookでの自動化
ansible-playbook -i inventory deploy-linux.yml --extra-vars "auth_method=ntlm"
```

## トラブルシューティング

### よくある問題と解決策

1. **401エラーが繰り返し発生**
   ```
   原因: NTLMハンドシェイクの失敗
   解決: ログでType1/Type2/Type3メッセージの流れを確認
   ```

2. **DOMAIN2ユーザーが認証できない**
   ```
   原因: 信頼関係の設定不備
   解決: nltest /domain_trusts で信頼関係を確認
   ```

3. **ユーザー情報が取得できない**
   ```
   原因: WindowsPrincipalの取得失敗
   解決: Waffleライブラリのバージョン確認
   ```

4. **Linux環境でNTLM認証失敗**
   ```
   原因: JCIFSの設定不備
   解決: jcifs.propertiesの設定確認
   ```

## セキュリティ考慮事項

### NTLMのセキュリティ脆弱性詳細

#### 1. パスワードハッシュの脆弱性（統合Windows認証では該当しない）

**NTLM一般の問題**:
```
手動NTLM認証フロー（Basic認証等）:
1. ユーザー入力: パスワード
2. クライアント: NT Hash生成 (MD4ベース)
3. チャレンジ・レスポンス: ハッシュを使用したレスポンス生成
4. ネットワーク送信: ハッシュベースのレスポンス
```

**今回の統合Windows認証（IWA）フロー**:
```
✅ 実際の流れ:
1. ユーザー: 既にWindowsにログイン済み（パスワード入力済み）
2. ブラウザ: 保存済みNTハッシュを自動使用
3. チャレンジ・レスポンス: 自動生成・送信
4. ユーザー: 追加のパスワード入力なし
```

**脆弱性の該当状況**:
- **MD4ハッシュ**: ✅ 該当（ハッシュ自体は使用される）
- **レインボーテーブル攻撃**: ❌ 非該当（パスワード入力なし）
- **ブルートフォース**: ❌ 非該当（パスワード推測攻撃なし）

**重要**: 統合Windows認証では、Webアプリでのパスワード入力は発生しないため、パスワード関連の攻撃リスクは大幅に軽減されます。

#### 2. Pass-the-Hash攻撃（統合Windows認証でも該当）

**攻撃手順**:
```
1. 攻撃者: NT Hash取得 (メモリダンプ、SAM等)
2. 攻撃者: パスワード知らずにハッシュで認証
3. 横展開: 同じハッシュで他のシステムにもアクセス
```

**統合Windows認証での該当性**: ✅ **該当** - NTハッシュが盗まれた場合、統合認証でも悪用可能

**実例**:
```bash
# Mimikazを使用した例
mimikatz # sekurlsa::logonpasswords
# → NTハッシュが露出

# ハッシュを使った認証
pth-winexe -U DOMAIN/user%hash //target cmd.exe
```

#### 3. リプレイ攻撃の脆弱性（統合Windows認証でも該当）

**問題の仕組み**:
```
正常な認証:
1. Server → Client: Challenge (8バイト)
2. Client → Server: Response (24バイト)

攻撃:
1. 攻撃者: ネットワーク盗聴でChallenge/Response取得
2. 攻撃者: 同じChallenge/Responseを別セッションで再送
3. 成功: サーバーが同じレスポンスを受け入れる
```

**統合Windows認証での該当性**: ✅ **該当** - ネットワーク盗聴で取得したトークンの再利用が可能

**対策が不十分**:
- タイムスタンプなし
- セッション固有のランダム性が低い

#### 4. 中間者攻撃（MITM）（統合Windows認証でも該当）

**攻撃シナリオ**:
```
Client ←→ Attacker ←→ Server

1. Client → Attacker: NTLM Type1
2. Attacker → Server: Type1 (転送)
3. Server → Attacker: Type2 (Challenge)
4. Attacker → Client: 偽のChallenge
5. Client → Attacker: Response
6. 攻撃者: レスポンスを解析/利用
```

**統合Windows認証での該当性**: ✅ **該当** - HTTPS未使用時に通信を傍受・改ざん可能

#### 5. NTLMv1 vs NTLMv2の問題

**NTLMv1の脆弱性**:
```
NTLMv1 Response計算:
- DES暗号化使用
- 56bit鍵長（現代では短すぎ）
- LM Hashとの併用で更に脆弱

NTLMv2の改善:
- HMAC-MD5使用
- より長いチャレンジ
- タイムスタンプ含む
```

### KerberosとNTLMのセキュリティ比較

| 項目 | NTLM | Kerberos | 備考 |
|------|------|----------|------|
| **暗号化** | MD4/MD5 | AES256/DES | Kerberosが強力 |
| **鍵配布** | なし | KDC中央管理 | Kerberosが安全 |
| **相互認証** | なし | あり | Kerberosのみ |
| **チケット期限** | なし | あり | Kerberosが安全 |
| **Pass-the-Hash** | 脆弱 | 困難 | Kerberosが安全 |
| **リプレイ攻撃** | 脆弱 | 耐性あり | Kerberosが安全 |
| **中間者攻撃** | 脆弱 | 困難 | Kerberosが安全 |
| **ブルートフォース** | 脆弱 | 困難 | Kerberosが安全 |

### リスク評価

#### **高リスク環境（NTLM非推奨）**
```
✗ インターネット経由のアクセス
✗ 信頼できないネットワーク
✗ 高度な攻撃者がいる環境
✗ 機密データを扱うシステム
✗ コンプライアンス要件が厳格（PCI DSS、HIPAA等）
✗ DMZ配置のサーバー
✗ ゲストネットワークからのアクセス
```

#### **許容可能な環境**
```
✓ 内部ネットワーク限定
✓ 物理的にセキュアな環境
✓ HTTPS必須での運用
✓ ネットワーク監視あり
✓ 定期的なパスワード変更
✓ VPN経由のアクセス
✓ 業務アプリケーション（社内限定）
```

### 必須のセキュリティ対策

#### **ネットワークレベル**
```yaml
必須設定:
  - HTTPS強制 (TLS 1.2以上)
  - ALB/WAFでのHTTPSリダイレクト
  - 社内ネットワーク限定アクセス
  - VPN経由のアクセス制御

設定例:
  # ALBでHTTPS強制
  - Type: redirect
    RedirectConfig:
      Protocol: HTTPS
      Port: 443
      StatusCode: HTTP_301
```

#### **Active Directory設定**
```powershell
# NTLMv1を無効化（NTLMv2強制）
Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Lsa" `
  -Name "LmCompatibilityLevel" -Value 5

# LM Hash保存を無効化
Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Lsa" `
  -Name "NoLMHash" -Value 1

# 強固なパスワードポリシー
Set-ADDefaultDomainPasswordPolicy -Identity "DOMAIN1.LAB" `
  -MinPasswordLength 12 `
  -PasswordHistoryCount 24 `
  -MaxPasswordAge "90.00:00:00" `
  -MinPasswordAge "1.00:00:00" `
  -LockoutThreshold 5
```

#### **アプリケーションレベル**
```java
// Spring Securityでの追加設定
@Configuration
public class SecurityEnhancementConfig {

    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(false);
        firewall.setAllowUrlEncodedPercent(false);
        return firewall;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)  // 1年
                    .includeSubdomains(true)
                    .preload(true)
                )
                .contentSecurityPolicy("default-src 'self'")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)  // 同時セッション制限
                .maxSessionsPreventsLogin(false)
            );

        return http.build();
    }
}
```

#### **監視とログ**
```yaml
# CloudWatch設定例
監視項目:
  - 認証失敗回数（閾値: 10回/5分）
  - 異常なアクセスパターン
  - 複数IPからの同時ログイン
  - 営業時間外のアクセス

アラート設定:
  - 認証失敗の急増
  - 新しい地域からのアクセス
  - 管理者権限での不審なアクセス
```

### 具体的な実装

#### **認証ログの強化**
```java
@Component
public class SecurityAuditLogger {

    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY-AUDIT");

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String clientIP = getClientIP(event);
        String userAgent = getUserAgent(event);

        auditLogger.info("NTLM_AUTH_SUCCESS: user={}, ip={}, agent={}, time={}",
            username, clientIP, userAgent, Instant.now());

        // 異常検知
        if (isUnusualAccess(username, clientIP)) {
            auditLogger.warn("UNUSUAL_ACCESS_DETECTED: user={}, ip={}", username, clientIP);
            // アラート送信
            alertService.sendSecurityAlert(username, clientIP, "Unusual access pattern");
        }
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String clientIP = getClientIP(event);

        auditLogger.warn("NTLM_AUTH_FAILURE: user={}, ip={}, reason={}",
            username, clientIP, event.getException().getMessage());

        // ブルートフォース検知
        if (authFailureTracker.isExcessiveFailure(username, clientIP)) {
            auditLogger.error("BRUTE_FORCE_DETECTED: user={}, ip={}", username, clientIP);
            // IP制限等の対策実行
            securityService.blockIP(clientIP, Duration.ofHours(1));
        }
    }
}
```

#### **WAFルールの設定**
```json
{
  "Rules": [
    {
      "Name": "RateLimitNTLMAuth",
      "Priority": 100,
      "Statement": {
        "RateBasedStatement": {
          "Limit": 100,
          "AggregateKeyType": "IP"
        }
      },
      "Action": {
        "Block": {}
      }
    },
    {
      "Name": "BlockNonHTTPS",
      "Priority": 200,
      "Statement": {
        "NotStatement": {
          "Statement": {
            "ByteMatchStatement": {
              "SearchString": "https",
              "FieldToMatch": {
                "UriPath": {}
              }
            }
          }
        }
      },
      "Action": {
        "Block": {}
      }
    }
  ]
}
```

### 長期的なセキュリティ戦略

#### **段階的移行計画**
```yaml
Phase 1 (短期: 0-3ヶ月):
  - NTLM統一実装
  - 基本的なセキュリティ対策
  - 監視体制構築

Phase 2 (中期: 3-12ヶ月):
  - 詳細なセキュリティ監査
  - 追加の防御策実装
  - OAuth2/SAML移行準備

Phase 3 (長期: 12ヶ月以降):
  - モダン認証への完全移行
  - ゼロトラストアーキテクチャ
  - 継続的セキュリティ改善
```

### 統合Windows認証（IWA）での実際のリスク

#### **今回の実装で軽減されるリスク**
```
❌ パスワード入力時の盗聴
❌ キーロガーによるパスワード取得
❌ ブルートフォース攻撃
❌ レインボーテーブル攻撃
❌ フィッシング攻撃（パスワード入力画面なし）
```

#### **今回の実装でも残るリスク**
```
⚠️ Pass-the-Hash攻撃（端末セキュリティに依存）
⚠️ リプレイ攻撃（HTTPS必須で軽減）
⚠️ 中間者攻撃（HTTPS必須で軽減）
⚠️ 端末の物理的盗難
```

### 現実的なリスク評価（本環境）

**あなたの環境での評価**:
```
✓ 内部ネットワーク（ALB経由）
✓ HTTPS必須 → リプレイ攻撃・MITM攻撃を大幅軽減
✓ 信頼関係済みドメイン
✓ 限定されたユーザー
✓ 業務アプリケーション
✓ AWS セキュリティグループで制限
✓ 監視可能な環境
✓ パスワード入力なし → 関連攻撃を完全排除
```

**結論**: **統合Windows認証により大幅にリスクが軽減され、適切な対策下で許容可能なリスクレベル**

**条件**:
- HTTPS通信の確実な実装（最重要）
- 端末セキュリティ対策（ウイルス対策、物理セキュリティ）
- 上記のその他セキュリティ対策を実装
- 定期的なセキュリティ監査
- 将来的なモダン認証への移行計画

## まとめ

NTLM認証への統一により：

✅ **簡素化**: keytab、SPN等の複雑な設定が不要
✅ **クロスドメイン対応**: 信頼関係で自動的に両ドメイン対応
✅ **DOMAIN2作業不要**: 海外拠点での作業一切不要
✅ **自動ログイン**: パスワード入力不要
✅ **ユーザー情報**: 完全なユーザー情報取得可能

この実装により、クロスドメイン環境での認証問題を根本的に解決できます。