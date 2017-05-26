import com.ft.jenkins.BuildConfig
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GitUtils
import com.ft.jenkins.slack.SlackUtils

def call(BuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()
  GitUtils gitUtils = new GitUtils()
  SlackUtils slackUtil = new SlackUtils()

  String environment
  List<String> deployedApps = null
  String tagName = gitUtils.getTagNameFromBranchName(env.BRANCH_NAME)
  String imageVersion = tagName

  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        stage('build image') {
          String dockerRepository = deployUtil.getDockerImageRepository()
          dockerUtils.buildAndPushImage("${dockerRepository}:${imageVersion}")
        }

      }

      List<String> apps = deployUtil.getAppNamesInRepo()
      //  todo [SB] release node while waiting for input
      stage("deploy to Pre-Prod") {
        timeout(30) {
          //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine

          String releaseMessage = "Release `${tagName}` of the applications [`${apps.join(",")}`] is ready to deploy in pre-prod. Do you want to proceed with the deployment?"
          slackUtil.sendEnvSlackNotification("pre-prod",
                                             releaseMessage + " Manual action: go here to deploy to pre-prod: ${env.BUILD_URL}input")
          String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Deploy to pre-prod")

          //  todo [sb] open and close CR
          deployedApps = deployUtil.deployAppWithHelm(imageVersion, "pre-prod")
        }
      }

      stage("deploy to Prod") {
        timeout(time: 1, unit: 'DAYS') {
          String releaseMessage = """The release `${tagName}` of the application [`${apps.join(",")}`] was deployed in pre-prod.
           Please check the functionality in pre-prod.
           Do you want to proceed with the deployment in PROD ?"""
          slackUtil.sendEnvSlackNotification("prod",
                                             releaseMessage + " Manual action: go here to deploy to Prod: ${env.BUILD_URL}input")
          String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Deploy to Prod")

          //  todo [sb] open and close CR
          deployedApps = deployUtil.deployAppWithHelm(imageVersion, "prod")
        }
      }

    }

    stage("cleanup") {
      cleanWs()
    }
  }
}
