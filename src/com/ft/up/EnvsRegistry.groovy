package com.ft.up

import static com.ft.up.Cluster.DELIVERY
import static com.ft.up.Cluster.PUBLISHING

class EnvsRegistry implements Serializable {

  public static final List<Environment> envs = [
      new Environment("k8s", "k8s_ro", [],
                      [(DELIVERY.toString())  : "https://k8s-delivery-upp-eu-api.ft.com",
                       (PUBLISHING.toString()): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
      new Environment("pre-prod", "@sorin.buliarca", ["eu", "us"],
                      [("eu-" + DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("us-" + DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("eu-" + PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
                       ("us-" + PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
      new Environment("prod", "@sorin.buliarca", ["eu", "us"],
                      [("eu-" + DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("us-" + DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
                       ("eu-" + PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
                       ("us-" + PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
                      ]),
  ]


  @NonCPS
  public static Environment getEnvironment(String name) {
    return envs.find {Environment environment -> environment.name = name }
  }

}
