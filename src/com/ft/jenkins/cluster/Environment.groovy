package com.ft.jenkins.cluster


import java.util.regex.Matcher

class Environment implements Serializable {
  public static final String DEV_NAME = "dev"
  public static final String STAGING_NAME = "staging"
  public static final String PROD_NAME = "prod"
  public static final String DEFAULT_KUBE_NAMESPACE = "default"
  public static final String UNKNOWN_ENV = "UNKNOWN-ENV"

  /*  The name of the environment. Example: prod*/
  String name
  /*  The slack channel where the notifications go for this environment */
  String slackChannel
  /*  The regions that this environment is split across */
  List<Region> regions = null

  String namespace = DEFAULT_KUBE_NAMESPACE

  Cluster cluster

  Set<ClusterType> associatedClusterTypes

  /** The GLB addresses an environment may wish to know about. */
  Map<String, String> glbMap = [:]

  /*  Mapping between region+cluster and their respective Kubernetes api servers. */
  Map<String, EnvClusterMapEntry> clusterToApiServerMap

  Environment(String name, Cluster cluster) {
    this.name = name
    this.cluster = cluster
  }

  static EnvType getEnvTypeForName(String envName) {
    if (envName == PROD_NAME) {
      return EnvType.PROD
    }
    if (envName == STAGING_NAME) {
      return EnvType.TEST
    }
    return EnvType.DEVELOPMENT
  }

  String getGlbUrl(ClusterType cluster) {
    glbMap.get(cluster.toString())
  }

  String getClusterSubDomain(ClusterType cluster, Region region = null) {
    String entryPointUrl = getClusterMapEntry(cluster, region)?.publicEndpoint
    if (entryPointUrl == null) {
      return null
    }
    if (entryPointUrl.contains("upp.ft.com")) {
      Matcher matcher = entryPointUrl =~ /https:\/\/(.*)\.upp\.ft\.com/
      return matcher[0][1]
    }
    //if def one regext, else use the one bellow
    else {
      Matcher matcher = entryPointUrl =~ /https:\/\/(.*)\.ft\.com/
      return matcher[0][1]
    }
  }

  EnvClusterMapEntry getClusterMapEntry(ClusterType cluster, Region region = null) {
    String lookupKey
    if (region) {
      lookupKey = "${region}-${cluster}"
    } else {
      lookupKey = cluster.name()
    }
    EnvClusterMapEntry envClusterUrl = clusterToApiServerMap.get(lookupKey)
    envClusterUrl
  }

  String getNamesWithRegions(List<Region> regions) {
    List<String> namesWithRegion = []
    for (Region regionName : regions) {
      namesWithRegion.add("${name}-${regionName.name}")
    }
    def concatNames = namesWithRegion.join(", ")
    concatNames
  }

  String getFullClusterName(ClusterType cluster, Region region = null) {
    String fullName = "${cluster.label}-${this.name}"
    if (region) {
      fullName = fullName + "-${region.name}"
    }
    fullName
  }

  List<String> getFullClusterNames(Collection<ClusterType> clusters, Set<Region> regions) {
    List<String> fullClusterNames = []
    for (ClusterType cluster : clusters) {
      if (regions) {
        for (Region region : regions) {
          fullClusterNames.add(getFullClusterName(cluster, region))
        }
      } else {
        fullClusterNames.add(getFullClusterName(cluster))
      }
    }
    fullClusterNames
  }

  List<Region> getRegionsToDeployTo(Region deployOnlyToRegion) {
    List<Region> deployToRegions = []
    boolean shouldDeployToAllRegions = !deployOnlyToRegion || deployOnlyToRegion == Region.ALL
    if (shouldDeployToAllRegions) {
      if (this.regions != null) {
        deployToRegions.addAll(this.regions)
      }
    } else if (this.regions && this.regions.contains(deployOnlyToRegion)) {
      deployToRegions.add(deployOnlyToRegion)
    }
    deployToRegions
  }

  List<Region> getValidatedRegions(List<Region> remainingRegions) {
    def validatedRegions = []
    validatedRegions.addAll(this.getRegions())
    validatedRegions.removeAll(remainingRegions)
    validatedRegions
  }

//  @Override
//  String toString() {
//    return "[name=${name}, slackChannel=${slackChannel}, regions=${regions}, namespace=${namespace}, cluster=${cluster}, associatedClusterTypes=${associatedClusterTypes}, glbMap=${glbMap}, clusterToApiServerMap=${clusterToApiServerMap}]".toString();
//  }
}
