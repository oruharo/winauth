# クロスドメイン認証ガイド

## 問題の概要

### 環境構成
- **DOMAIN1.LAB**: 本社ドメイン
  - DC1 (Domain Controller)
  - WIN1 (Windows Client)
  - Linux App Server (Spring Boot + Kerberos)
- **DOMAIN2.LAB**: 海外拠点ドメイン
  - DC2 (Domain Controller)
  - WIN2 (Windows Client)
- **信頼関係**: 双方向の信頼関係あり
- **ALB**: AWS Application Load Balancer (has-winauth-alb-xxx.elb.amazonaws.com)

### 発生した問題
1. WIN1 (DOMAIN1) → ALB → 認証成功 ✅
2. WIN2 (DOMAIN2) → ALB → 500エラー ❌

### 根本原因
- WIN2がDOMAIN2.LABに所属
- ALBのSPN (Service Principal Name) がDOMAIN1.LABにのみ登録
- WIN2がKerberosチケット取得に失敗し、NTLMトークンを送信
- Spring BootサーバーがNTLMトークンを処理できず500エラー

## 技術的制約

### SPNの制約
- SPNはActive Directoryフォレスト内で一意である必要がある
- 同じSPN（例：HTTP/alb.amazonaws.com）を複数ドメインに登録不可
- クロスドメイン環境でのKerberos認証に制限

### Java 17とRC4暗号化
- Java 17ではRC4暗号化がデフォルトで無効化
- Windows環境ではRC4がまだ使用される場合がある
- 設定変更が必要：
  ```properties
  # krb5.conf
  allow_weak_crypto = true

  # JVM引数
  -Djava.security.krb5.allow_weak_crypto=true
  ```

### Linuxサーバーの制約
- LinuxサーバーはWindowsドメインに参加していない
- Keytabファイルでの認証に依存
- ドメイン参加なしでのクロスドメイン認証が困難

## 解決策オプション

### オプション1: 両ドメインKeytab統合

**概要**: 両ドメインのSPNを含む統合keytabを作成

**実装手順**:
1. DOMAIN1でサービスアカウント作成
   ```powershell
   # DC1で実行
   New-ADUser -Name svcapp -AccountPassword (ConvertTo-SecureString "Pass123!" -AsPlainText -Force)
   setspn -A HTTP/alb.amazonaws.com svcapp
   ktpass -princ HTTP/alb.amazonaws.com@DOMAIN1.LAB -mapuser svcapp@DOMAIN1.LAB -out domain1.keytab
   ```

2. DOMAIN2でサービスアカウント作成（海外拠点での作業必要）
   ```powershell
   # DC2で実行
   New-ADUser -Name svcapp2 -AccountPassword (ConvertTo-SecureString "Pass123!" -AsPlainText -Force)
   setspn -A HTTP/alb.amazonaws.com svcapp2
   ktpass -princ HTTP/alb.amazonaws.com@DOMAIN2.LAB -mapuser svcapp2@DOMAIN2.LAB -out domain2.keytab
   ```

3. Keytabマージ
   ```bash
   ktutil
   rkt domain1.keytab
   rkt domain2.keytab
   wkt merged.keytab
   quit
   ```

**メリット**:
- 両ドメインからKerberos認証可能
- パフォーマンスが良い

**デメリット**:
- DOMAIN2側での作業が必要
- 海外拠点の協力が必要

### オプション2: リバースプロキシ分離

**概要**: ドメインごとに異なるALBとSPNを使用

**アーキテクチャ**:
```
WIN1 → ALB1 (alb1.amazonaws.com) → Backend
WIN2 → ALB2 (alb2.amazonaws.com) → Backend
```

**実装**:
1. 2つのALBを作成
2. 各ドメインで異なるSPNを登録
   - DOMAIN1: HTTP/alb1.amazonaws.com
   - DOMAIN2: HTTP/alb2.amazonaws.com

**メリット**:
- 完全に独立した認証
- SPN競合なし

**デメリット**:
- DOMAIN2側での作業が必要（DC2でのSPN登録とkeytab作成）
- 海外拠点の協力が必要
- インフラコスト2倍
- 管理が複雑

### オプション3: NTLM認証への統一 ⭐推奨

**概要**: KerberosではなくNTLM認証を使用（自動ログイン可能）

**実装手順**:

1. Spring Bootの依存関係追加
   ```xml
   <dependency>
       <groupId>com.github.waffle</groupId>
       <artifactId>waffle-spring-boot3</artifactId>
       <version>3.3.0</version>
   </dependency>
   ```

2. Spring Security設定
   ```java
   @Configuration
   @EnableWebSecurity
   public class NtlmAuthConfig {
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) {
           http
               .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
               .addFilterBefore(negotiateFilter(), BasicAuthenticationFilter.class);
           return http.build();
       }

       @Bean
       public NegotiateSecurityFilter negotiateFilter() {
           NegotiateSecurityFilter filter = new NegotiateSecurityFilter();
           filter.setProvider(new WindowsAuthProviderImpl());
           return filter;
       }
   }
   ```

3. ユーザー情報取得
   ```java
   @RestController
   public class UserController {
       @GetMapping("/api/whoami")
       public Map<String, String> getCurrentUser(Authentication auth) {
           return Map.of(
               "username", auth.getName(),        // "user1" or "user2"
               "domain", extractDomain(auth),     // "DOMAIN1" or "DOMAIN2"
               "authenticated", "true"
           );
       }
   }
   ```

**メリット**:
- DOMAIN2側の作業不要
- 設定がシンプル（keytab、SPN不要）
- 両ドメインから自動認証可能
- Java 17のRC4問題を回避
- ユーザー情報も完全に取得可能

**デメリット**:
- Kerberosより古いプロトコル
- セキュリティ的にKerberosより劣る
- 毎回DC認証（キャッシュなし）

### オプション4: WIN2のコンピューターアカウントをDOMAIN1に追加

**概要**: WIN2自体はDOMAIN2に所属したまま、そのコンピューターアカウントをDOMAIN1にも登録

**実装手順**:
```powershell
# DC1で実行（DOMAIN1側のみ）
New-ADComputer -Name "WIN2" -SAMAccountName "WIN2$" -Enabled $true -Description "Cross-domain access for WIN2"
```

**メリット**:
- WIN2のドメイン変更不要
- DOMAIN2側の作業不要
- 理論的には可能

**デメリット**:
- 実際には動作しない可能性が高い
- コンピューター信頼関係が複雑
- エラー: `0xc000018b` (The SAM database on the Windows Server does not have a computer account)
- Active Directoryの設計上、1つのコンピューターは1つのドメインにのみ参加可能

### オプション5: プロトコル遷移（Protocol Transition/S4U2Self）

**概要**: DOMAIN1のサービスアカウントにプロトコル遷移権限を付与し、NTLMトークンをKerberosに変換

**実装手順**:
```powershell
# DC1での設定（DOMAIN1のみ）
# 1. svcappにプロトコル遷移権限を付与
Set-ADAccountControl -Identity svcapp -TrustedToAuthForDelegation $true

# 2. ユーザー偽装権限を設定
Set-ADUser -Identity svcapp -Add @{
    'userAccountControl' = 0x1000000  # TRUSTED_TO_AUTH_FOR_DELEGATION
}

# 3. SPNはDOMAIN1のみでOK
setspn -A HTTP/alb.elb.amazonaws.com svcapp
```

**動作原理**:
```
1. WIN2 → ALB（NTLMトークン送信）
   ↓
2. Spring Boot → DC1（NTLMをKerberosに変換要求）
   ↓
3. DC1 → 信頼関係経由でDOMAIN2ユーザー検証
   ↓
4. DC1 → Kerberosチケット発行（S4U2Self）
```

**Spring Boot実装例**:
```java
@Component
public class ProtocolTransitionHandler {

    @Autowired
    private KerberosTicketValidator validator;

    public Authentication handleNtlmWithS4U(String ntlmToken) {
        // NTLMトークンからユーザー情報抽出
        WindowsPrincipal ntlmPrincipal = parseNtlmToken(ntlmToken);

        // S4U2Selfでユーザー代理チケット取得
        GSSCredential credential = GSSManager.getInstance()
            .createCredential(
                null,
                GSSCredential.DEFAULT_LIFETIME,
                new Oid("1.2.840.113554.1.2.2"),
                GSSCredential.INITIATE_ONLY
            );

        // プロトコル遷移でKerberosトークン生成
        GSSContext context = manager.createContext(credential);
        byte[] kerberosToken = context.initSecContext(ntlmToken.getBytes(), 0, ntlmToken.length());

        // Kerberos認証として処理
        return new KerberosServiceRequestToken(kerberosToken);
    }
}
```

**メリット**:
- DOMAIN2側の作業完全不要
- 高度なセキュリティ機能
- Microsoftの推奨アプローチ

**デメリット**:
- 設定が複雑
- デバッグが困難
- Linuxでの実装が難しい
- 適切な権限設定が必要

**制約付き委任（Constrained Delegation）との組み合わせ**:
プロトコル遷移と合わせて、制約付き委任を設定することでより安全な委任が可能：

```powershell
# DC1で実行
Set-ADUser -Identity svcapp -Add @{
    'msDS-AllowedToDelegateTo' = @(
        'HTTP/alb.amazonaws.com',
        'HTTP/alb.amazonaws.com@DOMAIN1.LAB',
        'HTTP/alb.amazonaws.com@DOMAIN2.LAB'
    )
}
```

この設定により、svcappは指定されたサービスに対してのみ委任権限を持ち、無制限な委任を防ぐことができます。

## 解決策の比較表

| オプション | DOMAIN2作業 | 実装難易度 | 動作確実性 | セキュリティ | 推奨度 |
|-----------|------------|-----------|-----------|-------------|--------|
| 1. 両ドメインKeytab | 必要 | 中 | 高 | 高 | ★★★ |
| 2. リバースプロキシ分離 | 必要 | 中 | 高 | 高 | ★★ |
| 3. NTLM認証統一 | 不要 | 低 | 高 | 中 | ★★★★★ |
| 4. WIN2アカウント追加 | 不要 | 低 | 低 | - | ★ |
| 5. プロトコル遷移 | 不要 | 高 | 中 | 高 | ★★★ |

## 推奨事項

### 短期的解決策
**NTLM認証への移行（オプション3）**を推奨
- 実装が簡単
- DOMAIN2側の作業不要
- 自動ログイン可能（パスワード入力不要）
- 両ドメインのユーザー情報取得可能
- 実績のある安定した動作

### 長期的解決策
セキュリティ要件に応じて以下を検討：
1. セキュリティ重視 → Kerberos + 両ドメインkeytab
2. 運用簡素化重視 → NTLM継続
3. クラウドネイティブ → OAuth2/SAML移行

## トラブルシューティング

### 問題: WIN2から500エラー
**原因**: NTLMトークンをサーバーが処理できない
**解決**: Spring BootにNTLMサポート追加

### 問題: Kerberosチケット取得失敗
**エラー**: `0xc000018b` (The SAM database on the Windows Server does not have a computer account)
**原因**: WIN2のコンピューターアカウントがDOMAIN1に存在しない
**解決**: NTLM使用またはWIN2をDOMAIN1に参加

### 問題: Java 17でRC4エラー
**エラー**: "Encryption type RC4 with HMAC is not supported/enabled"
**解決**:
```properties
# krb5.conf
allow_weak_crypto = true

# JVM引数
-Djava.security.krb5.allow_weak_crypto=true
-Dsun.security.krb5.disableReferrals=true
```

## まとめ

クロスドメイン環境でのWindows統合認証は複雑ですが、要件に応じて適切な解決策を選択できます：

1. **海外拠点の協力が得られる場合**: 両ドメインkeytab統合
2. **海外拠点の協力が困難な場合**: NTLM認証（推奨）
3. **セキュリティ要件が緩い場合**: NTLM認証で統一
4. **将来的なクラウド移行を考慮**: OAuth2/SAMLへの段階的移行

最も実用的なのは**NTLM認証**で、設定が簡単で両ドメインから自動認証が可能です。