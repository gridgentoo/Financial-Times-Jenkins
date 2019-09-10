package com.ft.jenkins

/**
 * There is currently no way to define these constants directly in the DeploymentUtils script, so putting them in a separate class.
 */
public class DeploymentUtilsConstants {
  public static final String CREDENTIALS_DIR = "credentials"
  public static final String K8S_CLI_IMAGE = "coco/k8s-cli-utils:latest"
  public static final String HELM_CONFIG_FOLDER = "helm"
  /*  todo [sb] After a jenkins plugins update, the following line no longer works. Please try again later, as we'd like to reuse the value of a constant in other constants */
//  public static String HELM_CHART_LOCATION_REGEX = "${HELM_CONFIG_FOLDER}/**/Chart.yaml"
  public static final String HELM_CHART_LOCATION_REGEX = "helm/**/Chart.yaml"
  public static final String CHART_VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+.*"
  public static final String APPS_CONFIG_FOLDER = "app-configs"
  public static final String DEFAULT_HELM_VALUES_FILE = "values.yaml"

  public static final String HELM_S3_BUCKET = "s3://upp-helm-repo/"
  public static final String HELM_AWS_CREDENTIALS = "ft.helm-repo.aws-credentials"
  //public static final String HELM_REPO_URL = "http://upp-helm-repo.s3-website-eu-west-1.amazonaws.com"
  public static final String HELM_REPO_URL = "http://k8s-pipeline-lib-helm-repo-test1.s3-website-eu-west-1.amazonaws.com"
  public static final String HELM_LOCAL_REPO_NAME = "upp"

  public static final String GENERIC_DEPLOY_JOB = 'k8s-deployment/utils/deploy-upp-helm-chart'

  public static final String APPROVER_INPUT = "approver"

}
