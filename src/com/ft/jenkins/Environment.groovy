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

  /*  The application clusters that an environment has*/
  List<Cluster> clusters = []

  /*  Mapping between region+cluster and their respective Kubernetes api servers. */
  public Map<String, String> clusterToApiServerMap

  public String getEntryPointUrl(Cluster cluster, String region = null) {
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
    for (String regionName : regions) {
      namesWithRegion.add("${name}-${regionName}")
    }
    return namesWithRegion.join(", ")
  }

  public String getFullClusterName(Cluster cluster, String region = null) {
    String fullName = "${cluster.getLabel()}-${this.name}"
    if (region) {
      fullName = fullName + "-${region}"
    }
    return fullName
  }

  public List<String> getFullClusterNames(Collection<Cluster> clusters, List<String> regions) {
    List<String> fullClusterNames = []
    for (Cluster cluster : clusters) {
      if (regions) {
        for (String region : regions) {
          fullClusterNames.add(getFullClusterName(cluster, region))
        }
      } else {
        fullClusterNames.add(getFullClusterName(cluster))
      }
    }

    return fullClusterNames
  }

  public List<String> getRegionsToDeployTo(String deployOnlyToRegion) {
    List<String> deployToRegions = []
    if (deployOnlyToRegion == null) {
      if (this.regions != null) {
        deployToRegions.addAll(this.regions)
      }
    } else if (this.regions && this.regions.contains(deployOnlyToRegion)) {
      deployToRegions.add(deployOnlyToRegion)
    }
    return deployToRegions
  }
  public List<String> getValidatedRegions(List<String> remainingRegions) {
    List<String> validatedRegions = []
    validatedRegions.addAll(this.getRegions())
    validatedRegions.removeAll(remainingRegions)
    return validatedRegions
  }

}
