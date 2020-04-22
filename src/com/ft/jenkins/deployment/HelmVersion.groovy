package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.EnvClusterMapEntry
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region

enum HelmVersion {
  V3, V2

  static HelmVersion discoverVersion(Environment env, Region region) {
    def clusterType = env.cluster.clusterType
    def key = "${region}-${clusterType}".toString()
    EnvClusterMapEntry envClusterUrl = env.clusterToApiServerMap.find{ it.key == key }?.value
    if (!envClusterUrl?.apiServer) {
      throw new IllegalArgumentException(
              "Cannot discover helm version for cluster type '${clusterType.label}' region '${region.name}' because the is no available cluster URL")
    }
    if (envClusterUrl.isEks) {
      return V3
    }
    return V2
  }
}
