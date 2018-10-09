package com.ft.jenkins

class BuildConfig implements Serializable {

  String preprodEnvName

  String prodEnvName

  List<Cluster> allowedClusters
}
