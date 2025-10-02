#!/bin/bash

# AWS Kerberos環境 インフラ構築スクリプト (CloudFormation)

set -e

# 色付きメッセージ用の設定
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='[0m' # No Color

# 使用方法を表示する関数
show_usage() {
    echo -e "${BLUE}=== AWS Kerberos環境 インフラ構築 ===${NC}"
    echo "CloudFormationでインフラを構築します（約10分）"
    echo
    echo -e "${YELLOW}使用方法:${NC}"
    echo "  $0 [オプション] <KeyPair名> <Admin パスワード> <ユーザーパスワード>"
    echo
    echo -e "${YELLOW}オプション:${NC}"
    echo "  -p, --prefix PREFIX     リソース名のプレフィックス (デフォルト: KrbTest)"
    echo "  -o, --owner OWNER       Ownerタグの値 (デフォルト: anyone)"
    echo "  -h, --help              このヘルプを表示"
    echo
    echo -e "${YELLOW}例:${NC}"
    echo "  $0 my-keypair MySecurePass123! UserPass123!"
    echo "  $0 -p MyTest -o TestUser my-keypair MySecurePass123! UserPass123!"
}

# デフォルト値
PREFIX="KrbTest"
OWNER="anyone"
TEMPLATE_FILE="cloudformation.yaml"

# パラメータ解析
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--prefix)
            PREFIX="$2"
            shift 2
            ;;
        -o|--owner)
            OWNER="$2"
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

# スタック名設定（パラメータ解析後）
STACK_NAME="${PREFIX}-kerberos-test-${RANDOM}"

# 残りの位置パラメータを確認
if [ $# -ne 3 ]; then
    echo -e "${RED}エラー: KeyPair名、Admin パスワード、ユーザーパスワードが必要です${NC}" >&2
    show_usage
    exit 1
fi

KEY_NAME=$1
ADMIN_PASSWORD=$2
USER_PASSWORD=$3

echo -e "${BLUE}=== AWS Kerberos環境 インフラ構築 ===${NC}"
echo "CloudFormationでインフラを構築します（約10分）"
echo

echo -e "${GREEN}設定確認:${NC}"
echo "  プレフィックス: $PREFIX"
echo "  Ownerタグ: $OWNER"
echo "  スタック名: $STACK_NAME"
echo "  KeyPair: $KEY_NAME"
echo

echo -e "${GREEN}1. インフラ構築開始...${NC}"

# CloudFormationスタックの作成
echo "CloudFormationスタックを作成中..."
aws cloudformation create-stack \
    --stack-name $STACK_NAME \
    --template-body file://$TEMPLATE_FILE \
    --parameters ParameterKey=KeyName,ParameterValue=$KEY_NAME \
                 ParameterKey=AdminPassword,ParameterValue="$ADMIN_PASSWORD" \
                 ParameterKey=UserPassword,ParameterValue="$USER_PASSWORD" \
                 ParameterKey=Prefix,ParameterValue="$PREFIX" \
                 ParameterKey=Owner,ParameterValue="$OWNER" \
    --capabilities CAPABILITY_IAM

echo "スタック作成の完了を待機中..."
aws cloudformation wait stack-create-complete --stack-name $STACK_NAME

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ CloudFormationスタックが正常に作成されました${NC}"
else
    echo -e "${RED}✗ CloudFormationスタックの作成に失敗しました${NC}"
    exit 1
fi

echo -e "${GREEN}2. 構築完了情報の表示...${NC}"

# CloudFormationの出力を表示
echo -e "${BLUE}=== インフラ構築完了 ===${NC}"
echo
echo -e "${YELLOW}サーバー接続情報:${NC}"

# 全IP情報を表示
aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?contains(OutputKey, `IP`)].{Key:OutputKey,Value:OutputValue}' --output table

echo
echo -e "${YELLOW}次のステップ:${NC}"
echo "1. サーバーの起動完了まで約3分待機"
echo "2. deploy-applications.sh を実行してアプリケーション設定"
echo
echo -e "${GREEN}インフラ構築が完了しました！${NC}"
echo
echo -e "${YELLOW}スタック名: $STACK_NAME${NC}"
echo -e "${YELLOW}続けて: ./deploy-applications.sh $KEY_NAME \"$ADMIN_PASSWORD\" \"$USER_PASSWORD\" ~/.ssh/$KEY_NAME.pem $STACK_NAME${NC}"
echo -e "${YELLOW}続けて: ./deploy-applications.sh $KEY_NAME \"$ADMIN_PASSWORD\" \"$USER_PASSWORD\"${NC}"