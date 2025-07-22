# Kerberos認証設定ガイド - Linux サーバー版

このガイドでは、通常のLinuxサーバー（非Kubernetes環境）でのKerberos認証設定手順を説明します。

## 前提条件

- Linux サーバー（Ubuntu/CentOS/RHEL）
- Active Directory ドメイン環境
- ドメイン管理者権限
- Java アプリケーション（Spring Boot）

## 1. Active Directory 設定

### 1.1 サービスアカウントの作成

#### PowerShellでのサービスアカウント作成（推奨）
```powershell
# ADドメインコントローラーで実行（管理者権限）
# Active Directory PowerShellモジュールをインポート
Import-Module ActiveDirectory

# サービスアカウント作成
New-ADUser -Name "svc-winauth-linux" `
  -SamAccountName "svc-winauth-linux" `
  -UserPrincipalName "svc-winauth-linux@DOMAIN.COM" `
  -DisplayName "WinAuth Linux Service Account" `
  -Description "Service account for Linux WinAuth application" `
  -Path "OU=ServiceAccounts,DC=domain,DC=com" `
  -AccountPassword (ConvertTo-SecureString "P@ssw0rd123!" -AsPlainText -Force) `
  -Enabled $true `
  -PasswordNeverExpires $true `
  -CannotChangePassword $true

# アカウントの確認
Get-ADUser svc-winauth-linux -Properties *

# 必要に応じてグループに追加
Add-ADGroupMember -Identity "Domain Users" -Members svc-winauth-linux
```

#### コマンドプロンプトでのサービスアカウント作成（代替方法）
```cmd
# ADドメインコントローラーで実行

# サービスアカウント作成
net user svc-winauth-linux Password123! /add /domain
net user svc-winauth-linux /passwordchg:no /domain
net user svc-winauth-linux /expires:never /domain

# または dsaddコマンドを使用
dsadd user "CN=svc-winauth-linux,OU=ServiceAccounts,DC=domain,DC=com" ^
  -samid svc-winauth-linux ^
  -upn svc-winauth-linux@DOMAIN.COM ^
  -fn "WinAuth" ^
  -ln "Linux Service" ^
  -display "WinAuth Linux Service Account" ^
  -desc "Service account for Linux WinAuth application" ^
  -pwd P@ssw0rd123! ^
  -pwdneverexpires yes ^
  -disabled no

# 確認
net user svc-winauth-linux /domain
dsquery user -name svc-winauth-linux
```

#### GUI（Active Directory ユーザーとコンピューター）での作成
1. **Active Directory ユーザーとコンピューター**を開く
2. **ServiceAccounts** OU（または適切なOU）を右クリック
3. **新規作成** → **ユーザー**
4. 以下を入力：
   - 名: `WinAuth`
   - 姓: `Linux Service`
   - ユーザーログオン名: `svc-winauth-linux`
5. パスワード設定：
   - パスワード: `P@ssw0rd123!`
   - ☑ パスワードを無期限にする
   - ☑ ユーザーはパスワードを変更できない
6. **完了**をクリック

### 1.2 Service Principal Name (SPN) の設定

```cmd
# メインSPN - 外部アクセス用
setspn -A HTTP/winauth.example.com svc-winauth-linux

# 追加ドメインがある場合
setspn -A HTTP/auth.company.com svc-winauth-linux

# 確認
setspn -L svc-winauth-linux

# 重複SPNのチェック
setspn -X
```

### エラー0x00000525 (ERROR_NO_SUCH_USER) の解決方法

setspnコマンド実行時にこのエラーが発生する場合の解決方法：

#### 方法1: PowerShellを使用（推奨）
```powershell
# PowerShellでSPNを設定（エラーが出にくい）
Set-ADUser -Identity svc-winauth-linux -ServicePrincipalNames @{Add="HTTP/winauth.example.com"}

# 確認
Get-ADUser svc-winauth-linux -Properties ServicePrincipalNames | Select-Object -ExpandProperty ServicePrincipalNames
```

#### 方法2: 完全なDistinguished Name (DN)を使用
```cmd
# サービスアカウントの完全なDNを取得
dsquery user -name svc-winauth-linux

# 取得したDNを使用してSPNを設定
setspn -A HTTP/winauth.example.com "CN=svc-winauth-linux,CN=Users,DC=example,DC=com"
```

#### 方法3: ドメイン名を明示
```cmd
# ドメイン管理者権限で実行
setspn -A HTTP/winauth.example.com DOMAIN\svc-winauth-linux

# または完全修飾ドメイン名で
setspn -A HTTP/winauth.example.com svc-winauth-linux@example.com
```

#### PowerShellでのSPN設定（推奨）
```powershell
# SPNを設定
Set-ADUser -Identity svc-winauth-linux -ServicePrincipalNames @{
    Add="HTTP/winauth.example.com",
        "HTTP/auth.company.com"
}

# SPNの確認
Get-ADUser svc-winauth-linux -Properties ServicePrincipalNames | 
    Select-Object -ExpandProperty ServicePrincipalNames
```

### 1.3 Keytabファイルの生成

```cmd
# 管理者権限のコマンドプロンプトで実行
# メインドメイン用のKeytab作成
ktpass /out winauth.keytab /princ HTTP/winauth.example.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL /crypto AES256-SHA1

# 追加ドメインがある場合
ktpass /out winauth-multi.keytab /princ HTTP/auth.company.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL /crypto AES256-SHA1
```

#### 複数SPNを含むKeytabの作成
```cmd
# 1つ目のSPN
ktpass /out winauth.keytab /princ HTTP/winauth.example.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL

# 2つ目のSPNを同じkeytabに追加
ktpass /in winauth.keytab /out winauth.keytab /princ HTTP/auth.company.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL
```

## 2. Linux サーバー設定

### 2.1 必要なパッケージのインストール

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install -y krb5-user libpam-krb5 ntpdate
```

#### CentOS/RHEL
```bash
sudo yum install -y krb5-workstation krb5-libs ntpdate
# または RHEL 8+
sudo dnf install -y krb5-workstation krb5-libs ntpdate
```

### 2.2 時刻同期の設定

```bash
# NTPでドメインコントローラーと時刻同期
sudo ntpdate -s dc.example.com

# 継続的な時刻同期（systemd-timesyncd）
sudo systemctl enable systemd-timesyncd
sudo systemctl start systemd-timesyncd

# または chrony を使用
sudo yum install -y chrony
echo "server dc.example.com iburst" | sudo tee -a /etc/chrony.conf
sudo systemctl enable chronyd
sudo systemctl restart chronyd
```

### 2.3 Keytabファイルの配置

```bash
# アプリケーション用ディレクトリ作成
sudo mkdir -p /opt/winauth/config

# Keytabファイルをサーバーにコピー（Windows側で生成したもの）
sudo cp winauth.keytab /opt/winauth/config/

# 権限設定
sudo chown winauth:winauth /opt/winauth/config/winauth.keytab
sudo chmod 600 /opt/winauth/config/winauth.keytab
```

### 2.4 Kerberos認証テスト（オプション）

**注意**: 以下のテストは`/etc/krb5.conf`設定が必要です。Spring Bootアプリケーションのみ使用する場合はスキップ可能です。

```bash
# Keytabファイルの検証
sudo klist -kt /opt/winauth/config/winauth.keytab

# サービスプリンシパルでの認証テスト
sudo kinit -kt /opt/winauth/config/winauth.keytab HTTP/winauth.example.com@EXAMPLE.COM

# チケット確認
sudo klist

# チケット削除
sudo kdestroy
```

## 3. Java アプリケーション設定

### 3.1 アプリケーション用ユーザー作成

```bash
# アプリケーション実行用ユーザー
sudo useradd -r -s /bin/false winauth
sudo mkdir -p /opt/winauth
sudo chown -R winauth:winauth /opt/winauth
```

### 3.2 Spring Boot設定 (application-kerberos.properties)

```properties
# Kerberos設定
spring.security.kerberos.keytab-location=file:/opt/winauth/config/winauth.keytab
spring.security.kerberos.service-principal=HTTP/winauth.example.com@EXAMPLE.COM
spring.security.kerberos.realm=EXAMPLE.COM
spring.security.kerberos.kdc=dc.example.com

# LDAP設定（フォールバック認証用）
spring.ldap.urls=ldap://dc.example.com:389
spring.ldap.base=dc=example,dc=com
spring.ldap.username=cn=svc-winauth-linux,cn=Users,dc=example,dc=com
spring.ldap.password=Password123!

# アプリケーション設定
server.port=8080
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.kerberos=TRACE
```

### 3.3 JVM設定

```bash
# 起動スクリプト作成
sudo tee /opt/winauth/start.sh > /dev/null <<'EOF'
#!/bin/bash

export JAVA_OPTS="
-Djava.security.auth.login.config=/opt/winauth/config/jaas.conf
-Dsun.security.krb5.debug=true
-Dspring.profiles.active=kerberos
"

cd /opt/winauth
java $JAVA_OPTS -jar winauth-server.jar
EOF

sudo chmod +x /opt/winauth/start.sh
sudo chown winauth:winauth /opt/winauth/start.sh
```

### 3.4 JAAS設定ファイル

```bash
sudo tee /opt/winauth/config/jaas.conf > /dev/null <<EOF
com.sun.security.jgss.accept {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="/opt/winauth/config/winauth.keytab"
    storeKey=true
    doNotPrompt=true
    principal="HTTP/winauth.example.com@EXAMPLE.COM"
    isInitiator=false;
};
EOF

sudo chown winauth:winauth /opt/winauth/config/jaas.conf
sudo chmod 600 /opt/winauth/config/jaas.conf
```

## 4. システムサービス設定

### 4.1 systemdサービス作成

```bash
sudo tee /etc/systemd/system/winauth.service > /dev/null <<EOF
[Unit]
Description=WinAuth Kerberos Authentication Service
After=network.target

[Service]
Type=simple
User=winauth
Group=winauth
WorkingDirectory=/opt/winauth
ExecStart=/opt/winauth/start.sh
Restart=always
RestartSec=10

Environment=JAVA_HOME=/usr/lib/jvm/java-11-openjdk
Environment=SPRING_PROFILES_ACTIVE=kerberos

StandardOutput=journal
StandardError=journal
SyslogIdentifier=winauth

[Install]
WantedBy=multi-user.target
EOF
```

### 4.2 サービス起動

```bash
# サービス有効化と起動
sudo systemctl daemon-reload
sudo systemctl enable winauth
sudo systemctl start winauth

# ステータス確認
sudo systemctl status winauth

# ログ確認
sudo journalctl -u winauth -f
```

## 5. ファイアウォール設定

```bash
# ファイアウォール設定（必要に応じて）
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# または ufw (Ubuntu)
sudo ufw allow 8080/tcp
```

## 6. 動作確認

### 6.1 アプリケーション確認

```bash
# アプリケーションの起動確認
curl -I http://localhost:8080/health

# Kerberos認証エンドポイントの確認
curl -I http://winauth.example.com:8080/api/user
```

### 6.2 DNS設定確認

```bash
# DNSの逆引き設定確認
nslookup winauth.example.com
nslookup <IPアドレス>
```

## 7. トラブルシューティング

### 7.1 よくある問題

#### 時刻同期エラー
```bash
# 時刻同期確認
timedatectl status
sudo ntpdate -q dc.example.com
```

#### DNS解決エラー
```bash
# DNS設定確認
cat /etc/resolv.conf
nslookup dc.example.com
```

#### Keytabエラー
```bash
# Keytab検証
klist -kt /opt/winauth/config/winauth.keytab
kinit -kt /opt/winauth/config/winauth.keytab HTTP/winauth.example.com@EXAMPLE.COM
```

### 7.2 ログ確認

```bash
# アプリケーションログ
sudo journalctl -u winauth -n 100

# Kerberosライブラリログ
sudo tail -f /var/log/krb5libs.log

# システムログ
sudo tail -f /var/log/messages

# Spring Bootのデバッグログ有効化
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY_KERBEROS=TRACE
```

### 7.3 よくあるエラーと解決方法

#### KRB5KDC_ERR_PREAUTH_FAILED
```bash
# 原因: パスワード不一致またはアカウントロック
# 解決: ADでパスワードリセット、アカウントステータス確認
```

#### KRB5_CC_NOTFOUND
```bash
# 原因: Kerberosチケットキャッシュが見つからない
# 解決: 
kinit -kt /opt/winauth/config/winauth.keytab HTTP/winauth.example.com@EXAMPLE.COM
```

#### Clock skew too great
```bash
# 原因: 時刻同期の問題
# 解決:
sudo ntpdate dc.example.com
sudo systemctl restart chronyd
```

## 8. セキュリティ考慮事項

### 8.1 ファイル権限
```bash
# 重要ファイルの権限確認
ls -la /opt/winauth/config/winauth.keytab  # 600
ls -la /opt/winauth/config/jaas.conf       # 600
ls -la /etc/krb5.conf                      # 644
```

### 8.2 ネットワークセキュリティ
- Kerberosトラフィック（ポート88）の暗号化
- LDAP over SSL/TLS（ポート636）の使用推奨
- アプリケーションのHTTPS化

## 関連ドキュメント

- [KERBEROS_SETUP.md](./KERBEROS_SETUP.md) - Kubernetes環境での設定
- [kerberos-authentication-guide.md](./kerberos-authentication-guide.md) - 技術詳細
- [README.md](./README.md) - プロジェクト概要