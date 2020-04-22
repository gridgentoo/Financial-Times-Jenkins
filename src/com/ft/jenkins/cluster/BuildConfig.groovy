package com.ft.jenkins.cluster

class BuildConfig implements Serializable {

  String preprodEnvName

  String prodEnvName

  List<ClusterType> allowedClusterTypes
}
