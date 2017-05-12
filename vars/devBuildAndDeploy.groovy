import com.ft.up.DeploymentUtils
import com.ft.up.DevBuildConfig
import com.ft.up.DockerUtils
import com.ft.up.SlackUtil

def call(DevBuildConfig config) {

  DeploymentUtils deployUtil = new DeploymentUtils()
  SlackUtil slackUtil = new SlackUtil()

  node('docker') {
    try {
      stage('checkout') {
        checkout scm
      }

      String imageVersion = deployUtil.getFeatureName(env.BRANCH_NAME)
      stage('build image') {
        DockerUtils dockerUtils = new DockerUtils()
        def image = dockerUtils.buildImage("${config.appDockerImageId}:${imageVersion}")
        dockerUtils.pushImageToDH(image)
      }

      String env = deployUtil.getEnvironment(env.BRANCH_NAME)
      //  todo [sb] handle the case when the environment is not specified in the branch name

      stage("deploy to ${env}") {
        //  todo [sb] handle the case when we have the same chart for many apps
        deployUtil.deployAppWithHelm(imageVersion, env)
      }
      stage("notifications") {
        slackUtil.sendTeamSlackNotification(env, "Deployment succeeded with version: ${config.appDockerImageId}:${imageVersion}")
      }
    }
    finally {
      cleanWs()
    }
  }

}






