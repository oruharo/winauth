# WinAuth - Windows Authentication Server

Windows認証サーバーの統合ソリューション。複数の認証方式をサポートし、ドメイン環境・非ドメイン環境の両方に対応しています。

## 🚀 機能

- **AD/LDAP認証**: ユーザー名・パスワードによる Active Directory 認証
- **Kerberos/SPNEGO認証**: ドメイン参加クライアントでの自動認証（SSO）
- **プロファイル切替**: 認証方式の動的切り替え
- **React UI**: 認証テスト用の直感的なWebインターフェース

## 📁 プロジェクト構成

```
winauth/
├── server/                    # Spring Boot バックエンド (ポート: 8082)
├── client/                    # React + TypeScript フロントエンド (ポート: 5173)
├── KERBEROS_SETUP.md         # Kerberos環境構築手順
├── kerberos-authentication-guide.md  # Kerberos技術仕様・実装ガイド
└── CLAUDE.md                 # 開発者向けプロジェクト指針
```

## 🔧 クイックスタート

### AD/LDAP認証モード（デフォルト）
```bash
# バックエンド起動
cd server
./mvnw spring-boot:run

# フロントエンド起動（別ターミナル）
cd client
npm run dev
```

### Kerberos認証モード
```bash
# Kerberosプロファイルでバックエンド起動
cd server
./mvnw spring-boot:run -Dspring.profiles.active=kerberos

# フロントエンド起動（別ターミナル）
cd client
npm run dev
```

アクセス: http://localhost:5173

## 📚 ドキュメント

| ファイル | 目的 | 対象読者 |
|---------|------|---------|
| **[KERBEROS_SETUP.md](./KERBEROS_SETUP.md)** | Kerberos環境の構築手順<br/>・AD設定<br/>・Keytab作成<br/>・トラブルシューティング | システム管理者<br/>インフラ担当者 |
| **[kerberos-authentication-guide.md](./kerberos-authentication-guide.md)** | Kerberos認証の技術仕様<br/>・実装詳細<br/>・Spring Security設定<br/>・アーキテクチャ解説 | 開発者<br/>技術者 |
| **[CLAUDE.md](./CLAUDE.md)** | 開発環境・プロジェクト指針<br/>・コーディング規約<br/>・開発コマンド | 開発者 |

## 🏗️ アーキテクチャ

### 認証フロー

#### AD/LDAP認証
```
Client → React UI → Vite Proxy → Spring Boot → Active Directory
```

#### Kerberos/SPNEGO認証  
```
Client → Windows OS → Chrome → React UI → Vite Proxy → Spring Boot
         ↓
    Kerberos TGT → Service Ticket → SPNEGO Token
```

### 技術スタック

- **バックエンド**: Java 11, Spring Boot 2.7.18, Spring Security
- **フロントエンド**: React 19, TypeScript 5.8, Vite 7
- **認証**: Spring Security Kerberos, Active Directory LDAP
- **プロキシ**: Vite development proxy

## 🔐 認証モード

| モード | プロファイル | ポート | 認証方式 | 要件 |
|-------|-------------|-------|---------|------|
| **AD/LDAP** | default | 8082 | ユーザー名・パスワード | AD接続のみ |
| **Kerberos** | kerberos | 8082 | 自動認証（SSO） | ドメイン参加 + Keytab |

## 🛠️ 開発

### 必要な環境
- Java 11+
- Node.js 18+
- Maven 3.6+

### 推奨コマンド
```bash
# 依存関係インストール
cd server && ./mvnw dependency:resolve
cd client && npm install

# コード品質チェック
cd server && ./mvnw test
cd client && npm run lint

# 本番ビルド
cd server && ./mvnw clean package
cd client && npm run build
```

## 📄 ライセンス

MIT License - 詳細は [LICENSE](./LICENSE) を参照

## 🤝 貢献

プロジェクトへの貢献を歓迎します。開発前に [CLAUDE.md](./CLAUDE.md) の開発ガイドラインを確認してください。