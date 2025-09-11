# Kerberos認証設定ガイド - Linux サーバー版

このガイドでは、通常のLinuxサーバー（非Kubernetes環境）でのKerberos認証設定手順を説明します。

## 前提条件

- Linux サーバー（Ubuntu/CentOS/RHEL）
- Active Directory ドメイン環境
- ドメイン管理者権限
- Java アプリケーション（Spring Boot）

## 1. Active Directory 設定

### 1.1 サービスアカウントの作成

```cmd
# Active Directory Users and Computersまたはコマンドプロンプトで実行

# サービスアカウント作成
net user svc-winauth-linux Password123! /add /domain
net user svc-winauth-linux /passwordchg:no /domain
net user svc-winauth-linux /expires:never /domain

# 確認
net user svc-winauth-linux /domain
```

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
ktpass /out winauth.keytab /princ HTTP/winauth.example.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL

# 追加ドメインがある場合
ktpass /out winauth-multi.keytab /princ HTTP/auth.company.com@EXAMPLE.COM /mapuser svc-winauth-linux /pass Password123! /ptype KRB5_NT_PRINCIPAL
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
sudo apt install -y krb5-user krb5-config libpam-krb5 ntpdate
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

### 2.3 Kerberos設定 (/etc/krb5.conf)

```bash
sudo tee /etc/krb5.conf > /dev/null <<EOF
[libdefaults]
    default_realm = EXAMPLE.COM
    dns_lookup_realm = false
    dns_lookup_kdc = false
    ticket_lifetime = 24h
    renew_lifetime = 7d
    forwardable = true
    proxiable = true
    default_tkt_enctypes = aes256-cts aes128-cts
    default_tgs_enctypes = aes256-cts aes128-cts
    permitted_enctypes = aes256-cts aes128-cts

[realms]
    EXAMPLE.COM = {
        kdc = dc.example.com:88
        admin_server = dc.example.com:749
        default_domain = example.com
    }

[domain_realm]
    .example.com = EXAMPLE.COM
    example.com = EXAMPLE.COM

[logging]
    default = FILE:/var/log/krb5libs.log
    kdc = FILE:/var/log/krb5kdc.log
    admin_server = FILE:/var/log/kadmind.log
EOF
```

### 2.4 Keytabファイルの配置

```bash
# アプリケーション用ディレクトリ作成
sudo mkdir -p /opt/winauth/config

# Keytabファイルをサーバーにコピー（Windows側で生成したもの）
sudo cp winauth.keytab /opt/winauth/config/

# 権限設定
sudo chown winauth:winauth /opt/winauth/config/winauth.keytab
sudo chmod 600 /opt/winauth/config/winauth.keytab
```

### 2.5 Kerberos認証テスト

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
-Djava.security.krb5.conf=/etc/krb5.conf
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