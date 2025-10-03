# 第3章: NTLM認証詳解

## 3.1 概要

NTLM認証は、チャレンジ・レスポンス方式の認証プロトコルです。本章では、NTLM認証の技術仕様と実装方法について解説します。

### 3.1.1 NTLM認証の特徴
1. **設定簡素化**: keytab、SPN登録、krb5.conf設定が不要
2. **クロスドメイン対応**: 信頼関係があれば自動的に両ドメイン対応
3. **DOMAIN2作業不要**: 海外拠点での作業が一切不要
4. **Java 17互換**: RC4暗号化問題を完全回避
5. **自動ログイン**: パスワード入力不要（統合Windows認証）
6. **ユーザー情報取得**: ドメイン、グループ、属性情報を完全取得

## 3.2 認証フローの比較

### Kerberos認証の場合
```
1. クライアント → KDC: TGT要求
2. KDC → クライアント: TGT発行
3. クライアント → KDC: サービスチケット要求（SPN必要）
4. KDC → クライアント: サービスチケット発行
5. クライアント → サーバー: サービスチケット送信
6. サーバー: keytabで検証
```

### NTLM認証の場合
```
1. クライアント → サーバー: Type1メッセージ（認証開始）
2. サーバー → クライアント: Type2メッセージ（チャレンジ）
3. クライアント → サーバー: Type3メッセージ（レスポンス）
4. サーバー → DC: ユーザー検証要求（既存の仕組み）
5. DC → サーバー: 検証結果
```

### 重要な違い
- **Kerberos**: 事前にSPNとkeytabをDCに設定する必要がある
- **NTLM**: DCの既存のユーザー認証機能をそのまま利用

## 3.3 なぜドメインコントローラー設定が不要なのか

### 既存のDC設定で十分な理由
1. **ユーザーアカウント**: 既にDOMAIN1\user1、DOMAIN2\user2が存在
2. **信頼関係**: 既にDOMAIN1⟷DOMAIN2間で設定済み
3. **DNS解決**: 既存のDNS設定で十分
4. **認証プロトコル**: NTLMはWindowsの標準機能

### 必要な確認項目（通常は既に設定済み）
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

## 3.4 実装手順

### 3.4.1 Maven依存関係
```xml
<dependencies>
    <!-- Spring Boot基本 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- NTLM認証（Windows環境用） -->
    <dependency>
        <groupId>com.github.waffle</groupId>
        <artifactId>waffle-spring-boot3</artifactId>
        <version>3.3.0</version>
    </dependency>

    <!-- NTLM認証（Linux環境用） -->
    <dependency>
        <groupId>org.samba.jcifs</groupId>
        <artifactId>jcifs</artifactId>
        <version>2.1.30</version>
    </dependency>
</dependencies>
```

### 3.4.2 Spring Security設定

#### JCIFSによる実装
```java
@Configuration
@EnableWebSecurity
@Profile("ntlm")
public class NtlmSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(jcifsNtlmFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JcifsNtlmAuthenticationFilter jcifsNtlmFilter() {
        JcifsNtlmAuthenticationFilter filter = new JcifsNtlmAuthenticationFilter();
        filter.setDomainController("DC1.DOMAIN1.LAB");
        filter.setDomain("DOMAIN1.LAB");
        filter.setEnableCrossDomainAuth(true);  // 信頼関係使用
        return filter;
    }
}
```

### 3.4.3 ユーザー情報取得

```java
@RestController
@RequestMapping("/api")
public class AuthenticationController {

    @GetMapping("/user")
    public Map<String, Object> getCurrentUser(Authentication auth) {
        if (auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();

            return Map.of(
                "success", true,
                "username", extractUsername(principal.getName()),
                "domain", extractDomain(principal.getName()),
                "roles", List.of("ROLE_USER"),
                "authMethod", "NTLM"
            );
        }

        return Map.of(
            "success", true,
            "username", auth.getName(),
            "roles", List.of("ROLE_USER"),
            "authMethod", "NTLM"
        );
    }

    private String extractUsername(String fqn) {
        return fqn.contains("\\\\") ? fqn.split("\\\\\\\\")[1] : fqn;
    }

    private String extractDomain(String fqn) {
        return fqn.contains("\\\\") ? fqn.split("\\\\\\\\")[0] : "UNKNOWN";
    }
}
```

### 3.4.4 設定ファイル

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

# セッション設定
server.servlet.session.timeout=30m
```

## 3.5 クライアント側設定

### ブラウザ設定（Kerberos と同じ）
NTLMでも統合Windows認証を使用するため、ブラウザ設定はKerberosと同じです：

```yaml
# Edge/Chrome設定（グループポリシーまたはレジストリ）
- name: Enable integrated Windows authentication
  win_regedit:
    path: 'HKCU:\\SOFTWARE\\Policies\\Microsoft\\Edge'
    name: AuthServerAllowlist
    data: "{{ alb_dns_name }}"
    type: string

- name: Enable automatic logon for Intranet zone
  win_regedit:
    path: 'HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\\Zones\\1'
    name: '1A00'
    data: 0
    type: dword
```

## 3.6 セキュリティ考慮事項

### NTLMの脆弱性

#### 統合Windows認証で軽減されるリスク
- ❌ パスワード入力時の盗聴
- ❌ キーロガーによるパスワード取得
- ❌ ブルートフォース攻撃
- ❌ レインボーテーブル攻撃
- ❌ フィッシング攻撃（パスワード入力画面なし）

#### 残るリスク
- ⚠️ Pass-the-Hash攻撃（端末セキュリティに依存）
- ⚠️ リプレイ攻撃（HTTPS必須で軽減）
- ⚠️ 中間者攻撃（HTTPS必須で軽減）
- ⚠️ 端末の物理的盗難

### 必須のセキュリティ対策

#### ネットワークレベル
```yaml
必須設定:
  - HTTPS強制 (TLS 1.2以上)
  - ALB/WAFでのHTTPSリダイレクト
  - 社内ネットワーク限定アクセス
  - VPN経由のアクセス制御
```

#### Active Directory設定
```powershell
# NTLMv1を無効化（NTLMv2強制）
Set-ItemProperty -Path "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Lsa" `
  -Name "LmCompatibilityLevel" -Value 5

# LM Hash保存を無効化
Set-ItemProperty -Path "HKLM:\\SYSTEM\\CurrentControlSet\\Control\\Lsa" `
  -Name "NoLMHash" -Value 1

# 強固なパスワードポリシー
Set-ADDefaultDomainPasswordPolicy -Identity "DOMAIN1.LAB" `
  -MinPasswordLength 12 `
  -PasswordHistoryCount 24 `
  -MaxPasswordAge "90.00:00:00" `
  -LockoutThreshold 5
```

#### アプリケーションレベル
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .headers(headers -> headers
            .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                .maxAgeInSeconds(31536000)  // 1年
                .includeSubdomains(true)
            )
        )
        .sessionManagement(session -> session
            .maximumSessions(1)  // 同時セッション制限
        );

    return http.build();
}
```

### セキュリティリスクの評価

NTLM認証のセキュリティリスクは、運用環境により大きく異なります：

**内部ネットワーク環境の場合**:
- HTTPS通信の使用
- 物理的にセキュアな環境
- ネットワーク監視体制
- 定期的なパスワード変更
- VPN経由のアクセス制御

**インターネット経由の場合**:
- より高度なセキュリティ対策が必要
- Kerberosやモダンな認証方式の検討を推奨

具体的なセキュリティ要件については、システムの用途・データの機密性・コンプライアンス要件を考慮して判断してください。

## 3.7 テストと検証

### 基本認証テスト
```bash
# WIN1からのテスト
curl -v --negotiate -u : https://alb.amazonaws.com/api/user

# WIN2からのテスト（クロスドメイン）
curl -v --negotiate -u : https://alb.amazonaws.com/api/user

# 期待される結果（両方とも成功）
{
  "success": true,
  "username": "user1",  // or "user2"
  "domain": "DOMAIN1",  // or "DOMAIN2"
  "roles": ["ROLE_USER"],
  "authMethod": "NTLM"
}
```

### ログ確認
```bash
# 認証成功ログ
INFO  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication successful for DOMAIN1\\user1
INFO  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication successful for DOMAIN2\\user2

# 認証失敗時のログ
WARN  c.e.a.s.NtlmAuthenticationFilter - NTLM authentication failed for user: unknown
ERROR c.e.a.s.NtlmAuthenticationFilter - Domain controller unreachable: DC1.DOMAIN1.LAB
```

## 3.8 トラブルシューティング

### よくある問題と解決策

**問題1: 401エラーが繰り返し発生**
```
原因: NTLMハンドシェイクの失敗
解決: ログでType1/Type2/Type3メッセージの流れを確認
```

**問題2: DOMAIN2ユーザーが認証できない**
```
原因: 信頼関係の設定不備
解決: nltest /domain_trusts で信頼関係を確認
```

**問題3: ユーザー情報が取得できない**
```
原因: WindowsPrincipalの取得失敗
解決: Waffleライブラリのバージョン確認
```

**問題4: Linux環境でNTLM認証失敗**
```
原因: JCIFSの設定不備
解決: jcifs.propertiesの設定確認
```

## 3.9 まとめ

NTLM認証の技術的特徴：

**実装上の特徴**:
- keytab、SPN登録、krb5.conf設定が不要
- 信頼関係があれば複数ドメインに対応可能
- 統合Windows認証による自動ログイン
- ドメイン、ユーザー、グループ情報の取得が可能

**Kerberosとの主な違い**:
- 設定がシンプル（ドメインコントローラー側の追加設定不要）
- 認証毎にDC通信が発生（チケットキャッシュなし）
- セキュリティレベルはKerberosより低い

NTLM認証の採用にあたっては、セキュリティ要件・運用環境・実装コストなどを総合的に検討してください。

## 次の章へ

- [第4章: クロスドメイン認証](./04_CROSS_DOMAIN.md) - クロスドメイン環境での認証課題と5つの解決策
- [第5章: 環境別セットアップ](./05_SETUP.md) - 実際の環境構築手順
- [第6章: トラブルシューティング](./06_TROUBLESHOOTING.md) - 一般的な問題と解決策
