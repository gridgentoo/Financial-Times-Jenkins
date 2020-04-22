package com.ft.jenkins.provision

import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.EnvType
import com.ft.jenkins.cluster.Region

class ClusterUpdateInfo implements Serializable {
  Region region
  String envName
  ClusterType clusterType = ClusterType.UNKNOWN
  EnvType envType
  String platform
  String oidcIssuerUrl

  boolean equals(o) {
    if (this.is(o)) return true
    if (!(o instanceof ClusterUpdateInfo)) return false

    ClusterUpdateInfo that = (ClusterUpdateInfo) o

    if (clusterType != that.clusterType) return false
    if (envName != that.envName) return false
    if (envType != that.envType) return false
    if (oidcIssuerUrl != that.oidcIssuerUrl) return false
    if (platform != that.platform) return false
    if (region != that.region) return false

    return true
  }

  int hashCode() {
    int result
    result = (region != null ? region.hashCode() : 0)
    result = 31 * result + (envName != null ? envName.hashCode() : 0)
    result = 31 * result + (clusterType != null ? clusterType.hashCode() : 0)
    result = 31 * result + (envType != null ? envType.hashCode() : 0)
    result = 31 * result + (platform != null ? platform.hashCode() : 0)
    result = 31 * result + (oidcIssuerUrl != null ? oidcIssuerUrl.hashCode() : 0)
    return result
  }
}
