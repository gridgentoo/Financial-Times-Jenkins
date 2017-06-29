package com.ft.jenkins

import com.ft.jenkins.exceptions.InvalidAppConfigFileNameException
import com.ft.jenkins.git.GitUtilsConstants

import java.util.regex.Matcher

import static DeploymentUtilsConstants.APPS_CONFIG_FOLDER
import static DeploymentUtilsConstants.CREDENTIALS_DIR
import static DeploymentUtilsConstants.DEFAULT_HELM_VALUES_FILE
import static DeploymentUtilsConstants.HELM_CONFIG_FOLDER
import static DeploymentUtilsConstants.K8S_CLI_IMAGE
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_CHART_LOCATION_REGEX

/**
 * Deploys the application(s) in the current workspace using helm. It expects the helm chart to be defined in the {@link DeploymentUtilsConstants#HELM_CONFIG_FOLDER} folder.
 *
 * @param imageVersion the version of the docker image to deploy
 * @param env the environment name where it will be deployed.
 * @return the list of applications deployed
 */
public Set<String> deployAppWithHelm(String imageVersion, Environment env, Cluster cluster, String region = null) {
  Set<String> appsToDeploy = getAppNamesInRepo()
  runWithK8SCliTools(env, cluster, region, {
    updateChartVersionFile(imageVersion)

    String chartName = getHelmChartFolderName()
    Iterator<String> i = appsToDeploy.iterator()
    while(i.hasNext()) {
      String app = i.next()
      String configurationFileName = getAppConfigurationFileName(env, cluster, app)
      if (!configurationFileName) {
        echo "Cannot find app configuration file under ${HELM_CONFIG_FOLDER}. Maybe it does not meet the naming conventions."
        return
      }

      echo "Using app config file ${configurationFileName} to deploy with helm"

      sh "helm upgrade ${app} ${HELM_CONFIG_FOLDER}/${chartName} -i -f ${configurationFileName}"
    }
  })
  return appsToDeploy
}

/**
 * Retrieves the repository of the Docker image configured in the Helm chart in the current folder.
 *
 * @return the Docker image repository. Example: "coco/people-rw-neo4j"
 */
public String getDockerImageRepository() {
  String chartFolderName = getHelmChartFolderName()
  String valuesContents = readFile("${HELM_CONFIG_FOLDER}/${chartFolderName}/${DEFAULT_HELM_VALUES_FILE}")
  Matcher matcher = (valuesContents =~ /repository: (.*)\s/)
  /*  get the value matching the group */
  return matcher[0][1]
}

public Set<String> getAppNamesInRepo() {
  String chartFolderName = getHelmChartFolderName()
  Set<String> appNames = []
  def foundConfigFiles = findFiles(glob: "${HELM_CONFIG_FOLDER}/${chartFolderName}/${APPS_CONFIG_FOLDER}/*.yaml")

  for (def configFile : foundConfigFiles) {
    /*  strip the .yaml extension from the files */
    String fileName = configFile.name
    if (fileName.contains("_")) {
      appNames.add(fileName.substring(0, fileName.indexOf('_')))
    } else {
      throw new InvalidAppConfigFileNameException("found invalid app configuration file name: ${fileName} with path: ${configFile.path}")
    }
  }

  return appNames
}

/**
 * Retrieves the folder name where the Helm chart is defined .
 */
private String getHelmChartFolderName() {
  def chartFile = findFiles(glob: HELM_CHART_LOCATION_REGEX)[0]
  String[] chartFilePathComponents = ((String) chartFile.path).split('/')
  /* return the parent folder of Chart.yaml */
  return chartFilePathComponents[chartFilePathComponents.size() - 2]
}

public void runWithK8SCliTools(Environment env, Cluster cluster, String region = null, Closure codeToRun) {
  prepareK8SCliCredentials()
  String currentDir = pwd()

  String apiServer = env.getApiServerForCluster(cluster, region)
  GString dockerRunArgs =
      "-v ${currentDir}/${CREDENTIALS_DIR}:/${CREDENTIALS_DIR} " +
      "-e 'K8S_API_SERVER=${apiServer}' " +
      "-e 'KUBECONFIG=${currentDir}/kubeconfig'"

  docker.image(K8S_CLI_IMAGE).inside(dockerRunArgs) {
    sh "/docker-entrypoint.sh"

    codeToRun.call()
  }
}

private void prepareK8SCliCredentials() {
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

String getTeamFromReleaseCandidateTag(String rcTag) {
  String[] tagComponents = rcTag.split("-")
  if (tagComponents.length > 1) {
    return tagComponents[1]
  }
  throw new IllegalArgumentException(
      "The tag '${rcTag}' is not a valid release candidate tag. A good example is: 0.2.0-xp-test-release-rc2")
}

/**
 * Gets the environment name where to deploy from the specified branch name by getting the penultimate one path item.
 * <p>
 * Example:
 * <ol>
 *   <li> for a branch named "deploy-on-push/xp/test", it will return "xp".</li>
 *   <li> for a branch named "test", it will throw an IllegalArgumentException.</li>
 * </ol>
 * @param branchName the name of the branch
 * @return the environment name where to deploy the branch
 */
String getEnvironmentName(String branchName) {
  String[] values = branchName.split('/')
  if (values.length > 2) {
    return values[values.length - 2]
  }

  throw new IllegalArgumentException(
      "The branch '${branchName}' does not contain the environment where to deploy the application. A valid name is 'deploy-on-push/xp/test'")
}

/**
 * Gets the docker image version from a branch name by getting the last item after the last "/".
 * Example: for a branch name as "deploy-on-push/xp/test", it will return "test" and for "tags/v0.1.4" it will return "v0.1.4"
 *
 * @param branchName the name of the branch
 * @return the docker image version
 */
String getReleaseCandidateName(String branchName) {
  String[] values = branchName.split('/')
  return values[values.length - 1]
}

void updateChartVersionFile(String chartVersion) {
  echo "Setting chart version to: ${chartVersion}"
  def chartFile = findFiles(glob: HELM_CHART_LOCATION_REGEX)[0]
  String chartFileContent = readFile chartFile.path
  String updatedChartFileContent = chartFileContent.
      replaceAll("(Version|version): ${GitUtilsConstants.GIT_VERSION_REGEX}", "Version: ${chartVersion}")
  writeFile file: chartFile.path, text: updatedChartFileContent

  echo "Updated chart yaml:"
  sh "cat ${chartFile.path}"
}

private String getAppConfigurationFileName(Environment targetEnv, Cluster targetCluster, String app) {
  String appsConfigFolder = "${HELM_CONFIG_FOLDER}/${app}/${APPS_CONFIG_FOLDER}"
  String appConfigFileName = "${app}_${targetCluster.getLabel()}_${targetEnv.getName()}"
  def foundConfigFiles = findFiles(glob: "${appsConfigFolder}/${appConfigFileName}.yaml")
  if (foundConfigFiles.length > 0) {
    return foundConfigFiles[0].path
  }

  appConfigFileName = "${app}_${targetCluster.getLabel()}"
  foundConfigFiles = findFiles(glob: "${appsConfigFolder}/${appConfigFileName}.yaml")
  if (foundConfigFiles.length > 0) {
    return foundConfigFiles[0].path
  }
}
