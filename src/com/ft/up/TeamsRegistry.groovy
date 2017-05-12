package com.ft.up

class TeamsRegistry implements Serializable {

  public static final Map<String, String> teamToApiServerMap = [
      "xp"  : "https://k8s-delivery-upp-eu-api.ft.com",
      "test": "https://k8s-delivery-upp-eu-api.ft.com"
  ]

  public static final Map<String, String> teamToSlackChannelMap = [
      "xp"  : "#k8s_ro"
  ]

  public static String getApiServerForTeam(String team) {
    return  teamToApiServerMap[team]
  }

  public static String getSlackChannelForTeam(String team) {
    return teamToSlackChannelMap[team]
  }


}
