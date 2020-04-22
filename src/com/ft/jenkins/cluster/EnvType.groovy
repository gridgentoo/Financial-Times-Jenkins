package com.ft.jenkins.cluster

enum EnvType implements Serializable {
  PROD("p"),
  TEST("t"),
  DEVELOPMENT("d")

  String shortName

  EnvType(String shortName) {
    this.shortName = shortName
  }
}
