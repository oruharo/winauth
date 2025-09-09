# Kerberos/SPNEGO設定手順（ドメイン参加クライアント + Linux非ドメインサーバー）

## 概要

この設定により、以下が実現できます：
- **クライアント**: Windowsドメイン参加済み、ID/パスワード入力不要
- **サーバー**: Linux、ドメイン非参加、SPNEGOでWindows認証を受け取り

> **技術的な詳細**: Kerberos認証の仕組みや実装の技術仕様については [kerberos-authentication-guide.md](./kerberos-authentication-guide.md) を参照してください。

## 1. Active Directory設定（ドメイン管理者が実施）

### 1.1 サービスプリンシパル名（SPN）の作成
```cmd
# ADドメインコントローラーで実行
setspn -A HTTP/your-linux-server.domain.com adauth-service-account
setspn -A HTTP/your-linux-server adauth-service-account
```

### 1.2 Keytabファイルの生成
```cmd
# ADドメインコントローラーで実行
ktpass -princ HTTP/your-linux-server.domain.com@DOMAIN.COM ^
       -mapuser adauth-service-account@DOMAIN.COM ^
       -crypto AES256-SHA1 ^
       -ptype KRB5_NT_PRINCIPAL ^
       -pass ServiceAccountPassword ^
       -out adauth.keytab
```

### 1.3 KeytabファイルをLinuxサーバーに配置
```bash
# Linuxサーバーで実行
sudo cp adauth.keytab /etc/krb5.keytab
sudo chmod 600 /etc/krb5.keytab
sudo chown root:root /etc/krb5.keytab
```

## 2. Linuxサーバー設定

### 2.1 Kerberos設定ファイル作成
```bash
sudo vi /etc/krb5.conf
```

内容：
```ini
[libdefaults]
    default_realm = DOMAIN.COM
    dns_lookup_realm = false
    dns_lookup_kdc = false
    ticket_lifetime = 24h
    renew_lifetime = 7d
    forwardable = true

[realms]
    DOMAIN.COM = {
        kdc = dc.domain.com
        admin_server = dc.domain.com
        default_domain = domain.com
    }

[domain_realm]
    .domain.com = DOMAIN.COM
    domain.com = DOMAIN.COM

[logging]
    default = FILE:/var/log/krb5libs.log
    kdc = FILE:/var/log/krb5kdc.log
    admin_server = FILE:/var/log/kadmind.log
```

### 2.2 DNS設定（重要）
```bash
# /etc/hosts に追加（または適切なDNS設定）
echo "192.168.1.10  dc.domain.com" >> /etc/hosts
echo "192.168.1.20  your-linux-server.domain.com" >> /etc/hosts
```

### 2.3 時刻同期設定
```bash
# NTPでADドメインコントローラーと時刻同期
sudo ntpdate dc.domain.com
sudo systemctl enable ntp
```

## 3. Spring Boot設定

### 3.1 application-kerberos.properties編集
```properties
# 実際の値に置き換え
kerberos.principal=HTTP/your-linux-server.domain.com@DOMAIN.COM
kerberos.keytab=/etc/krb5.keytab

ad.domain=DOMAIN.COM
ad.url=ldap://dc.domain.com:389

java.security.krb5.realm=DOMAIN.COM
java.security.krb5.kdc=dc.domain.com
```

## 4. 認証フローの詳細

### 4.1 完全なKerberos認証シーケンス図（OS認証含む）

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Win as Windows OS<br/>(クライアントPC)
    participant Chrome as Chrome<br/>ブラウザ
    participant React as React Client<br/>(localhost:5173)
    participant Vite as Vite Proxy
    participant Spring as Spring Boot<br/>(localhost:8082)
    participant KDC as Kerberos KDC<br/>(AD Domain Controller)
    participant LDAP as LDAP Server<br/>(Active Directory)

    Note over User,LDAP: 前提: ユーザーはWindowsにドメインアカウントでログイン済み

    %% 1. Windowsドメインログイン（既に完了）
    rect rgb(240, 240, 255)
        Note over User,KDC: Phase 1: Windows OS認証（起動時に完了済み）
        User->>Win: ドメイン\ユーザー名 + パスワード
        Win->>KDC: AS-REQ (Authentication Server Request)
        KDC->>KDC: ユーザー認証
        KDC-->>Win: AS-REP + TGT (Ticket Granting Ticket)
        Win->>Win: TGTをキャッシュ（ログオンセッション）
        Note over Win: Windows認証完了<br/>TGTがメモリに保存される
    end

    %% 2. ブラウザでの認証開始
    rect rgb(255, 240, 240)
        Note over User,Spring: Phase 2: ブラウザでの認証開始
        User->>Chrome: ブラウザ起動
        Chrome->>React: http://localhost:5173 アクセス
        React-->>Chrome: 認証画面表示
        User->>Chrome: "Kerberos認証" ボタンクリック
        Chrome->>React: handleKerberosAuth() 実行
    end

    %% 3. 初回リクエストとSPNEGOネゴシエーション
    rect rgb(240, 255, 240)
        Note over Chrome,Spring: Phase 3: SPNEGOネゴシエーション開始
        React->>Vite: GET /api/user (withCredentials: true)
        Vite->>Spring: GET /user (プロキシ)
        
        Note over Spring: SpnegoAuthenticationProcessingFilter
        Spring-->>Vite: 401 Unauthorized<br/>WWW-Authenticate: Negotiate
        Vite-->>React: 401応答
        React-->>Chrome: 認証チャレンジ
        
        Note over Chrome: Negotiate認証開始<br/>Windows統合認証を使用
    end

    %% 4. Service Ticket取得
    rect rgb(255, 255, 240)
        Note over Chrome,KDC: Phase 4: Service Ticket取得
        Chrome->>Win: Negotiate認証要求<br/>(SSPI API呼び出し)
        Win->>Win: キャッシュされたTGTを確認
        Win->>KDC: TGS-REQ (Ticket Granting Service Request)<br/>Target: HTTP/linux-server.domain.com@DOMAIN.COM
        KDC->>KDC: TGT検証 + Service Principal確認
        KDC-->>Win: TGS-REP + Service Ticket
        Win-->>Chrome: Service Ticket (Kerberosトークン)
    end

    %% 5. 認証付きリクエスト
    rect rgb(240, 255, 255)
        Note over Chrome,Spring: Phase 5: 認証付きリクエスト送信
        Chrome->>React: Service Ticketを含む認証情報
        React->>Vite: GET /api/user<br/>Authorization: Negotiate <base64-token>
        Vite->>Spring: GET /user<br/>Authorization: Negotiate <base64-token>
        
        Note over Spring: SunJaasKerberosTicketValidator
        Spring->>Spring: Service Ticket検証<br/>(Keytabで復号化)
        Spring->>Spring: ユーザープリンシパル抽出<br/>(user@DOMAIN.COM)
    end

    %% 6. ユーザー情報取得（オプション）
    rect rgb(255, 240, 255)
        Note over Spring,LDAP: Phase 6: ユーザー詳細情報取得（オプション）
        Spring->>LDAP: LDAP検索<br/>(&(sAMAccountName=user))
        LDAP->>LDAP: ユーザー属性検索
        LDAP-->>Spring: ユーザー詳細情報<br/>(displayName, email, memberOf等)
    end

    %% 7. 認証完了とレスポンス
    rect rgb(240, 240, 240)
        Note over Spring,User: Phase 7: 認証完了とレスポンス
        Spring->>Spring: Spring Security Context設定<br/>KerberosAuthenticationToken作成
        Spring-->>Vite: 200 OK + JSON<br/>{success: true, username: "user@DOMAIN.COM", roles: [...]}
        Vite-->>React: 認証成功レスポンス
        React->>React: 認証結果をstate更新
        React-->>Chrome: ユーザー情報画面表示
        Chrome-->>User: 認証完了（ID/パスワード入力なし）
    end

    Note over User,LDAP: 完了: ユーザーは何も入力せずに自動認証された
```

### 4.2 Windows OS内でのKerberos処理詳細

```mermaid
sequenceDiagram
    participant LSA as LSA<br/>(Local Security Authority)
    participant Winlogon as Winlogon Service
    participant SSPI as SSPI<br/>(Security Support Provider Interface)
    participant KerbSSP as Kerberos SSP<br/>(Security Support Provider)
    participant Cache as Ticket Cache<br/>(メモリ)
    participant KDC as KDC<br/>(Domain Controller)

    Note over LSA,KDC: Windows起動時のドメインログイン処理

    %% Windows起動時の認証
    rect rgb(245, 245, 255)
        Note over LSA,Cache: ドメインログオン時（PC起動時）
        Winlogon->>LSA: ユーザー認証要求
        LSA->>KerbSSP: Kerberos認証開始
        KerbSSP->>KDC: AS-REQ (Initial Authentication)
        KDC-->>KerbSSP: AS-REP + TGT
        KerbSSP->>Cache: TGTをキャッシュに保存
        Cache-->>LSA: 認証成功
        LSA-->>Winlogon: ログオン成功
    end

    %% ブラウザからの要求時
    rect rgb(255, 245, 245)
        Note over LSA,Cache: ブラウザからのNegotiate認証要求時
        Chrome->>SSPI: InitializeSecurityContext()<br/>(Negotiate)
        SSPI->>KerbSSP: Kerberos認証要求
        KerbSSP->>Cache: キャッシュされたTGTを取得
        Cache-->>KerbSSP: TGT
        KerbSSP->>KDC: TGS-REQ (Service Ticket要求)
        KDC-->>KerbSSP: TGS-REP + Service Ticket
        KerbSSP-->>SSPI: Service Ticket
        SSPI-->>Chrome: GSS-APIトークン<br/>(base64エンコード済み)
    end
```

### 4.3 トラブルシューティング：OS認証レベル

#### Windows認証状態の確認
```cmd
# 現在のKerberosチケット一覧
klist

# TGT（Ticket Granting Ticket）の確認
klist tgt

# 特定サービス用チケットの確認
klist get HTTP/linux-server.domain.com

# Kerberosログの有効化（レジストリ設定後、再起動が必要）
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Lsa\Kerberos\Parameters" /v LogLevel /t REG_DWORD /d 1
```

#### SSPI/GSS-API レベルの問題
- **ブラウザのデベロッパーツール**で以下を確認：
  1. 最初のリクエスト: Authorization ヘッダーなし → 401
  2. 2回目のリクエスト: Authorization: Negotiate YII... → 200

- **エラーの場合の一般的なパターン**：
  - "No credentials available": TGTが期限切れ
  - "The target principal name is incorrect": SPN設定エラー  
  - "Clock skew too great": 時刻同期エラー

## 5. 起動とテスト

### 5.1 サーバー起動
```bash
cd /Users/hashiro/nodeprj/winauth/server
./mvnw spring-boot:run -Dspring.profiles.active=kerberos
```

### 5.2 クライアント起動
```bash
cd /Users/hashiro/nodeprj/winauth/client
npm run dev
```

### 5.3 テスト手順
1. ブラウザで `http://localhost:5173` にアクセス
2. "Kerberos認証 (kerberosプロファイル - ポート8082)" を選択
3. "Windows統合認証（Kerberos）" ボタンをクリック
4. ユーザー情報が自動的に表示されることを確認

## 6. トラブルシューティング

### 6.1 よくあるエラー

#### "GSSException: No valid credentials provided"
- Keytabファイルのパスと権限を確認
- SPNが正しく設定されているか確認

#### "Clock skew too great"
- Linuxサーバーの時刻をADドメインコントローラーと同期

#### "Server not found in Kerberos database"
- DNS設定を確認
- /etc/hosts にドメインコントローラーのエントリを追加

### 6.2 デバッグ用コマンド

```bash
# Keytabファイルの内容確認
klist -k /etc/krb5.keytab

# Kerberosチケットの取得テスト
kinit -k -t /etc/krb5.keytab HTTP/your-linux-server.domain.com@DOMAIN.COM

# チケットの確認
klist

# ログの確認
tail -f /var/log/krb5libs.log
```

### 6.3 JVMシステムプロパティ（デバッグ用）
サーバー起動時に以下を追加：
```bash
./mvnw spring-boot:run -Dspring.profiles.active=kerberos \
  -Dsun.security.krb5.debug=true \
  -Djava.security.debug=gssloginconfig,configfile,configparser,logincontext
```

## 7. セキュリティ考慮事項

1. **Keytabファイルの保護**
   - 600権限で保護
   - 定期的なローテーション

2. **HTTPSの使用**
   - 本番環境では必須
   - SSL証明書の適切な設定

3. **ファイアウォール設定**
   - 必要なポートのみ開放
   - 88/tcp, 88/udp (Kerberos)
   - 389/tcp (LDAP)

## 8. 本番環境への適用

1. **負荷分散**
   - 複数サーバーで同じKeytabを共有可能
   - SPNは各サーバーごとに作成

2. **監視**
   - Kerberosチケットの有効期限監視
   - 認証失敗ログの監視

3. **バックアップ**
   - Keytabファイルのバックアップ
   - 設定ファイルのバージョン管理