import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.ParamUtils
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

def call() {
  /*  determine parameter values */
  ParamUtils paramUtils = new ParamUtils()

  Environment targetEnv = computeTargetEnvironment()
  String chart = paramUtils.getRequiredParameterValue("Chart")
  String version = paramUtils.getRequiredParameterValue("Version")
  Cluster deployOnlyInCluster = computeDeployOnlyInCluster()
  String deployOnlyInRegion = computeDeployOnlyInRegion()
  Boolean sendSuccessNotifications = params."Send success notifications"

  DeploymentUtils deploymentUtils = new DeploymentUtils()
  node('docker') {
    currentBuild.description = "${chart}:${version} -> ${targetEnv.name}"
    Map<Cluster, List<String>> deployedAppsPerCluster

    catchError {
      stage("deploy apps") {
        deployedAppsPerCluster = deploymentUtils.
            deployAppFromHelmRepo(chart, version, targetEnv, deployOnlyInCluster, deployOnlyInRegion)
      }
    }

    catchError {
      sendNotifications(targetEnv, chart, version, deployedAppsPerCluster, sendSuccessNotifications, deployOnlyInRegion)
    }

    stage('cleanup') {
      cleanWs()
    }
  }
}

String computeDeployOnlyInRegion() {
  String regionInput = params.Region
  String deployOnlyInRegion = regionInput
  if (regionInput == "all") {
    deployOnlyInRegion = null
  }
  return deployOnlyInRegion
}

Cluster computeDeployOnlyInCluster() {
  String clusterInput = params.Cluster
  Cluster deployOnlyInCluster = Cluster.valueOfLabel(clusterInput)
  return deployOnlyInCluster
}

Environment computeTargetEnvironment() {
  String environmentInput = params.Environment
  Environment targetEnv = EnvsRegistry.getEnvironment(environmentInput)
  if (targetEnv == null) {
    throw new IllegalArgumentException("Unknown environment ${environmentInput}. The environment is required.")
  }
  return targetEnv
}

void sendNotifications(Environment environment, String chart, String version,
                       Map<Cluster, List<String>> appsPerCluster, boolean sendSuccessNotifications,
                       String deployOnlyRegion) {
  stage("notifications") {
    if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
      if (sendSuccessNotifications) {
        sendSuccessNotification(environment, chart, version, appsPerCluster, deployOnlyRegion)
      }
    } else {
      sendFailureNotifications()
    }
  }
}

private void sendFailureNotifications() {
  String subject = "${env.JOB_BASE_NAME} - Build # ${env.BUILD_NUMBER} failed !"
  String body = "Check console output at ${env.BUILD_URL} to view the results."
  emailext(body: body,
           recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
           subject: subject, attachLog: true, compressLog: true)
}

void sendSuccessNotification(Environment environment, String chart, String version,
                             Map<Cluster, List<String>> appsPerCluster, String deployOnlyRegion) {
  SlackUtils slackUtil = new SlackUtils()
  DeploymentUtils deploymentUtils = new DeploymentUtils()

  SlackAttachment attachment = new SlackAttachment()
  attachment.titleUrl = env.BUILD_URL
  attachment.title = "[${chart}]:${version} deployed in '${environment.name}'"

  boolean sameAppsInAllClusters = deploymentUtils.areSameAppsInAllClusters(appsPerCluster)

  if (sameAppsInAllClusters) {
    //  we need a single message, as the same apps were deployed.
    List<String> appsInFirstCluster = deploymentUtils.getAppsInFirstCluster(appsPerCluster)
    Set<Cluster> allClusters = appsPerCluster.keySet()

    String text = getDeploymentMessageForApps(appsInFirstCluster, environment, allClusters, deployOnlyRegion, version)
    attachment.text = text
  } else { /* we need a message per cluster, as different apps were deployed in different clusters */
    List<String> messages = []
    appsPerCluster.each {Cluster cluster, List<String> appsInCluster ->
      messages.add(getDeploymentMessageForApps(appsInCluster, environment, [cluster], deployOnlyRegion, version))
    }
    attachment.text = messages.join(".\n")
  }

  slackUtil.sendEnhancedSlackNotification(environment.slackChannel, attachment)
}

String getDeploymentMessageForApps(List<String> apps, Environment environment, Collection<Cluster> clusters,
                                          String deployOnlyRegion, String version) {
  List<String> deployedHealthUrls =
      getHealthUrlsForDeployClusters(environment, clusters, deployOnlyRegion)

  return "The applications `${apps}` were deployed automatically with version `${version}` in ${deployedHealthUrls}"
}



public List<String> getHealthUrlsForDeployClusters(Environment environment, Collection<Cluster> clusters,
                                                   String deployOnlyRegion) {
  SlackUtils slackUtil = new SlackUtils()
  /*  compute the regions where it was deployed */
  List<String> regionsDeployed = environment.getRegionsToDeployTo(deployOnlyRegion)

  List<String> deployedHealthUrls = []
  for (Cluster cluster : clusters) {
    /*  if the Environment has regions, get the urls per region */
    if (regionsDeployed) {
      for (String region : regionsDeployed) {
        deployedHealthUrls.add(slackUtil.getHealthUrl(environment, cluster, region))
      }
    } else {
      deployedHealthUrls.add(slackUtil.getHealthUrl(environment, cluster))
    }
  }
  return deployedHealthUrls
}
