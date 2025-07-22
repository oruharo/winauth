# Kerberos/SPNEGO設定手順（ドメイン参加クライアント + Linux非ドメインサーバー）

## 概要

この設定により、以下が実現できます：
- **クライアント**: Windowsドメイン参加済み、ID/パスワード入力不要
- **サーバー**: Linux、ドメイン非参加、SPNEGOでWindows認証を受け取り

> **技術的な詳細**: Kerberos認証の仕組みや実装の技術仕様については [kerberos-authentication-guide.md](./kerberos-authentication-guide.md) を参照してください。

## 1. Active Directory設定（ドメイン管理者が実施）

### 1.1 サービスアカウントの作成

#### PowerShellでのサービスアカウント作成
```powershell
# ADドメインコントローラーで実行（管理者権限）
# Active Directory PowerShellモジュールをインポート
Import-Module ActiveDirectory

# サービスアカウント作成
New-ADUser -Name "svc-winauth-k8s" `
  -SamAccountName "svc-winauth-k8s" `
  -UserPrincipalName "svc-winauth-k8s@DOMAIN.COM" `
  -DisplayName "WinAuth K8S Service Account" `
  -Description "Service account for Kubernetes WinAuth application" `
  -Path "OU=ServiceAccounts,DC=domain,DC=com" `
  -AccountPassword (ConvertTo-SecureString "P@ssw0rd123!" -AsPlainText -Force) `
  -Enabled $true `
  -PasswordNeverExpires $true `
  -CannotChangePassword $true

# アカウントの確認
Get-ADUser svc-winauth-k8s -Properties *

# 必要に応じてグループに追加
Add-ADGroupMember -Identity "Domain Users" -Members svc-winauth-k8s
```

#### コマンドプロンプトでのサービスアカウント作成（代替方法）
```cmd
# ADドメインコントローラーで実行
# サービスアカウント作成
dsadd user "CN=svc-winauth-k8s,OU=ServiceAccounts,DC=domain,DC=com" ^
  -samid svc-winauth-k8s ^
  -upn svc-winauth-k8s@DOMAIN.COM ^
  -fn "WinAuth" ^
  -ln "K8S Service" ^
  -display "WinAuth K8S Service Account" ^
  -desc "Service account for Kubernetes WinAuth application" ^
  -pwd P@ssw0rd123! ^
  -pwdneverexpires yes ^
  -disabled no

# アカウントの確認
dsquery user -name svc-winauth-k8s
dsget user "CN=svc-winauth-k8s,OU=ServiceAccounts,DC=domain,DC=com" -display -desc -samid
```

#### GUI（Active Directory ユーザーとコンピューター）での作成
1. **Active Directory ユーザーとコンピューター**を開く
2. **ServiceAccounts** OU（または適切なOU）を右クリック
3. **新規作成** → **ユーザー**
4. 以下を入力：
   - 名: `WinAuth`
   - 姓: `K8S Service`
   - ユーザーログオン名: `svc-winauth-k8s`
   - ユーザーログオン名（Windows 2000より前）: `svc-winauth-k8s`
5. パスワード設定：
   - パスワード: `P@ssw0rd123!`（適切な強度のパスワード）
   - ☑ パスワードを無期限にする
   - ☑ ユーザーはパスワードを変更できない
   - ☐ ユーザーは次回ログオン時にパスワード変更が必要
6. **完了**をクリック

### 1.2 サービスプリンシパル名（SPN）の作成

#### K8S環境用のSPN（Ingress使用）
```cmd
# ADドメインコントローラーで実行
# メインSPN - Ingress経由の外部アクセス用（必須）
setspn -A HTTP/winauth.example.com svc-winauth-k8s

# 追加のIngress用ドメイン（複数ドメインがある場合）
setspn -A HTTP/auth.company.com svc-winauth-k8s

# 確認
setspn -L svc-winauth-k8s

# 重複SPNのチェック（重要）
setspn -X

# --- 以下は参考（K8S内部通信が必要な場合のみ） ---
# K8S Service内部通信用（通常は不要）
# setspn -A HTTP/winauth-service.winauth.svc.cluster.local svc-winauth-k8s
```

#### PowerShellでのSPN設定（代替方法）
```powershell
# SPNを設定
Set-ADUser -Identity svc-winauth-k8s -ServicePrincipalNames @{
    Add="HTTP/winauth-service.winauth.svc.cluster.local",
        "HTTP/winauth.example.com",
        "HTTP/winauth-service"
}

# SPNの確認
Get-ADUser svc-winauth-k8s -Properties ServicePrincipalNames | 
    Select-Object -ExpandProperty ServicePrincipalNames
```

**重要**: Ingress使用時のSPN設定：
- `HTTP/winauth.example.com` - メインSPN（Ingressドメイン）
- `HTTP/auth.company.com` - 追加ドメイン（必要に応じて）

**注意**: K8S内部Service名（`winauth-service.winauth.svc.cluster.local`）のSPNは通常不要です。クライアントはIngress経由でアクセスするため。

### 1.2 Keytabファイルの生成

#### Ingress用のKeytab生成（推奨）
```cmd
# ADドメインコントローラーで実行
# メインドメイン用のKeytab作成
ktpass -princ HTTP/winauth.example.com@DOMAIN.COM ^
       -mapuser svc-winauth-k8s@DOMAIN.COM ^
       -crypto AES256-SHA1 ^
       -ptype KRB5_NT_PRINCIPAL ^
       -pass ServiceAccountPassword ^
       -out winauth.keytab

# 複数ドメインがある場合の追加
ktpass -princ HTTP/auth.company.com@DOMAIN.COM ^
       -mapuser svc-winauth-k8s@DOMAIN.COM ^
       -crypto AES256-SHA1 ^
       -ptype KRB5_NT_PRINCIPAL ^
       -pass ServiceAccountPassword ^
       -out winauth.keytab ^
       -in winauth.keytab

# --- 参考: K8S内部Service用（通常は不要） ---
# ktpass -princ HTTP/winauth-service.winauth.svc.cluster.local@DOMAIN.COM ^
#        -mapuser svc-winauth-k8s@DOMAIN.COM ^
#        -crypto AES256-SHA1 ^
#        -ptype KRB5_NT_PRINCIPAL ^
#        -pass ServiceAccountPassword ^
#        -out winauth-internal.keytab

# 生成されたKeytabの確認（Windows）
ktpass -? | findstr keytab
```

**注意事項**:
- 同じサービスアカウントに複数のSPNを紐付ける場合、`-in`オプションで既存のkeytabに追加
- パスワードは全SPNで同一である必要があります
- 実際のIngress FQDNに合わせてドメイン名を変更してください
- K8S内部Service用のSPNは、Pod間直接通信が必要な特殊な場合のみ設定

### 1.3 KeytabファイルをKubernetesに配置
```bash
# KeytabファイルをSecretとして作成
kubectl create secret generic kerberos-keytab \
  --from-file=krb5.keytab=adauth.keytab \
  -n winauth

# 確認
kubectl get secret kerberos-keytab -n winauth
```

## 2. Kubernetes環境設定

### 2.1 Namespace作成
```bash
kubectl create namespace winauth
```

### 2.2 Kerberos設定ファイル作成（ConfigMap）
```bash
# ConfigMapを適用
kubectl apply -f k8s/configmap.yaml

# 確認
kubectl get configmap krb5-config -n winauth -o yaml
```

ConfigMapの内容（k8s/configmap.yaml）に含まれる設定：
- Kerberos設定（krb5.conf）
- アプリケーション設定（application-kerberos.yaml）

### 2.2 DNS設定（重要）
```bash
# /etc/hosts に追加（または適切なDNS設定）
echo "192.168.1.10  dc.domain.com" >> /etc/hosts
echo "192.168.1.20  your-linux-server.domain.com" >> /etc/hosts
```

### 2.3 時刻同期の設定（重要）

Kerberos認証では時刻同期が必須です（5分以内の誤差）。

#### Kubernetes Podでの時刻同期
```yaml
# deployment.yamlに追加
spec:
  containers:
  - name: winauth
    env:
    - name: TZ
      value: "Asia/Tokyo"  # または適切なタイムゾーン
```

#### ホストノードの時刻同期確認
```bash
# Kubernetesノードで実行
# NTPサービスの状態確認
systemctl status chronyd
# または
systemctl status systemd-timesyncd

# 時刻同期状態の確認
timedatectl status

# ドメインコントローラーとの時刻差確認
ntpdate -q dc.domain.com
```

### 2.4 K8S環境へのデプロイ

#### Secret作成（Keytab）
```bash
# Keytabファイルをbase64エンコード
cat adauth.keytab | base64 -w 0 > keytab.b64

# SecretのYAMLを編集して適用
kubectl apply -f k8s/secret.yaml
```

#### Deployment適用
```bash
# すべてのK8Sリソースを適用
kubectl apply -f k8s/

# デプロイ状況確認
kubectl get all -n winauth

# Pod のログ確認
kubectl logs -f deployment/winauth-server -n winauth
```

### 2.4 時刻同期設定（K8Sノード）
```bash
# 各K8Sノードで実行（重要）
sudo ntpdate dc.domain.com
sudo systemctl enable ntp
```

## 3. Spring Boot設定

### 3.1 K8S環境用の設定

#### ConfigMap経由での設定（k8s/configmap.yaml）
```yaml
data:
  application-kerberos.yaml: |
    kerberos:
      # Ingress用のプリンシパル（メイン）
      principal: HTTP/winauth.example.com@DOMAIN.COM
      keytab: /etc/krb5/krb5.keytab
    
    ad:
      domain: DOMAIN.COM
      url: ldap://dc.domain.com:389
      searchBase: DC=domain,DC=com
```

#### 環境変数での設定（deployment.yaml）
```yaml
env:
- name: KERBEROS_PRINCIPAL
  value: "HTTP/winauth.example.com@DOMAIN.COM"
- name: KERBEROS_KEYTAB
  value: "/etc/krb5/krb5.keytab"
```

### 3.2 複数SPN対応（Ingress使用時）

外部アクセス用に複数のSPNをサポートする場合：

```properties
# プライマリSPN（K8S内部）
kerberos.principal=HTTP/winauth-service.winauth.svc.cluster.local@DOMAIN.COM

# 追加SPN（Ingress経由）
kerberos.additional.spns=HTTP/winauth.example.com@DOMAIN.COM
```

## 4. 認証フローの詳細

### 4.1 クロスドメインKerberos認証シーケンス図（異なるドメイン間の認証）

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Win as Windows OS<br/>(クライアントPC)
    participant Chrome as Chrome<br/>ブラウザ
    participant React as React Client<br/>(localhost:5173)
    participant Vite as Vite Proxy
    participant Spring as Spring Boot<br/>(localhost:8082)
    participant KDC1 as KDC-CLIENT<br/>(CLIENT.DOMAIN.COM)
    participant KDC2 as KDC-SERVICE<br/>(SERVICE.DOMAIN.COM)
    participant LDAP as LDAP Server<br/>(SERVICE.DOMAIN.COM)

    Note over User,LDAP: 前提: クライアントPC: CLIENT.DOMAIN.COM所属<br/>サービス: SERVICE.DOMAIN.COM所属<br/>ドメイン間信頼関係が設定済み

    %% 1. Windowsドメインログイン（既に完了）
    rect rgba(240, 240, 255, 0.2)
        Note over User,KDC1: Phase 1: クライアントドメインでの認証（起動時に完了済み）
        User->>Win: CLIENT\ユーザー名 + パスワード
        Win->>KDC1: AS-REQ (Authentication Server Request)
        KDC1->>KDC1: ユーザー認証
        KDC1-->>Win: AS-REP + TGT (Ticket Granting Ticket)<br/>for CLIENT.DOMAIN.COM
        Win->>Win: TGTをキャッシュ（ログオンセッション）
        Note over Win: CLIENT.DOMAIN.COMのTGT保持
    end

    %% 2. ブラウザでの認証開始
    rect rgba(255, 240, 240, 0.2)
        Note over User,Spring: Phase 2: ブラウザでの認証開始
        User->>Chrome: ブラウザ起動
        Chrome->>React: http://localhost:5173 アクセス
        React-->>Chrome: 認証画面表示
        User->>Chrome: "Kerberos認証" ボタンクリック
        Chrome->>React: handleKerberosAuth() 実行
    end

    %% 3. 初回リクエストとSPNEGOネゴシエーション
    rect rgba(240, 255, 240, 0.2)
        Note over Chrome,Spring: Phase 3: SPNEGOネゴシエーション開始
        React->>Vite: GET /api/user (withCredentials: true)
        Vite->>Spring: GET /api/user → /user (プロキシ)
        
        Note over Spring: SpnegoAuthenticationProcessingFilter
        Spring-->>Vite: 401 Unauthorized<br/>WWW-Authenticate: Negotiate
        Vite-->>React: 401応答
        React-->>Chrome: 認証チャレンジ
        
        Note over Chrome: Negotiate認証開始<br/>Windows統合認証を使用
    end

    %% 4. Service Ticket取得
    rect rgba(255, 255, 240, 0.2)
        Note over Chrome,KDC2: Phase 4: クロスドメインService Ticket取得
        Chrome->>Win: Negotiate認証要求<br/>(SSPI API呼び出し)
        Win->>Win: キャッシュされたTGTを確認<br/>(CLIENT.DOMAIN.COMのTGT)
        
        Note over Win,KDC1: Step 4a: クロスレルムTGT取得
        Win->>KDC1: TGS-REQ<br/>Target: krbtgt/SERVICE.DOMAIN.COM@CLIENT.DOMAIN.COM
        KDC1->>KDC1: 信頼関係の確認
        KDC1-->>Win: TGS-REP + Cross-Realm TGT<br/>(SERVICE.DOMAIN.COM用のTGT)
        
        Note over Win,KDC2: Step 4b: サービスチケット取得
        Win->>KDC2: TGS-REQ with Cross-Realm TGT<br/>Target: HTTP/winauth.service.domain.com@SERVICE.DOMAIN.COM
        KDC2->>KDC2: Cross-Realm TGT検証<br/>Service Principal確認
        KDC2-->>Win: TGS-REP + Service Ticket
        Win-->>Chrome: Service Ticket (Kerberosトークン)
    end

    %% 5. 認証付きリクエスト
    rect rgba(240, 255, 255, 0.2)
        Note over Chrome,Spring: Phase 5: 認証付きリクエスト送信
        Chrome->>React: Service Ticketを含む認証情報
        React->>Vite: GET /api/user<br/>Authorization: Negotiate <base64-token>
        Vite->>Spring: GET /api/user → /user<br/>Authorization: Negotiate <base64-token>
        
        Note over Spring: AuthController.getCurrentUser()<br/>Negotiate token validation
        Spring->>Spring: Service Ticket検証<br/>(Keytabで復号化)
        Spring->>Spring: ユーザープリンシパル抽出<br/>(user@CLIENT.DOMAIN.COM)
    end

    %% 6. ユーザー情報取得（オプション）
    rect rgba(255, 240, 255, 0.2)
        Note over Spring,LDAP: Phase 6: ユーザー詳細情報取得（オプション）
        Spring->>LDAP: LDAP検索<br/>(&(sAMAccountName=user))
        LDAP->>LDAP: ユーザー属性検索
        LDAP-->>Spring: ユーザー詳細情報<br/>(displayName, email, memberOf等)
    end

    %% 7. 認証完了とレスポンス
    rect rgba(240, 240, 240, 0.2)
        Note over Spring,User: Phase 7: 認証完了とレスポンス
        Spring->>Spring: Spring Security Context設定<br/>KerberosAuthenticationToken作成
        Spring-->>Vite: 200 OK + JSON<br/>{success: true, username: "user@CLIENT.DOMAIN.COM", roles: [...]}
        Vite-->>React: 認証成功レスポンス
        React->>React: 認証結果をstate更新
        React-->>Chrome: ユーザー情報画面表示
        Chrome-->>User: 認証完了（ID/パスワード入力なし）
    end

    Note over User,LDAP: 完了: クロスドメイン認証により異なるドメインのサービスへ自動認証された
```

### 4.2 ドメイン間信頼関係の設定

クロスドメインKerberos認証を実現するには、ドメイン間の信頼関係が必要です：

#### 必要な信頼関係
```
CLIENT.DOMAIN.COM ←→ SERVICE.DOMAIN.COM
（双方向の信頼または単方向の信頼）
```

#### Active Directoryでの信頼設定（管理者権限必要）
```powershell
# SERVICE.DOMAIN.COMのドメインコントローラーで実行
# CLIENT.DOMAIN.COMからの信頼を受け入れる
New-ADTrust -Name "CLIENT.DOMAIN.COM" `
  -Type Forest `
  -Direction Bidirectional `
  -LocalForestCredential (Get-Credential) `
  -RemoteForestCredential (Get-Credential)
```

#### SPNの設定（SERVICE.DOMAIN.COM側）
```cmd
# サービスアカウントはSERVICE.DOMAIN.COMに作成
setspn -A HTTP/winauth.service.domain.com svc-winauth@SERVICE.DOMAIN.COM
```

#### Keytabの生成（SERVICE.DOMAIN.COM側）
```cmd
ktpass /out winauth.keytab `
  /princ HTTP/winauth.service.domain.com@SERVICE.DOMAIN.COM `
  /mapuser svc-winauth@SERVICE.DOMAIN.COM `
  /pass Password123! `
  /ptype KRB5_NT_PRINCIPAL `
  /crypto AES256-SHA1
```

### 4.3 Windows OS内でのKerberos処理詳細

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
    rect rgba(245, 245, 255, 0.2)
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
    rect rgba(255, 245, 245, 0.2)
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