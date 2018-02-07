import com.ft.jenkins.aws.ClusterManagementUtils
import com.ft.jenkins.docker.DockerUtils

def call() {
  String repoURL = 'https://github.com/Financial-Times/k8s-aws-delivery-poc.git'
  String relativeTargetDir = 'k8s-provisioner'

  String awsRegion = env."AWS region"
  String clusterName = env."Cluster name"
  String clusterEnvironment = env."Cluster environment"
  String environmentType = env."Environment type"
  String platform = env."Platform"
  String gitBranch = env."Git branch"

  ClusterManagementUtils clusterManagementUtils = new ClusterManagementUtils()
  DockerUtils dockerUtils = new DockerUtils()

  catchError {
    node('docker') {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout([$class           : 'GitSCM',
                    branches         : [[name: gitBranch]],
                    extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: relativeTargetDir]],
                    userRemoteConfigs: [[url: repoURL]]
          ])
        }

        stage('build image') {
          dockerUtils.buildImage("k8s-provisioner:${gitBranch}", relativeTargetDir)
        }

        stage('update cluster') {
          String vaultPass = getVaultPass(ClusterManagementUtils.getFullEnvironmentType(environmentType))
          clusterManagementUtils.updateCluster(awsRegion, clusterName, clusterEnvironment,
                  environmentType, platform, vaultPass, gitBranch)
        }
      }
    }
  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

private String getVaultPass(String envType) {
  withCredentials([
          [$class: 'StringBinding', credentialsId: "ft.k8s-provision.content-${envType}.vault.pass", variable: 'VAULT_PASS']]) {
    return env.VAULT_PASS
  }
}
