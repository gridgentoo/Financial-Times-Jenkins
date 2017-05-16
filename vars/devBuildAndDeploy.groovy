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

      String environment = deployUtil.getEnvironment(env.BRANCH_NAME)
      //  todo [sb] handle the case when the environment is not specified in the branch name

      List<String> deployedApps
      stage("deploy to ${environment}") {
        //  todo [sb] handle the case when we have the same chart for many apps
         deployedApps = deployUtil.deployAppWithHelm(imageVersion, environment)
      }
      stage("notifications") {
        slackUtil.sendEnvSlackNotification(environment, "The applications [${deployedApps.join(",")}] were deployed by helm with version ${imageVersion} in env: ${environment}")
      }
    }
    finally {
      stage("cleanup") {
        cleanWs()
      }
    }
  }

}






