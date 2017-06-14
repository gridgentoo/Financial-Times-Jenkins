package com.ft.jenkins

class Environment implements Serializable {
  public static final String PRE_PROD_NAME = "pre-prod"
  public static final String PROD_NAME = "prod"


  /*  The name of the environment. Example: prod*/
  String name
  /*  The slack channel where the notifications go for this environment */
  String slackChannel
  /*  The regions that this environment is split across */
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

  public String getEntryPointUrl(Cluster cluster, String region) {
    return getApiServerForCluster(cluster,region).replace("-api","")
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

  public String getNamesWithRegions(List<String> regions) {
    List<String> namesWithRegion = []
    for (int i = 0; i < regions.size(); i++) {
      String regionName = regions.get(i);
      namesWithRegion.add("${name}-${regionName}")
    }
    return namesWithRegion.join(", ")
  }

  public List<String> getValidatedRegions(List<String> remainingRegions) {
    List<String> validatedRegions = []
    validatedRegions.addAll(this.getRegions())
    validatedRegions.removeAll(remainingRegions)
    return validatedRegions
  }

}
