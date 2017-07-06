package com.ft.jenkins

class BuildConfig implements Serializable {

  /* The list of clusters where the application is deployed. */
  List<Cluster> deployToClusters
}
