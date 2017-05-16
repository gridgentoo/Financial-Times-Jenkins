import com.ft.up.DeploymentUtils
import com.ft.up.DevBuildConfig
import com.ft.up.DockerUtils
import com.ft.up.SlackUtil


def call(DevBuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()

  node('docker') {
    try {
      stage('checkout') {
        checkout scm
      }

      String imageVersion = deployUtil.getFeatureName(env.BRANCH_NAME)
      stage('build image') {
        def image = dockerUtils.buildImage("${config.appDockerImageId}:${imageVersion}")
        dockerUtils.pushImageToDH(image)
      }

      String environment = deployUtil.getEnvironment(env.BRANCH_NAME)
      //  todo [sb] handle the case when the environment is not specified in the branch name

      List<String> deployedApps = null
      stage("deploy to ${environment}") {
        //  todo [sb] handle the case when we have the same chart for many apps
         deployedApps = deployUtil.deployAppWithHelm(imageVersion, environment)
      }
      sendSuccessNotifications(environment, deployedApps, imageVersion)
    }
    catch (Exception e) {
      sendFailureNotifications()
      throw e
    }

    finally {
      stage("cleanup") {
        cleanWs()
      }
    }
  }

}

public void sendSuccessNotifications(String environment, deployedApps, String imageVersion) {
  stage("notifications") {
    SlackUtil slackUtil = new SlackUtil()

    String message = """ 
      The applications [${deployedApps.join(",")}] were deployed automatically with helm with version '${imageVersion}' in env: ${environment}. 
      Build url: ${env.JOB_URL}"""
    slackUtil.sendEnvSlackNotification(environment, message)
  }
}

public void sendFailureNotifications() {
  stage("notifications") {
    /*  send email notification if job fails */
    emailext(body: "Build URL: ${env.BUILD_URL}".toString(),
             recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
             subject: 'K8S auto-deploy job failed')
  }
}






