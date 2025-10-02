# ビルドステージ
FROM maven:3.8-openjdk-11 AS builder
WORKDIR /app

# 依存関係のキャッシュ最適化
COPY pom.xml .
RUN mvn dependency:go-offline

# ソースコードのコピーとビルド
COPY src ./src
RUN mvn clean package -DskipTests

# 実行ステージ
FROM openjdk:11-jre-slim
WORKDIR /app

# Kerberos関連パッケージのインストール
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    krb5-user \
    libpam-krb5 \
    ntp \
    dnsutils \
    curl \
    && rm -rf /var/lib/apt/lists/*

# ユーザー作成（セキュリティ向上）
RUN groupadd -r spring && useradd -r -g spring spring

# アプリケーションJARのコピー
COPY --from=builder /app/target/*.jar app.jar

# Kerberos設定用ディレクトリ作成
RUN mkdir -p /etc/krb5 /tmp/krb5cc && \
    chown -R spring:spring /tmp/krb5cc

# アプリケーション設定ディレクトリ
RUN mkdir -p /config && \
    chown -R spring:spring /config

# ユーザー切替
USER spring:spring

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# ポート公開
EXPOSE 8082

# エントリーポイント
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.config.additional-location=/config/", \
  "-jar", \
  "/app/app.jar"]