import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GithubReleaseInfo
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

import static com.ft.jenkins.DeploymentUtilsConstants.HELM_CONFIG_FOLDER

def call(GithubReleaseInfo releaseInfo, BuildConfig buildConfig) {

  DeploymentUtils deployUtil = new DeploymentUtils()

  Map<Cluster, List<String>> appsInRepo = null
  String tagName = releaseInfo.tagName
  String appVersion = tagName
  DockerUtils dockerUtils = new DockerUtils()
  String chartName

  catchError {
    node('docker') {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        if (fileExists("Dockerfile")) { //  build Docker image only if we have a Dockerfile
          stage('build image') {
            String dockerRepository = deployUtil.getDockerImageRepository()
            dockerUtils.buildAndPushImage("${dockerRepository}:${appVersion}")
          }
        }

        stage('publish chart') {
          chartName = deployUtil.publishHelmChart(appVersion)
        }
      }
      appsInRepo = deployUtil.getAppsInChart("${HELM_CONFIG_FOLDER}/${chartName}", EnvsRegistry.getEnvironment(buildConfig.preprodEnvName))
    }

    initiateDeploymentToEnvironment(buildConfig.preprodEnvName, chartName, appVersion, releaseInfo, appsInRepo
                                    , 1)

    initiateDeploymentToEnvironment(buildConfig.prodEnvName, chartName, appVersion, releaseInfo, appsInRepo
                                    , 7)

  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

public void initiateDeploymentToEnvironment(String targetEnvName, String chartName,
                                            String version,
                                            GithubReleaseInfo releaseInfo, Map<Cluster, List<String>> appsPerCluster,
                                            int daysForTheDeployment) {
  Environment environment = EnvsRegistry.getEnvironment(targetEnvName)
  stage("deploy to ${environment.name}") {
    timeout(time: daysForTheDeployment, unit: 'DAYS') {

      //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine

      sendSlackMessageForDeployReady(releaseInfo, chartName, environment, appsPerCluster)

      List<String> remainingRegionsToDeployTo = environment.getRegions()
      String crId = null
      String deployInitiator = null
      while (!remainingRegionsToDeployTo.isEmpty()) {

        if (deployInitiator != null) {
          sendSlackMessageForIntermediaryDeploy(releaseInfo, environment, appsPerCluster, remainingRegionsToDeployTo,
                                                deployInitiator, chartName)
        }

        JenkinsDeployInput deployInput = displayJenkinsInputForDeploy(releaseInfo, environment, appsPerCluster,
                                                                      remainingRegionsToDeployTo)

        deployInitiator = deployInput.approver

        if (crId == null) {
          crId = openCr(deployInitiator, releaseInfo, environment, appsPerCluster, chartName)
        }

        List<String> regionsToDeployTo = []
        if (deployInput.selectedRegion == "All") {
          regionsToDeployTo.addAll(remainingRegionsToDeployTo)
        } else {
          regionsToDeployTo.add(deployInput.getSelectedRegion())
        }

        deployAppsToEnvironmentRegions(regionsToDeployTo, chartName, version, environment)

        remainingRegionsToDeployTo.removeAll(regionsToDeployTo)

        stage("validate apps in ${environment.getNamesWithRegions(regionsToDeployTo)}") {
          sendSlackMessageForValidation(releaseInfo, environment, appsPerCluster, regionsToDeployTo,
                                        deployInitiator, chartName)
          displayJenkinsInputForValidation(releaseInfo, environment, appsPerCluster, regionsToDeployTo)
        }
      }
    }
  }

}

public void deployAppsToEnvironmentRegions(List<String> regionsToDeployTo, String chartName, String imageVersion,
                                           Environment environment) {

  /*  deploy at the same time in all clusters */
  /*  todo [sb] - if more than one cluster, do it one at a time */
  for (String region : regionsToDeployTo) {
    build job: DeploymentUtilsConstants.GENERIC_DEPLOY_JOB,
          parameters: [
              string(name: 'Chart', value: chartName),
              string(name: 'Version', value: imageVersion),
              string(name: 'Environment', value: environment.getName()),
              string(name: 'Cluster', value: 'all-in-chart'),
              string(name: 'Region', value: region),
              booleanParam(name: 'Send success notifications', value: false)]

  }
}

public void sendSlackMessageForDeployReady(GithubReleaseInfo releaseInfo, String chartName,
                                           Environment targetEnv, Map<Cluster, List<String>> appsPerCluster) {

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${chartName}]:${releaseInfo.tagName} ready to deploy in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input"

  String appsText = computeSlackTextForAppsToDeploy(appsPerCluster)

  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${appsText}, is ready to deploy in `${targetEnv.name}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

String computeSlackTextForAppsToDeploy(Map<Cluster, List<String>> appsPerCluster) {
  String appsText
  DeploymentUtils deployUtils = new DeploymentUtils()
  if (deployUtils.areSameAppsInAllClusters(appsPerCluster)) {
    List<String> apps = deployUtils.getAppsInFirstCluster(appsPerCluster)
    appsText = "`${apps}` in clusters `${Cluster.toLabels(appsPerCluster.keySet())}`"
  } else {
    List<String> messages = []
    appsPerCluster.each { Cluster cluster, List<String> appsInCluster ->
      messages.add("`${appsInCluster}` in cluster `${cluster.label}`")
    }
    appsText = messages.join(" and ")
  }
  return appsText
}

String computeSimpleTextForAppsToDeploy(Map<Cluster, List<String>> appsPerCluster) {
  String appsText
  DeploymentUtils deployUtils = new DeploymentUtils()
  if (deployUtils.areSameAppsInAllClusters(appsPerCluster)) {
    List<String> apps = deployUtils.getAppsInFirstCluster(appsPerCluster)
    appsText = "${apps} in clusters ${Cluster.toLabels(appsPerCluster.keySet())}"
  } else {
    List<String> messages = []
    appsPerCluster.each { Cluster cluster, List<String> appsInCluster ->
      messages.add("${appsInCluster} in cluster ${cluster.label}")
    }
    appsText = messages.join(" and ")
  }
  return appsText
}

public JenkinsDeployInput displayJenkinsInputForDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                                       Map<Cluster, List<String>> appsPerCluster,
                                                       List<String> availableRegions) {
  String envWithRegions = targetEnv.getNamesWithRegions(availableRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} is ready to deploy in '${envWithRegions}'."
  if (availableRegions.size() == 1) {
    String regionToDeploy = availableRegions.get(0)
    releaseMessage = releaseMessage + "It will be deployed in region ${regionToDeploy}"
    String approver = input(message: releaseMessage, submitterParameter: 'approver',
                            ok: "Deploy to ${targetEnv.getNamesWithRegions(availableRegions)}")
    return new JenkinsDeployInput(approver, regionToDeploy)
  } else {
    String choices = "All" + "\n" + availableRegions.join("\n")
    String regionParamName = "Region to deploy to"
    def regionChoice = choice(choices: choices,
                              description: 'Please choose in what region(s) do you want to deploy first. If you choose only one region, you will be presented later with the deploy option for the other one',
                              name: regionParamName)
    def chosenParams = input(message: releaseMessage, parameters: [regionChoice], submitterParameter: 'approver',
                             ok: "Deploy to ${targetEnv.name}")
    return new JenkinsDeployInput(chosenParams.get('approver') as String, chosenParams.get(regionParamName) as String)
  }
}

public void sendSlackMessageForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                          Map<Cluster, List<String>> appsPerCluster,
                                          List<String> deployedInRegions, String approver, String chartName) {
  SlackUtils slackUtils = new SlackUtils()

  List<String> healthURLs = []
  for (Cluster cluster : appsPerCluster.keySet()) {
    for (String region : deployedInRegions) {
      healthURLs.add(slackUtils.getHealthUrl(targetEnv, cluster, region))
    }
  }

  SlackAttachment attachment = new SlackAttachment()
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)
  attachment.title = "Click for manual decision: ${chartName}:${releaseInfo.tagName} was deployed and waits validation in '${envWithRegions}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${computeSlackTextForAppsToDeploy(appsPerCluster)}, was deployed successfully and is waiting validation in ${healthURLs}."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar

  /* send notification only to approver */
  slackUtils.sendEnhancedSlackNotification("@${approver}", attachment)
}

public String displayJenkinsInputForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                               Map<Cluster, List<String>> appsPerCluster,
                                               List<String> deployedInRegions) {
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} was deployed in '${envWithRegions}' and needs validation. Is this release valid?"
  String approver =
      input(message: releaseMessage, submitterParameter: 'approver', ok: "Release is valid in ${envWithRegions}")
  return approver
}

private String openCr(String approver, GithubReleaseInfo releaseInfo, Environment environment,
                      Map<Cluster, List<String>> appsPerCluster, String chartName) {
  try {
    ChangeRequestOpenData data = new ChangeRequestOpenData()
    data.ownerEmail = "${approver}@ft.com"
    data.systemCode = "${chartName}"
    data.summary = "Deploying chart ${chartName}:${releaseInfo.tagName} with apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} in ${environment.name}"
    data.environment = environment.name == Environment.PROD_NAME ? ChangeRequestEnvironment.Production :
                       ChangeRequestEnvironment.Test
    data.notifyChannel = environment.slackChannel

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    return crUtils.open(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while opening CR for release ${releaseInfo.getTagName()}: ${e.message} "
  }
}

final class JenkinsDeployInput implements Serializable {

  String approver
  String selectedRegion

  JenkinsDeployInput(String approver, String selectedRegion) {
    this.approver = approver
    this.selectedRegion = selectedRegion
  }
}

void sendSlackMessageForIntermediaryDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                           Map<Cluster, List<String>> appsPerCluster, List<String> remainingRegions,
                                           String initiator, String chartName) {
  SlackAttachment attachment = new SlackAttachment()
  String envWithRegionNames = targetEnv.getNamesWithRegions(remainingRegions)
  attachment.title = "Click for manual decision: ${chartName}:${releaseInfo.tagName} deploy in remaining regions '${envWithRegionNames}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  String validatedEnvWithRegionNames = targetEnv.getNamesWithRegions(targetEnv.getValidatedRegions(remainingRegions))
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${computeSlackTextForAppsToDeploy(appsPerCluster)}, was *validated in ${validatedEnvWithRegionNames}* and is ready to deploy in `${envWithRegionNames}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification("@${initiator}", attachment)
}
