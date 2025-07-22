# AD Authentication Server (ドメイン非参加環境用)

このサーバーは、ドメインに参加していない環境でActive Directory認証を実行するためのSpring Bootアプリケーションです。

## 特徴

- ドメイン非参加環境でAD認証が可能
- REST APIとWebインターフェースの両方を提供
- LDAPプロトコルを使用したAD接続
- Spring SecurityのActiveDirectoryLdapAuthenticationProviderを使用

## 設定

`application.properties`を編集して、ADサーバー情報を設定してください：

```properties
# ADドメイン名
ad.domain=YOUR_DOMAIN.LOCAL

# ADサーバーのURL
ad.url=ldap://YOUR_AD_SERVER:389
```

## 起動方法

```bash
cd /Users/hashiro/nodeprj/winauth/server2
mvn spring-boot:run
```

または環境変数で設定：

```bash
export AD_DOMAIN=contoso.com
export AD_URL=ldap://dc.contoso.com:389
mvn spring-boot:run
```

## 使用方法

### Webインターフェース

1. ブラウザで `http://localhost:8082` にアクセス
2. ログインページで以下の形式でユーザー名を入力：
   - `DOMAIN\username` (例: CONTOSO\john.doe)
   - `username@domain.com` (例: john.doe@contoso.com)

### REST API

```bash
# ヘルスチェック
curl http://localhost:8082/api/health

# ログイン
curl -X POST http://localhost:8082/api/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "DOMAIN\\username",
    "password": "password"
  }'

# ユーザー情報取得（要認証）
curl http://localhost:8082/api/user

# ログアウト
curl -X POST http://localhost:8082/api/logout
```

## トラブルシューティング

1. **接続エラー**: ADサーバーへの接続（ポート389）が可能か確認
2. **認証失敗**: ユーザー名の形式（DOMAIN\username）を確認
3. **SSL証明書エラー**: LDAPSを使用する場合は証明書の設定が必要

## セキュリティ注意事項

- CSRFは無効化されています（本番環境では有効化を推奨）
- HTTPSの使用を推奨
- 本番環境では適切なCORS設定が必要