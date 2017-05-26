package com.ft.jenkins

class Environment implements Serializable {
  /*  The name of the environment. Example: prod*/
  String name
  /*  The slack channel where the notifications go for this environment */
  String slackChannel
  /*  The regions that this environment is split accross */
  List<String> regions = null
  /*  Mapping between region+cluster and their respective Kubernetes api servers. */
  public Map<String, String> clusterToApiServerMap

  Environment(String name, String slackChannel, List<String> regions,
              Map<String, String> clusterToApiServerMap) {
    this.name = name
    this.slackChannel = slackChannel
    this.regions = regions
    this.clusterToApiServerMap = clusterToApiServerMap
  }

  public String getApiServerForCluster(Cluster cluster, String region = null) {
    String lookupKey
    if (region) {
      lookupKey = "${region}-${cluster}"
    }
    else {
      lookupKey = cluster.toString()
    }
    return clusterToApiServerMap.get(lookupKey)
  }

}
