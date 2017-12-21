package com.ft.jenkins

class EnvsRegistry implements Serializable {

  public static List<Environment> envs

  static {
    Environment k8s = new Environment()
    k8s.name = "k8s"
    k8s.slackChannel = "#k8s-pipeline-notif"
    k8s.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING, Cluster.NEO4J]
    k8s.regions = ['eu']
    k8s.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu-api.ft.com",
        ("eu-" + Cluster.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu-api.ft.com",
        ("eu-" + Cluster.NEO4J.toString()): "https://upp-k8s-neo4j-eu-api.ft.com"
    ]

    Environment stagingPAC = new Environment()
    stagingPAC.name = "stagingpac"
    stagingPAC.slackChannel = "#k8s-pipeline-notif"
    stagingPAC.regions = ["eu", "us"]
    stagingPAC.clusters = [Cluster.PAC]
    stagingPAC.clusterToApiServerMap = [
        ("eu-" + Cluster.PAC.toString()): "https://pac-staging-eu-api.ft.com",
        ("us-" + Cluster.PAC.toString()): "https://pac-staging-us-api.ft.com",
    ]

    Environment prodPAC = new Environment()
    prodPAC.name = "prodpac"
    prodPAC.slackChannel = "#k8s-pipeline-notif"
    prodPAC.regions = ["eu", "us"]
    prodPAC.clusters = [Cluster.PAC]
    prodPAC.clusterToApiServerMap = [
        ("eu-" + Cluster.PAC.toString()): "https://pac-prod-eu-api.ft.com",
        ("us-" + Cluster.PAC.toString()): "https://pac-prod-us-api.ft.com",
    ]

// todo uncomment and adjust when we'll have a staging env.
//    Environment staging = new Environment()
//    staging.name = Environment.STAGING_NAME
//    staging.slackChannel = "#k8s-pipeline-notif"
//    staging.regions = ["eu", "us"]
//    staging.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
//    staging.clusterToApiServerMap = [
//        ("eu-" + Cluster.DELIVERY)  : "https://upp-k8s-delivery-test-eu-api.ft.com",
//        ("us-" + Cluster.DELIVERY)  : "https://upp-k8s-delivery-test-eu-api.ft.com",
//        ("eu-" + Cluster.PUBLISHING): "https://upp-k8s-publishing-test-eu-api.ft.com",
//        ("us-" + Cluster.PUBLISHING): "https://upp-k8s-publishing-test-eu-api.ft.com"
//    ]

    Environment prod = new Environment()
    prod.name = Environment.PROD_NAME
    prod.slackChannel = "#k8s-pipeline-notif"
    prod.regions = ["eu", "us"]
    prod.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING, Cluster.NEO4J]
    prod.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-eu-api.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-us-api.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://upp-prod-publish-eu-api.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://upp-prod-publish-us-api.ft.com",
        ("eu-" + Cluster.NEO4J): "https://upp-prod-neo4j-eu-api.ft.com",
        ("us-" + Cluster.NEO4J): "https://upp-prod-neo4j-us-api.ft.com"
    ]

    envs = [k8s, stagingPAC, prodPAC, prod]
  }


  public static Environment getEnvironment(String name) {
    for (Environment environment: envs) {
      if (environment.name == name) {
        return environment
      }
    }
    return null
  }

}
