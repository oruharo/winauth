# Windows統合認証 Spring Bootアプリケーション

## 概要
このアプリケーションは、Windows統合認証（NTLM/Kerberos）を使用してユーザーを認証するSpring Bootアプリケーションです。

## 実装済み機能
- Windows統合認証（WAFFLE使用）
- 認証成功/失敗時の画面表示
- ユーザー情報とグループ情報の表示
- セッション管理とログアウト機能

## プロジェクト構造
```
server/
├── src/main/java/com/example/winauth/
│   ├── config/
│   │   └── SecurityConfig.java     # セキュリティ設定
│   └── controller/
│       └── AuthController.java     # 認証コントローラー
├── src/main/resources/
│   ├── templates/                  # Thymeleafテンプレート
│   │   ├── index.html             # トップページ
│   │   ├── home.html              # ホームページ
│   │   ├── success.html           # 認証成功画面
│   │   └── error.html             # 認証失敗画面
│   └── application.properties      # アプリケーション設定
└── pom.xml                        # Maven設定
```

## ビルドと実行

### 前提条件
- Java 11以上
- Maven 3.6以上
- Windowsドメイン環境（または適切な認証設定）

### ビルド
```bash
cd server
mvn clean install
```

### 実行
```bash
mvn spring-boot:run
```

アプリケーションは http://localhost:8080 で起動します。

## エンドポイント
- `/` - トップページ（認証不要）
- `/home` - ホームページ（認証不要、認証情報を表示）
- `/secure` - セキュアページ（要認証）
- `/login-error` - 認証エラーページ
- `/logout` - ログアウト

## 設定
`application.properties`で以下の設定を変更できます：
- `server.port` - サーバーポート（デフォルト: 8080）
- `logging.level.*` - ログレベル
- `waffle.servlet.sso.protocols` - 使用する認証プロトコル

## トラブルシューティング
認証が失敗する場合は以下を確認してください：
1. Windowsドメインにログインしているか
2. ブラウザがWindows統合認証をサポートしているか
3. ブラウザの設定で信頼済みサイトに追加されているか
4. 企業ネットワーク内からアクセスしているか