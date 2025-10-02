#!/bin/bash

# AWS Kerberos環境 アプリケーション設定スクリプト (Ansible)

set -e

# プロキシ設定を無効化（macOSのプロキシ設定によるPythonクラッシュを防ぐ）
export no_proxy="*"
export NO_PROXY="*"
export http_proxy=""
export https_proxy=""
export HTTP_PROXY=""
export HTTPS_PROXY=""

# 色付きメッセージ用の設定
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 使用方法を表示する関数
show_usage() {
    echo -e "${BLUE}=== AWS Kerberos環境 アプリケーション設定 ===${NC}"
    echo "Ansibleでアプリケーション設定を行います（約15分）"
    echo
    echo -e "${YELLOW}使用方法:${NC}"
    echo "  $0 [オプション] <KeyPair名> <Admin パスワード> <ユーザーパスワード> [SSH秘密鍵パス] [スタック名]"
    echo
    echo -e "${YELLOW}オプション:${NC}"
    echo "  -p, --prefix PREFIX     検索するスタックのプレフィックス (デフォルト: KrbTest)"
    echo "  -s, --server SERVER     実行対象サーバー (dc1, dc2, win1, win2, linux-app, all)"
    echo "  -t, --task TASK         実行タスク (domain-controllers, windows-clients, linux-app, all)"
    echo "  -h, --help              このヘルプを表示"
    echo
    echo -e "${YELLOW}例:${NC}"
    echo "  $0 my-keypair MySecurePass123! UserPass123!"
    echo "  $0 -s dc1 my-keypair MySecurePass123! UserPass123!"
    echo "  $0 -t domain-controllers my-keypair MySecurePass123! UserPass123!"
    echo "  $0 -s linux-app my-keypair MySecurePass123! UserPass123!"
}

# デフォルト値
PREFIX="KrbTest"
SERVER="all"
TASK="all"

# パラメータ解析
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--prefix)
            PREFIX="$2"
            shift 2
            ;;
        -s|--server)
            SERVER="$2"
            shift 2
            ;;
        -t|--task)
            TASK="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        -*)
            echo -e "${RED}エラー: 不明なオプション $1${NC}" >&2
            show_usage
            exit 1
            ;;
        *)
            break
            ;;
    esac
done

# 残りの位置パラメータを確認
if [ $# -lt 3 ]; then
    echo -e "${RED}エラー: KeyPair名、Admin パスワード、ユーザーパスワードが必要です${NC}" >&2
    show_usage
    exit 1
fi

KEY_NAME=$1
ADMIN_PASSWORD=$2
USER_PASSWORD=$3
SSH_KEY_PATH=${4:-~/.ssh/${KEY_NAME}.pem}

# スタック名を取得または検索
if [ $# -ge 5 ] && [ -n "$5" ]; then
    STACK_NAME="$5"
else
    # 最新のスタックを検索（プレフィックス使用）
    STACK_NAME=$(aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE --query "StackSummaries[?starts_with(StackName, \`${PREFIX}-kerberos-test-\`)].StackName" --output text | head -1)
    if [ -z "$STACK_NAME" ]; then
        echo -e "${RED}エラー: ${PREFIX}-kerberos-test-* スタックが見つかりません${NC}"
        echo "deploy-infrastructure.sh を先に実行してください"
        exit 1
    fi
fi

echo -e "${BLUE}=== AWS Kerberos環境 アプリケーション設定 ===${NC}"
echo "Ansibleでアプリケーション設定を行います（約15分）"
echo

echo -e "${GREEN}設定確認:${NC}"
echo "  プレフィックス: $PREFIX"
echo "  スタック名: $STACK_NAME"
echo "  KeyPair: $KEY_NAME"
echo "  SSH鍵: $SSH_KEY_PATH"
echo "  対象サーバー: $SERVER"
echo "  実行タスク: $TASK"
echo

# SSH鍵の存在確認
if [ ! -f "$SSH_KEY_PATH" ]; then
    echo -e "${RED}エラー: SSH秘密鍵が見つかりません: $SSH_KEY_PATH${NC}"
    exit 1
fi

# Ansibleの確認
if ! command -v ansible-playbook &> /dev/null; then
    echo -e "${RED}エラー: Ansibleがインストールされていません${NC}"
    echo "インストール方法:"
    echo "  macOS: brew install ansible"
    echo "  Ubuntu: sudo apt install ansible"
    echo "  CentOS/RHEL: sudo yum install ansible"
    exit 1
fi

# Ansibleコレクションの確認・インストール
echo -e "${GREEN}Ansibleコレクションの確認...${NC}"

# コレクションの存在確認関数
check_ansible_collection() {
    local collection=$1
    ansible-galaxy collection list | grep -q "$collection" 2>/dev/null
    return $?
}

# 必要なコレクションをチェック
COLLECTIONS_MISSING=false
REQUIRED_COLLECTIONS=("amazon.aws" "community.aws")

for collection in "${REQUIRED_COLLECTIONS[@]}"; do
    if ! check_ansible_collection "$collection"; then
        echo -e "${YELLOW}コレクション $collection が見つかりません${NC}"
        COLLECTIONS_MISSING=true
    else
        echo -e "${GREEN}✓ コレクション $collection は既にインストール済み${NC}"
    fi
done

# 不足しているコレクションをインストール
if [ "$COLLECTIONS_MISSING" = true ]; then
    echo -e "${GREEN}不足しているAnsibleコレクションをインストール中...${NC}"
    
    if [ -f "ansible/requirements.yml" ]; then
        ansible-galaxy collection install -r ansible/requirements.yml --force
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Ansibleコレクションのインストールが完了しました${NC}"
        else
            echo -e "${RED}エラー: Ansibleコレクションのインストールに失敗しました${NC}"
            exit 1
        fi
    else
        # requirements.ymlが見つからない場合は個別インストール
        for collection in "${REQUIRED_COLLECTIONS[@]}"; do
            if ! check_ansible_collection "$collection"; then
                echo "Installing $collection..."
                ansible-galaxy collection install "$collection" --force
            fi
        done
    fi
else
    echo -e "${GREEN}✓ 必要なAnsibleコレクションは全て利用可能です${NC}"
fi

echo -e "${GREEN}1. CloudFormation出力の取得...${NC}"

# CloudFormationの出力を取得
DC1_IP=$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==`DC1PublicIP`].OutputValue' --output text)
DC2_IP=$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==`DC2PublicIP`].OutputValue' --output text)
WIN1_IP=$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==`WIN1PublicIP`].OutputValue' --output text)
WIN2_IP=$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==`WIN2PublicIP`].OutputValue' --output text)
LINUX_PUBLIC_IP=$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==`LinuxAppPublicIP`].OutputValue' --output text)

echo "DC1 IP: $DC1_IP"
echo "DC2 IP: $DC2_IP"
echo "WIN1 IP: $WIN1_IP"
echo "WIN2 IP: $WIN2_IP"
echo "Linux IP: $LINUX_PUBLIC_IP"

echo -e "${GREEN}2. 統合インベントリ作成...${NC}"

# inventoryディレクトリが存在しない場合は作成
[ ! -d "ansible/inventory" ] && mkdir -p ansible/inventory

# 統合インベントリを動的生成
cat > ansible/inventory/inventory.yml << EOF
all:
  children:
    # Windows Domain Controllers
    domain_controllers:
      hosts:
        dc1:
          ansible_host: $DC1_IP
          domain_name: "DOMAIN1.LAB"
          domain_netbios: "DOMAIN1"
        dc2:
          ansible_host: $DC2_IP
          domain_name: "DOMAIN2.LAB"
          domain_netbios: "DOMAIN2"
    
    # Windows Clients
    windows_clients:
      hosts:
        win1:
          ansible_host: $WIN1_IP
          target_domain: "DOMAIN1.LAB"
          domain_controller: "10.0.10.10"
          domain_user: "user1"
        win2:
          ansible_host: $WIN2_IP
          target_domain: "DOMAIN2.LAB"
          domain_controller: "10.0.20.10"
          domain_user: "user2"
    
    # Linux Application Server
    linux_servers:
      hosts:
        linux-app:
          ansible_host: $LINUX_PUBLIC_IP
          ansible_user: ec2-user
          ansible_ssh_private_key_file: $SSH_KEY_PATH
          ansible_ssh_common_args: '-o StrictHostKeyChecking=no'
    
    # Windows group (DC + Clients)
    windows:
      children:
        domain_controllers:
        windows_clients:
      vars:
        ansible_user: Administrator
        ansible_password: $ADMIN_PASSWORD
        ansible_connection: winrm
        ansible_winrm_transport: basic
        ansible_winrm_server_cert_validation: ignore
        ansible_winrm_scheme: http
        ansible_port: 5985
        
  # Global variables
  vars:
    # Passwords
    admin_password: "$ADMIN_PASSWORD"
    user_password: "$USER_PASSWORD"
    
    # Network settings
    dc1_ip: "10.0.10.10"
    dc2_ip: "10.0.20.10"
    app_hostname: "winauth.app.local"
    app_private_ip: "10.0.30.10"
    
    # Kerberos settings
    primary_realm: "DOMAIN1.LAB"
    secondary_realm: "DOMAIN2.LAB"
    
    # Application settings
    java_package: "java-17-amazon-corretto-devel"
    app_user: "ec2-user"
    app_dir: "/opt/winauth"
    keytab_dir: "/etc/kerberos"
EOF

# 実行タスクの判定と実行
run_domain_controllers() {
    echo -e "${GREEN}3. ドメインコントローラー設定...${NC}"
    echo "ドメインコントローラーを設定中..."

    local LIMIT_OPTION=""
    if [ "$SERVER" != "all" ] && [[ "$SERVER" =~ ^(dc1|dc2)$ ]]; then
        LIMIT_OPTION="--limit $SERVER"
    fi

    export STACK_NAME="$STACK_NAME"
    ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-domain-controllers.yml $LIMIT_OPTION -v

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ドメインコントローラー設定が完了しました${NC}"
    else
        echo -e "${RED}✗ ドメインコントローラー設定に失敗しました${NC}"
        exit 1
    fi
}

run_windows_clients() {
    echo -e "${GREEN}4. Windows クライアント設定...${NC}"
    echo "Windows クライアントを設定中..."

    local LIMIT_OPTION=""
    if [ "$SERVER" != "all" ] && [[ "$SERVER" =~ ^(win1|win2)$ ]]; then
        LIMIT_OPTION="--limit $SERVER"
    fi

    ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-windows-clients.yml $LIMIT_OPTION -v

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Windows クライアント設定が完了しました${NC}"
    else
        echo -e "${RED}✗ Windows クライアント設定に失敗しました${NC}"
        exit 1
    fi

    echo -e "${GREEN}5. ブラウザ設定（Kerberos認証用）...${NC}"
    echo "Windows クライアントのブラウザを設定中..."

    export STACK_NAME="$STACK_NAME"
    ansible-playbook -i ansible/inventory/inventory.yml ansible/configure-browser-kerberos.yml $LIMIT_OPTION -v

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ブラウザ設定が完了しました${NC}"
    else
        echo -e "${RED}✗ ブラウザ設定に失敗しました${NC}"
        exit 1
    fi
}

run_linux_app() {
    echo -e "${GREEN}3. ALB Target Groups設定...${NC}"

    # ALB Target Groups作成
    echo "ALB Target Groupsを作成中..."
    export STACK_NAME="$STACK_NAME"
    export PREFIX="$PREFIX"

    ansible-playbook -i localhost, ansible/setup-alb-target-groups.yml -v
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ALB Target Groups作成が完了しました${NC}"
    else
        echo -e "${RED}✗ ALB Target Groups作成に失敗しました${NC}"
        exit 1
    fi

    echo -e "${GREEN}4. ALB Listeners設定...${NC}"

    # ALB Listeners作成
    echo "ALB Listenersを作成中..."

    ansible-playbook -i localhost, ansible/setup-alb-listeners.yml -v
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ALB Listeners作成が完了しました${NC}"
    else
        echo -e "${RED}✗ ALB Listeners作成に失敗しました${NC}"
        exit 1
    fi

    echo -e "${GREEN}5. Linux アプリケーション設定...${NC}"

    # SSH接続の準備を待つ
    echo "SSH接続の準備を待機中..."
    sleep 30

    # Ansibleでアプリケーションをデプロイ
    ansible-playbook -i ansible/inventory/inventory.yml ansible/deploy-linux.yml -v

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Linux アプリケーション設定が完了しました${NC}"
    else
        echo -e "${RED}✗ Linux アプリケーション設定に失敗しました${NC}"
        exit 1
    fi
}

# タスク実行制御
case "$TASK" in
    "domain-controllers")
        if [[ "$SERVER" == "all" || "$SERVER" =~ ^(dc1|dc2)$ ]]; then
            run_domain_controllers
        else
            echo -e "${RED}エラー: domain-controllersタスクは dc1, dc2, または all のみ実行可能です${NC}"
            exit 1
        fi
        ;;
    "windows-clients")
        if [[ "$SERVER" == "all" || "$SERVER" =~ ^(win1|win2)$ ]]; then
            run_windows_clients
        else
            echo -e "${RED}エラー: windows-clientsタスクは win1, win2, または all のみ実行可能です${NC}"
            exit 1
        fi
        ;;
    "linux-app")
        if [[ "$SERVER" == "all" || "$SERVER" == "linux-app" ]]; then
            run_linux_app
        else
            echo -e "${RED}エラー: linux-appタスクは linux-app または all のみ実行可能です${NC}"
            exit 1
        fi
        ;;
    "all")
        if [[ "$SERVER" == "all" || "$SERVER" =~ ^(dc1|dc2)$ ]]; then
            run_domain_controllers
        fi
        if [[ "$SERVER" == "all" || "$SERVER" =~ ^(win1|win2)$ ]]; then
            run_windows_clients
        fi
        if [[ "$SERVER" == "all" || "$SERVER" == "linux-app" ]]; then
            run_linux_app
        fi
        ;;
    *)
        echo -e "${RED}エラー: 不明なタスク '$TASK'${NC}"
        echo "利用可能なタスク: domain-controllers, windows-clients, linux-app, all"
        exit 1
        ;;
esac

echo -e "${GREEN}6. 構築完了情報の表示...${NC}"

echo -e "${BLUE}=== 全体構築完了 ===${NC}"
echo
echo -e "${YELLOW}接続情報:${NC}"
echo "DC1 (DOMAIN1): $DC1_IP"
echo "DC2 (DOMAIN2): $DC2_IP"  
echo "WIN1: $WIN1_IP"
echo "WIN2: $WIN2_IP"
echo "Linux App: $LINUX_PUBLIC_IP"
echo
echo -e "${YELLOW}アプリケーションURL:${NC}"
echo "WinAuth: http://$LINUX_PUBLIC_IP:8082"
echo
echo -e "${YELLOW}SSH接続:${NC}"
echo "ssh -i $SSH_KEY_PATH ec2-user@$LINUX_PUBLIC_IP"
echo
echo -e "${GREEN}全ての設定が完了しました！${NC}"