package com.ft.jenkins

import java.util.regex.Matcher

class Environment implements Serializable {
  public static final String STAGING_NAME = "staging"
  public static final String PROD_NAME = "prod"


  /*  The name of the environment. Example: prod*/
  String name
  /*  The slack channel where the notifications go for this environment */
  String slackChannel
  /*  The regions that this environment is split across */
  List<String> regions = null

  /*  The application clusters that an environment has*/
  List<Cluster> clusters = []

  /** The GLB addresses an environment may wish to know about. */
  Map<String,String> glbMap = [:]
  
  /*  Mapping between region+cluster and their respective Kubernetes api servers. */
  Map<String, String> clusterToApiServerMap

  public static EnvType getEnvTypeForName(String envName) {
    if (envName == PROD_NAME) {
      return EnvType.PROD
    }
    if (envName == STAGING_NAME) {
      return EnvType.TEST
    }
    return EnvType.DEVELOPMENT
  }

  public String getEntryPointUrl(Cluster cluster, String region = null) {
    println "---------------getEntryPointUrl------------"
    printf("cluster %s /region %s", cluster.toString(), region)
    def apiServer = getApiServerForCluster(cluster, region)
    if (apiServer != null) {
      return apiServer.replace("-api", "")
    }
    return null
  }

  public String getGlbUrl(Cluster cluster) {
    return glbMap.get(cluster.toString())
  }
  
  public String getClusterSubDomain(Cluster cluster, String region = null) {
    println "-----------getClusterSubDomain-----------"
    printf("cluster %s", cluster.toString())
    String entryPointUrl = getEntryPointUrl(cluster, region)
    if (entryPointUrl == null) {
      return null
    }
    printf("entryPointUrl %s", entryPointUrl)
    //Matcher matcher = entryPointUrl =~ /https:\/\/(.*)\.ft\.com/
    Matcher matcher = entryPointUrl =~ /https:\/\/(.*)\.amazonaws\.com/
    // https://upp-k8s-dev-delivery-eu-api.ft.com
    // https://1638169b446e0546ff623c7721bc76fa.sk1.eu-west-1.eks.amazonaws.com/

    return matcher[0][1]
  }

  public String getApiServerForCluster(Cluster cluster, String region = null) {
    println "-----------getApiServerForCluster----------"
    String lookupKey
    if (region) {
      lookupKey = "${region}-${cluster}"
    }
    else {
      lookupKey = cluster.toString()
    }

    printf("Looking for ApiServer for cluster %s with lookupkey %s", cluster.toString(), lookupKey)
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
