# AWS Kerberos環境 アプリケーション設定 Makefile
#
# 使用方法:
#   初回: make save-config KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass
#   以降: make deploy (パラメータ不要)
#
# または従来通り毎回指定:
#   make deploy KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass

# .envファイルから設定を読み込み (存在する場合)
-include .env

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
help: ## ヘルプメッセージを表示
	@awk 'BEGIN {FS = ":.*##"; printf "
[34mAWS Kerberos環境 アプリケーション設定 Makefile[0m

Usage:
  make [36m<target>[0m KEY_NAME=<keypair> ADMIN_PASSWORD=<pass> USER_PASSWORD=<pass>
"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  [36m%-20s[0m %s
", $$1, $$2 } /^##@/ { printf "
[1m%s[0m
", substr($$0, 5) } ' $(MAKEFILE_LIST)
	@echo ""
	@echo "[33m必須パラメータ:[0m"
	@echo "  KEY_NAME          AWS KeyPair名"
	@echo "  ADMIN_PASSWORD    Administrator パスワード"
	@echo "  USER_PASSWORD     ユーザーパスワード"
	@echo ""
	@echo "[33mオプション:[0m"
	@echo "  PREFIX=KrbTest      スタックプレフィックス"
	@echo "  SSH_KEY_PATH        SSH秘密鍵パス (デフォルト: ~/.ssh/KEY_NAME.pem)"
	@echo "  SERVICE_PASSWORD    サービスアカウントパスワード (デフォルト: DefaultServicePass123!)"
	@echo "  TRUST_PASSWORD      ドメイン信頼パスワード (デフォルト: DefaultTrustPass123!)"
	@echo ""
	@echo "[33m設定の保存:[0m"
	@echo "  make save-config    現在のパラメータを保存（次回以降は省略可能）"
	@echo "  make show-config    保存された設定を表示"

save-config: ## パラメータを.envファイルに保存（次回以降省略可能）
ifndef KEY_NAME
	@echo "$(RED)エラー: KEY_NAME が設定されていません$(NC)"
	@echo "例: make save-config KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif
ifndef ADMIN_PASSWORD
	@echo "$(RED)エラー: ADMIN_PASSWORD が設定されていません$(NC)"
	@echo "例: make save-config KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif
ifndef USER_PASSWORD
	@echo "$(RED)エラー: USER_PASSWORD が設定されていません$(NC)"
	@echo "例: make save-config KEY_NAME=my-keypair ADMIN_PASSWORD=pass USER_PASSWORD=pass"
	@exit 1
endif
	@echo "# Auto-generated configuration" > .env
	@echo "KEY_NAME=$(KEY_NAME)" >> .env
	@echo "ADMIN_PASSWORD=$(ADMIN_PASSWORD)" >> .env
	@echo "USER_PASSWORD=$(USER_PASSWORD)" >> .env
	@echo "PREFIX=$(PREFIX)" >> .env
	@echo "SERVICE_PASSWORD=$(SERVICE_PASSWORD)" >> .env
	@echo "TRUST_PASSWORD=$(TRUST_PASSWORD)" >> .env
	@echo "$(GREEN)✓ 設定を .env に保存しました$(NC)"
	@echo "$(YELLOW)次回以降は 'make deploy' だけで実行できます$(NC)"

show-config: ## 保存された設定を表示
	@if [ -f .env ]; then \
		echo "$(GREEN)保存された設定:$(NC)"; \
		cat .env | grep -v "^#" | sed 's/^/  /'; \
	else \
		echo "$(YELLOW)保存された設定はありません$(NC)"; \
		echo "$(YELLOW)make save-config で設定を保存してください$(NC)"; \
	fi

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

# ALB設定
.run-alb-config: ansible/inventory/inventory.yml
	@echo "$(GREEN)ALB Target Groups設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; export PREFIX="$(PREFIX)"; \
	ansible-playbook -i localhost, ansible/setup-alb-target-groups.yml -v
	@echo "$(GREEN)✓ ALB Target Groups作成が完了しました$(NC)"
	@echo "$(GREEN)ALB Listeners設定...$(NC)"
	@export STACK_NAME="$(STACK_NAME)"; \
	ansible-playbook -i localhost, ansible/setup-alb-listeners.yml -v
	@echo "$(GREEN)✓ ALB Listeners作成が完了しました$(NC)"

# Linux アプリケーション設定
.run-linux-app: ansible/inventory/inventory.yml
	@echo "$(GREEN)Linux アプリケーション設定...$(NC)"
	@ansible-playbook -i ansible/inventory/inventory.yml ansible/deploy-linux.yml -v
	@echo "$(GREEN)✓ Linux アプリケーション設定が完了しました$(NC)"

##---------------------------------------------------------------------
##@ Server-specific Deployment
##---------------------------------------------------------------------

# 個別サーバー設定 - ドメインコントローラー
deploy-dc1: ANSIBLE_LIMIT = --limit dc1
deploy-dc1: check-ssh-key setup-ansible-collections .run-domain-controllers ## DC1のみデプロイ（DOMAIN1）

deploy-dc2: ANSIBLE_LIMIT = --limit dc2
deploy-dc2: check-ssh-key setup-ansible-collections .run-domain-controllers ## DC2のみデプロイ（DOMAIN2）

deploy-dc: check-ssh-key setup-ansible-collections .run-domain-controllers ## 両方のドメインコントローラーをデプロイ

# 個別サーバー設定 - Windows クライアント
deploy-win1: ANSIBLE_LIMIT = --limit win1
deploy-win1: check-ssh-key setup-ansible-collections .run-windows-clients ## WIN1のみデプロイ（DOMAIN1クライアント）

deploy-win2: ANSIBLE_LIMIT = --limit win2
deploy-win2: check-ssh-key setup-ansible-collections .run-windows-clients ## WIN2のみデプロイ（DOMAIN2クライアント）

deploy-win: check-ssh-key setup-ansible-collections .run-windows-clients ## 両方のWindowsクライアントをデプロイ

# ALB設定
deploy-alb: check-ssh-key setup-ansible-collections .run-alb-config ## ALB設定をデプロイ（Target Groups + Listeners）

# Linux アプリケーション
deploy-linux: check-ssh-key setup-ansible-collections .run-linux-app ## Linuxアプリケーションサーバーをデプロイ

# 全体デプロイ
deploy: deploy-dc deploy-win deploy-alb deploy-linux ## 全コンポーネントをデプロイ（DC + WIN + ALB + Linux）
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
	@echo "  WinAuth (直接):    https://$(LINUX_PUBLIC_IP):8082"
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
	@echo "$(GREEN)✅ 全ての設定が完了しました！認証テストを開始できます。$(NC)"
	@echo ""

##---------------------------------------------------------------------
##@ Information & Testing
##---------------------------------------------------------------------

show-info: get-cloudformation-outputs ## 接続情報を表示
	@echo "$(GREEN)✅ 接続情報を表示しました$(NC)"

test-urls: get-cloudformation-outputs ## アプリケーションURLをテスト
	@echo "$(GREEN)アプリケーションURLをテスト中...$(NC)"
	@echo ""
	@echo "$(YELLOW)🌐 ALB経由テスト:$(NC)"
	@curl -s -o /dev/null -w "  Status: %{http_code} - %{url_effective}\n" http://$(ALB_DNS_NAME) || echo "  ❌ ALB経由接続失敗"
	@echo ""

##---------------------------------------------------------------------
##@ Maintenance
##---------------------------------------------------------------------

clean: ## 生成されたファイルを削除
	@echo "$(GREEN)生成されたファイルを削除中...$(NC)"
	@rm -f ansible/inventory/inventory.yml
	@echo "$(GREEN)✓ クリーンアップが完了しました$(NC)"

# デフォルトターゲット
.DEFAULT_GOAL := help