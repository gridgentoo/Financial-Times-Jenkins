package com.ft.jenkins.cluster

enum ClusterType implements Serializable {
  DELIVERY("delivery"),
  PUBLISHING("publishing", "publish"),
  PAC("pac"),
  ALL_IN_CHART("all-in-chart"),
  UNKNOWN("UNKNOWN-CLUSTER-TYPE")

  /*  The label for the cluster. Used for displaying. */
  String label

  String alias

  ClusterType(String label) {
    this.label = label
  }

  ClusterType(String label, String alias) {
    this.label = label
    this.alias = alias
  }

  static final ClusterType toClusterType(String label) {
    ClusterType clusterType = values().find { it.label == label || it.alias == label }
    if (!clusterType) {
      clusterType = UNKNOWN
    }
    clusterType
  }

  static final List<String> toLabels(Collection<ClusterType> clusters) {
    List<String> labels = []
    for (ClusterType cluster : clusters) {
      labels.add(cluster.label)
    }
    labels
  }
}
