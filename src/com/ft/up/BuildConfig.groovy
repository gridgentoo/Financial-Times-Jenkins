package com.ft.up

class BuildConfig implements Serializable {
  /*  The Docker Image id of the application to deploy  */
  String appDockerImageId

  boolean useInternalDockerReg = false
}
