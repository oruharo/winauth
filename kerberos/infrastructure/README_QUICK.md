# AWS Kerberos環境 - クイックスタート

最速でKerberos環境を構築する手順です。詳細な設定や運用については `DEPLOYMENT_GUIDE_JP.md` を参照してください。

## 2ステップで完了

### ステップ1: AWS準備
```bash
# AWS CLIとキーペアの準備
aws --version
aws ec2 create-key-pair --key-name my-key --region ap-northeast-1 --query 'KeyMaterial' --output text > my-key.pem
chmod 400 my-key.pem
```

### ステップ2: 環境構築
```bash
# デプロイ実行（約20分）
./quick-deploy.sh my-key 'MyStr0ngP@ssw0rd!' 'UserP@ss123!' mytest alice
```

**完了！** 以下が自動構築されます：
- DOMAIN1.LAB & DOMAIN2.LAB ドメイン
- 双方向信頼関係
- テストユーザー & SPNs
- Linuxクライアント設定

## 動作確認

### Kerberos認証テスト
```bash
# LinuxでKerberos確認
ssh -i my-key.pem ec2-user@<Linux-IP>
kinit user1@DOMAIN1.LAB  # パスワード: 設定したユーザーパスワード
klist
```

### Windowsクライアントへのドメインユーザーログイン
```
# WIN1へドメインユーザーでログイン
mstsc /v:<WIN1-IP>
ユーザー名: DOMAIN1\user1
パスワード: 設定したユーザーパスワード

# WIN2へドメインユーザーでログイン
mstsc /v:<WIN2-IP>
ユーザー名: DOMAIN2\user2
パスワード: 設定したユーザーパスワード
```

### ドメイン信頼確認
```powershell
# DC1にRDP接続して実行
nltest /trusted_domains
```

## クリーンアップ

```bash
# 使用後は必ず削除（月額約$110を回避）
aws cloudformation delete-stack --stack-name <stack-name> --region ap-northeast-1
```

## トラブルシューティング

| 問題 | 解決方法 |
|------|----------|
| デプロイ失敗 | CloudFormationコンソールでエラー確認 |
| ログイン失敗 | マネジメントコンソールでパスワード取得 |
| 認証失敗 | 構築後15分待機してから再試行 |

---

**詳細情報**: `DEPLOYMENT_GUIDE_JP.md` - 完全な設定ガイド・運用方法・トラブルシューティング