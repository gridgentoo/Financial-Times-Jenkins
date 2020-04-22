package com.ft.jenkins.cluster

class Cluster implements Serializable{
  ClusterType clusterType
  List<Environment> environments

  Cluster(ClusterType clusterType) {
    this.clusterType = clusterType
  }

  @Override
  String toString() {
    return "[clusterType=${clusterType.label}, environments=${environments}]".toString();
  }
}
