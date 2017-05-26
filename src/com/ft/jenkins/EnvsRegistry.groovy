package com.ft.jenkins

class EnvsRegistry implements Serializable {

  public static final List<Environment> envs = [
      new Environment("k8s", "k8s_ro", [],
                      [(Cluster.DELIVERY.toString())  : "https://k8s-delivery-upp-eu-api.ft.com",
                       (Cluster.PUBLISHING.toString()): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
      new Environment("pre-prod", "@sorin.buliarca", ["eu", "us"],
                      [("eu-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("us-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("eu-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
                       ("us-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
      new Environment("prod", "@sorin.buliarca", ["eu", "us"],
                      [("eu-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("us-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("eu-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
                       ("us-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
  ]


  @NonCPS
  public static Environment getEnvironment(String name) {
    return envs.find {Environment environment -> environment.name = name }
  }

}
