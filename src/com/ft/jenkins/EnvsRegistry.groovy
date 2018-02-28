package com.ft.jenkins

class EnvsRegistry implements Serializable {

  public static List<Environment> envs

  static {
    Environment devCJ = new Environment()
    devCJ.name = "devcj"
    devCJ.slackChannel = "#k8s-pipeline-notif"
    devCJ.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    devCJ.regions = ["eu"]
    devCJ.clusterToApiServerMap = [
            ("eu-" + Cluster.DELIVERY)  : "https://upp-dev-cj-delivery-eu-api.ft.com",
            ("eu-" + Cluster.PUBLISHING): "https://upp-dev-cj-publish-eu-api.ft.com"
    ]

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

    Environment staging = new Environment()
    staging.name = Environment.STAGING_NAME
    staging.slackChannel = "#k8s-pipeline-notif"
    staging.regions = ["eu", "us"]
    staging.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING, Cluster.NEO4J]
    staging.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://upp-staging-delivery-eu-api.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://upp-staging-delivery-us-api.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://upp-staging-publish-eu-api.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://upp-staging-publish-us-api.ft.com",
        ("eu-" + Cluster.NEO4J): "https://upp-staging-neo4j-eu-api.ft.com",
        ("us-" + Cluster.NEO4J): "https://upp-staging-neo4j-us-api.ft.com"
    ]

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

    envs = [devCJ, k8s, stagingPAC, staging, prodPAC, prod]
  }


  public static Environment getEnvironment(String name) {
    for (Environment environment: envs) {
      if (environment.name == name) {
        return environment
      }
    }
    return null
  }

  public static Environment getEnvironmentByFullName(String clusterFullName) {
    if (clusterFullName == null) {
      return null
    }

    for (Environment environment: envs) {
      for (String apiServer: environment.clusterToApiServerMap.values()) {
        if (apiServer.contains("${clusterFullName}-api.ft.com")) {
          return environment
        }
      }
    }

    return null
  }

}
