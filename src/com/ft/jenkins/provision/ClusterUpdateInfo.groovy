package com.ft.jenkins.provision

import com.ft.jenkins.EnvType

class ClusterUpdateInfo implements Serializable {
  String region
  String envName
  String cluster
  EnvType envType
  String platform

  @Override
  String toString() {
    return "[envname: ${envName}, region: ${region}, cluster: ${cluster}, envType: ${envType.shortName}, platform: ${platform}]"
  }
}
