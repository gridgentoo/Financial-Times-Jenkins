package com.ft.jenkins

class EnvsRegistry implements Serializable {

  public static List<Environment> envs

  static {
    Environment k8s = new Environment()
    k8s.name = "k8s"
    k8s.slackChannel = "#k8s-pipeline-notif"
    k8s.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    k8s.clusterToApiServerMap = [
        (Cluster.DELIVERY.toString())  : "https://k8s-delivery-upp-eu-api.ft.com",
        (Cluster.PUBLISHING.toString()): "https://k8s-pub-upp-eu-api.ft.com"
    ]

    Environment pac = new Environment()
    pac.name = "pac"
    pac.slackChannel = "#k8s-pipeline-notif"
    pac.clusters = [Cluster.PAC]
    pac.clusterToApiServerMap = [
        (Cluster.PAC.toString()): "https://pre-prod-eu-pac-api.ft.com"
    ]

    Environment preProd = new Environment()
    preProd.name = Environment.PRE_PROD_NAME
    preProd.slackChannel = "#k8s-pipeline-notif"
    preProd.regions = ["eu", "us"]
    preProd.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    preProd.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
    ]

    Environment prod = new Environment()
    prod.name = Environment.PROD_NAME
    prod.slackChannel = "#k8s-pipeline-notif"
    prod.regions = ["eu", "us"]
    prod.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING]
    prod.clusterToApiServerMap = [
        ("eu-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
        ("us-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
        ("eu-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
        ("us-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
    ]

    envs = [k8s, pac, preProd, prod]
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
