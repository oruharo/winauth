# Windowsログインユーザー識別実装ガイド

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

### **ドメコンアクセスの必要性**

**注意**: ユーザー識別でも最低限のドメコンアクセスは必要です

```java
// Waffleライブラリが内部的に実行
NTLMAuthenticationProcess {
    1. NTLMハンドシェイク処理
    2. ドメコンへの最低限検証（偽装防止）
    3. ユーザー情報取得
    4. アプリケーションへ結果返却
}
```

**ただし、認証との違い**:
- **頻度**: 初回セッション確立時のみ
- **複雑度**: 基本的な妥当性確認のみ
- **目的**: 偽装防止（セキュリティ認証ではない）

## 実装手順

### 1. Maven依存関係の更新

`pom.xml`の変更：

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

    <!-- 削除: Kerberos関連の依存関係 -->
    <!--
    <dependency>
        <groupId>org.springframework.security.kerberos</groupId>
        <artifactId>spring-security-kerberos-core</artifactId>
    </dependency>
    -->
</dependencies>
```

### 2. Spring Security設定（シンプル化）

#### ユーザー識別設定クラス

```java
package com.example.adauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import waffle.servlet.NegotiateSecurityFilter;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

@Configuration
@EnableWebSecurity
@Profile("user-identification")
public class UserIdentificationConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()  // 認証ではなく識別のため軽量
            )
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(userIdentificationFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public NegotiateSecurityFilter userIdentificationFilter() {
        NegotiateSecurityFilter filter = new NegotiateSecurityFilter();
        WindowsAuthProviderImpl provider = new WindowsAuthProviderImpl();
        provider.setAllowGuestLogin(false);
        filter.setProvider(provider);
        return filter;
    }
}
```

### 3. ユーザー識別コントローラー

```java
package com.example.adauth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import waffle.windows.auth.WindowsPrincipal;
import waffle.windows.auth.WindowsAccount;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserIdentificationController {

    @GetMapping("/whoami")
    public Map<String, Object> getCurrentUser(Authentication auth, HttpServletRequest request) {
        if (auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();

            return Map.of(
                "userId", extractUsername(principal.getName()),
                "domain", extractDomain(principal.getName()),
                "fullIdentity", principal.getName(),
                "groups", getGroups(principal),
                "identified", true,
                "method", "WindowsIntegratedAuth",
                "clientIP", getClientIP(request),
                "timestamp", System.currentTimeMillis()
            );
        }

        return Map.of(
            "userId", auth.getName(),
            "identified", true,
            "method", "Basic"
        );
    }

    @GetMapping("/user-info")
    public Map<String, Object> getUserDetails(Authentication auth) {
        WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();

        return Map.of(
            "user", Map.of(
                "id", extractUsername(principal.getName()),
                "domain", extractDomain(principal.getName()),
                "displayName", principal.getName()
            ),
            "groups", getDetailedGroups(principal),
            "session", Map.of(
                "identifiedAt", System.currentTimeMillis(),
                "method", "NTLM_UserIdentification",
                "secure", true
            )
        );
    }

    // 監査ログ用エンドポイント
    @GetMapping("/audit-info")
    public Map<String, Object> getAuditInfo(Authentication auth, HttpServletRequest request) {
        return Map.of(
            "audit", Map.of(
                "userId", extractUsername(auth.getName()),
                "domain", extractDomain(auth.getName()),
                "clientIP", getClientIP(request),
                "userAgent", request.getHeader("User-Agent"),
                "timestamp", System.currentTimeMillis(),
                "action", "user_identification"
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
            .map(WindowsAccount::getName)
            .collect(Collectors.toList());
    }

    private List<Map<String, String>> getDetailedGroups(WindowsPrincipal principal) {
        return principal.getGroups().stream()
            .map(group -> Map.of(
                "name", group.getName(),
                "domain", group.getDomain(),
                "fqn", group.getFqn()
            ))
            .collect(Collectors.toList());
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### 4. 設定ファイル

#### application-user-identification.properties

```properties
# ユーザー識別プロファイル設定
spring.profiles.active=user-identification

# CORS設定
spring.security.cors.allowed-origins=http://localhost:5173
spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.security.cors.allowed-headers=*
spring.security.cors.allow-credentials=true

# ユーザー識別設定
user-identification.enabled=true
user-identification.log-access=true
user-identification.include-groups=true

# ログ設定（識別用）
logging.level.com.example.adauth=INFO
logging.level.waffle=WARN
logging.level.org.springframework.security=WARN

# セッション設定（軽量）
server.servlet.session.timeout=30m
server.servlet.session.cookie.secure=false
server.servlet.session.cookie.http-only=true

# 監査ログ設定
audit.log.enabled=true
audit.log.include-ip=true
audit.log.include-user-agent=true
```

### 5. 監査・ログ機能

#### ユーザー識別ログ

```java
@Component
public class UserIdentificationLogger {

    private static final Logger logger = LoggerFactory.getLogger("USER-IDENTIFICATION");
    private static final Logger auditLogger = LoggerFactory.getLogger("USER-AUDIT");

    @EventListener
    public void onUserIdentified(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String clientIP = getClientIP(event);

        logger.info("USER_IDENTIFIED: user={}, ip={}, time={}",
            username, clientIP, Instant.now());

        // 監査ログ
        auditLogger.info("AUDIT: action=user_identification, user={}, ip={}, success=true",
            username, clientIP);
    }

    @EventListener
    public void onIdentificationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String clientIP = getClientIP(event);

        logger.warn("USER_IDENTIFICATION_FAILED: user={}, ip={}, reason={}",
            username, clientIP, event.getException().getMessage());

        auditLogger.warn("AUDIT: action=user_identification, user={}, ip={}, success=false, reason={}",
            username, clientIP, event.getException().getMessage());
    }
}
```

## 実行とテスト

### 1. アプリケーション起動

```bash
# ユーザー識別モードで起動
mvn spring-boot:run -Dspring.profiles.active=user-identification

# デバッグモード
mvn spring-boot:run -Dspring.profiles.active=user-identification -Ddebug=true
```

### 2. 動作確認

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
```

### 3. ログ確認

```bash
# ユーザー識別ログ
INFO  USER-IDENTIFICATION - USER_IDENTIFIED: user=DOMAIN1\user1, ip=10.0.10.20
INFO  USER-IDENTIFICATION - USER_IDENTIFIED: user=DOMAIN2\user2, ip=10.0.20.20

# 監査ログ
INFO  USER-AUDIT - AUDIT: action=user_identification, user=DOMAIN1\user1, ip=10.0.10.20, success=true
INFO  USER-AUDIT - AUDIT: action=user_identification, user=DOMAIN2\user2, ip=10.0.20.20, success=true
```

## セキュリティ考慮事項（大幅簡素化）

### **実際のリスク（認証ではなく識別）**

#### **リスクが大幅軽減される理由**
```
✅ 目的: ユーザー識別のみ
✅ パスワード処理なし
✅ 認証ロジックなし
✅ セキュリティクリティカルな処理なし
```

#### **残る軽微なリスク**
```
⚠️ ユーザーID詐称（端末セキュリティに依存）
⚠️ 通信傍受（HTTPS で対策）
⚠️ ログ情報の漏洩
```

### **最低限の対策**

```yaml
必須対策:
  - HTTPS通信
  - 基本的なログ保護
  - アクセス制御

推奨対策:
  - 定期的なログ監査
  - 異常アクセス検知
```

## 移行手順

### 1. 段階的移行

```bash
# Step 1: 現在のKerberos設定をバックアップ
cp application-kerberos.properties application-kerberos.properties.backup

# Step 2: NTLM設定でテスト環境起動
mvn spring-boot:run -Dspring.profiles.active=user-identification

# Step 3: WIN1とWIN2で動作確認

# Step 4: 本番環境への適用
```

### 2. 設定ファイルの更新

```bash
# Ansible Playbookでの自動化
ansible-playbook -i inventory deploy-linux.yml --extra-vars "auth_method=user-identification"
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

## まとめ

### **ユーザー識別への変更により**

✅ **大幅簡素化**: keytab、SPN、複雑なKerberos設定が不要
✅ **クロスドメイン対応**: 信頼関係のみで両ドメイン対応
✅ **DOMAIN2作業不要**: 海外拠点での作業一切不要
✅ **シームレス**: 完全に透明なユーザー識別
✅ **軽量**: 認証処理がないため高速
✅ **安全**: セキュリティリスクが大幅軽減

### **用途**
- 監査ログ用のユーザー識別
- アクセス制御のためのドメイン・グループ情報取得
- 個人設定の管理
- 利用状況の分析

**目的が「認証」から「識別」に変わることで、実装とセキュリティの両面で大幅に簡素化されます。**