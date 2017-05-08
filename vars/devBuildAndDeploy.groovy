import com.ft.up.DeploymentUtils
import com.ft.up.DockerUtils

def call(String appImageId) {
  def lib = new DeploymentUtils()

  node('docker') {
    try {
      stage('checkout') {
        checkout scm
      }

      String imageVersion = lib.getFeatureName(env.BRANCH_NAME)
      stage('build image') {
        DockerUtils dockerUtils = new DockerUtils()
        def image = dockerUtils.buildImage("${appImageId}:${imageVersion}")
        dockerUtils.pushImageToDH(image)
      }

      String env = lib.getEnvironment(env.BRANCH_NAME)
      //  todo [sb] handle the case when the environment is not specified in the branch name

      stage("deploy to ${env}") {
        lib.deployAppWithHelm(imageVersion, env)
      }
    }
    finally {
      cleanWs()
    }
  }

}






