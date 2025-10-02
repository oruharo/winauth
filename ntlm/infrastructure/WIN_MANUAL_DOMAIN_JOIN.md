# Windows クライアント手動ドメイン参加手順

## WIN1をDOMAIN1.LABに参加させる

### 1. WIN1にRDP接続
```
mstsc /v:<WIN1のパブリックIP>
ユーザー名: Administrator
パスワード: 設定したAdminPassword
```

### 2. DNSサーバーの確認
```powershell
# PowerShellで確認
ipconfig /all
# DNSサーバーが10.0.10.10になっていることを確認

# DNS解決テスト
nslookup DOMAIN1.LAB
ping dc1.domain1.lab
```

### 3. ドメイン参加（GUI方法）
1. スタート → 設定 → システム → バージョン情報
2. 「ドメインまたはワークグループの変更」をクリック
3. 「変更」ボタンをクリック
4. 「ドメイン」を選択し、`DOMAIN1.LAB` と入力
5. OKをクリック
6. 認証情報入力:
   - ユーザー名: `Administrator`
   - パスワード: 設定したAdminPassword
7. 成功メッセージ確認後、再起動

### 4. ドメイン参加（PowerShell方法）
```powershell
# 資格情報を作成
$password = ConvertTo-SecureString "設定したAdminPassword" -AsPlainText -Force
$credential = New-Object System.Management.Automation.PSCredential("DOMAIN1\Administrator", $password)

# ドメイン参加
Add-Computer -DomainName "DOMAIN1.LAB" -Credential $credential -Force -Restart
```

### 5. ドメイン参加確認（再起動後）
```powershell
# ドメイン参加状態確認
Get-ComputerInfo | Select CsDomain, CsDomainRole
# 期待値:
# CsDomain: DOMAIN1.LAB
# CsDomainRole: MemberServer

# セキュアチャネル確認
Test-ComputerSecureChannel
```

## WIN2をDOMAIN2.LABに参加させる

### 1. WIN2にRDP接続
```
mstsc /v:<WIN2のパブリックIP>
ユーザー名: Administrator
パスワード: 設定したAdminPassword
```

### 2. DNSサーバーの確認
```powershell
ipconfig /all
# DNSサーバーが10.0.20.10になっていることを確認

nslookup DOMAIN2.LAB
ping dc2.domain2.lab
```

### 3. ドメイン参加（GUI方法）
1. スタート → 設定 → システム → バージョン情報
2. 「ドメインまたはワークグループの変更」をクリック
3. 「変更」ボタンをクリック
4. 「ドメイン」を選択し、`DOMAIN2.LAB` と入力
5. OKをクリック
6. 認証情報入力:
   - ユーザー名: `Administrator`
   - パスワード: 設定したAdminPassword
7. 成功メッセージ確認後、再起動

### 4. ドメイン参加確認（再起動後）
```powershell
Get-ComputerInfo | Select CsDomain, CsDomainRole
# 期待値:
# CsDomain: DOMAIN2.LAB
# CsDomainRole: MemberServer
```

## ドメインユーザーでのRDP接続設定

### WIN1でuser1のRDP許可
```powershell
# WIN1で実行（Administratorとして）
Add-LocalGroupMember -Group "Remote Desktop Users" -Member "DOMAIN1\user1"

# エラーが出る場合、NLA無効化
Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp' -Name "UserAuthentication" -Value 0

# グループメンバー確認
net localgroup "Remote Desktop Users"
```

### WIN2でuser2のRDP許可
```powershell
# WIN2で実行（Administratorとして）
Add-LocalGroupMember -Group "Remote Desktop Users" -Member "DOMAIN2\user2"

# NLA無効化（必要な場合）
Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp' -Name "UserAuthentication" -Value 0
```

## ドメインユーザーでのRDP接続テスト

### WIN1へのドメインユーザー接続
```
mstsc /v:<WIN1のパブリックIP>
ユーザー名: DOMAIN1\user1
パスワード: 設定したユーザーパスワード
```

### WIN2へのドメインユーザー接続
```
mstsc /v:<WIN2のパブリックIP>
ユーザー名: DOMAIN2\user2
パスワード: 設定したユーザーパスワード
```

## トラブルシューティング

### セキュアチャネルエラーの場合
```powershell
# セキュアチャネル修復
$password = ConvertTo-SecureString "設定したAdminPassword" -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential("DOMAIN1\Administrator", $password)
Test-ComputerSecureChannel -Repair -Credential $cred
```

### RPC Server Unavailableエラーの場合
```powershell
# サービス再起動
Restart-Service -Name "Netlogon"
Restart-Service -Name "RpcSs"

# ファイアウォール確認
Get-NetFirewallProfile | Select Name, Enabled
```

### ドメイン参加時に既存アカウントエラーの場合
DC側で古いコンピューターアカウントを削除:
```powershell
# DC1またはDC2で実行
Remove-ADComputer -Identity "WIN1" -Confirm:$false
# または
Remove-ADComputer -Identity "WIN2" -Confirm:$false
```