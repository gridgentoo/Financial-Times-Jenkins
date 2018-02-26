import com.ft.jenkins.aws.ProvisionerUtil
import com.ft.jenkins.docker.DockerUtils

def call() {
  //  todo [move those]
  String repoURL = 'https://github.com/Financial-Times/k8s-aws-delivery-poc.git'
  String relativeTargetDir = 'k8s-provisioner'

  String clusterFullname=env."Cluster name"
  String gitBranch = env."Git branch"

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
          //  todo [sb] create CR for staging & prod
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

