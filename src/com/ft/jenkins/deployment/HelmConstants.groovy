package com.ft.jenkins.deployment

final class HelmConstants {
  public static final String HELM_CONFIG_FOLDER = "helm"
  public static final String HELM_CLI_TOOL = "helm"
  /*  todo [sb] After a jenkins plugins update, the following line no longer works. Please try again later, as we'd like to reuse the value of a constant in other constants */
//  public static String HELM_CHART_LOCATION_REGEX = "${HELM_CONFIG_FOLDER}/**/Chart.yaml"
  public static final String CHART_LOCATION_REGEX = "helm/**/Chart.yaml"
  public static final String CHART_VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+.*"
  public static final String APP_CONFIGS_FOLDER = "app-configs"
  public static final String DEFAULT_HELM_VALUES_FILE = "values.yaml"

  public static final String HELM_S3_BUCKET = "s3://upp-helm-repo/"
  public static final String HELM_AWS_CREDENTIALS = "ft.helm-repo.aws-credentials"
  public static final String HELM_REPO_URL = "http://upp-helm-repo.s3-website-eu-west-1.amazonaws.com"
  public static final String HELM_LOCAL_REPO_NAME = "upp"
  public static final String HELM_REPO_INDEX_FILE_PATH = "\$HOME/.cache/helm/repository/${HELM_LOCAL_REPO_NAME}-index.yaml"
  public static final String CHART_NAME_COMMAND_PARAM = "chartName"
  public static final String CHART_RELEASE_NAME_COMMAND_PARAM = "chartReleaseName"
  public static final String CHART_VERSION_COMMAND_PARAM = "chartVersion"
  public static final String REGION_COMMAND_PARAM = "region"
  public static final String CLUSTER_URLS_COMMAND_PARAM = "clusterUrls"
  public static final String GLB_URLS_COMMAND_PARAM = "glbUrls"
  public static final String VALUES_FILE_PATH_COMMAND_PARAM = "valuesAppConfigFilePath"
  public static final String TARGET_ENV_COMMAND_PARAM = "targetEnvName"
  public static final String TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM = "targetClusterSubDomain"
  public static final String TARGET_ENV_NAMESPACE_PARAM = "targetEnvNamespaceParam"
  public static final String LOCAL_REPO_NAME_PARAM = "localRepoName"
  public static final String REPO_URL_PARAM = "repoUrl"
  public static final String TEMP_CHART_DIR = "tempChartDir"
}
