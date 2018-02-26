import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.provision.ProvisionConstants
import com.ft.jenkins.provision.ProvisionerUtil

def call() {
  String clusterFullname = env."Cluster name"
  String gitBranch = env."Provisioner Git branch"

  setCurrentBuildInfo(clusterFullname, gitBranch)

  ProvisionerUtil provisionerUtil = new ProvisionerUtil()

  catchError {
    node('docker') {
      buildProvisionerImage(gitBranch)

      stage('update cluster') {
        //  todo [sb] create CR for staging & prod
        provisionerUtil.updateCluster(clusterFullname, gitBranch)
      }
    }
  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

private void setCurrentBuildInfo(String clusterFullname, String gitBranch) {
  currentBuild.displayName = "${env.BUILD_NUMBER} - ${clusterFullname}"
  currentBuild.description = "Provisioner branch: ${gitBranch}"
}

private void buildProvisionerImage(String gitBranch) {
  String relativeTargetDir = 'k8s-provisioner'

  timeout(60) { //  timeout after 60 mins to not block jenkins
    stage('checkout provisioner branch') {
      checkout([$class           : 'GitSCM',
                branches         : [[name: gitBranch]],
                extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: relativeTargetDir]],
                userRemoteConfigs: [[url: ProvisionConstants.REPO_URL]]
      ])
    }

    stage('build provisioner image') {
      DockerUtils dockerUtils = new DockerUtils()
      dockerUtils.buildImage("${ProvisionConstants.DOCKER_IMAGE}:${gitBranch}", relativeTargetDir)
    }

  }
}

