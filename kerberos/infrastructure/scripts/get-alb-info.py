#!/usr/bin/env python3
"""
ALB情報を取得してAnsible変数として利用するスクリプト
"""
import boto3
import json
import sys
import argparse

def get_cloudformation_outputs(stack_name, region='ap-northeast-1'):
    """CloudFormationスタックの出力値を取得"""
    cf_client = boto3.client('cloudformation', region_name=region)

    try:
        response = cf_client.describe_stacks(StackName=stack_name)
        stack = response['Stacks'][0]
        outputs = {}

        for output in stack.get('Outputs', []):
            outputs[output['OutputKey']] = output['OutputValue']

        return outputs
    except Exception as e:
        print(f"Error getting CloudFormation outputs: {e}", file=sys.stderr)
        return {}

def get_alb_info():
    """ALBの情報を取得"""
    parser = argparse.ArgumentParser(description='Get ALB information for Ansible')
    parser.add_argument('--stack', default='hashi-kerberos-test',
                       help='CloudFormation stack name')
    parser.add_argument('--region', default='ap-northeast-1',
                       help='AWS region')

    args = parser.parse_args()

    outputs = get_cloudformation_outputs(args.stack, args.region)

    # ALB関連の情報を抽出
    alb_info = {}
    if 'ALBDNSName' in outputs:
        alb_info['alb_dns_name'] = outputs['ALBDNSName']
    if 'ALBArn' in outputs:
        alb_info['alb_arn'] = outputs['ALBArn']
    if 'HTTPSListenerArn' in outputs:
        alb_info['https_listener_arn'] = outputs['HTTPSListenerArn']
    if 'NginxTargetGroupArn' in outputs:
        alb_info['nginx_target_group_arn'] = outputs['NginxTargetGroupArn']
    if 'SpringBootTargetGroupArn' in outputs:
        alb_info['springboot_target_group_arn'] = outputs['SpringBootTargetGroupArn']

    return alb_info

if __name__ == "__main__":
    info = get_alb_info()
    print(json.dumps(info, indent=2))