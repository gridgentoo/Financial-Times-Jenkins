package com.ft.jenkins

/**
 * There is currently no way to define these constants directly in the DeploymentUtils script, so putting them in a separate class.
 */
final class DeploymentUtilsConstants {
  public static String CREDENTIALS_DIR = "credentials"
  public static String K8S_CLI_IMAGE = "coco/k8s-cli-utils:latest"
  public static String HELM_CONFIG_FOLDER = "helm"
  public static String HELM_CHART_LOCATION_REGEX = "${HELM_CONFIG_FOLDER}/**/Chart.yaml"
  public static String APPS_CONFIG_FOLDER = "app-configs"
  public static final String DEFAULT_HELM_VALUES_FILE = "values.yaml"
  public static final String OPTION_ALL = "All"

}
