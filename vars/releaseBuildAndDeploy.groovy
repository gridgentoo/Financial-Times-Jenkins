import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GitUtils
import com.ft.jenkins.git.GithubReleaseInfo
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

def call(BuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()
  GitUtils gitUtils = new GitUtils()

  List<String> appsInRepo = null
  String tagName = gitUtils.getTagNameFromBranchName(env.BRANCH_NAME)
  String imageVersion = tagName
  String jenkinsStashId = env.BUILD_NUMBER
  GithubReleaseInfo releaseInfo = gitUtils.getGithubReleaseInfo(tagName)

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
                                            int daysToWaitForDecision, BuildConfig config) {
  DeploymentUtils deployUtil = new DeploymentUtils()
  Environment environment = EnvsRegistry.getEnvironment(targetEnvName)
  stage("deploy to ${environment.name}") {
    timeout(time: daysToWaitForDecision, unit: 'DAYS') {

      //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine

      sendSlackMessageForDeployReady(releaseInfo, environment, appsInRepo)
      String approver = displayJenkinsInputForDeploy(releaseInfo, environment, appsInRepo)

      //  todo [sb] open and close CR
      node('docker') {
        unstash(jenkinsStashId) // we need this to bring back to the workspace the helm configuration.
        /*  todo[sb] allow for choosing where to deploy and how */
        /*  deploy at the same time in all envs and clusters */
        for (int i = 0; i < environment.regions.size(); i++) {
          String region = environment.regions.get(i);
          for (int j = 0; j < config.deployToClusters.size(); j++) {
            Cluster cluster = config.deployToClusters.get(j);
            deployUtil.deployAppWithHelm(imageVersion, environment, cluster, region)
          }
        }
      }
    }
  }
  stage("validate apps in ${environment.name}") {
    sendSlackMessageForValidation(releaseInfo, environment, appsInRepo, config)
    displayJenkinsInputForValidation(releaseInfo, environment, appsInRepo)
  }
}


public void sendSlackMessageForDeployReady(GithubReleaseInfo releaseInfo, Environment targetEnv, List<String> appsInRepo) {
  String appsJoined = appsInRepo.join(",")

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} ready to deploy in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps `[${appsJoined}]` is ready to deploy in `${targetEnv.name}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

public String displayJenkinsInputForDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                           List<String> appsInRepo) {
  String appsJoined = appsInRepo.join(",")
  String releaseMessage = "The release ${releaseInfo.tagName} of apps `[${appsJoined}]` is ready to deploy in `${targetEnv.name}`."
  String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Deploy to ${targetEnv.name}")
  return approver
}

public void sendSlackMessageForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv, List<String> appsInRepo, BuildConfig config) {
  String appsJoined = appsInRepo.join(",")
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} is waiting validation in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps `[${appsJoined}]` was deployed successfully and is waiting validation in `${targetEnv.name}` in clusters: *${Cluster.toLabels(config.deployToClusters).join(',')}*."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

public String displayJenkinsInputForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                           List<String> appsInRepo) {
  String appsJoined = appsInRepo.join(",")
  String releaseMessage = "The release ${releaseInfo.tagName} of apps `[${appsJoined}]` was deployed in `${targetEnv.name}` and needs validation. Is this release valid?"
  String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Release is valid in ${targetEnv.name}")
  return approver
}
