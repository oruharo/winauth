

./deploy-infrastructure.sh -p has -o hashiro hashi-kerberos-key 'Aa@12341234' 'Aa@12341234'

./deploy-applications.sh -p has -s linux-app hashi-kerberos-key "Aa@12341234" "Aa@12341234" ~/.aws/hashi-kerberos-key.pem





echo $NO_PROXY
echo $http_proxy
echo $https_proxy
echo $HTTP_PROXY
echo $HTTPS_PROXY