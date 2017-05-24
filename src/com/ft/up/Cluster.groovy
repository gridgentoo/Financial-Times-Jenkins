package com.ft.up

enum Cluster implements Serializable {
  DELIVERY("delivery"),
  PUBLISHING("publishing")

  /*  The label for the cluster. Used for displaying*/
  String label

  Cluster(String label) {
    this.label = label
  }
}
