package com.ft.jenkins

import com.ft.jenkins.aws.AwsUtils
import com.ft.jenkins.exceptions.ConfigurationNotFoundException
import com.ft.jenkins.exceptions.InvalidAppConfigFileNameException

import java.util.regex.Matcher

import static DeploymentUtilsConstants.APPS_CONFIG_FOLDER
import static DeploymentUtilsConstants.DEFAULT_HELM_VALUES_FILE
import static DeploymentUtilsConstants.HELM_CONFIG_FOLDER
import static DeploymentUtilsConstants.K8S_CLI_IMAGE
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_AWS_CREDENTIALS
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_CHART_LOCATION_REGEX
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_LOCAL_REPO_NAME
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_REPO_URL
import static com.ft.jenkins.DeploymentUtilsConstants.HELM_S3_BUCKET
import static com.ft.jenkins.HashUtil.md5

public Map<Cluster, List<String>> deployAppsInChartWithHelm(String chartFolderLocation, Environment env,
                                                            Cluster deployOnlyInCluster = null, String region = null) {
  Map<Cluster, List<String>> appsPerCluster = getAppsInChart(chartFolderLocation, env, deployOnlyInCluster)
  List<String> regionsToDeployTo = env.getRegionsToDeployTo(region)


  println "------------deployAppsInChartWithHelm----------"

  /*  deploy apps in all target clusters */
  appsPerCluster.each { Cluster targetCluster, List<String> appsToDeploy ->
    printf("Deploying to cluster %s", targetCluster.toString())
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

public void removeAppsInChartWithHelm(String chartName, String chartVersion, Environment targetEnv,
                                      Cluster onlyFromCluster = null, String region = null) {
  /*  fetch the chart locally */
  runHelmOperations {
    sh "helm fetch --untar ${HELM_LOCAL_REPO_NAME}/${chartName} --version ${chartVersion}"
  }

  Map<Cluster, List<String>> appsPerCluster = getAppsInChart(chartName, targetEnv, onlyFromCluster)
  List<String> regionsToRemoveFrom = targetEnv.getRegionsToDeployTo(region)

  /*  delete the apps */
  appsPerCluster.each { Cluster targetCluster, List<String> appsToRemove ->
    if (regionsToRemoveFrom) {
      for (String regionToRemoveFrom : regionsToRemoveFrom) {
        executeAppsRemoval(targetCluster, appsToRemove, targetEnv, regionToRemoveFrom)
      }
    } else { // the environment has no region
      executeAppsRemoval(targetCluster, appsToRemove, targetEnv)
    }
  }
}

public executeAppsRemoval(Cluster targetCluster, List<String> appsToRemove, Environment env,
                          String region = null) {
  runWithK8SCliTools(env, targetCluster, region, {
    for (String app : appsToRemove) {
      echo "Removing app ${app} from ${env.getFullClusterName(targetCluster, region)}"
      sh "helm delete ${app}"
    }
  })
}

public executeAppsDeployment(Cluster targetCluster, List<String> appsToDeploy, String chartFolderLocation,
                             Environment env, String region = null) {
  println "----------------executeAppsDeployment--------------"
  runWithK8SCliTools(env, targetCluster, region, {
    for (String app : appsToDeploy) {
      printf("appName %s", app)
      String configurationFileName = getAppConfigurationFileName(chartFolderLocation, env, targetCluster, app, region)
      if (!configurationFileName) {
        throw new ConfigurationNotFoundException(
            "Cannot find app configuration file ${configurationFileName}. Maybe it does not meet the naming conventions.")
      }

      echo "Using app config file ${configurationFileName} to deploy with helm"

      String additionalHelmValues =
          "--set region=${region} " +
          "--set target_env=${env.name} " +
          "--set __ext.target_cluster.sub_domain=${env.getClusterSubDomain(targetCluster, region)} " +
          "${getClusterUrlsAsHelmValues(env, region)} " +
          "${getGlbUrlsAsHelmValues(env)}"
      print "=============="
      print additionalHelmValues
      print "=============="
      //sh "helm upgrade ${app} ${chartFolderLocation} -i --timeout 1200 -f ${configurationFileName} ${additionalHelmValues}"
      sh "helm upgrade ${app} ${chartFolderLocation} -i --timeout 1200 -f ${configurationFileName}"
    }
  })
}

public String getClusterUrlsAsHelmValues(Environment environment, String region) {
  String result = ""
  for (Cluster cluster : environment.clusters) {
    String clusterUrl = environment.getEntryPointUrl(cluster, region)
    result += " --set cluster.${cluster.label}.url=${clusterUrl}"
  }
  return result
}

public String getGlbUrlsAsHelmValues(Environment environment) {
  String result = ""
  environment.glbMap.each { entry ->
    result += " --set glb.${entry.key.toLowerCase()}.url=${entry.value}"
  }
  return result
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

public Map<Cluster, List<String>> getAppsInChart(String chartFolderLocation, Environment targetEnv,
                                                 Cluster includeOnlyCluster = null) {
  Map<Cluster, List<String>> result = [:]
  def foundConfigFiles = findFiles(glob: "${chartFolderLocation}/${APPS_CONFIG_FOLDER}/*.yaml")

  for (def configFile : foundConfigFiles) {
    /*  compute the app name and the cluster where it will be deployed. The format is ${app-name}_${cluster}[_${env}].yaml */
    String configFileName = configFile.name
    /*  strip the yaml extension */
    configFileName = configFileName.replace(".yaml", "")
    String[] fileNameParts = configFileName.split("_")

    if (fileNameParts.length > 1) {
      /*  add the app name to the corresponding cluster if it wasn't added yet */
      Cluster targetCluster = Cluster.valueOfLabel(fileNameParts[1])
      if ((includeOnlyCluster && targetCluster != includeOnlyCluster) ||
          (!targetEnv.clusters.contains(targetCluster))) {
        continue
      }
      String appName = fileNameParts[0]
      addAppToCluster(result, targetCluster, appName)

      printf("Adding app %s to cluster %s", appName, targetCluster.toString())

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
    sh "helm repo update"
    codeToRun.call()
  }
}

public String publishHelmChart(String version) {
  String chartFolderName = getHelmChartFolderName()
  //  lock the helm repo, so other jobs will wait on this, as we need to upload the new index before any other
  // job starts download it for merging. If we don't do this we might end up with an incomplete index.
  lock("helm-repo") {
    runHelmOperations {
      updateChartDeps("${HELM_CONFIG_FOLDER}/${chartFolderName}")

      /*  pack the chart  */
      sh "helm package --version ${version} ${HELM_CONFIG_FOLDER}/${chartFolderName}"

      /* update the repository index.yaml */
      sh ("helm repo index --merge \$HELM_HOME/repository/cache/${HELM_LOCAL_REPO_NAME}-index.yaml --url ${HELM_REPO_URL} .")
    }

    /*  upload chart and updated index to S3  */
    AwsUtils awsUtils = new AwsUtils()
    awsUtils.uploadS3Files(HELM_S3_BUCKET, HELM_AWS_CREDENTIALS,
                           (String) "${chartFolderName}-${version}.tgz", "index.yaml")
    return chartFolderName
  }
}

void updateChartDeps(String chartFolder) {
  String depsFile = "${chartFolder}/requirements.yaml"
  if (!fileExists(depsFile)) {
    return
  }
  echo "Updating chart dependencies ..."

  Set<String> depsRepos = getChartDepsRepos(depsFile)

  /*  adding the repos locally to helm. Using as the name the md5 hash of repo URL */
  for (String repo : depsRepos) {
    sh "helm repo add ${md5(repo)} ${repo}"
  }

  sh "helm dep update ${chartFolder}"
}


Set<String> getChartDepsRepos(String depsFile) {
  def deps = readYaml(file: depsFile)
  if (!deps) {
    return Collections.emptySet()
  }

  Set<String> repos = new HashSet<>()

  for (def dep : deps.dependencies) {
    if (dep.repository) {
      repos.add(dep.repository)
    }
  }
  return repos
}

/**
 * Retrieves the folder name where the Helm chart is defined .
 */
private String getHelmChartFolderName() {
  def chartFile = findFiles(glob: HELM_CHART_LOCATION_REGEX)[0]
  if (chartFile == null) {
    throw new IllegalStateException("There is no Helm Chart.yaml defined in the children of the Helm folder")
  }
  String[] chartFilePathComponents = ((String) chartFile.path).split('/')
  /* return the parent folder of Chart.yaml */
  return chartFilePathComponents[chartFilePathComponents.size() - 2]
}

public void runWithK8SCliTools(Environment targetEnv, Cluster cluster, String region = null, Closure codeToRun) {
  String currentDir = pwd()

  String apiServer = targetEnv.getApiServerForCluster(cluster, region)
  String clusterSubDomain = targetEnv.getClusterSubDomain(cluster, region)
  withCredentials([string(credentialsId: "ft.k8s-auth.${clusterSubDomain}.token", variable: 'TOKEN')]) {
    GString dockerRunArgs =
        "-e 'K8S_API_SERVER=${apiServer}' " +
        "-e 'KUBECONFIG=${currentDir}/kubeconfig' " +
        "-e 'K8S_TOKEN=${env.TOKEN}'"

    println "=========== DOCKER RUN ARGS ==================="
    println dockerRunArgs
    println "=========== DOCKER RUN ARGS ==================="

    docker.image(K8S_CLI_IMAGE).inside(dockerRunArgs) {
      sh "/docker-entrypoint.sh"

      codeToRun.call()
    }
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
    return values[1]
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

private String computeAppConfigFullPath(String appConfigFileName, String chartFolderLocation) {
  return "${chartFolderLocation}/${APPS_CONFIG_FOLDER}/${appConfigFileName}.yaml"
}

private String getAppConfigurationFileName(String chartFolderLocation, Environment targetEnv, Cluster targetCluster,
                                           String app, String region) {
  String appConfigPath
  // if region is specified looking for configuration file for a specific env for specific region, e.g. publishing_pre-prod_us
  if (region) {
    appConfigPath = computeAppConfigFullPath("${app}_${targetCluster.getLabel()}_${targetEnv.getName()}_${region}",
                                             chartFolderLocation)
    println "Checking if file exists: " + appConfigPath

    if (fileExists(appConfigPath)) {
      println "Found " + appConfigPath
      return appConfigPath
    }
  }
  println "Not found"

  //looking for configuration file for a specific env, e.g. publishing_pre-prod
  appConfigPath =
      computeAppConfigFullPath("${app}_${targetCluster.getLabel()}_${targetEnv.getName()}", chartFolderLocation)
    println "Checking if file exists: " + appConfigPath

  if (fileExists(appConfigPath)) {
    println "Found " + appConfigPath
    return appConfigPath
  }
  println "Not found"

  //looking for configuration file for all envs, e.g publishing
  appConfigPath = computeAppConfigFullPath("${app}_${targetCluster.getLabel()}", chartFolderLocation)
    println "Checking if file exists: " + appConfigPath

  if (fileExists(appConfigPath)) {
    println "Found " + appConfigPath
    return appConfigPath
  }
  println "No configuration files found"
}

public List<String> getAppsInFirstCluster(Map<Cluster, List<String>> appsPerCluster) {
  Cluster firstCluster = appsPerCluster.keySet().iterator().next()
  return appsPerCluster.get(firstCluster)
}

public boolean areSameAppsInAllClusters(Map<Cluster, List<String>> appsPerCluster) {
  List<String> appsInFirstCluster = getAppsInFirstCluster(appsPerCluster)
  Boolean sameAppsInAllClusters = true
  appsPerCluster.each { Cluster cluster, List<String> appsInCluster ->
    if (appsInFirstCluster != appsInCluster) {
      sameAppsInAllClusters = false
    }
  }
  return sameAppsInAllClusters
}

