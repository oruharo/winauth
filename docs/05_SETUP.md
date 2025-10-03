# 第5章: 環境別セットアップ

## 5.1 Kerberos環境のセットアップ

### 5.1.1 前提条件
- Docker & Docker Compose
- Active Directory ドメイン環境
- ドメイン管理者権限
- ホストシステム（Amazon Linux 2023推奨）

### 5.1.2 Kerberos固有のActive Directory設定

#### サービスアカウントの作成
```powershell
# ADドメインコントローラーで実行（管理者権限）
Import-Module ActiveDirectory

# サービスアカウント作成
New-ADUser -Name "svc-winauth" `
  -SamAccountName "svc-winauth" `
  -UserPrincipalName "svc-winauth@DOMAIN1.LAB" `
  -DisplayName "WinAuth Service Account" `
  -AccountPassword (ConvertTo-SecureString "ServicePass123!" -AsPlainText -Force) `
  -Enabled $true `
  -PasswordNeverExpires $true `
  -CannotChangePassword $true
```

#### SPN登録
```powershell
# ALB DNS名でSPNを登録
setspn -A HTTP/has-winauth-alb-123456.ap-northeast-1.elb.amazonaws.com svc-winauth

# 確認
setspn -L svc-winauth
```

#### Keytabファイル生成
```powershell
# ktpassコマンドでkeytab生成
ktpass -out C:\winauth.keytab `
  -princ HTTP/has-winauth-alb-123456.ap-northeast-1.elb.amazonaws.com@DOMAIN1.LAB `
  -mapUser svc-winauth@DOMAIN1.LAB `
  -mapOp set `
  -pass ServicePass123! `
  -crypto AES256-SHA1 `
  -pType KRB5_NT_PRINCIPAL

# ホストシステムに転送
# SCP、SFTP、またはAnsibleで転送
```

### 5.1.3 ホストシステム設定

#### Dockerインストール
```bash
# Amazon Linux 2023
sudo dnf update -y
sudo dnf install -y docker

# Docker Compose plugin
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Docker起動
sudo systemctl enable docker
sudo systemctl start docker
```

#### 設定ファイル配置
```bash
# 設定ディレクトリ作成
sudo mkdir -p /opt/winauth/app-config

# krb5.confを配置
sudo tee /opt/winauth/app-config/krb5.conf > /dev/null <<EOF
[libdefaults]
    default_realm = DOMAIN1.LAB
    dns_lookup_realm = false
    dns_lookup_kdc = false
    ticket_lifetime = 24h
    renew_lifetime = 7d
    forwardable = true
    allow_weak_crypto = true

[realms]
    DOMAIN1.LAB = {
        kdc = 10.0.10.10
        admin_server = 10.0.10.10
    }

[domain_realm]
    .domain1.lab = DOMAIN1.LAB
    domain1.lab = DOMAIN1.LAB
EOF

# Keytabファイル配置
sudo cp winauth.keytab /opt/winauth/app-config/winauth.keytab
sudo chmod 600 /opt/winauth/app-config/winauth.keytab

# application.properties配置（Ansibleが自動生成）
```

#### 時刻同期設定
```bash
# NTPサービス設定（重要！）
sudo timedatectl set-ntp true
sudo systemctl enable chronyd
sudo systemctl start chronyd

# 時刻差確認（5分以内必須）
ntpdate -q 10.0.10.10
```

## 5.2 Docker Compose設定

### 5.2.1 Kerberos用設定

#### Dockerfile（サーバー）
```dockerfile
FROM amazoncorretto:17-alpine

WORKDIR /app

# アプリケーションJARをコピー
COPY target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### docker-compose.yml
```yaml
services:
  app:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: winauth-kerberos-app
    ports:
      - "8082:8082"
    volumes:
      - ../../app-config/application.properties:/config/application.properties:ro
      - ../../app-config/application-kerberos.properties:/config/application-kerberos.properties:ro
      - ../../app-config/krb5.conf:/config/krb5.conf:ro
      - ../../app-config/winauth.keytab:/config/winauth.keytab:ro
    environment:
      - SPRING_PROFILES_ACTIVE=kerberos
      - SPRING_CONFIG_LOCATION=file:/config/application.properties,file:/config/application-kerberos.properties
      - KRB5_CONFIG=/config/krb5.conf
      - JAVA_TOOL_OPTIONS=-Djava.security.krb5.conf=/config/krb5.conf

  nginx:
    build:
      context: .
      dockerfile: nginx/Dockerfile
    container_name: winauth-kerberos-nginx
    ports:
      - "80:80"
    depends_on:
      - app
```

#### クライアント用 Multi-stage Dockerfile
```dockerfile
# Stage 1: Build React application
FROM node:22-alpine AS client-builder

WORKDIR /build

RUN --mount=type=bind,source=client,target=. \
    --mount=type=cache,target=/root/.npm \
    npm install --legacy-peer-deps && \
    npm run build

# Stage 2: Nginx runtime
FROM nginx:alpine

# Remove default nginx configuration
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx configuration
COPY nginx/nginx.conf /etc/nginx/conf.d/winauth.conf

# Copy React build artifacts from builder stage
COPY --from=client-builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### 5.2.2 NTLM用設定

#### docker-compose.yml
```yaml
services:
  app:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: winauth-ntlm-app
    ports:
      - "8082:8082"
    volumes:
      - ../../app-config/application.properties:/config/application.properties:ro
      - ../../app-config/application-ntlm.properties:/config/application-ntlm.properties:ro
    environment:
      - SPRING_PROFILES_ACTIVE=ntlm
      - SPRING_CONFIG_LOCATION=file:/config/application.properties,file:/config/application-ntlm.properties

  nginx:
    build:
      context: .
      dockerfile: nginx/Dockerfile
    container_name: winauth-ntlm-nginx
    ports:
      - "80:80"
    depends_on:
      - app
```

## 5.3 Kubernetes 環境（参考）

### 5.3.1 Secret作成

```bash
# Keytab Secretの作成
kubectl create secret generic winauth-keytab \
  --from-file=winauth.keytab=/path/to/winauth.keytab \
  --namespace=default

# ConfigMap作成（krb5.conf）
kubectl create configmap krb5-config \
  --from-file=krb5.conf=/path/to/krb5.conf \
  --namespace=default
```

### 5.3.2 Deployment設定

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: winauth-kerberos
spec:
  replicas: 2
  selector:
    matchLabels:
      app: winauth
  template:
    metadata:
      labels:
        app: winauth
    spec:
      containers:
      - name: app
        image: winauth-server:latest
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kerberos"
        - name: KRB5_CONFIG
          value: "/etc/krb5.conf"
        - name: JAVA_TOOL_OPTIONS
          value: "-Djava.security.krb5.conf=/etc/krb5.conf"
        volumeMounts:
        - name: keytab
          mountPath: /etc/kerberos
          readOnly: true
        - name: krb5-config
          mountPath: /etc/krb5.conf
          subPath: krb5.conf
          readOnly: true
      volumes:
      - name: keytab
        secret:
          secretName: winauth-keytab
          defaultMode: 0600
      - name: krb5-config
        configMap:
          name: krb5-config
```

## 5.4 手動ドメイン参加（Windows クライアント）

### 5.4.1 ドメイン参加手順

#### PowerShellでのドメイン参加
```powershell
# 管理者権限でPowerShellを実行

# DNS設定（ドメインコントローラーを指定）
Set-DnsClientServerAddress -InterfaceAlias "Ethernet" -ServerAddresses "10.0.10.10"

# ドメイン参加
Add-Computer -DomainName "DOMAIN1.LAB" `
  -Credential (Get-Credential DOMAIN1\Administrator) `
  -Restart -Force
```

#### GUIでのドメイン参加
1. **システムのプロパティ**を開く
2. **コンピューター名**タブ → **変更**
3. **所属するグループ**: ドメインを選択
4. ドメイン名入力: `DOMAIN1.LAB`
5. 管理者資格情報入力
6. 再起動

### 5.4.2 ブラウザ設定

#### Microsoft Edge / Google Chrome
```powershell
# グループポリシー設定（レジストリ）
Set-ItemProperty -Path "HKCU:\SOFTWARE\Policies\Microsoft\Edge" `
  -Name "AuthServerAllowlist" `
  -Value "https://has-winauth-alb-*.ap-northeast-1.elb.amazonaws.com" `
  -Type String

# または手動でEdge設定
# edge://policy → AuthServerAllowlist
```

#### Internet Explorer
```powershell
# イントラネットゾーン設定
Set-ItemProperty -Path "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Internet Settings\Zones\1" `
  -Name "1A00" `
  -Value 0 `
  -Type DWord
```

## 5.5 Ansibleによる自動化

### 5.5.1 Makefile実行
```bash
# Kerberos環境の場合
cd /path/to/winauth/kerberos/infrastructure
make deploy

# NTLM環境の場合
cd /path/to/winauth/ntlm/infrastructure
make deploy
```

### 5.5.2 個別タスク実行
```bash
# ドメインコントローラー設定のみ
make setup-domain

# Windowsクライアント設定のみ
make setup-clients

# Linuxサーバーデプロイのみ
make deploy-linux
```

## 次の章へ

- [第6章: トラブルシューティング](./06_TROUBLESHOOTING.md) - 一般的な問題と解決策
- [第1章: Windows統合認証の基礎](./01_WINDOWS_INTEGRATED_AUTH.md) - 認証方式の概要に戻る
