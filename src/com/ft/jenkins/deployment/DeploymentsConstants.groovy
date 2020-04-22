package com.ft.jenkins.deployment

final class DeploymentsConstants {
  public static final String CREDENTIALS_DIR = "credentials"
  public static final String K8S_CLI_IMAGE = "coco/k8s-cli-utils:latest"
  public static final String EKS_PROVISIONER_IMAGE = "ftcore/ft-core-eks-provisioner:v0.0.6"
  public static final String GENERIC_DEPLOY_JOB = 'k8s-deployment/utils/deploy-upp-helm-chart'
  public static final String APPROVER_INPUT = "approver"
}
