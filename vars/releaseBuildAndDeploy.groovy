import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.changerequests.ChangeRequestCloseData
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GithubReleaseInfo
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

def call(BuildConfig config, GithubReleaseInfo releaseInfo) {

  DeploymentUtils deployUtil = new DeploymentUtils()

  List<String> appsInRepo = null
  String tagName = releaseInfo.tagName
  String imageVersion = tagName
  String jenkinsStashId = env.BUILD_NUMBER
  DockerUtils dockerUtils = new DockerUtils()

  catchError {
    node('docker') {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        stage('build image') {
          String dockerRepository = deployUtil.getDockerImageRepository()
          dockerUtils.buildAndPushImage("${dockerRepository}:${imageVersion}")
        }
      }
      appsInRepo = deployUtil.getAppNamesInRepo()
      stash(includes: 'helm/**', name: jenkinsStashId)
    }

    initiateDeploymentToEnvironment(Environment.PRE_PROD_NAME, releaseInfo, appsInRepo, jenkinsStashId,
                                    imageVersion, 1, config)

    initiateDeploymentToEnvironment(Environment.PROD_NAME, releaseInfo, appsInRepo, jenkinsStashId,
                                    imageVersion, 7, config)

  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

public void initiateDeploymentToEnvironment(String targetEnvName, GithubReleaseInfo releaseInfo,
                                            List<String> appsInRepo,
                                            String jenkinsStashId, String imageVersion,
                                            int daysForTheDeployment, BuildConfig config) {
  Environment environment = EnvsRegistry.getEnvironment(targetEnvName)
  stage("deploy to ${environment.name}") {
    timeout(time: daysForTheDeployment, unit: 'DAYS') {

      //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine

      sendSlackMessageForDeployReady(releaseInfo, environment, appsInRepo)

      List<String> remainingRegionsToDeployTo = environment.getRegions()
      String crId = null
      String deployInitiator = null
      while (!remainingRegionsToDeployTo.isEmpty()) {

        if (deployInitiator != null) {
          sendSlackMessageForIntermediaryDeploy(releaseInfo, environment, appsInRepo, remainingRegionsToDeployTo, deployInitiator)
        }

        JenkinsDeployInput deployInput = displayJenkinsInputForDeploy(releaseInfo, environment, appsInRepo,
                                                                      remainingRegionsToDeployTo)

        deployInitiator = deployInput.approver

        if (crId == null) {
          crId = openCr(deployInput.approver, releaseInfo, environment, appsInRepo)
        }

        List<String> regionsToDeployTo = []
        if (deployInput.selectedRegion == "All") {
          regionsToDeployTo.addAll(remainingRegionsToDeployTo)
        } else {
          regionsToDeployTo.add(deployInput.getSelectedRegion())
        }

        deployAppsToEnvironmentRegions(regionsToDeployTo, jenkinsStashId, imageVersion, environment, config)

        remainingRegionsToDeployTo.removeAll(regionsToDeployTo)

        stage("validate apps in ${environment.getNamesWithRegions(regionsToDeployTo)}") {
          sendSlackMessageForValidation(releaseInfo, environment, appsInRepo, config, regionsToDeployTo,
                                        deployInput.approver)
          displayJenkinsInputForValidation(releaseInfo, environment, appsInRepo, regionsToDeployTo)
        }
      }

      closeCr(crId, environment)
    }
  }

}

public void deployAppsToEnvironmentRegions(regionsToDeployTo, String jenkinsStashId, String imageVersion,
                                             Environment environment, BuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()

  node('docker') {
    unstash(jenkinsStashId) // we need this to bring back to the workspace the helm configuration.
    /*  deploy at the same time in all clusters */
    /*  todo [sb] - if more than one cluster, do it one at a time */
    for (int i = 0; i < regionsToDeployTo.size(); i++) {
      String region = regionsToDeployTo.get(i);
      for (int j = 0; j < config.deployToClusters.size(); j++) {
        Cluster cluster = config.deployToClusters.get(j);
        deployUtil.deployAppWithHelm(imageVersion, environment, cluster, region)
      }
    }
  }
}

public void sendSlackMessageForDeployReady(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                           List<String> appsInRepo) {
  String appsJoined = appsInRepo.join(",")

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} ready to deploy in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps `[${appsJoined}]` is ready to deploy in `${targetEnv.name}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

public JenkinsDeployInput displayJenkinsInputForDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                                       List<String> appsInRepo, List<String> availableRegions) {
  String appsJoined = appsInRepo.join(",")
  String envWithRegions = targetEnv.getNamesWithRegions(availableRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps [${appsJoined}] is ready to deploy in '${envWithRegions}'."
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

public void sendSlackMessageForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv, List<String> appsInRepo,
                                          BuildConfig config, List<String> deployedInRegions, String approver) {
  String appsJoined = appsInRepo.join(",")
  List<String> healthchecks = []
  for (Cluster cluster : config.deployToClusters) {
    for (String region : deployedInRegions) {
      String apiServerUrl = targetEnv.getApiServerForCluster(cluster, region)
      String healthBaseUrl = apiServerUrl.replace("-api","")
      String healthcheckURL = "<${healthBaseUrl}/__health|${cluster.getLabel()}-${targetEnv.name}-${region}>"
      healthchecks.add(healthcheckURL)
    }
  }

  SlackAttachment attachment = new SlackAttachment()
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} was deployed and waits validation in '${envWithRegions}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps `[${appsJoined}]` was deployed successfully and is waiting validation in ${healthchecks}."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar

  SlackUtils slackUtils = new SlackUtils()
  /* send notification only to approver */
  slackUtils.sendEnhancedSlackNotification("@${approver}", attachment)
}

public String displayJenkinsInputForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                               List<String> appsInRepo, List<String> deployedInRegions) {
  String appsJoined = appsInRepo.join(",")
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps '[${appsJoined}]' was deployed in '${envWithRegions}' and needs validation. Is this release valid?"
  String approver =
      input(message: releaseMessage, submitterParameter: 'approver', ok: "Release is valid in ${envWithRegions}")
  return approver
}

private String openCr(String approver, GithubReleaseInfo releaseInfo, Environment environment,
                      List<String> appsInRepo) {
  try {
    ChangeRequestOpenData data = new ChangeRequestOpenData()
    data.ownerEmail = "${approver}@ft.com"
    data.summary = "Deploying release ${releaseInfo.tagName} of apps [${appsInRepo.join(",")}] in ${environment.name}"
    data.description = releaseInfo.description ? releaseInfo.description : releaseInfo.title
    data.details = releaseInfo.title
    data.environment = environment.name == Environment.PROD_NAME ? ChangeRequestEnvironment.Production :
                       ChangeRequestEnvironment.Test
    data.notifyChannel = environment.slackChannel
    data.notify = true

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    return crUtils.open(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while opening CR for release ${releaseInfo.getTagName()}: ${e.message} "
  }
}

private void closeCr(String crId, Environment environment) {
  if (crId == null) {
    return
  }

  try {
    ChangeRequestCloseData data = new ChangeRequestCloseData()
    data.notifyChannel = environment.slackChannel
    data.id = crId

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    crUtils.close(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while closing CR ${crId}: ${e.message} "
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
                                           List<String> appsInRepo, List<String> remainingRegions, String initiator) {
  String appsJoined = appsInRepo.join(",")

  SlackAttachment attachment = new SlackAttachment()
  String envWithRegionNames = targetEnv.getNamesWithRegions(remainingRegions)
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} deploy in remaining regions '${envWithRegionNames}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  String validatedEnvWithRegionNames = targetEnv.getNamesWithRegions(targetEnv.getValidatedRegions(remainingRegions))
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps `[${appsJoined}]` was *validated in ${ validatedEnvWithRegionNames}* and is ready to deploy in `${envWithRegionNames}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification("@${initiator}", attachment)
}
