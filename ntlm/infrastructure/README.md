# NTLM環境 構築ガイド

NTLM認証を使用したWindows統合認証システムの環境構築手順です。

## 🚀 クイックスタート

### 前提条件
- AWS CLI設定済み
- AWS認証情報設定済み
- AWSキーペア作成済み

### 環境構築（Makefile使用）

```bash
# 全環境を一括デプロイ
make deploy

# または個別にデプロイ
make setup-domain        # ドメインコントローラー設定
make setup-clients       # Windowsクライアント設定
make deploy-linux        # Linuxアプリケーションデプロイ
```

### パラメータ設定

デプロイ前にパラメータを保存してください：

```bash
# パラメータを設定して保存（必須パラメータ）
make save-config \
  KEY_NAME=my-keypair \
  SSH_KEY_PATH=~/.ssh/my-keypair.pem \
  ADMIN_PASSWORD=YourAdminPass \
  USER_PASSWORD=YourUserPass

# オプションパラメータも指定可能
make save-config \
  KEY_NAME=my-keypair \
  SSH_KEY_PATH=~/.ssh/my-keypair.pem \
  ADMIN_PASSWORD=YourAdminPass \
  USER_PASSWORD=YourUserPass \
  PREFIX=MyTest \
  SERVICE_PASSWORD=YourServicePass \
  TRUST_PASSWORD=YourTrustPass

# 保存された設定を確認
cat .env
```

## 📁 ファイル構成

```
ntlm/infrastructure/
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
- NTLM認証設定

### Windowsクライアント
- WIN1: DOMAIN1.LABに参加
- WIN2: DOMAIN2.LABに参加
- ブラウザ統合認証設定済み

## 📝 デプロイ手順

### 1. インフラストラクチャ作成
```bash
# CloudFormationでAWSリソース作成
make create-stack
```

### 2. ドメインコントローラー設定
```bash
# ドメイン設定、信頼関係、ユーザー作成
# ⚠️ NTLM認証ではSPN/keytab設定は不要
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

## ✨ NTLMの利点

### Kerberos と比べて設定が簡単
- ✅ SPN登録不要
- ✅ Keytab生成不要
- ✅ krb5.conf設定不要
- ✅ 両ドメインで追加作業不要
- ✅ Java 17のRC4問題なし

### クロスドメイン対応
- ✅ 信頼関係があれば自動的に両ドメイン対応
- ✅ DOMAIN2での作業一切不要
- ✅ 統合Windows認証（自動ログイン）可能

## 🧪 動作確認

### ブラウザでの統合認証テスト
1. WIN1またはWIN2にドメインユーザーでログイン
2. ブラウザで `http://<ALB-DNS-NAME>/` にアクセス
3. 自動的に認証される（パスワード入力不要）
4. ドメイン名、ユーザー名が表示される

### クロスドメイン認証テスト
```bash
# WIN1 (DOMAIN1ユーザー) からアクセス → ✅ 成功
# WIN2 (DOMAIN2ユーザー) からアクセス → ✅ 成功
```

## 🔍 トラブルシューティング

### よくある問題

**問題**: 401エラーが繰り返し発生
```bash
# NTLM ハンドシェイクログ確認
docker logs winauth-ntlm-app | grep -i ntlm
```

**問題**: DOMAIN2ユーザーが認証できない
```powershell
# DC1で信頼関係確認
nltest /domain_trusts
netdom trust DOMAIN1.LAB /domain:DOMAIN2.LAB /verify
```

**問題**: ユーザー情報が取得できない
```xml
<!-- pom.xmlでWaffleバージョン確認 -->
<dependency>
    <groupId>com.github.waffle</groupId>
    <artifactId>waffle-spring-boot3</artifactId>
    <version>3.3.0</version>
</dependency>
```

## 📚 詳細ドキュメント

技術詳細や高度な設定については、以下のドキュメントを参照してください：

- [第1章: Windows認証の基礎](/docs/01_OVERVIEW.md)
- [第3章: NTLM認証詳解](/docs/03_NTLM.md)
- [第4章: クロスドメイン認証](/docs/04_CROSS_DOMAIN.md)
- [第5章: 環境別セットアップ](/docs/05_SETUP.md)
- [第6章: トラブルシューティング](/docs/06_TROUBLESHOOTING.md)
- [NTLM デプロイガイド](/docs/DEPLOYMENT_GUIDE_NTLM.md)
- [セキュリティガイド](/docs/SECURITY.md)

## 🔒 セキュリティ考慮事項

NTLM認証を使用する場合の必須対策：

### 必須設定
- ✅ HTTPS強制（TLS 1.2以上）
- ✅ 社内ネットワーク限定アクセス
- ✅ NTLMv2強制（NTLMv1無効化）
- ✅ 強固なパスワードポリシー
- ✅ 認証ログ監視

### 推奨設定
- VPN経由のアクセス制御
- WAFでのレート制限
- セッション管理の厳格化
- 定期的なパスワード変更

詳細は[セキュリティガイド](/docs/SECURITY.md)を参照してください。

## 🗑️ 環境削除

```bash
# 全リソース削除
make delete-stack

# または個別削除
make clean-inventory
```

## ⚙️ 高度な設定

### カスタムパラメータでのデプロイ
```bash
# Makefileの変数を上書き
make deploy STACK_NAME=my-ntlm ADMIN_PASSWORD='YourPassword'
```

### 特定のホストのみ実行
```bash
# Linux サーバーのみ
ansible-playbook -i ansible/inventory/inventory.yml \
  ansible/deploy-linux.yml \
  --limit linux-app
```

## 📞 サポート

問題が発生した場合：
1. [トラブルシューティング](/docs/06_TROUBLESHOOTING.md)を確認
2. ログを確認: `docker logs winauth-ntlm-app`
3. Issueを作成してください
