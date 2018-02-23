import com.ft.jenkins.aws.ProvisionerUtil
import com.ft.jenkins.docker.DockerUtils

def call() {
  String repoURL = 'https://github.com/Financial-Times/k8s-aws-delivery-poc.git'
  String relativeTargetDir = 'k8s-provisioner'

//  String awsRegion = env."AWS region"
//  String clusterName = env."Cluster name"
//  String clusterEnvironment = env."Cluster environment"
//  String environmentType = env."Environment type"
//  String platform = env."Platform"
  String gitBranch = env."Git branch"

  String clusterFullname=env."Cluster name"

  ProvisionerUtil provisionerUtil = new ProvisionerUtil()
  DockerUtils dockerUtils = new DockerUtils()

  catchError {
    node('docker') {
      timeout(60) { //  timeout after 60 mins to not block jenkins
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
          provisionerUtil.updateCluster(clusterFullname, gitBranch)
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

