package com.ft.jenkins.notification

import com.ft.jenkins.appconfigs.AppConfig
import com.ft.jenkins.appconfigs.AppConfigs
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.slack.Slack
import com.ft.jenkins.slack.SlackAttachment
import com.google.common.collect.Multimap

void sendFailureNotifications() {
  String subject = "${env.JOB_BASE_NAME} - Build # ${env.BUILD_NUMBER} failed !"
  String body = "Check console output at ${env.BUILD_URL} to view the results."
  emailext(body: body,
          recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
          subject: subject, attachLog: true, compressLog: true)
}

void sendSuccessNotification(Environment environment, String chart, String version,
                             Multimap<ClusterType, AppConfig> appsPerCluster, Region deployOnlyRegion) {
  Slack slack = new Slack()

  SlackAttachment attachment = new SlackAttachment()
  attachment.titleUrl = env.BUILD_URL
  attachment.title = "[${chart}]:${version} deployed in '${environment.name}'"

  boolean sameAppsInAllClusters = AppConfigs.areSameAppsInAllClusters(appsPerCluster)

  if (sameAppsInAllClusters) {
    //  we need a single message, as the same apps were deployed.
    List<AppConfig> appsInFirstCluster = AppConfigs.getAppsInFirstCluster(appsPerCluster)
    Set<ClusterType> allClusters = appsPerCluster.keySet()

    String text = getDeploymentMessageForApps(appsInFirstCluster, environment, allClusters, deployOnlyRegion, version)
    attachment.text = text
  } else { /* we need a message per cluster, as different apps were deployed in different clusters */
    List<String> messages = []
    appsPerCluster.entries().each { ClusterType cluster, List<AppConfig> appsInCluster ->
      messages.add(getDeploymentMessageForApps(appsInCluster, environment, [cluster], deployOnlyRegion, version))
    }
    attachment.text = messages.join(".\n")
  }

  slack.sendEnhancedSlackNotification(environment.slackChannel, attachment)
}

static String getDeploymentMessageForApps(List<AppConfig> apps, Environment environment, Collection<ClusterType> clusters,
                                          Region deployOnlyRegion, String version) {
  List<String> deployedHealthUrls =
          getHealthUrlsForDeployClusters(environment, clusters, deployOnlyRegion)

  def msg = "The applications `${apps}` were deployed automatically with version `${version}` in ${deployedHealthUrls}"
  msg
}

static List<String> getHealthUrlsForDeployClusters(Environment environment, Collection<ClusterType> clusters,
                                                   Region deployOnlyRegion) {
  Slack slack = new Slack()
  /*  compute the regions where it was deployed */
  List<Region> regionsDeployed = environment.getRegionsToDeployTo(deployOnlyRegion)

  List<String> deployedHealthUrls = []
  for (ClusterType cluster : clusters) {
    /*  if the Environment has regions, get the urls per region */
    if (regionsDeployed) {
      for (Region region : regionsDeployed) {
        deployedHealthUrls.add(slack.getHealthUrl(environment, cluster, region))
      }
    } else {
      deployedHealthUrls.add(slack.getHealthUrl(environment, cluster))
    }
  }
  deployedHealthUrls
}
