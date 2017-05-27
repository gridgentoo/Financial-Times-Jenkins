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
  String stashId = env.BUILD_URL
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
      stash(includes: 'helm/**', name: stashId)
    }

    //  todo [SB] release node while waiting for input
    Environment environment = EnvsRegistry.getEnvironment(Environment.PRE_PROD_NAME)
    stage("deploy to ${environment.name}") {
      timeout(time: 1, unit: 'DAYS') {

        //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine

        GString releaseMessage = sendSlackMessageForDeploy(releaseInfo, environment, appsInRepo)
        String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Deploy to ${environment.name}")

        //  todo [sb] open and close CR
        node('docker') {
          unstash(stashId)
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

//    stage("deploy to Prod") {
//      timeout(time: 3, unit: 'DAYS') { // wait 3 days for the decision to deploy to prod
//        String releaseMessage = """The release `${tagName}` of the application [`${apps.join(",")}`] was deployed in pre-prod.
//           Please check the functionality in pre-prod.
//           Do you want to proceed with the deployment in PROD ?"""
//        slackUtil.sendEnvSlackNotification("prod",
//                                           releaseMessage +
//                                           " Manual action: go here to deploy to Prod: ${env.BUILD_URL}input")
//        String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Deploy to Prod")
//
//        //  todo [sb] open and close CR
//        appsInRepo = deployUtil.deployAppWithHelm(imageVersion, "prod")
//      }
//    }


  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

public void sendSlackMessageForDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv, List<String> appsInRepo) {
  String appsJoined = appsInRepo.join(",")
  String releaseMessage = "The release `<${releaseInfo.url}|${releaseInfo.tagName}>` of apps `[${ appsJoined}]` is ready to deploy in `${targetEnv.name}`."

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${appsJoined}]:${releaseInfo.tagName} ready to deploy in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input}"
  attachment.text = releaseMessage
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.getAuthorUrl()

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}
