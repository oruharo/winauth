# AWS NTLM環境 アプリケーション設定 Makefile
#
# 使用方法:
#   初回設定: make set-config
#   デプロイ:   make deploy

# コマンドラインからのパラメータ指定を検出
CMDLINE_KEY_NAME := $(filter command line,$(origin KEY_NAME))
CMDLINE_ADMIN_PASSWORD := $(filter command line,$(origin ADMIN_PASSWORD))
CMDLINE_USER_PASSWORD := $(filter command line,$(origin USER_PASSWORD))
CMDLINE_STACK_NAME := $(filter command line,$(origin STACK_NAME))
CMDLINE_ENV_PREFIX := $(filter command line,$(origin ENV_PREFIX))
CMDLINE_OWNER := $(filter command line,$(origin OWNER))
CMDLINE_TRUST_PASSWORD := $(filter command line,$(origin TRUST_PASSWORD))

# .envファイルから設定を読み込み (存在する場合)
-include .env

# デフォルト設定
STACK_NAME ?=
ENV_PREFIX ?=
OWNER ?= $(shell echo $${USER:-anyone})
SSH_KEY_PATH ?= ~/.ssh/$(KEY_NAME).pem

# 色設定
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m

# デフォルト値設定
SERVICE_PASSWORD ?= DefaultServicePass123!
TRUST_PASSWORD ?= DefaultTrustPass123!

# プロキシ設定の無効化
export no_proxy = *
export NO_PROXY = *
export http_proxy =
export https_proxy =
export HTTP_PROXY =
export HTTPS_PROXY =

##---------------------------------------------------------------------
##@ General
##---------------------------------------------------------------------
.PHONY: help
help: ## ヘルプメッセージを表示
	@awk 'BEGIN {FS = ":.*##"; printf "\n\033[34mAWS NTLM環境 アプリケーション設定 Makefile\033[0m\n\nUsage:\n  1. 設定:          make \033[36mset-config\033[0m\n  2. インフラ構築: make \033[36minfrastructure\033[0m\n  3. デプロイ:      make \033[36mdeploy\033[0m\n\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

set-config: ## 設定を対話式で入力して.envファイルに保存
            ## パラメータ指定例: make set-config KEY_NAME=xxx ADMIN_PASSWORD=xxx USER_PASSWORD=xxx STACK_NAME=xxx ENV_PREFIX=xxx OWNER=xxx TRUST_PASSWORD=xxx
	@# コマンドラインからパラメータが指定されている場合は全て必須
	@if [ -n "$(CMDLINE_KEY_NAME)" ] || [ -n "$(CMDLINE_ADMIN_PASSWORD)" ] || [ -n "$(CMDLINE_USER_PASSWORD)" ] || \
	   [ -n "$(CMDLINE_STACK_NAME)" ] || [ -n "$(CMDLINE_ENV_PREFIX)" ] || [ -n "$(CMDLINE_OWNER)" ] || [ -n "$(CMDLINE_TRUST_PASSWORD)" ]; then \
		if [ -z "$(CMDLINE_KEY_NAME)" ] || [ -z "$(CMDLINE_ADMIN_PASSWORD)" ] || [ -z "$(CMDLINE_USER_PASSWORD)" ] || \
		   [ -z "$(CMDLINE_STACK_NAME)" ] || [ -z "$(CMDLINE_ENV_PREFIX)" ] || [ -z "$(CMDLINE_OWNER)" ] || [ -z "$(CMDLINE_TRUST_PASSWORD)" ]; then \
			echo "$(RED)エラー: パラメータ指定時は全てのパラメータが必須です$(NC)"; \
			echo "必須: KEY_NAME, ADMIN_PASSWORD, USER_PASSWORD, STACK_NAME, ENV_PREFIX, OWNER, TRUST_PASSWORD"; \
			exit 1; \
		fi; \
		echo "# Auto-generated configuration" > .env; \
		echo "KEY_NAME=$(KEY_NAME)" >> .env; \
		echo "SSH_KEY_PATH=$(SSH_KEY_PATH)" >> .env; \
		echo "ADMIN_PASSWORD=$(ADMIN_PASSWORD)" >> .env; \
		echo "USER_PASSWORD=$(USER_PASSWORD)" >> .env; \
		echo "STACK_NAME=$(STACK_NAME)" >> .env; \
		echo "ENV_PREFIX=$(ENV_PREFIX)" >> .env; \
		echo "OWNER=$(OWNER)" >> .env; \
		echo "TRUST_PASSWORD=$(TRUST_PASSWORD)" >> .env; \
		echo "$(GREEN)✓ 設定を .env に保存しました$(NC)"; \
	else \
		echo "$(GREEN)=== 環境設定 ===$(NC)"; \
		echo ""; \
		if [ -f .env ]; then \
			echo "$(YELLOW)既存の設定が見つかりました。変更しない場合はEnterを押してください。$(NC)"; \
			echo ""; \
		fi; \
		current_key=$$(grep "^KEY_NAME=" .env 2>/dev/null | cut -d= -f2); \
		read -p "KEY_NAME (AWS KeyPair名)$${current_key:+ [$$current_key]}: " key_name; \
		key_name=$${key_name:-$$current_key}; \
		current_ssh=$$(grep "^SSH_KEY_PATH=" .env 2>/dev/null | cut -d= -f2); \
		default_ssh=$${current_ssh:-~/.ssh/$$key_name.pem}; \
		read -p "SSH_KEY_PATH [$$default_ssh]: " ssh_path; \
		ssh_path=$${ssh_path:-$$default_ssh}; \
		current_admin=$$(grep "^ADMIN_PASSWORD=" .env 2>/dev/null | cut -d= -f2); \
		read -p "ADMIN_PASSWORD (Administrator パスワード)$${current_admin:+ [$$current_admin]}: " admin_pass; \
		admin_pass=$${admin_pass:-$$current_admin}; \
		current_user=$$(grep "^USER_PASSWORD=" .env 2>/dev/null | cut -d= -f2); \
		read -p "USER_PASSWORD (ユーザーパスワード)$${current_user:+ [$$current_user]}: " user_pass; \
		user_pass=$${user_pass:-$$current_user}; \
		current_stack=$$(grep "^STACK_NAME=" .env 2>/dev/null | cut -d= -f2); \
		read -p "STACK_NAME (CloudFormationスタック名)$${current_stack:+ [$$current_stack]}: " stack_name; \
		stack_name=$${stack_name:-$$current_stack}; \
		current_env=$$(grep "^ENV_PREFIX=" .env 2>/dev/null | cut -d= -f2); \
		read -p "ENV_PREFIX (リソース名prefix、1-10文字英数字)$${current_env:+ [$$current_env]}: " env_prefix; \
		env_prefix=$${env_prefix:-$$current_env}; \
		current_owner=$$(grep "^OWNER=" .env 2>/dev/null | cut -d= -f2); \
		default_owner=$${current_owner:-$${USER:-anyone}}; \
		read -p "OWNER (タグ用、1-10文字英数字) [$$default_owner]: " owner; \
		owner=$${owner:-$$default_owner}; \
		current_trust=$$(grep "^TRUST_PASSWORD=" .env 2>/dev/null | cut -d= -f2); \
		default_trust=$${current_trust:-DefaultTrustPass123!}; \
		read -p "TRUST_PASSWORD [$$default_trust]: " trust_pass; \
		trust_pass=$${trust_pass:-$$default_trust}; \
		echo "# Auto-generated configuration" > .env; \
		echo "KEY_NAME=$$key_name" >> .env; \
		echo "SSH_KEY_PATH=$$ssh_path" >> .env; \
		echo "ADMIN_PASSWORD=$$admin_pass" >> .env; \
		echo "USER_PASSWORD=$$user_pass" >> .env; \
		echo "STACK_NAME=$$stack_name" >> .env; \
		echo "ENV_PREFIX=$$env_prefix" >> .env; \
		echo "OWNER=$$owner" >> .env; \
		echo "TRUST_PASSWORD=$$trust_pass" >> .env; \
		echo ""; \
		echo "$(GREEN)✓ 設定を .env に保存しました$(NC)"; \
		echo "$(YELLOW)次回以降は 'make deploy' だけで実行できます$(NC)"; \
	fi

show-config: ## 保存された設定を表示
	@if [ -f .env ]; then \
		echo "$(GREEN)保存された設定:$(NC)"; \
		cat .env | grep -v "^#" | sed 's/^/  /'; \
	else \
		echo "$(YELLOW)保存された設定はありません$(NC)"; \
		echo "$(YELLOW)make set-config で設定を保存してください$(NC)"; \
	fi

##---------------------------------------------------------------------
## Prerequisites
##---------------------------------------------------------------------

# .envファイルの存在チェック
check-env:
	@if [ ! -f .env ]; then \
		echo "$(RED)エラー: .env ファイルが見つかりません$(NC)"; \
		echo "$(YELLOW)先に 'make set-config' を実行してください$(NC)"; \
		exit 1; \
	fi

# SSH鍵の存在確認
check-ssh-key: check-env
	@echo "$(GREEN)SSH鍵を確認中...$(NC)"
	@EXPANDED_PATH=$$(eval echo "$(SSH_KEY_PATH)"); \
	if [ ! -f "$$EXPANDED_PATH" ]; then \
		echo "$(RED)エラー: SSH秘密鍵が見つかりません: $$EXPANDED_PATH$(NC)"; \
		echo "$(YELLOW)確認事項:$(NC)"; \
		echo "  - ファイルパスが正しいか: $$EXPANDED_PATH"; \
		echo "  - ファイルの権限が適切か: ls -la $$EXPANDED_PATH"; \
		exit 1; \
	fi
	@EXPANDED_PATH=$$(eval echo "$(SSH_KEY_PATH)"); \
	echo "$(GREEN)✓ SSH鍵が確認できました: $$EXPANDED_PATH$(NC)"

# Ansible環境のセットアップ
setup-ansible: check-env
	@echo "$(GREEN)Ansibleを確認中...$(NC)"
	@command -v ansible-playbook >/dev/null || (echo "$(RED)エラー: Ansibleがインストールされていません$(NC)" && exit 1)
	@echo "$(GREEN)✓ Ansible が利用可能です$(NC)"
	@echo "$(GREEN)Ansibleコレクションをインストール中...$(NC)"
	@ansible-galaxy collection install -r ansible/requirements.yml --force >/dev/null 2>&1
	@echo "$(GREEN)✓ Ansibleセットアップが完了しました$(NC)"

# CloudFormation出力の取得
get-cloudformation-outputs: check-env
	@echo "$(GREEN)CloudFormation出力を取得中...$(NC)"
	$(eval DC1_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`DC1PublicIP`].OutputValue' --output text))
	$(eval DC2_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`DC2PublicIP`].OutputValue' --output text))
	$(eval WIN1_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`WIN1PublicIP`].OutputValue' --output text))
	$(eval WIN2_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`WIN2PublicIP`].OutputValue' --output text))
	$(eval LINUX_PUBLIC_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`LinuxAppPublicIP`].OutputValue' --output text))
	$(eval ALB_DNS_NAME := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`ALBDNSName`].OutputValue' --output text))
	$(eval ALB_ARN := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`ALBArn`].OutputValue' --output text))
	$(eval VPC_ID := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`VPCId`].OutputValue' --output text))
	@echo "$(GREEN)✓ CloudFormation出力を取得しました$(NC)"

# 統合インベントリの作成（Make inline で生成）

ansible/inventory/inventory.yml: get-cloudformation-outputs
	@echo "$(GREEN)統合インベントリを作成中...$(NC)"
	@mkdir -p ansible/inventory
	@{ \
		echo "all:"; \
		echo "  children:"; \
		echo "    # Windows Domain Controllers"; \
		echo "    domain_controllers:"; \
		echo "      hosts:"; \
		echo "        dc1:"; \
		echo "          ansible_host: $(DC1_IP)"; \
		echo "          domain_name: \"DOMAIN1.LAB\""; \
		echo "          domain_netbios: \"DOMAIN1\""; \
		echo "        dc2:"; \
		echo "          ansible_host: $(DC2_IP)"; \
		echo "          domain_name: \"DOMAIN2.LAB\""; \
		echo "          domain_netbios: \"DOMAIN2\""; \
		echo "      vars:"; \
		echo "        ansible_connection: winrm"; \
		echo "        ansible_winrm_server_cert_validation: ignore"; \
		echo "        ansible_winrm_transport: basic"; \
		echo "        ansible_winrm_scheme: http"; \
		echo "        ansible_port: 5985"; \
		echo "        ansible_user: \"Administrator\""; \
		echo "        ansible_password: \"$(ADMIN_PASSWORD)\""; \
		echo ""; \
		echo "    # Windows Clients"; \
		echo "    windows_clients:"; \
		echo "      hosts:"; \
		echo "        win1:"; \
		echo "          ansible_host: $(WIN1_IP)"; \
		echo "          target_domain: \"DOMAIN1.LAB\""; \
		echo "          domain_controller: \"10.0.10.10\""; \
		echo "          domain_user: \"user1\""; \
		echo "        win2:"; \
		echo "          ansible_host: $(WIN2_IP)"; \
		echo "          target_domain: \"DOMAIN2.LAB\""; \
		echo "          domain_controller: \"10.0.20.10\""; \
		echo "          domain_user: \"user2\""; \
		echo "      vars:"; \
		echo "        ansible_connection: winrm"; \
		echo "        ansible_winrm_server_cert_validation: ignore"; \
		echo "        ansible_winrm_transport: basic"; \
		echo "        ansible_winrm_scheme: http"; \
		echo "        ansible_port: 5985"; \
		echo "        ansible_user: \"Administrator\""; \
		echo "        ansible_password: \"$(ADMIN_PASSWORD)\""; \
		echo ""; \
		echo "    # Linux Application Server"; \
		echo "    linux_servers:"; \
		echo "      hosts:"; \
		echo "        linux-app:"; \
		echo "          ansible_host: $(LINUX_PUBLIC_IP)"; \
		echo "          ansible_user: ec2-user"; \
		EXPANDED_SSH_PATH=$$(eval echo "$(SSH_KEY_PATH)"); \
		echo "          ansible_ssh_private_key_file: $$EXPANDED_SSH_PATH"; \
		echo "          ansible_ssh_common_args: '-o StrictHostKeyChecking=no'"; \
		echo ""; \
		echo "    # Windows group (DC + Clients)"; \
		echo "    windows:"; \
		echo "      children:"; \
		echo "        domain_controllers:"; \
		echo "        windows_clients:"; \
		echo ""; \
		echo "  # Global variables"; \
		echo "  vars:"; \
		echo "    # Passwords"; \
		echo "    admin_password: \"$(ADMIN_PASSWORD)\""; \
		echo "    user_password: \"$(USER_PASSWORD)\""; \
		echo "    service_password: \"$(SERVICE_PASSWORD)\""; \
		echo "    trust_password: \"$(TRUST_PASSWORD)\""; \
		echo ""; \
		echo "    # Network settings"; \
		echo "    dc1_ip: \"10.0.10.10\""; \
		echo "    dc2_ip: \"10.0.20.10\""; \
		echo "    app_hostname: \"winauth.app.local\""; \
		echo "    app_private_ip: \"10.0.30.10\""; \
		echo "    linux_public_ip: \"$(LINUX_PUBLIC_IP)\""; \
		echo "    alb_dns_name: \"$(ALB_DNS_NAME)\""; \
		echo "    alb_arn: \"$(ALB_ARN)\""; \
		echo "    vpc_id: \"$(VPC_ID)\""; \
		echo ""; \
		echo "    # Application settings"; \
		echo "    java_package: \"java-17-amazon-corretto-devel\""; \
		echo "    app_user: \"ec2-user\""; \
		echo "    app_dir: \"/opt/winauth\""; \
	} > $@
	@echo "$(GREEN)✓ インベントリファイルを作成しました$(NC)"

##---------------------------------------------------------------------
## Core Tasks (Combined Workflows)
##---------------------------------------------------------------------

# ドメインコントローラー設定
.run-domain-controllers: ansible/inventory/inventory.yml
	@echo "$(GREEN)ドメインコントローラーを設定中...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-domain-controllers.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ ドメインコントローラー設定が完了しました$(NC)"

# Group Policy設定（イントラネットゾーン）
.run-group-policy: ansible/inventory/inventory.yml
	@echo "$(GREEN)Group Policy（イントラネットゾーン設定）を適用中...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-group-policy-intranet.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ Group Policy設定が完了しました$(NC)"

# Windows クライアント設定
.run-windows-clients: ansible/inventory/inventory.yml
	@echo "$(GREEN)Windows クライアントを設定中...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-windows-clients.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ Windows クライアント設定が完了しました$(NC)"

# ALB設定
.run-alb-config: ansible/inventory/inventory.yml
	@echo "$(GREEN)ALB Target Groups設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; export ENV_PREFIX="$(ENV_PREFIX)"; export OWNER="$(OWNER)"; \
	ansible-playbook -i localhost, ansible/setup-alb-target-groups.yml -v
	@echo "$(GREEN)✓ ALB Target Groups作成が完了しました$(NC)"
	@echo "$(GREEN)ALB Listeners設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; export ENV_PREFIX="$(ENV_PREFIX)"; export OWNER="$(OWNER)"; \
	ansible-playbook -i localhost, ansible/setup-alb-listeners.yml -v
	@echo "$(GREEN)✓ ALB Listeners作成が完了しました$(NC)"

# Linux アプリケーション設定
.run-linux-app: ansible/inventory/inventory.yml
	@echo "$(GREEN)Linux アプリケーション設定...$(NC)"
	@ansible-playbook -i ansible/inventory/inventory.yml ansible/deploy-linux.yml -v
	@echo "$(GREEN)✓ Linux アプリケーション設定が完了しました$(NC)"

##---------------------------------------------------------------------
##@ Infrastructure
##---------------------------------------------------------------------

infrastructure: check-env ## AWSインフラを構築（CloudFormation）
	@echo "$(GREEN)CloudFormationでインフラを構築中...$(NC)"
	@echo "$(GREEN)スタック名: $(STACK_NAME)$(NC)"; \
	aws cloudformation create-stack \
		--stack-name $(STACK_NAME) \
		--template-body file://cloudformation.yaml \
		--parameters \
			ParameterKey=KeyName,ParameterValue=$(KEY_NAME) \
			ParameterKey=AdminPassword,ParameterValue=$(ADMIN_PASSWORD) \
			ParameterKey=UserPassword,ParameterValue=$(USER_PASSWORD) \
			ParameterKey=Prefix,ParameterValue=$(ENV_PREFIX) \
			ParameterKey=Owner,ParameterValue=$(OWNER) \
		--capabilities CAPABILITY_IAM \
		--region ap-northeast-1; \
	echo "$(YELLOW)インフラ構築完了を待機中...（約10分）$(NC)"; \
	aws cloudformation wait stack-create-complete \
		--stack-name $(STACK_NAME) \
		--region ap-northeast-1; \
	echo "$(GREEN)✓ インフラ構築が完了しました$(NC)"; \
	echo ""; \
	$(MAKE) generate-rdp; \
	echo ""; \
	echo "$(YELLOW)次のステップ:$(NC)"; \
	echo "  make deploy    # 全コンポーネントをデプロイ"

infra: infrastructure ## infrastructureの短縮形

destroy: check-env ## AWSインフラを削除
	@echo "$(RED)警告: スタック $(STACK_NAME) を削除します$(NC)"
	@echo "$(YELLOW)削除されるリソース:$(NC)"
	@echo "  - EC2インスタンス (DC1, DC2, WIN1, WIN2, Linux)"
	@echo "  - Application Load Balancer"
	@echo "  - VPC およびサブネット"
	@echo ""
	@read -p "続行しますか？ (yes/no): " confirm; \
	if [ "$$confirm" = "yes" ]; then \
		aws cloudformation delete-stack \
			--stack-name $(STACK_NAME) \
			--region ap-northeast-1; \
		echo "$(GREEN)スタック削除リクエストを送信しました$(NC)"; \
		echo "$(YELLOW)削除完了を待機中...$(NC)"; \
		aws cloudformation wait stack-delete-complete \
			--stack-name $(STACK_NAME) \
			--region ap-northeast-1; \
		echo "$(GREEN)✓ インフラ削除が完了しました$(NC)"; \
		rm -f ansible/inventory/inventory.yml; \
	else \
		echo "$(YELLOW)削除をキャンセルしました$(NC)"; \
	fi

stack-status: check-env ## CloudFormationスタックの状態を確認
	@aws cloudformation describe-stacks \
		--stack-name $(STACK_NAME) \
		--query 'Stacks[0].[StackName,StackStatus]' \
		--output table \
		--region ap-northeast-1

##---------------------------------------------------------------------
##@ Server-specific Deployment
##---------------------------------------------------------------------

# 個別サーバー設定 - ドメインコントローラー
deploy-dc1: ANSIBLE_LIMIT = --limit dc1
deploy-dc1: setup-ansible .run-domain-controllers ## DC1のみデプロイ（DOMAIN1）

deploy-dc2: ANSIBLE_LIMIT = --limit dc2
deploy-dc2: setup-ansible .run-domain-controllers ## DC2のみデプロイ（DOMAIN2）

deploy-dc: setup-ansible .run-domain-controllers .run-group-policy ## 両方のドメインコントローラーをデプロイ（GPO含む）

# 個別サーバー設定 - Windows クライアント
deploy-win1: ANSIBLE_LIMIT = --limit win1
deploy-win1: setup-ansible .run-windows-clients ## WIN1のみデプロイ（DOMAIN1クライアント）

deploy-win2: ANSIBLE_LIMIT = --limit win2
deploy-win2: setup-ansible .run-windows-clients ## WIN2のみデプロイ（DOMAIN2クライアント）

deploy-win: setup-ansible .run-windows-clients ## 両方のWindowsクライアントをデプロイ

# Group Policy設定
deploy-gpo: setup-ansible .run-group-policy ## Group Policy（イントラネットゾーン設定）をデプロイ

# ALB設定
deploy-alb: setup-ansible .run-alb-config ## ALB設定をデプロイ（Target Groups + Listeners）

# Linux アプリケーション
deploy-linux: check-ssh-key setup-ansible .run-linux-app ## Linuxアプリケーションサーバーをデプロイ

# 全体デプロイ
deploy: deploy-dc deploy-win deploy-alb deploy-linux show-info ## 全コンポーネントをデプロイ（DC + WIN + ALB + Linux）
	@echo ""
	@echo "$(BLUE)=== 🎉 全体構築完了 🎉 ===$(NC)"
	@echo "$(GREEN)✅ 全ての設定が完了しました！認証テストを開始できます。$(NC)"
	@echo ""

##---------------------------------------------------------------------
##@ Information & Testing
##---------------------------------------------------------------------

show-info: get-cloudformation-outputs ## 接続情報を表示
	@echo ""
	@echo "$(YELLOW)📋 接続情報:$(NC)"
	@echo "  DC1 (DOMAIN1):     $(DC1_IP)"
	@echo "  DC2 (DOMAIN2):     $(DC2_IP)"
	@echo "  WIN1:              $(WIN1_IP)"
	@echo "  WIN2:              $(WIN2_IP)"
	@echo "  Linux App:         $(LINUX_PUBLIC_IP)"
	@echo "  ALB DNS:           $(ALB_DNS_NAME)"
	@echo ""
	@echo "$(YELLOW)🌐 アプリケーションURL:$(NC)"
	@echo "  WinAuth (ALB経由): https://$(ALB_DNS_NAME)"
	@echo ""
	@echo "$(YELLOW)🖥️ RDP接続 (Administrator):$(NC)"
	@echo "  DC1:               rdp://$(DC1_IP)"
	@echo "  DC2:               rdp://$(DC2_IP)"
	@echo "  WIN1:              rdp://$(WIN1_IP)"
	@echo "  WIN2:              rdp://$(WIN2_IP)"
	@echo ""
	@echo "$(YELLOW)🔐 SSH接続:$(NC)"
	@EXPANDED_PATH=$$(eval echo "$(SSH_KEY_PATH)"); \
	echo "  Linux App:         ssh -i $$EXPANDED_PATH ec2-user@$(LINUX_PUBLIC_IP)"
	@echo ""

test-urls: get-cloudformation-outputs ## アプリケーションURLをテスト
	@echo "$(GREEN)アプリケーションURLをテスト中...$(NC)"
	@echo ""
	@echo "$(YELLOW)🌐 ALB経由テスト:$(NC)"
	@curl -s -o /dev/null -w "  Status: %{http_code} - %{url_effective}\n" https://$(ALB_DNS_NAME) || echo "  ❌ ALB経由接続失敗"
	@echo ""

generate-rdp: get-cloudformation-outputs ## RDP接続ファイルを生成
	@echo "$(GREEN)RDP接続ファイルを生成中...$(NC)"
	@mkdir -p rdp
	@sed 's/full address:s:.*/full address:s:$(DC1_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:$(DC1_IP)\\Administrator/' > rdp/dc1.rdp
	@sed 's/full address:s:.*/full address:s:$(DC2_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:$(DC2_IP)\\Administrator/' > rdp/dc2.rdp
	@sed 's/full address:s:.*/full address:s:$(WIN1_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:$(WIN1_IP)\\Administrator/' > rdp/win1.rdp
	@sed 's/full address:s:.*/full address:s:$(WIN1_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:DOMAIN1\\user1/' > rdp/win1-user1.rdp
	@sed 's/full address:s:.*/full address:s:$(WIN2_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:$(WIN2_IP)\\Administrator/' > rdp/win2.rdp
	@sed 's/full address:s:.*/full address:s:$(WIN2_IP)/' rdp.template | \
	 sed 's/username:s:.*/username:s:DOMAIN2\\user2/' > rdp/win2-user2.rdp
	@echo "$(GREEN)✓ RDP接続ファイルを生成しました: rdp/$(NC)"
	@echo "  - rdp/dc1.rdp        ($(DC1_IP) - Administrator)"
	@echo "  - rdp/dc2.rdp        ($(DC2_IP) - Administrator)"
	@echo "  - rdp/win1.rdp       ($(WIN1_IP) - Administrator)"
	@echo "  - rdp/win1-user1.rdp ($(WIN1_IP) - DOMAIN1\\user1)"
	@echo "  - rdp/win2.rdp       ($(WIN2_IP) - Administrator)"
	@echo "  - rdp/win2-user2.rdp ($(WIN2_IP) - DOMAIN2\\user2)"

##---------------------------------------------------------------------
##@ Maintenance
##---------------------------------------------------------------------

clean: ## 生成されたファイルを削除
	@echo "$(GREEN)生成されたファイルを削除中...$(NC)"
	@rm -f ansible/inventory/inventory.yml
	@rm -rf rdp
	@echo "$(GREEN)✓ クリーンアップが完了しました$(NC)"

# デフォルトターゲット
.DEFAULT_GOAL := help