package com.ft.jenkins

import com.ft.jenkins.aws.AwsUtils
import com.ft.jenkins.exceptions.ConfigurationNotFoundException
import com.ft.jenkins.exceptions.InvalidAppConfigFileNameException

import java.util.regex.Matcher

import static DeploymentUtilsConstants.APPS_CONFIG_FOLDER
import static DeploymentUtilsConstants.CREDENTIALS_DIR
import static DeploymentUtilsConstants.DEFAULT_HELM_VALUES_FILE
import static DeploymentUtilsConstants.HELM_CONFIG_FOLDER
import static DeploymentUtilsConstants.K8S_CLI_IMAGE
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_AWS_CREDENTIALS
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_CHART_LOCATION_REGEX
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_LOCAL_REPO_NAME
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_REPO_URL
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_S3_BUCKET

/**
 * Deploys the application(s) in the current workspace using helm. It expects the helm chart to be defined in the {@link DeploymentUtilsConstants#HELM_CONFIG_FOLDER} folder.
 *
 * @param imageVersion the version of the docker image to deploy
 * @param env the environment name where it will be deployed.
 * @return the list of applications deployed
 */
//  todo [sb] remove this
public List<String> deployAppWithHelm(String imageVersion, Environment env, Cluster cluster, String region = null) {
  List<String> appsToDeploy = getAppNamesInRepo()
  runWithK8SCliTools(env, cluster, region, {
    updateChartVersionFile(imageVersion)

    String chartName = getHelmChartFolderName()
    for (int i = 0; i < appsToDeploy.size(); i++) {
      String app = appsToDeploy.get(i)
      String configurationFileName =
          getAppConfigurationFileName("${HELM_CONFIG_FOLDER}/${chartName}", env, cluster, app)
      if (!configurationFileName) {
        throw new ConfigurationNotFoundException(
            "Cannot find app configuration file under ${HELM_CONFIG_FOLDER}. Maybe it does not meet the naming conventions.")
      }

      echo "Using app config file ${configurationFileName} to deploy with helm"

      sh "helm upgrade ${app} ${HELM_CONFIG_FOLDER}/${chartName} -i -f ${configurationFileName}"
    }
  })
  return appsToDeploy
}

public Map<Cluster, List<String>> deployAppsInChartWithHelm(String chartFolderLocation, Environment env,
                                                            Cluster deployOnlyInCluster = null, String region = null) {
  Map<Cluster, List<String>> appsPerCluster = getAppsToDeployInChart(chartFolderLocation, deployOnlyInCluster)
  List<String> regionsToDeployTo = env.getRegionsToDeployTo(region)

  /*  deploy apps in all target clusters */
  appsPerCluster.each { Cluster targetCluster, List<String> appsToDeploy ->
    if (regionsToDeployTo) {
      for (String regionToDeployTo : regionsToDeployTo) {
        executeAppsDeployment(targetCluster, appsToDeploy, chartFolderLocation, env, regionToDeployTo)
      }
    } else { // the environment has no region
      executeAppsDeployment(targetCluster, appsToDeploy, chartFolderLocation, env)
    }

  }
  return appsPerCluster
}

public executeAppsDeployment(Cluster targetCluster, List<String> appsToDeploy, String chartFolderLocation,
                             Environment env, String region = null) {
  runWithK8SCliTools(env, targetCluster, region, {
    for (String app : appsToDeploy) {
      String configurationFileName = getAppConfigurationFileName(chartFolderLocation, env, targetCluster, app)
      if (!configurationFileName) {
        throw new ConfigurationNotFoundException(
            "Cannot find app configuration file ${configurationFileName}. Maybe it does not meet the naming conventions.")
      }

      echo "Using app config file ${configurationFileName} to deploy with helm"

      sh "helm upgrade ${app} ${chartFolderLocation} -i -f ${configurationFileName}"
    }
  })
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

//  todo [sb] remove this
public List<String> getAppNamesInRepo() {
  String chartFolderName = getHelmChartFolderName()
  Set<String> appNames = []
  def foundConfigFiles = findFiles(glob: "${HELM_CONFIG_FOLDER}/${chartFolderName}/${APPS_CONFIG_FOLDER}/*.yaml")
  echo "test : ${HELM_CONFIG_FOLDER}/${chartFolderName}/${APPS_CONFIG_FOLDER}/*.yaml"

  for (def configFile : foundConfigFiles) {
    /*  strip the .yaml extension from the files */
    String fileName = configFile.name
    if (fileName.contains("_")) {
      appNames.add(fileName.substring(0, fileName.indexOf('_')))
    } else {
      throw new InvalidAppConfigFileNameException(
          "found invalid app configuration file name: ${fileName} with path: ${configFile.path}")
    }
  }

  return new ArrayList<>(appNames)
}

public Map<Cluster, List<String>> getAppsToDeployInChart(String chartFolderLocation,
                                                         Cluster includeOnlyCluster = null) {
  Map<Cluster, List<String>> result = [:]
  def foundConfigFiles = findFiles(glob: "${chartFolderLocation}/${APPS_CONFIG_FOLDER}/*.yaml")

  for (def configFile : foundConfigFiles) {
    /*  compute the app name and the cluster where it will be deployed. The format is ${app-name}_${cluster}[_${env}].yaml */
    String configFileName = configFile.name
    /*  strip the yaml extenstion */
    configFileName = configFileName.replace(".yaml", "")
    String[] fileNameParts = configFileName.split("_")

    if (fileNameParts.length > 1) {
      /*  add the app name to the corresponding cluster if it wasn't added yet */
      Cluster targetCluster = Cluster.valueOfLabel(fileNameParts[1])
      if (includeOnlyCluster && targetCluster != includeOnlyCluster) {
        continue
      }
      String appName = fileNameParts[0]
      addAppToCluster(result, targetCluster, appName)

    } else {
      throw new InvalidAppConfigFileNameException(
          "found invalid app configuration file name: ${configFileName} with path: ${configFile.path}")
    }
  }

  return result
}

private void addAppToCluster(LinkedHashMap<Cluster, List<String>> result, Cluster targetCluster, String appName) {
  if (result[targetCluster] == null) {
    result.put(targetCluster, [])
  }

  if (!result[targetCluster].contains(appName)) {
    result[targetCluster].add(appName)
    echo "App ${appName} will be deployed in cluster ${targetCluster.label}"
  }
}

public Map<Cluster, List<String>> deployAppFromHelmRepo(String chartName, String chartVersion, Environment targetEnv,
                                                        Cluster onlyToCluster = null, String region = null) {
  /*  fetch the chart locally */
  runHelmOperations {
    sh "helm fetch --untar ${HELM_LOCAL_REPO_NAME}/${chartName} --version ${chartVersion}"
  }

  return deployAppsInChartWithHelm(chartName, targetEnv, onlyToCluster, region)
}

public void runHelmOperations(Closure codeToRun) {
  docker.image(K8S_CLI_IMAGE).inside("-e 'HELM_HOME=/tmp/.helm'") {
    sh "helm init -c"
    sh "helm repo add ${HELM_LOCAL_REPO_NAME} ${HELM_REPO_URL}"
    codeToRun.call()
  }
}

public String publishHelmChart(String version) {
  String chartFolderName = getHelmChartFolderName()
  runHelmOperations {
    /*  pack the chart  */
    sh "helm package --version ${version} ${HELM_CONFIG_FOLDER}/${chartFolderName}"

    /*  todo [sb] here we'd need a solution for blocking any updates from other jobs until this update finishes */
    /* update the repository index.yaml */
    sh "helm repo index --merge \$HELM_HOME/repository/cache/${HELM_LOCAL_REPO_NAME}-index.yaml --url ${HELM_REPO_URL} ."
  }

  /*  upload chart and updated index to S3  */
  AwsUtils awsUtils = new AwsUtils()
  awsUtils.uploadS3Files(HELM_S3_BUCKET, HELM_AWS_CREDENTIALS,
                         (String) "${chartFolderName}-${version}.tgz", "index.yaml")
  return chartFolderName
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
      replaceAll("(Version|version):.*", "Version: ${chartVersion}")
  writeFile file: chartFile.path, text: updatedChartFileContent

  echo "Updated chart yaml:"
  sh "cat ${chartFile.path}"
}

private String getAppConfigurationFileName(String chartFolderLocation, Environment targetEnv, Cluster targetCluster,
                                           String app) {
  String appsConfigFolder = "${chartFolderLocation}/${APPS_CONFIG_FOLDER}"

  //looking for configuration file for a specific env, e.g. publishing_pre-prod
  String appConfigFileName = "${app}_${targetCluster.getLabel()}_${targetEnv.getName()}"
  String appConfigPath = "${appsConfigFolder}/${appConfigFileName}.yaml"
  echo "searching for: ${appConfigPath}"
  if (fileExists(appConfigPath)) {
    return appConfigPath
  }

  //looking for configuration file for all envs
  appConfigFileName = "${app}_${targetCluster.getLabel()}"
  appConfigPath = "${appsConfigFolder}/${appConfigFileName}.yaml"
  echo "searching for: ${appConfigPath}"
  if (fileExists(appConfigPath)) {
    return appConfigPath
  }
}

private boolean fileExists(String path) {
  def foundConfigFiles = findFiles(glob: path)
  return foundConfigFiles.length > 0
}

