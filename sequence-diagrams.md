# Windows/AD認証シーケンス図

このドキュメントでは、2つの認証サーバーの動作フローをシーケンス図で説明します。

## Server1 (Windows統合認証 - WAFFLE) シーケンス図

```mermaid
sequenceDiagram
    participant Browser as ブラウザ
    participant Client as React Client
    participant Proxy as Vite Proxy
    participant Server1 as Server1:8081<br/>(WAFFLE)
    participant AD as Active Directory<br/>(Windows Domain)

    Browser->>Client: アクセス
    Client->>Browser: 認証画面表示
    Browser->>Client: "Windows統合認証"クリック

    Client->>Proxy: GET /api/secure
    Proxy->>Server1: GET /secure

    Note over Server1: WAFFLE Filter処理
    Server1-->>Browser: 401 Unauthorized<br/>WWW-Authenticate: Negotiate

    Browser->>Browser: Windows資格情報取得
    Browser->>Server1: GET /secure<br/>Authorization: Negotiate [token]

    Server1->>AD: Kerberos/NTLM認証
    AD-->>Server1: 認証成功 + ユーザー情報

    Server1->>Server1: ユーザー情報をJSON化
    Server1-->>Proxy: 200 OK + JSON Response
    Proxy-->>Client: 認証結果

    Client->>Browser: ユーザー情報表示
```

### Server1の特徴
- **前提条件**: クライアントPCがWindowsドメインに参加している必要がある
- **認証方式**: Kerberos/NTLM (Negotiate)
- **ユーザー操作**: 不要（ブラウザが自動的に資格情報を送信）
- **プロトコル**: Windows統合認証（SPNEGO）

## Server2 (AD認証 - フォームベース) シーケンス図

```mermaid
sequenceDiagram
    participant Browser as ブラウザ
    participant Client as React Client
    participant Proxy as Vite Proxy
    participant Server2 as Server2:8082<br/>(Spring LDAP)
    participant LDAP as AD Server<br/>(LDAP:389)

    Browser->>Client: アクセス
    Client->>Browser: AD認証フォーム表示
    Browser->>Client: ユーザー名/パスワード入力
    Browser->>Client: "ADログイン"クリック

    Client->>Proxy: POST /api/login<br/>{username, password}
    Proxy->>Server2: POST /login<br/>{username, password}

    Note over Server2: ActiveDirectoryLdapAuthenticationProvider
    Server2->>LDAP: LDAP Bind要求<br/>(username@domain)
    LDAP->>LDAP: 認証処理
    LDAP-->>Server2: Bind成功 + 属性情報

    Server2->>Server2: Spring Security Context設定
    Server2->>Server2: ロール情報取得
    Server2-->>Proxy: 200 OK<br/>{success, username, roles}
    Proxy-->>Client: 認証結果

    Client->>Browser: 認証成功表示

    Note over Browser,Client: 後続のAPI呼び出し
    Browser->>Client: "現在のユーザー"クリック
    Client->>Proxy: GET /api/user
    Proxy->>Server2: GET /user
    Server2->>Server2: SecurityContext確認
    Server2-->>Proxy: ユーザー情報
    Proxy-->>Client: ユーザー情報
    Client->>Browser: 情報表示
```

### Server2の特徴
- **前提条件**: ドメイン参加不要（ADサーバーへのネットワーク接続のみ必要）
- **認証方式**: LDAP Bind
- **ユーザー操作**: ユーザー名とパスワードの入力が必要
- **プロトコル**: LDAP (port 389) または LDAPS (port 636)

## ログアウトフロー（両サーバー共通）

```mermaid
sequenceDiagram
    participant Browser as ブラウザ
    participant Client as React Client
    participant Server as Server

    Browser->>Client: "ログアウト"クリック
    Client->>Server: POST /api/logout
    Server->>Server: SecurityContext削除
    Server->>Server: Session無効化
    Server-->>Client: 200 OK<br/>{success: true}
    Client->>Client: 状態クリア
    Client->>Browser: ログアウト完了表示
```

## エラーハンドリング

### Server1 - Windows統合認証エラー
```mermaid
sequenceDiagram
    participant Browser as ブラウザ
    participant Client as React Client
    participant Server1 as Server1:8081

    Browser->>Client: "Windows統合認証"クリック
    Client->>Server1: GET /api/secure
    Server1-->>Browser: 401 Unauthorized<br/>WWW-Authenticate: Negotiate
    
    alt ドメイン非参加 or 認証失敗
        Browser-->>Client: 認証エラー
        Client->>Browser: エラー表示<br/>"Windows認証に失敗しました"
    end
```

### Server2 - AD認証エラー
```mermaid
sequenceDiagram
    participant Browser as ブラウザ
    participant Client as React Client
    participant Server2 as Server2:8082
    participant LDAP as AD Server

    Browser->>Client: ユーザー名/パスワード入力
    Client->>Server2: POST /api/login
    Server2->>LDAP: LDAP Bind要求
    
    alt 認証失敗
        LDAP-->>Server2: Bind失敗
        Server2-->>Client: 401 Unauthorized<br/>{success: false, message: "認証失敗"}
        Client->>Browser: エラー表示
    else ADサーバー接続エラー
        Server2-->>Client: 500 Internal Server Error
        Client->>Browser: "ADサーバーに接続できません"
    end
```

## 技術仕様

### Server1 (WAFFLE)
- **ライブラリ**: WAFFLE (Windows Authentication Framework)
- **認証ヘッダー**: `Authorization: Negotiate <base64-token>`
- **セキュリティプロバイダー**: NegotiateSecurityFilterProvider
- **必要な設定**: なし（Windowsドメイン環境で自動動作）

### Server2 (Spring LDAP)
- **ライブラリ**: Spring Security LDAP
- **認証プロバイダー**: ActiveDirectoryLdapAuthenticationProvider
- **必要な設定**:
  ```properties
  ad.domain=YOUR_DOMAIN.COM
  ad.url=ldap://YOUR_AD_SERVER:389
  ```
- **サポートする認証形式**:
  - `DOMAIN\username`
  - `username@domain.com`

## セキュリティ考慮事項

1. **Server1**: 
   - HTTPSの使用を推奨（Kerberosトークンの保護）
   - SPNの適切な設定が必要

2. **Server2**:
   - HTTPSの使用を強く推奨（パスワードの保護）
   - LDAPS（SSL/TLS）の使用を検討
   - CSRFトークンの実装（本番環境）

## トラブルシューティング

### Server1の一般的な問題
- ブラウザがNegotiate認証をサポートしていない
- クライアントPCがドメインに参加していない
- Kerberosチケットの有効期限切れ

### Server2の一般的な問題
- ADサーバーへの接続がファイアウォールでブロックされている
- ユーザー名の形式が正しくない
- LDAP検索ベースDNの設定ミス