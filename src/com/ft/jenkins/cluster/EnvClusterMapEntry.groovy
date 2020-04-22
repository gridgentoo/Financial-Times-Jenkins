package com.ft.jenkins.cluster

import com.cloudbees.groovy.cps.NonCPS

class EnvClusterMapEntry implements Serializable {
  String apiServer
  String eksClusterName
  String publicEndpoint
  boolean isEks

  private EnvClusterMapEntry(String apiServer, String publicEndpoint) {
    this.apiServer = apiServer
    this.publicEndpoint = publicEndpoint
  }

  private EnvClusterMapEntry(String eksClusterName, String apiServer, String publicEndpoint) {
    this(apiServer, publicEndpoint)
    this.eksClusterName = eksClusterName
    this.isEks = true
  }

  @NonCPS
  static EnvClusterMapEntry newEntry(Map args) {
    String apiServer = args.apiServer
    String publicEndpoint = args.publicEndpoint
    new EnvClusterMapEntry(apiServer, publicEndpoint)
  }

  @NonCPS
  static EnvClusterMapEntry newEksEntry(Map args) {
    String apiServer = args.apiServer
    String publicEndpoint = args.publicEndpoint
    String eksClusterName = args.eksClusterName
    new EnvClusterMapEntry(eksClusterName, apiServer, publicEndpoint)
  }
}
