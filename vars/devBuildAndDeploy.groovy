import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GitUtils

def call(String environmentName, String releaseName, boolean branchRelease) {
  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()
  GitUtils gitUtils = new GitUtils()

  String imageVersion

  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        imageVersion = getImageVersion(releaseName, gitUtils.getMostRecentGitTag(), branchRelease)

        stage('build image') {
          String dockerRepository = deployUtil.getDockerImageRepository()
          dockerUtils.buildAndPushImage("${dockerRepository}:${imageVersion}")
        }

        String chartName
        stage('publish chart') {
          chartName = deployUtil.publishHelmChart(imageVersion)
        }

        stage("deploy chart") {
          /*  trigger the generic job for deployment */
          build job: DeploymentUtilsConstants.GENERIC_DEPLOY_JOB,
                parameters: [
                    string(name: 'Chart', value: chartName),
                    string(name: 'Version', value: imageVersion),
                    string(name: 'Environment', value: environmentName),
                    string(name: 'Cluster', value: 'all-in-chart'),
                    string(name: 'Region', value: 'all'),
                    booleanParam(name: 'Send success notifications', value: true)]
        }
      }
    }

    stage("cleanup") {
      cleanWs()
    }
  }
}

private String getImageVersion(String releaseName, String gitTag, boolean branchRelease) {
  if (branchRelease) {
    return "${gitTag}-${releaseName}"
  }

  return releaseName
}






