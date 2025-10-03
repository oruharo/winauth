# 第6章: トラブルシューティング

## 6.1 Kerberos認証の問題

### 6.1.1 Kerberosチケット取得失敗

**エラー**: `KRB_AP_ERR_TKT_EXPIRED`

**原因**: Kerberosチケットの有効期限切れ

**解決策**:
```bash
# Windowsクライアントでチケット更新
klist purge
# ログアウト・ログインで再取得

# Linuxで確認
klist
kinit username@DOMAIN.LAB
```

### 6.1.2 Keytab検証失敗

**エラー**: `500 Internal Server Error`、ログに`Keytab validation failed`

**原因**: Keytabファイルのパスまたは権限の問題

**解決策**:
```bash
# Keytabファイル確認
ls -la /etc/kerberos/winauth.keytab
sudo chmod 600 /etc/kerberos/winauth.keytab

# Keytab内容確認
sudo klist -k /etc/kerberos/winauth.keytab

# application.propertiesのパス確認
kerberos.keytab=/etc/kerberos/winauth.keytab
```

### 6.1.3 時刻同期エラー

**エラー**: `KRB_AP_ERR_SKEW`

**原因**: クライアント/サーバーとKDCの時刻差が5分以上

**解決策**:
```bash
# NTPサービス確認
sudo systemctl status chronyd
sudo timedatectl

# ドメインコントローラーと時刻同期
sudo ntpdate -q 10.0.10.10

# 時刻を強制同期
sudo ntpdate -b 10.0.10.10
```

### 6.1.4 SPN重複エラー

**エラー**: `Duplicate SPN found`

**原因**: 同じSPNが複数のアカウントに登録されている

**解決策**:
```powershell
# DC重複SPN確認
setspn -X

# 既存SPN削除
setspn -D HTTP/alb.amazonaws.com old-account

# 正しいアカウントに登録
setspn -A HTTP/alb.amazonaws.com svc-winauth
```

### 6.1.5 RC4暗号化エラー (Java 17)

**エラー**: `Encryption type RC4 with HMAC is not supported/enabled`

**原因**: Java 17でRC4がデフォルト無効

**解決策**:
```bash
# krb5.confに追加
[libdefaults]
    allow_weak_crypto = true

# JVM起動オプション
-Djava.security.krb5.allow_weak_crypto=true
-Dsun.security.krb5.disableReferrals=true

# Dockerの場合、環境変数で設定
JAVA_TOOL_OPTIONS=-Djava.security.krb5.conf=/config/krb5.conf
```

## 6.2 NTLM認証の問題

### 6.2.1 401エラーが繰り返し発生

**エラー**: 継続的な`401 Unauthorized`

**原因**: NTLMハンドシェイクの失敗

**解決策**:
```bash
# アプリケーションログでハンドシェイク確認
# Type1, Type2, Type3メッセージの流れを確認
logging.level.com.example.adauth.security=DEBUG
logging.level.waffle=DEBUG

# ブラウザのネットワークタブで確認
# WWW-Authenticate: NTLM ヘッダーを確認
```

### 6.2.2 DOMAIN2ユーザーが認証できない

**エラー**: DOMAIN1ユーザーは成功、DOMAIN2ユーザーは失敗

**原因**: 信頼関係の設定不備

**解決策**:
```powershell
# DC1で信頼関係確認
nltest /domain_trusts

# 信頼関係の詳細確認
netdom trust DOMAIN1.LAB /domain:DOMAIN2.LAB /verify
netdom trust DOMAIN2.LAB /domain:DOMAIN1.LAB /verify

# 信頼関係の再作成（必要に応じて）
netdom trust DOMAIN1.LAB /domain:DOMAIN2.LAB /add /twoway
```

### 6.2.3 Linux環境でNTLM認証失敗

**エラー**: `Domain controller unreachable`

**原因**: JCIFSの設定不備またはネットワーク問題

**解決策**:
```properties
# application-ntlm.propertiesの確認
jcifs.smb.client.domain=DOMAIN1.LAB
jcifs.smb.client.laddr=10.0.30.10
jcifs.netbios.wins=10.0.10.10,10.0.20.10

# ネットワーク疎通確認
ping 10.0.10.10
telnet 10.0.10.10 445
telnet 10.0.10.10 139
```

### 6.2.4 ユーザー情報が取得できない

**エラー**: 認証成功だがユーザー情報が空

**原因**: WindowsPrincipalの取得失敗

**解決策**:
```xml
<!-- pom.xmlでWaffleバージョン確認 -->
<dependency>
    <groupId>com.github.waffle</groupId>
    <artifactId>waffle-spring-boot3</artifactId>
    <version>3.3.0</version>
</dependency>

<!-- JCIFSバージョン確認 -->
<dependency>
    <groupId>org.samba.jcifs</groupId>
    <artifactId>jcifs</artifactId>
    <version>2.1.30</version>
</dependency>
```

## 6.3 Docker関連の問題

### 6.3.1 設定ファイルがディレクトリになる

**エラー**: `/config/application.properties is a directory`

**原因**: Docker volume mountのパス問題

**解決策**:
```yaml
# docker-compose.yml修正
volumes:
  # ❌ 間違い: Gitリポジトリ内のconfig/を使用
  - ./config/application.properties:/config/application.properties:ro

  # ✅ 正しい: app-config/を使用
  - ../../app-config/application.properties:/config/application.properties:ro
```

### 6.3.2 Port already in use

**エラー**: `bind: address already in use`

**原因**: 既存のサービスやコンテナがポート使用中

**解決策**:
```bash
# 古いコンテナを停止・削除
docker stop $(docker ps -aq)
docker rm $(docker ps -aq)

# systemdサービスも停止
sudo systemctl stop winauth
sudo systemctl stop nginx

# ポート使用状況確認
sudo lsof -i :8082
sudo ss -tulpn | grep 8082
```

### 6.3.3 krb5.confマウントエラー

**エラー**: `/etc/krb5.conf not a directory`

**原因**: コンテナ内で/etc/krb5.confが既にディレクトリとして存在

**解決策**:
```yaml
# docker-compose.yml修正
volumes:
  # ❌ 間違い
  - ../../app-config/krb5.conf:/etc/krb5.conf:ro

  # ✅ 正しい: 別のパスにマウント
  - ../../app-config/krb5.conf:/config/krb5.conf:ro

environment:
  - KRB5_CONFIG=/config/krb5.conf
  - JAVA_TOOL_OPTIONS=-Djava.security.krb5.conf=/config/krb5.conf
```

## 6.4 ネットワーク関連の問題

### 6.4.1 DNS解決失敗

**エラー**: `Unknown host`、`Name or service not known`

**原因**: DNS設定の問題

**解決策**:
```bash
# /etc/hostsに追加
echo "10.0.10.10 dc1.domain1.lab DC1.DOMAIN1.LAB" | sudo tee -a /etc/hosts
echo "10.0.20.10 dc2.domain2.lab DC2.DOMAIN2.LAB" | sudo tee -a /etc/hosts

# DNS設定確認
cat /etc/resolv.conf
nslookup dc1.domain1.lab
dig dc1.domain1.lab
```

### 6.4.2 ファイアウォールブロック

**エラー**: `Connection timeout`

**原因**: ファイアウォールがKerberos/NTLM通信をブロック

**解決策**:
```bash
# 必要なポートを開放
# Kerberos: 88 (KDC)
# LDAP: 389, 636 (SSL)
# SMB/CIFS: 445, 139

# AWS Security Group設定確認
# インバウンドルールに以下を追加:
# - TCP 88 (Kerberos)
# - TCP 389 (LDAP)
# - TCP 445 (SMB)
```

## 6.5 クロスドメイン認証の問題

### 6.5.1 WIN2から500エラー

**エラー**: WIN1は成功、WIN2は500エラー

**原因**: クロスドメインKerberos制約、またはNTLMトークン処理失敗

**解決策**:
- **短期**: NTLM認証に切り替え（第3章参照）
- **長期**: 両ドメインkeytab統合（第4章参照）

### 6.5.2 ALB DNS名とSPNの不一致

**エラー**: `Service ticket validation failed`

**原因**: SPNに登録したホスト名とALB DNS名が不一致

**解決策**:
```powershell
# 正しいALB DNS名でSPN登録
setspn -A HTTP/has-winauth-alb-424888436.ap-northeast-1.elb.amazonaws.com svc-winauth

# application-kerberos.propertiesも確認
kerberos.principal=HTTP/has-winauth-alb-424888436.ap-northeast-1.elb.amazonaws.com@DOMAIN1.LAB
```

## 6.6 デバッグ手法

### 6.6.1 Kerberosデバッグ

```bash
# JVMデバッグオプション
-Dsun.security.krb5.debug=true
-Djava.security.debug=gssloginconfig,configfile,configparser,logincontext

# Kerberos手動テスト
kinit username@DOMAIN.LAB
klist
kvno HTTP/alb.amazonaws.com@DOMAIN.LAB

# Wireshark/tcpdumpでパケット確認
sudo tcpdump -i any port 88 -w kerberos.pcap
```

### 6.6.2 NTLMデバッグ

```properties
# application-ntlm.properties
logging.level.com.example.adauth.security=DEBUG
logging.level.waffle=DEBUG
logging.level.jcifs=DEBUG
logging.level.org.springframework.security=DEBUG
```

```bash
# ブラウザDevToolsでネットワーク確認
# - Authorization: Negotiate ヘッダー
# - WWW-Authenticate: NTLM ヘッダー
# - Type1, Type2, Type3メッセージの流れ
```

### 6.6.3 ログ分析

```bash
# Spring Boot認証ログ
grep "Authentication" /var/log/winauth/application.log
grep "NTLM\|Kerberos" /var/log/winauth/application.log

# Dockerコンテナログ
docker logs winauth-kerberos-app
docker logs winauth-ntlm-app

# システムログ
sudo journalctl -u winauth.service | grep -i auth
```

## 6.7 よくある質問 (FAQ)

**Q: KerberosとNTLM、どちらを選ぶべきか？**

A: 認証方式の選択については、[第1章1.3節「選択時の考慮事項」](./01_WINDOWS_INTEGRATED_AUTH.md#13-選択時の考慮事項)を参照してください。

**Q: 認証は成功するがユーザー情報が取得できない**

A: WindowsPrincipalの取得を確認。第3章のコントローラー実装を参照。

**Q: HTTPS必須か？**

A: 本番環境では必須。特にNTLMはHTTPSなしでは脆弱。

**Q: Makefileで自動生成されるinventory.ymlが変更される**

A: Makefileが自動生成するため、手動編集は無効。Makefileを修正してalb_dns_nameを追加。

**Q: Pass-the-Hash攻撃への対策は？**

A:
- 端末のウイルス対策強化
- HTTPS必須
- 定期的なパスワード変更
- NTLMv2強制（NTLMv1無効化）

## まとめ

トラブルシューティングの基本アプローチ：

1. **ログ確認**: アプリケーションログ、システムログ、Dockerログ
2. **ネットワーク確認**: DNS、ファイアウォール、疎通確認
3. **設定確認**: SPN、keytab、krb5.conf、application.properties
4. **デバッグ有効化**: 詳細ログ出力で問題箇所を特定
5. **段階的確認**: 認証フローを順に確認

## 参考資料

- [第1章: Windows統合認証の基礎](./01_WINDOWS_INTEGRATED_AUTH.md)
- [第2章: Kerberos認証詳解](./02_KERBEROS.md)
- [第3章: NTLM認証詳解](./03_NTLM.md)
- [第4章: クロスドメイン認証](./04_CROSS_DOMAIN.md)
- [第5章: 環境別セットアップ](./05_SETUP.md)
