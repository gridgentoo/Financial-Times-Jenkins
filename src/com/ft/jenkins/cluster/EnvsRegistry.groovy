package com.ft.jenkins.cluster

class EnvsRegistry implements Serializable {
  public static List<Cluster> clusters

  static {
    Cluster deliveryCluster = Clusters.initDeliveryCluster()
    Cluster publishingCluster = Clusters.initPublishingCluster()
    Cluster pacCluster = Clusters.initPacCluster()

    clusters = [deliveryCluster, publishingCluster, pacCluster]
  }

  static Environment getEnvironmentPerAssociatedClusterTypes(Set<ClusterType> associatedClusterTypes, String name) {
    for (Cluster cluster : clusters) {
      return cluster.environments.find { it.name == name && it.associatedClusterTypes.sort() == associatedClusterTypes.sort() }
    }
    return null
  }

  static Environment getEnvironment(ClusterType clusterType, String name) {
    for (Cluster c : clusters) {
      if (c.clusterType == clusterType) {
        for (Environment e : c.environments) {
          if (e.name == name) {
            return e
          }
        }
      }
    }
//    return new Environment(Environment.UNKNOWN_ENV, null)
    return null
//    Cluster cluster = clusters.find { it.clusterType == clusterType }
//    Environment env = cluster.environments.find { it.name == name }
//    env
  }

  static Environment getEnvironment(String clusterTypeLabel, String name) {
    ClusterType clusterType = ClusterType.toClusterType(clusterTypeLabel)
    Environment env = getEnvironment(clusterType, name)
    env
  }

  static boolean hasAllowedClusterType(List<ClusterType> allowedClusterTypes, String name) {
    for (Cluster cluster : clusters) {
      Environment env = cluster.environments.find { it.name == name }
      ClusterType allowedClusterType = allowedClusterTypes.find { it == env?.cluster?.clusterType }
      if (allowedClusterType) {
        return true
      }
    }
    return false
  }

  static Environment getEnvironmentByFullName(String fullName) {
    if (fullName == null) {
      return null
    }

    // This would work for names such as - "pac-staging-eu", "delivery-prod-us", "publish-staging-eu"
    String clusterLabel = fullName.split("-")[1]
    ClusterType clusterType = ClusterType.toClusterType(clusterLabel)
    Cluster cluster = clusters.find { it.clusterType == clusterType }

    for (Environment environment : cluster?.environments) {
      for (EnvClusterMapEntry apiServer : environment.clusterToApiServerMap.values()) {
        String url = apiServer.apiServer
        if (url.contains("${fullName}-api.ft.com") || url.contains("${fullName}-api.upp.ft.com")) {
          return environment
        }
      }
    }
    return null
  }
}
