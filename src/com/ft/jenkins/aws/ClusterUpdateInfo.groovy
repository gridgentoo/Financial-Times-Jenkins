package com.ft.jenkins.aws

import com.ft.jenkins.EnvType

class ClusterUpdateInfo implements Serializable {
  String region
  String envName
  String cluster
  EnvType envType
  String platform
}
