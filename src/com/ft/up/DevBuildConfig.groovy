package com.ft.up

class DevBuildConfig implements Serializable {
  /*  The Docker Image id of the application to deploy  */
  String appDockerImageId

  boolean useInternalDockerReg = false
}
