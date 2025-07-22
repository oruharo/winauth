# Kubernetes環境でのKerberos認証セットアップ

## 📋 前提条件

- Kubernetes 1.20+
- Active Directory環境
- DNS解決可能なADドメインコントローラー
- Keytabファイル（ADで生成済み）

## 🚀 クイックスタート

### 1. Namespace作成
```bash
kubectl create namespace winauth
```

### 2. Keytab SecretとConfigMap作成

#### Keytabファイルの準備
```bash
# ADで生成したkeytabファイルをbase64エンコード
cat adauth.keytab | base64 -w 0 > keytab.b64

# secret.yamlを編集してbase64値を設定
vi k8s/secret.yaml
# <BASE64_ENCODED_KEYTAB>を実際の値に置換
```

#### ConfigMap編集
```bash
# 環境に合わせて設定を更新
vi k8s/configmap.yaml
# DOMAIN.COM、dc.domain.comを実際の値に置換
```

### 3. デプロイ
```bash
# すべてのリソースを適用
kubectl apply -f k8s/

# デプロイ状況確認
kubectl get all -n winauth
```

## 🏗️ アーキテクチャ

### K8S内のコンポーネント構成
```
┌─────────────────────────────────────────────┐
│                 Kubernetes Cluster           │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │         Namespace: winauth           │   │
│  │                                      │   │
│  │  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │    Pod 1    │  │    Pod 2    │   │   │
│  │  │  (Replica)  │  │  (Replica)  │   │   │
│  │  │             │  │             │   │   │
│  │  │ ┌─────────┐ │  │ ┌─────────┐ │   │   │
│  │  │ │Container│ │  │ │Container│ │   │   │
│  │  │ │Java App │ │  │ │Java App │ │   │   │
│  │  │ └─────────┘ │  │ └─────────┘ │   │   │
│  │  │             │  │             │   │   │
│  │  │ Volumes:    │  │ Volumes:    │   │   │
│  │  │ - krb5.conf │  │ - krb5.conf │   │   │
│  │  │ - keytab    │  │ - keytab    │   │   │
│  │  └─────────────┘  └─────────────┘   │   │
│  │                                      │   │
│  │        Service: winauth-service      │   │
│  │              (ClusterIP)             │   │
│  └──────────────────────────────────────┘   │
│                                              │
│                  Ingress                     │
│           (winauth.example.com)              │
└─────────────────────────────────────────────┘
                        │
                        │ Kerberos/SPNEGO
                        ▼
                 Active Directory
                (dc.domain.com)
```

## 📁 ファイル構成

```
k8s/
├── README.md           # このファイル
├── configmap.yaml      # Kerberos設定とアプリ設定
├── secret.yaml         # Keytabファイル（暗号化）
├── deployment.yaml     # アプリケーションPod定義
├── service.yaml        # Service/Ingress定義
└── rbac.yaml          # ServiceAccount/Role定義
```

## 🔧 設定詳細

### ConfigMap構成
- **krb5-config**: Kerberos設定ファイル
- **app-config**: Spring Boot設定ファイル

### Secret構成
- **kerberos-keytab**: Keytabファイル（base64エンコード）
- **ldap-credentials**: LDAP接続情報（オプション）

### Deployment設定
- **レプリカ数**: 2（高可用性）
- **DNS設定**: カスタムDNS（AD解決用）
- **ヘルスチェック**: Liveness/Readiness Probe
- **リソース制限**: CPU/メモリ制限設定

### Service/Ingress
- **Service**: ClusterIP（内部通信）
- **Ingress**: NGINX（外部公開）
- **セッションアフィニティ**: ClientIP（3時間）

## 🔍 トラブルシューティング

### Pod状態確認
```bash
# Pod一覧
kubectl get pods -n winauth

# Podの詳細
kubectl describe pod <pod-name> -n winauth

# ログ確認
kubectl logs -f <pod-name> -n winauth

# Pod内部にアクセス
kubectl exec -it <pod-name> -n winauth -- bash
```

### Kerberos認証デバッグ
```bash
# Pod内でKerberosチケット確認
kubectl exec -it <pod-name> -n winauth -- klist

# Keytab確認
kubectl exec -it <pod-name> -n winauth -- klist -k /etc/krb5/krb5.keytab

# DNS解決確認
kubectl exec -it <pod-name> -n winauth -- nslookup dc.domain.com
```

### ConfigMap/Secret確認
```bash
# ConfigMap内容確認
kubectl get configmap krb5-config -n winauth -o yaml

# Secret存在確認（内容は暗号化）
kubectl get secret kerberos-keytab -n winauth
```

## 🔐 セキュリティ考慮事項

### Keytab保護
- Secretとして保存（base64エンコード）
- Pod内では読み取り専用でマウント
- ファイル権限: 400

### ネットワークセキュリティ
- NetworkPolicyで通信制限（推奨）
- TLS/HTTPSでの通信暗号化
- Ingressでの認証ヘッダー転送

### RBAC
- 最小権限の原則
- ServiceAccountによる権限分離
- SecretへのアクセスはPodのみ

## 📝 環境変数一覧

| 環境変数 | 説明 | デフォルト値 |
|---------|------|------------|
| KERBEROS_PRINCIPAL | サービスプリンシパル名 | HTTP/winauth-service.winauth.svc.cluster.local@DOMAIN.COM |
| KERBEROS_KEYTAB | Keytabファイルパス | /etc/krb5/krb5.keytab |
| AD_DOMAIN | ADドメイン名 | DOMAIN.COM |
| AD_URL | LDAP URL | ldap://dc.domain.com:389 |
| KRB5_CONFIG | Kerberos設定ファイル | /etc/krb5/krb5.conf |
| JAVA_OPTS | JVMオプション | Kerberos関連設定 |

## 🚨 よくある問題と解決方法

### 1. DNS解決エラー
```yaml
# deployment.yamlのdnsConfigを確認
dnsConfig:
  nameservers:
    - "YOUR_AD_DNS_IP"  # 実際のAD DNSサーバーIPに変更
```

### 2. 時刻同期エラー
```bash
# K8Sノードで時刻同期を確認
timedatectl status

# NTP同期
sudo ntpdate dc.domain.com
```

### 3. Keytabエラー
```bash
# Keytabが正しくマウントされているか確認
kubectl exec -it <pod-name> -n winauth -- ls -la /etc/krb5/

# Keytabの内容確認
kubectl exec -it <pod-name> -n winauth -- klist -k /etc/krb5/krb5.keytab
```

## 📊 監視とログ

### Prometheus メトリクス（オプション）
```yaml
# ServiceMonitor設定例
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: winauth-metrics
  namespace: winauth
spec:
  selector:
    matchLabels:
      app: winauth
  endpoints:
  - port: http
    path: /actuator/prometheus
```

### ログ集約（オプション）
```bash
# Fluentd/Elasticsearch設定
kubectl logs -n winauth -l app=winauth --tail=100
```

## 🔄 CI/CD統合

### GitHub Actions例
```yaml
- name: Deploy to K8S
  run: |
    kubectl apply -f k8s/
    kubectl rollout status deployment/winauth-server -n winauth
```

### ArgoCD Application例
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: winauth
spec:
  source:
    repoURL: https://github.com/yourorg/winauth
    path: k8s
    targetRevision: main
  destination:
    server: https://kubernetes.default.svc
    namespace: winauth
```