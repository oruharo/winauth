# AWS Kerberos環境 アプリケーション設定 Makefile
#
# 使用方法:
#   make deploy KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass
#   make deploy-dc1 KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass
#   make deploy-win1 KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass

# デフォルト設定
PREFIX ?= KrbTest
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
help: ## Display this help.
	@awk 'BEGIN {FS = ":.*##"; printf "\n\033[34mAWS Kerberos環境 アプリケーション設定 Makefile\033[0m\n\nUsage:\n  make \033[36m<target>\033[0m KEY_NAME=<keypair> ADMIN_PASSWORD=<pass> USER_PASSWORD=<pass>\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)
	@echo ""
	@echo "\033[33m必須パラメータ:\033[0m"
	@echo "  KEY_NAME          AWS KeyPair名"
	@echo "  ADMIN_PASSWORD    Administrator パスワード"
	@echo "  USER_PASSWORD     ユーザーパスワード"
	@echo ""
	@echo "\033[33mオプション:\033[0m"
	@echo "  PREFIX=KrbTest      スタックプレフィックス"
	@echo "  SSH_KEY_PATH        SSH秘密鍵パス (デフォルト: ~/.ssh/KEY_NAME.pem)"
	@echo "  SERVICE_PASSWORD    サービスアカウントパスワード (デフォルト: DefaultServicePass123!)"
	@echo "  TRUST_PASSWORD      ドメイン信頼パスワード (デフォルト: DefaultTrustPass123!)"

##---------------------------------------------------------------------
##@ Prerequisites
##---------------------------------------------------------------------

# 必須パラメータのチェック
check-params:
ifndef KEY_NAME
	@echo "$(RED)エラー: KEY_NAME が設定されていません$(NC)"
	@echo "例: make deploy KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif
ifndef ADMIN_PASSWORD
	@echo "$(RED)エラー: ADMIN_PASSWORD が設定されていません$(NC)"
	@echo "例: make deploy KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif
ifndef USER_PASSWORD
	@echo "$(RED)エラー: USER_PASSWORD が設定されていません$(NC)"
	@echo "例: make deploy KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif

# SSH鍵の存在確認
check-ssh-key: check-params
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

# Ansibleの確認
check-ansible:
	@echo "$(GREEN)Ansibleを確認中...$(NC)"
	@command -v ansible-playbook >/dev/null || (echo "$(RED)エラー: Ansibleがインストールされていません$(NC)" && exit 1)
	@echo "$(GREEN)✓ Ansible が利用可能です$(NC)"

# スタック名の取得
get-stack-name: check-params
	$(eval STACK_NAME := $(shell aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE --query "StackSummaries[?starts_with(StackName, \`$(PREFIX)-kerberos-test-\`)].StackName" --output text | head -1))
	@test -n "$(STACK_NAME)" || (echo "$(RED)エラー: $(PREFIX)-kerberos-test-* スタックが見つかりません$(NC)" && exit 1)
	@echo "$(GREEN)スタック名: $(STACK_NAME)$(NC)"

# Ansibleコレクションの確認・インストール
setup-ansible-collections: check-ansible
	@echo "$(GREEN)Ansibleコレクションを確認中...$(NC)"
	@if [ -f "ansible/requirements.yml" ]; then \
		ansible-galaxy collection install -r ansible/requirements.yml --force >/dev/null 2>&1; \
	else \
		ansible-galaxy collection install amazon.aws community.aws --force >/dev/null 2>&1; \
	fi
	@echo "$(GREEN)✓ Ansibleコレクションが準備完了$(NC)"

# CloudFormation出力の取得
get-cloudformation-outputs: get-stack-name
	@echo "$(GREEN)CloudFormation出力を取得中...$(NC)"
	$(eval DC1_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`DC1PublicIP`].OutputValue' --output text))
	$(eval DC2_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`DC2PublicIP`].OutputValue' --output text))
	$(eval WIN1_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`WIN1PublicIP`].OutputValue' --output text))
	$(eval WIN2_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`WIN2PublicIP`].OutputValue' --output text))
	$(eval LINUX_PUBLIC_IP := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`LinuxAppPublicIP`].OutputValue' --output text))
	$(eval ALB_DNS_NAME := $(shell aws cloudformation describe-stacks --stack-name $(STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`ALBDNSName`].OutputValue' --output text))
	@echo ""
	@echo "$(YELLOW)取得したIP情報:$(NC)"
	@echo "  DC1 (DOMAIN1):     $(DC1_IP)"
	@echo "  DC2 (DOMAIN2):     $(DC2_IP)"
	@echo "  WIN1:              $(WIN1_IP)"
	@echo "  WIN2:              $(WIN2_IP)"
	@echo "  Linux App:         $(LINUX_PUBLIC_IP)"
	@echo "  ALB DNS:           $(ALB_DNS_NAME)"
	@echo ""
	@echo "$(YELLOW)接続URL:$(NC)"
	@echo "  WinAuth App:       http://$(LINUX_PUBLIC_IP):8082"
	@echo "  WinAuth via ALB:   http://$(ALB_DNS_NAME)"
	@echo ""
	@echo "$(YELLOW)RDP接続 (ユーザー: Administrator):$(NC)"
	@echo "  DC1:               rdp://$(DC1_IP)"
	@echo "  DC2:               rdp://$(DC2_IP)"
	@echo "  WIN1:              rdp://$(WIN1_IP)"
	@echo "  WIN2:              rdp://$(WIN2_IP)"
	@echo ""
	@echo "$(YELLOW)SSH接続:$(NC)"
	@EXPANDED_PATH=$$(eval echo "$(SSH_KEY_PATH)"); \
	echo "  Linux App:         ssh -i $$EXPANDED_PATH ec2-user@$(LINUX_PUBLIC_IP)"
	@echo ""

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
		echo ""; \
		echo "    # Kerberos settings"; \
		echo "    primary_realm: \"DOMAIN1.LAB\""; \
		echo "    secondary_realm: \"DOMAIN2.LAB\""; \
		echo ""; \
		echo "    # Application settings"; \
		echo "    java_package: \"java-17-amazon-corretto-devel\""; \
		echo "    app_user: \"ec2-user\""; \
		echo "    app_dir: \"/opt/winauth\""; \
		echo "    keytab_dir: \"/etc/kerberos\""; \
		echo "    keytab_name: \"winauth-combined.keytab\""; \
	} > $@
	@echo "$(GREEN)✓ インベントリファイルを作成しました$(NC)"

##---------------------------------------------------------------------
##@ Core Tasks (Combined Workflows)
##---------------------------------------------------------------------

# ドメインコントローラー設定
.run-domain-controllers: ansible/inventory/inventory.yml
	@echo "$(GREEN)ドメインコントローラーを設定中...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-domain-controllers.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ ドメインコントローラー設定が完了しました$(NC)"

# Windows クライアント設定
.run-windows-clients: ansible/inventory/inventory.yml
	@echo "$(GREEN)Windows クライアントを設定中...$(NC)"
	@ansible-playbook -i ansible/inventory/inventory.yml ansible/setup-windows-clients.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ Windows クライアント設定が完了しました$(NC)"
	@echo "$(GREEN)ブラウザ設定（Kerberos認証用）...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i ansible/inventory/inventory.yml ansible/configure-browser-kerberos.yml $(ANSIBLE_LIMIT) -v
	@echo "$(GREEN)✓ ブラウザ設定が完了しました$(NC)"

# Linux アプリケーション設定
.run-linux-app: ansible/inventory/inventory.yml
	@echo "$(GREEN)ALB Target Groups設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; export PREFIX="$(PREFIX)"; \
	ansible-playbook -i localhost, ansible/setup-alb-target-groups.yml -v
	@echo "$(GREEN)✓ ALB Target Groups作成が完了しました$(NC)"
	@echo "$(GREEN)ALB Listeners設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i localhost, ansible/setup-alb-listeners.yml -v
	@echo "$(GREEN)✓ ALB Listeners作成が完了しました$(NC)"
	@echo "$(GREEN)Linux アプリケーション設定...$(NC)"
	@echo "SSH接続の準備を待機中..."
	@sleep 30
	@ansible-playbook -i ansible/inventory/inventory.yml ansible/deploy-linux.yml -v
	@echo "$(GREEN)✓ Linux アプリケーション設定が完了しました$(NC)"

##---------------------------------------------------------------------
##@ Individual Playbooks
##---------------------------------------------------------------------

# Playbookを直接実行する汎用ターゲット
run-playbook: ansible/inventory/inventory.yml ## Run specific playbook (usage: make run-playbook PLAYBOOK=setup-domain-controllers.yml)
ifndef PLAYBOOK
	@echo "$(RED)エラー: PLAYBOOK パラメータが必要です$(NC)"
	@echo "例: make run-playbook PLAYBOOK=setup-domain-controllers.yml KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@echo "利用可能なPlaybook:"
	@ls ansible/*.yml | grep -v requirements.yml | sed 's|ansible/||' | sed 's/^/  /'
	@exit 1
endif
	@echo "$(GREEN)Playbook '$(PLAYBOOK)' を実行中...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	if [ "$(PLAYBOOK)" = "setup-alb-target-groups.yml" ] || [ "$(PLAYBOOK)" = "setup-alb-listeners.yml" ]; then \
		export PREFIX="$(PREFIX)"; \
		ansible-playbook -i localhost, ansible/$(PLAYBOOK) $(ANSIBLE_LIMIT) -v; \
	else \
		ansible-playbook -i ansible/inventory/inventory.yml ansible/$(PLAYBOOK) $(ANSIBLE_LIMIT) -v; \
	fi
	@echo "$(GREEN)✓ Playbook '$(PLAYBOOK)' が完了しました$(NC)"

# 個別Playbookターゲット
setup-domain-controllers: PLAYBOOK = setup-domain-controllers.yml
setup-domain-controllers: check-ssh-key setup-ansible-collections run-playbook ## Setup domain controllers

setup-windows-clients: check-ssh-key setup-ansible-collections .run-windows-clients ## Setup Windows clients

configure-browser-kerberos: PLAYBOOK = configure-browser-kerberos.yml
configure-browser-kerberos: check-ssh-key setup-ansible-collections run-playbook ## Configure browser for Kerberos

configure-browser-kerberos-new: PLAYBOOK = configure-browser-kerberos-new.yml
configure-browser-kerberos-new: check-ssh-key setup-ansible-collections run-playbook ## Configure browser for Kerberos (new version)

deploy-linux: PLAYBOOK = deploy-linux.yml
deploy-linux: check-ssh-key setup-ansible-collections run-playbook ## Deploy Linux application

setup-alb-target-groups: PLAYBOOK = setup-alb-target-groups.yml
setup-alb-target-groups: check-ssh-key setup-ansible-collections run-playbook ## Setup ALB target groups

setup-alb-listeners: PLAYBOOK = setup-alb-listeners.yml
setup-alb-listeners: check-ssh-key setup-ansible-collections run-playbook ## Setup ALB listeners

create-alb-keytab: PLAYBOOK = create-alb-keytab.yml
create-alb-keytab: check-ssh-key setup-ansible-collections run-playbook ## Create ALB keytab

add-alb-spn: PLAYBOOK = add-alb-spn.yml
add-alb-spn: check-ssh-key setup-ansible-collections run-playbook ## Add ALB SPN

setup-alb: PLAYBOOK = setup-alb.yml
setup-alb: check-ssh-key setup-ansible-collections run-playbook ## Setup ALB

configure-nginx-alb: PLAYBOOK = configure-nginx-alb.yml
configure-nginx-alb: check-ssh-key setup-ansible-collections run-playbook ## Configure nginx for ALB

##---------------------------------------------------------------------
##@ Step-by-step Workflow
##---------------------------------------------------------------------

# ステップ別実行（推奨順序）
step1-domain-controllers: setup-domain-controllers ## Step 1: Setup domain controllers

step2-windows-clients: setup-windows-clients ## Step 2: Setup Windows clients

step3-browser-config: configure-browser-kerberos ## Step 3: Configure browser for Kerberos

step4-alb-target-groups: setup-alb-target-groups ## Step 4a: Setup ALB target groups

step5-alb-listeners: setup-alb-listeners ## Step 5b: Setup ALB listeners

step6-linux-app: deploy-linux-with-wait ## Step 6: Deploy Linux application (with wait)

# Linux デプロイ用の待機付きターゲット
deploy-linux-with-wait: ansible/inventory/inventory.yml
	@echo "$(GREEN)SSH接続の準備を待機中...$(NC)"
	@sleep 30
	@echo "$(GREEN)Linux アプリケーション設定...$(NC)"
	@ansible-playbook -i ansible/inventory/inventory.yml ansible/deploy-linux.yml -v
	@echo "$(GREEN)✓ Linux アプリケーション設定が完了しました$(NC)"

# 全ステップを順次実行
deploy-step-by-step: step1-domain-controllers step2-windows-clients step3-browser-config step4-alb-target-groups step5-alb-listeners step6-linux-app ## Deploy all components step by step
	@echo ""
	@echo "$(BLUE)=== 🎉 ステップ別デプロイ完了 🎉 ===$(NC)"
	@echo "$(GREEN)全6ステップが正常に完了しました$(NC)"

##---------------------------------------------------------------------
##@ Server-specific Deployment
##---------------------------------------------------------------------

# 個別サーバー設定 - ドメインコントローラー
deploy-dc1: ANSIBLE_LIMIT = --limit dc1
deploy-dc1: check-ssh-key setup-ansible-collections .run-domain-controllers ## Deploy DC1 only

deploy-dc2: ANSIBLE_LIMIT = --limit dc2
deploy-dc2: check-ssh-key setup-ansible-collections .run-domain-controllers ## Deploy DC2 only

deploy-domain-controllers: check-ssh-key setup-ansible-collections .run-domain-controllers ## Deploy both domain controllers

# 個別サーバー設定 - Windows クライアント
deploy-win1: ANSIBLE_LIMIT = --limit win1
deploy-win1: check-ssh-key setup-ansible-collections .run-windows-clients ## Deploy WIN1 only

deploy-win2: ANSIBLE_LIMIT = --limit win2
deploy-win2: check-ssh-key setup-ansible-collections .run-windows-clients ## Deploy WIN2 only

deploy-windows-clients: check-ssh-key setup-ansible-collections .run-windows-clients ## Deploy both Windows clients

# Linux アプリケーション
deploy-linux-app: check-ssh-key setup-ansible-collections .run-linux-app ## Deploy Linux application

##---------------------------------------------------------------------
##@ Combined Deployment
##---------------------------------------------------------------------

deploy-dc: deploy-domain-controllers ## Deploy all domain controllers

deploy-clients: deploy-windows-clients ## Deploy all Windows clients

deploy-linux: deploy-linux-app ## Deploy Linux application

deploy: deploy-domain-controllers deploy-windows-clients deploy-linux-app ## Deploy all components
	@echo ""
	@echo "$(BLUE)=== 🎉 全体構築完了 🎉 ===$(NC)"
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
	@echo "  WinAuth (直接):    http://$(LINUX_PUBLIC_IP):8082"
	@echo "  WinAuth (ALB経由): http://$(ALB_DNS_NAME)"
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
	@echo "$(GREEN)✅ 全ての設定が完了しました！認証テストを開始できます。$(NC)"
	@echo ""

##---------------------------------------------------------------------
##@ Information & Testing
##---------------------------------------------------------------------

show-info: get-cloudformation-outputs ## Show all connection information
	@echo "$(GREEN)✅ 接続情報を表示しました$(NC)"

test-urls: get-cloudformation-outputs ## Test application URLs
	@echo "$(GREEN)アプリケーションURLをテスト中...$(NC)"
	@echo ""
	@echo "$(YELLOW)🌐 直接接続テスト:$(NC)"
	@curl -s -o /dev/null -w "  Status: %{http_code} - %{url_effective}\n" http://$(LINUX_PUBLIC_IP):8082 || echo "  ❌ 直接接続失敗"
	@echo ""
	@echo "$(YELLOW)🌐 ALB経由テスト:$(NC)"
	@curl -s -o /dev/null -w "  Status: %{http_code} - %{url_effective}\n" http://$(ALB_DNS_NAME) || echo "  ❌ ALB経由接続失敗"
	@echo ""

##---------------------------------------------------------------------
##@ Maintenance
##---------------------------------------------------------------------

clean: ## Remove generated files
	@echo "$(GREEN)生成されたファイルを削除中...$(NC)"
	@rm -f ansible/inventory/inventory.yml
	@echo "$(GREEN)✓ クリーンアップが完了しました$(NC)"

# デフォルトターゲット
.DEFAULT_GOAL := help