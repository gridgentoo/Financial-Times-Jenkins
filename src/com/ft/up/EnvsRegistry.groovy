package com.ft.up

class EnvsRegistry implements Serializable {

  public static final Map<String, String> envToApiServerMap = [
      "xp"    : "https://k8s-delivery-upp-eu-api.ft.com",
      "pub-xp": "https://k8s-pub-upp-eu-api.ft.com",
      "test"  : "https://k8s-delivery-upp-eu-api.ft.com"
  ]

  public static final Map<String, String> envToSlackChannelMap = [
      "xp"    : "k8s_ro",
      "pub-xp": "k8s_ro"
  ]

  public static String getApiServerForEnv(String env) {
    return envToApiServerMap[env]
  }

  public static String getSlackChannelForEnv(String env) {
    return envToSlackChannelMap[env]
  }


}
