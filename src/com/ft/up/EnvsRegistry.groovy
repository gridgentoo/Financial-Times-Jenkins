package com.ft.up

import static com.ft.up.Cluster.*

class EnvsRegistry implements Serializable {

  public static final Map<String, String> envToApiServerMap = [
      "k8s-${DELIVERY}"    : "https://k8s-delivery-upp-eu-api.ft.com",
      "k8s-${PUBLISHING}": "https://k8s-pub-upp-eu-api.ft.com",
      "pre-prod-${DELIVERY}" : "https://k8s-delivery-upp-eu-api.ft.com",
      "prod-${DELIVERY}" : "https://k8s-delivery-upp-eu-api.ft.com",
  ]

  public static final Map<String, String> envToSlackChannelMap = [
      "k8s"    : "k8s_ro",
      "pre-prod": "@sorin.buliarca",
      "prod": "@sorin.buliarca"
  ]

  public static String getApiServerForEnv(String env) {
    return envToApiServerMap[env]
  }

  public static String getApiServerForEnv(String env, Cluster cluster) {
    return envToApiServerMap["${env}-${cluster}"]
  }

  public static String getSlackChannelForEnv(String env) {
    return envToSlackChannelMap[env]
  }


}
