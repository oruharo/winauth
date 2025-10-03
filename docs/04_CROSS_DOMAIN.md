# 第4章: クロスドメイン認証

## 4.1 クロスドメイン認証の技術的課題

### 検証環境の構成
- **DOMAIN1.LAB**: プライマリドメイン
  - DC1 (Domain Controller)
  - WIN1 (Windows Client)
  - Linux App Server (Spring Boot + Kerberos)
- **DOMAIN2.LAB**: セカンダリドメイン
  - DC2 (Domain Controller)
  - WIN2 (Windows Client)
- **信頼関係**: 双方向の信頼関係
- **ALB**: AWS Application Load Balancer (has-winauth-alb-xxx.elb.amazonaws.com)

### クロスドメイン環境における認証の仕組み

クロスドメイン環境でWindows統合認証を実現する場合、以下の技術的な特性を理解する必要があります：

**単一ドメイン環境（DOMAIN1ユーザー）**:
- クライアントは自ドメインのKDCからサービスチケットを取得
- SPNが自ドメインに登録されている場合、Kerberos認証が成功

**クロスドメイン環境（DOMAIN2ユーザー）**:
- クライアントは自ドメイン（DOMAIN2）のKDCに接続
- サービスのSPNがDOMAIN1にのみ登録されている場合、DOMAIN2のKDCはサービスチケットを発行できない
- クライアントはKerberos認証にフォールバックし、NTLM認証を試行
- サーバー側でNTLM処理が実装されていない場合、認証が失敗

## 4.2 技術的制約

### SPNの制約
- SPNはActive Directoryフォレスト内で一意である必要がある
- 同じSPN（例：HTTP/alb.amazonaws.com）を複数ドメインに登録不可
- この制約がクロスドメイン環境でのKerberos認証における主要な課題

### Java 17とRC4暗号化
Java 17ではRC4暗号化がデフォルトで無効化されています。詳細とその対応方法については、[第2章2.4節「Java 17とRC4暗号化対応」](./02_KERBEROS.md#24-java-17とrc4暗号化対応)を参照してください。

### コンテナ環境の制約
- コンテナ環境はWindowsドメインに参加していない
- Keytabファイルでの認証に依存
- ドメイン参加なしでのクロスドメイン認証には特別な考慮が必要

## 4.3 クロスドメイン認証の実装方式

### 方式1: 両ドメインKeytab統合

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

**特徴**:
- 両ドメインからKerberos認証可能
- パフォーマンスが良い
- DOMAIN2側での作業が必要
- 海外拠点の協力が必要

### 方式2: リバースプロキシ分離

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

**特徴**:
- 完全に独立した認証
- SPN競合なし
- DOMAIN2側での作業が必要（DC2でのSPN登録とkeytab作成）
- 海外拠点の協力が必要
- インフラコスト2倍
- 管理が複雑

### 方式3: NTLM認証の使用

**概要**: KerberosではなくNTLM認証を使用

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

**特徴**:
- DOMAIN2側の作業不要
- 設定がシンプル（keytab、SPN不要）
- 両ドメインから自動認証可能
- Java 17のRC4問題を回避
- ユーザー情報も完全に取得可能
- Kerberosより古いプロトコル
- セキュリティレベルはKerberosより低い
- 毎回DC認証が発生（チケットキャッシュなし）

### 方式4: WIN2のコンピューターアカウントをDOMAIN1に追加

**概要**: WIN2自体はDOMAIN2に所属したまま、そのコンピューターアカウントをDOMAIN1にも登録

**実装手順**:
```powershell
# DC1で実行（DOMAIN1側のみ）
New-ADComputer -Name "WIN2" -SAMAccountName "WIN2$" -Enabled $true -Description "Cross-domain access for WIN2"
```

**特徴**:
- WIN2のドメイン変更不要
- DOMAIN2側の作業不要
- 理論的には可能だが実際には動作しない可能性が高い
- コンピューター信頼関係が複雑
- エラー: `0xc000018b` (The SAM database on the Windows Server does not have a computer account)
- Active Directoryの設計上、1つのコンピューターは1つのドメインにのみ参加可能

### 方式5: プロトコル遷移（Protocol Transition/S4U2Self）

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

**特徴**:
- DOMAIN2側の作業完全不要
- 高度なセキュリティ機能
- Microsoftの推奨アプローチの一つ
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

## 4.4 実装方式の比較

| 実装方式 | DOMAIN2作業 | 実装難易度 | 動作確実性 | セキュリティ |
|---------|------------|-----------|-----------|-------------|
| 1. 両ドメインKeytab | 必要 | 中 | 高 | 高 |
| 2. リバースプロキシ分離 | 必要 | 中 | 高 | 高 |
| 3. NTLM認証 | 不要 | 低 | 高 | 中 |
| 4. WIN2アカウント追加 | 不要 | 低 | 低 | - |
| 5. プロトコル遷移 | 不要 | 高 | 中 | 高 |

## 4.5 選択の考慮事項

各実装方式の選択にあたっては、以下の要素を総合的に評価してください：

### 検討要素
- **海外拠点との協力体制**: DOMAIN2での作業が可能か
- **実装期間**: 短期間で構築する必要があるか
- **セキュリティ要件**: システムが扱うデータの機密性
- **運用環境**: 社内ネットワーク限定か、インターネット経由のアクセスがあるか
- **技術スキル**: チームの技術スキルレベル
- **将来の拡張性**: 他の認証方式への移行可能性

### 選択パターンの例
1. **海外拠点との協力が容易 + セキュリティ重視**: 両ドメインKeytab統合
2. **海外拠点との協力が困難 + 迅速な実装が必要**: NTLM認証
3. **高度なセキュリティとDOMAIN2作業回避の両立**: プロトコル遷移
4. **将来的なクラウド移行を見据える場合**: OAuth2/SAML への移行検討

## 4.6 検証時の注意事項

### クロスドメイン認証の検証
クロスドメイン環境で認証の動作を検証する際は、以下の点に注意してください：

**Kerberos認証の場合**:
- SPNの登録状況を確認（各ドメインのDCで `setspn -L` を実行）
- Keytabファイルに両ドメインのエントリが含まれているか確認（`klist -k` を実行）
- クライアント側でKerberosチケットが正しく取得されているか確認（`klist` を実行）

**NTLM認証の場合**:
- 信頼関係が正しく設定されているか確認（`nltest /domain_trusts` を実行）
- サーバー側でNTLMハンドシェイクが正常に動作しているかログで確認

**共通事項**:
- ネットワーク疎通の確認（ファイアウォール、ポート開放）
- DNS名前解決の確認
- 認証ログの詳細な分析

## 4.7 まとめ

クロスドメイン環境でのWindows統合認証には、本章で紹介した5つのアプローチがあります。それぞれに技術的特徴とトレードオフがあるため、プロジェクトの要件に応じて適切な方式を選択してください。

本プロジェクトでは、これらの認証方式の実装方法と動作検証を通じて、クロスドメイン環境におけるSSO実現の技術的課題と解決策を明らかにすることを目的としています。