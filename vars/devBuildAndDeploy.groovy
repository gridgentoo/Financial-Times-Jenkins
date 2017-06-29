import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GitUtils
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

def call(BuildConfig config, String environmentName, String releaseName, boolean branchRelease) {
  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()
  GitUtils gitUtils = new GitUtils()

  Environment environment
  Set<String> deployedApps = null
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

        environment = EnvsRegistry.getEnvironment(environmentName)

        List<Cluster> deployToClusters = config.getDeployToClusters()
        for (int i = 0; i < deployToClusters.size(); i++) {
          Cluster clusterToDeploy = deployToClusters.get(i)

          stage("deploy to ${environment.name}-${clusterToDeploy.label}") {
            deployedApps = deployUtil.deployAppWithHelm(imageVersion, environment, clusterToDeploy)
          }
        }
      }
    }

    catchError {
      sendNotifications(environment, config, deployedApps, imageVersion)
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

private void sendNotifications(Environment environment, BuildConfig config, Set<String> deployedApps,
                               String imageVersion) {
  stage("notifications") {
    if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
      sendSuccessNotifications(environment, config, deployedApps, imageVersion)
    } else {
      sendFailureNotifications()
    }
  }
}

private void sendSuccessNotifications(Environment environment, BuildConfig config, Set<String> deployedApps,
                                      String imageVersion) {
  SlackUtils slackUtil = new SlackUtils()

  SlackAttachment attachment = new SlackAttachment()
  String deployedAppsAsString = deployedApps.join(",")
  List<String> healthURLs = []
  for (Cluster cluster : config.deployToClusters) {
    String entryPointURL = environment.getEntryPointUrl(cluster)
    String healthURL = "<${entryPointURL}/__health|${cluster.getLabel()}-${environment.name}>"
    healthURLs.add(healthURL)
  }

  String healthURLsAsString = healthURLs.join(",")
  attachment.titleUrl = env.BUILD_URL
  attachment.title = "[${deployedAppsAsString}]:${imageVersion} deployed in '${environment.name}'"
  attachment.text = """The applications `[${deployedAppsAsString}]` were deployed automatically with version `${
    imageVersion
  }` in ${healthURLsAsString}"""
  slackUtil.sendEnhancedSlackNotification(environment.slackChannel, attachment)
}

private void sendFailureNotifications() {
  String subject = "${env.JOB_BASE_NAME} - Build # ${env.BUILD_NUMBER} failed !"
  String body = "Check console output at ${env.BUILD_URL} to view the results."
  emailext(body: body,
           recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
           subject: subject, attachLog: true, compressLog: true)
}






