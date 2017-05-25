import com.ft.up.Cluster
import com.ft.up.DeploymentUtils
import com.ft.up.BuildConfig
import com.ft.up.DockerUtils
import com.ft.up.slack.SlackAttachment
import com.ft.up.slack.SlackUtil

def call(BuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()

  String imageVersion = null
  String environment
  List<String> deployedApps = null

  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        stage('build image') {
          imageVersion = deployUtil.getDockerImageVersion(env.BRANCH_NAME)
          String dockerRepository = deployUtil.getDockerImageRepository()
          dockerUtils.buildAndPushImage("${dockerRepository}:${imageVersion}")
        }

        environment = deployUtil.getEnvironment(env.BRANCH_NAME)
        //  todo [sb] handle the case when the environment is not specified in the branch name

        List<Cluster> deployToClusters = config.getDeployToClusters()
        for (int i = 0; i < deployToClusters.size(); i++) {
          Cluster clusterToDeploy = deployToClusters.get(i)

          stage("deploy to ${environment}-${clusterToDeploy.label}") {
            deployedApps = deployUtil.deployAppWithHelm(imageVersion, environment, clusterToDeploy)
          }
        }

      }
    }

    catchError {
      sendNotifications(environment, deployedApps, imageVersion)
    }

    stage("cleanup") {
      cleanWs()
    }
  }
}

private void sendNotifications(String environment, List<String> deployedApps, String imageVersion) {
  stage("notifications") {
    if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
      sendSuccessNotifications(environment, deployedApps, imageVersion)
    } else {
      sendFailureNotifications()
    }
  }
}

private void sendSuccessNotifications(String environment, deployedApps, String imageVersion) {
  SlackUtil slackUtil = new SlackUtil()

  SlackAttachment attachment = new SlackAttachment()
  String deployedAppsAsString = deployedApps.join(",")
  attachment.titleUrl = env.BUILD_URL
  attachment.title = "[${deployedAppsAsString}]:${imageVersion} deployed in '${environment}'"
  attachment.text = """The applications `[${ deployedAppsAsString}]` were deployed automatically with version `${imageVersion}` in env: `${environment}`."""
  attachment.includeTimestamp = true
  slackUtil.sendEnvEnhancedSlackNotification(environment, attachment)
}

private void sendFailureNotifications() {
  String subject = "${env.JOB_BASE_NAME} - Build # ${env.BUILD_NUMBER} failed !"
  String body = "Check console output at ${env.BUILD_URL} to view the results."
  emailext(body: body,
           recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
           subject: subject, attachLog: true, compressLog: true)
}






