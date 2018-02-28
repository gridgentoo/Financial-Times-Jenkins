package com.ft.jenkins

enum Cluster implements Serializable {
  DELIVERY("delivery"),
  PUBLISHING("publishing", "publish"),
  NEO4J("neo4j"),
  PAC("pac")

  /*  The label for the cluster. Used for displaying. */
  String label

  List<String> aliases

  Cluster(String label, String... aliases) {
    this.label = label
    this.aliases = aliases
  }

  public static final Cluster valueOfLabel(String label) {
    for (Cluster cluster : Cluster.values()) {
      if (cluster.label == label || cluster.aliases.contains(label)) {
        return cluster
      }
    }
    return null
  }

  public static final List<String> toLabels(Collection<Cluster> clusters) {
    List<String> labels = []
    for (Cluster cluster : clusters) {
      labels.add(cluster.label)
    }
    return labels
  }
}
