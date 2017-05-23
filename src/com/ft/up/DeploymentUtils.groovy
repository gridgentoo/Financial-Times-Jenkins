package com.ft.up

import static com.ft.up.DeploymentUtilsConstants.APPS_CONFIG_FOLDER
import static com.ft.up.DeploymentUtilsConstants.CREDENTIALS_DIR
import static com.ft.up.DeploymentUtilsConstants.HELM_CONFIG_FOLDER
import static com.ft.up.DeploymentUtilsConstants.K8S_CLI_IMAGE

final class DeploymentUtilsConstants {
  public static String CREDENTIALS_DIR = "credentials"
  public static String K8S_CLI_IMAGE = "coco/k8s-cli-utils:latest"
  public static String HELM_CONFIG_FOLDER = "helm"
  public static String APPS_CONFIG_FOLDER = "helm/app-configs"
}

/**
 * Deploys the application(s) in the current workspace using helm. It expects the helm chart to be defined in the {@link DeploymentUtilsConstants#HELM_CONFIG_FOLDER} folder.
 *
 * @param imageVersion the version of the docker image to deploy
 * @param env the environment name where it will be deployed.
 * @return the list of applications deployed
 */
public List<String> deployAppWithHelm(String imageVersion, String env) {
  List<String> appsToDeploy = getAppNamesInRepo()
  runWithK8SCliTools(env) {
    def chartName = getHelmChartFolderName()

    /*  todo [sb] handle the case when the chart is used by more than 1 app */
    /*  using the chart name also as release name.. we have one release per app */
    for (String application : appsToDeploy) {
      sh "helm upgrade ${chartName} ${HELM_CONFIG_FOLDER}/${chartName} -i -f ${APPS_CONFIG_FOLDER}/${application}.yaml --set image.version=${imageVersion}"
    }
  }
  return appsToDeploy
}


public List<String> getAppNamesInRepo() {
  List<String> appNames = []
  def foundConfigFiles = findFiles(glob: "${APPS_CONFIG_FOLDER}/*.yaml")

  for (def configFile :foundConfigFiles) {
    /*  strip the .yaml extension from the files */
    String fileName = configFile.name
    appNames.add(fileName.substring(0, fileName.indexOf('.')))
  }

  return appNames
}

/**
 * Retrieves the folder name where the Helm chart is defined .
 */
private String getHelmChartFolderName() {
  def chartFile = findFiles(glob: "${HELM_CONFIG_FOLDER}/**/Chart.yaml")[0]
  String[] chartFilePathComponents = ((String) chartFile.path).split('/')
  /* return the parent folder of Chart.yaml */
  return chartFilePathComponents[chartFilePathComponents.size() - 2]
}

public void runWithK8SCliTools(String env, Closure codeToRun) {
  prepareK8SCliCredentials(env)
  String currentDir = pwd()

  String apiServer = EnvsRegistry.getApiServerForEnv(env)
  GString dockerRunArgs =
      "-v ${currentDir}/${CREDENTIALS_DIR}:/${CREDENTIALS_DIR} " +
      "-e 'K8S_API_SERVER=${apiServer}' " +
      "-e 'KUBECONFIG=${currentDir}/kubeconfig'"

  docker.image(K8S_CLI_IMAGE).inside(dockerRunArgs) {
    sh "/docker-entrypoint.sh"

    codeToRun.call()
  }
}

private void prepareK8SCliCredentials(String environment) {
  withCredentials([
      [$class: 'FileBinding', credentialsId: "ft.k8s.auth.client-certificate", variable: 'CLIENT_CERT'],
      [$class: 'FileBinding', credentialsId: "ft.k8s.auth.ca-cert", variable: 'CA_CERT'],
      [$class: 'FileBinding', credentialsId: "ft.k8s.auth.client-key", variable: 'CLIENT_KEY']]) {
    sh """
      mkdir -p ${CREDENTIALS_DIR}
      rm -f ${CREDENTIALS_DIR}/*
      cp ${env.CLIENT_CERT} ${CREDENTIALS_DIR}/
      cp ${env.CLIENT_KEY} ${CREDENTIALS_DIR}/
      cp ${env.CA_CERT} ${CREDENTIALS_DIR}/
    """
  }
}

/**
 * Gets the environment name where to deploy from the specified branch name by getting the penultimate one path item.
 * <p>
 * Example:
 * <ol>
 *   <li> for a branch named "feature/xp/test", it will return "xp".</li>
 *   <li> for a branch named "test", it will return null.</li>
 * </ol>
 * @param branchName the name of the branch
 * @return the environment name where to deploy the branch
 */
String getEnvironment(String branchName) {
  String[] values = branchName.split('/')
  if (values.length > 2) {
    return values[values.length - 2]
  }
  return null
}

/**
 * Gets the docker image version from a branch name by getting the last item after the last "/".
 * Example: for a branch name as "feature/xp/test", it will return "test" and for "tags/v0.1.4" it will return "v0.1.4"
 *
 * @param branchName the name of the branch
 * @return the docker image version
 */
String getDockerImageVersion(String branchName) {
  String[] values = branchName.split('/')
  return values[values.length - 1]
}
