import com.ft.jenkins.docker.Docker
import com.ft.jenkins.provision.ProvisionersConstants
import com.ft.jenkins.provision.Provisioners

def call() {
  String fullClusterName = env."Cluster name"
  String gitBranch = env."Provisioner Git branch"
  String updateReason = env."Update reason"

  setCurrentBuildInfo(fullClusterName, gitBranch, updateReason)

  Provisioners provisioners = new Provisioners()

  catchError {
    node('docker') {
      buildProvisionerImage(gitBranch)

      stage('update cluster') {
        provisioners.updateCluster(fullClusterName, gitBranch, updateReason)
      }
    }
  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

private void setCurrentBuildInfo(String clusterFullname, String gitBranch, String updateReason) {
  currentBuild.displayName = "${env.BUILD_NUMBER} - ${clusterFullname}"
  currentBuild.description = "Reason: '${updateReason}'; Provisioner branch: `${gitBranch}`"
}

private void buildProvisionerImage(String gitBranch) {
  String relativeTargetDir = 'k8s-provisioner'

  timeout(60) { //  timeout after 60 mins to not block jenkins
    stage('checkout provisioner branch') {
      checkout([$class           : 'GitSCM',
                branches         : [[name: gitBranch]],
                extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: relativeTargetDir]],
                userRemoteConfigs: [[url: ProvisionersConstants.REPO_URL]]
      ])
    }
    /*  create a dummy credentials folder so that the image build will work */
    sh "mkdir -p ${relativeTargetDir}/credentials"
    stage('build provisioner image') {
      Docker dockerUtils = new Docker()
      dockerUtils.buildImage("${ProvisionersConstants.DOCKER_IMAGE}:${gitBranch}", relativeTargetDir)
    }

  }
}

