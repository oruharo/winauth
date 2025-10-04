# Kerberos環境 構築ガイド

Kerberos認証を使用したWindows統合認証システムの環境構築手順です。

## 🚀 クイックスタート

### 前提条件
- AWS CLI設定済み
- AWS認証情報設定済み
- AWSキーペア作成済み

### 環境構築（Makefile使用）

```bash
# 1. 設定
make set-config

# 2. インフラ構築（CloudFormation）
make infrastructure

# 3. アプリケーションデプロイ（Ansible）
make deploy
```

または個別にデプロイ：

```bash
make setup-domain        # ドメインコントローラー設定
make setup-clients       # Windowsクライアント設定
make deploy-linux        # Linuxアプリケーションデプロイ
```

## 📁 ファイル構成

```
kerberos/infrastructure/
├── Makefile                    # メインデプロイスクリプト
├── cloudformation/             # AWSインフラ定義
├── ansible/                    # 設定自動化
│   ├── inventory/             # 自動生成される
│   ├── setup-domain-controllers.yml
│   ├── configure-browser-kerberos.yml
│   ├── deploy-linux.yml
│   └── templates/             # 設定テンプレート
└── README.md                  # このファイル
```

## 🔧 構築される環境

### ドメイン環境
- **DOMAIN1.LAB**: プライマリドメイン (DC1: 10.0.10.10)
- **DOMAIN2.LAB**: セカンダリドメイン (DC2: 10.0.20.10)
- **信頼関係**: 双方向の信頼関係
- **テストユーザー**: user1@DOMAIN1.LAB, user2@DOMAIN2.LAB

### Linuxアプリケーションサーバー
- Docker + Docker Compose
- Spring Boot アプリケーション (ポート 8082)
- Nginx (ポート 80)
- Kerberos設定 (krb5.conf, keytab)

### Windowsクライアント
- WIN1: DOMAIN1.LABに参加
- WIN2: DOMAIN2.LABに参加
- ブラウザ統合認証設定済み

## 📝 デプロイ手順

### 1. インフラストラクチャ作成
```bash
# CloudFormationでAWSリソース作成（約10分）
make infrastructure
```

### 2. ドメインコントローラー設定
```bash
# ドメイン設定、信頼関係、ユーザー作成
make setup-domain
```

### 3. Windowsクライアント設定
```bash
# ドメイン参加、ブラウザ設定
make setup-clients
```

### 4. Linuxアプリケーションデプロイ
```bash
# Docker環境構築、アプリケーションデプロイ
make deploy-linux
```

## ⚠️ 重要な注意事項

### パブリックIPアドレスの変更について

**注意**: インスタンス停止/起動時にパブリックIPアドレスが変更されます。

- **アプリケーションへのアクセス**: ALBのDNS名を使用しているため影響ありません
- **RDP接続**: インスタンスのパブリックIPが変更されるため、接続先IPの確認が必要です

RDP接続時は以下のコマンドで最新のIPを確認してください：
```bash
make show-info
```

## 🧪 動作確認

### Kerberos認証テスト
```bash
# LinuxサーバーにSSH接続
ssh -i ~/.aws/hashi-kerberos-key.pem ec2-user@<Linux-IP>

# Kerberos認証確認
kinit user1@DOMAIN1.LAB
klist
```

### ブラウザでの統合認証テスト
1. WIN1またはWIN2にドメインユーザーでログイン
2. ブラウザで `http://<ALB-DNS-NAME>/` にアクセス
3. 自動的に認証される（パスワード入力不要）

## 🔍 トラブルシューティング

### よくある問題

**問題**: Keytabファイルが見つからない
```bash
# Keytabの場所確認
ls -la /opt/winauth/app-config/winauth.keytab

# Keytab内容確認
klist -k /opt/winauth/app-config/winauth.keytab
```

**問題**: SPN設定エラー
```powershell
# DC1で実行
setspn -L svc-winauth
setspn -X  # 重複SPN確認
```

**問題**: 時刻同期エラー
```bash
# Linuxサーバーで実行
sudo ntpdate -q 10.0.10.10
sudo systemctl status chronyd
```

## 🗑️ 環境削除

```bash
# 全リソース削除
make destroy
```

## 📚 詳細ドキュメント

技術詳細や高度な設定については、以下のドキュメントを参照してください：

- [第1章: Windows認証の基礎](/docs/01_OVERVIEW.md)
- [第2章: Kerberos認証詳解](/docs/02_KERBEROS.md)
- [第4章: クロスドメイン認証](/docs/04_CROSS_DOMAIN.md)
- [第5章: 環境別セットアップ](/docs/05_SETUP.md)
- [第6章: トラブルシューティング](/docs/06_TROUBLESHOOTING.md)
- [セキュリティガイド](/docs/SECURITY.md)

## 📞 サポート

問題が発生した場合：
1. [トラブルシューティング](/docs/06_TROUBLESHOOTING.md)を確認
2. ログを確認: `docker logs winauth-kerberos-app`
3. Issueを作成してください
