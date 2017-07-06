package com.ft.jenkins

enum Cluster implements Serializable {
  DELIVERY("delivery"),
  PUBLISHING("publishing")

  /*  The label for the cluster. Used for displaying*/
  String label

  Cluster(String label) {
    this.label = label
  }

  public static final List<String> toLabels(List<Cluster> clusters) {
    List<String> labels = []
    for (int i = 0; i < clusters.size(); i++) {
      Cluster cluster = clusters.get(i);
      labels.add(cluster.label)
    }
    return labels
  }
}
