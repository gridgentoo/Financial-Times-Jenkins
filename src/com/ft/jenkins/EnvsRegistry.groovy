package com.ft.jenkins

class EnvsRegistry implements Serializable {

  public static List<Environment> envs

  static {
    Environment k8s = new Environment()
    k8s.name = "k8s"
    k8s.slackChannel = "#upp-changes"
    k8s.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    k8s.regions = ['eu']
    k8s.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
        ("eu-" + Cluster.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu-api.upp.ft.com"
    ]
    k8s.glbMap = [
        (Cluster.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu.upp.ft.com",
        (Cluster.DELIVERY.toString()): "https://upp-k8s-dev-delivery-eu.upp.ft.com"
    ]

    Environment stagingPAC = new Environment()
    stagingPAC.name = "stagingpac"
    stagingPAC.slackChannel = "#upp-changes"
    stagingPAC.regions = ["eu", "us"]
    stagingPAC.clusters = [Cluster.PAC]
    stagingPAC.clusterToApiServerMap = [
        ("eu-" + Cluster.PAC.toString()): "https://pac-staging-eu-api.upp.ft.com",
        ("us-" + Cluster.PAC.toString()): "https://pac-staging-us-api.upp.ft.com",
    ]
    stagingPAC.glbMap = [
        (Cluster.PUBLISHING.toString()): "https://upp-staging-publish-glb.upp.ft.com",
        (Cluster.DELIVERY.toString()): "https://upp-staging-delivery-glb.upp.ft.com"
    ]

    Environment prodPAC = new Environment()
    prodPAC.name = "prodpac"
    prodPAC.slackChannel = "#upp-changes"
    prodPAC.regions = ["eu", "us"]
    prodPAC.clusters = [Cluster.PAC]
    prodPAC.clusterToApiServerMap = [
        ("eu-" + Cluster.PAC.toString()): "https://pac-prod-eu-api.ft.com",
        ("us-" + Cluster.PAC.toString()): "https://pac-prod-us-api.ft.com",
    ]
    prodPAC.glbMap = [
        (Cluster.PUBLISHING.toString()): "https://upp-prod-publish.ft.com",
        (Cluster.DELIVERY.toString()): "https://upp-prod-delivery.ft.com"
    ]

    Environment gcPAC = new Environment()
    gcPAC.name = "gcpac"
    gcPAC.slackChannel = "#upp-changes"
    gcPAC.regions = ["eu"]
    gcPAC.clusters = [Cluster.PAC]
    gcPAC.clusterToApiServerMap = [
        ("eu-" + Cluster.PAC.toString()): "https://pac-golden-corpus-eu-api.ft.com"
    ]

    Environment staging = new Environment()
    staging.name = Environment.STAGING_NAME
    staging.slackChannel = "#upp-changes"
    staging.regions = ["eu", "us"]
    staging.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    staging.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://upp-staging-delivery-eu-api.upp.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://upp-staging-delivery-us-api.upp.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://upp-staging-publish-eu-api.upp.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://upp-staging-publish-us-api.upp.ft.com"
    ]
    staging.glbMap = [
        (Cluster.PUBLISHING.toString()): "https://upp-staging-publish-glb.upp.ft.com",
        (Cluster.DELIVERY.toString()): "https://upp-staging-delivery-glb.upp.ft.com"
    ]

    Environment prod = new Environment()
    prod.name = Environment.PROD_NAME
    prod.slackChannel = "#upp-changes"
    prod.regions = ["eu", "us"]
    prod.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    prod.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-eu-api.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-us-api.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://upp-prod-publish-eu-api.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://upp-prod-publish-us-api.ft.com"
    ]
    prod.glbMap = [
        (Cluster.PUBLISHING.toString()): "https://upp-prod-publish.ft.com",
        (Cluster.DELIVERY.toString()): "https://upp-prod-delivery.ft.com"
    ]

    envs = [k8s, stagingPAC, staging, gcPAC, prodPAC, prod]
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
        if (apiServer.contains("${clusterFullName}-api.ft.com") || apiServer.contains("${clusterFullName}-api.upp.ft.com")) {
          return environment
        }
      }
    }

    return null
  }

}
