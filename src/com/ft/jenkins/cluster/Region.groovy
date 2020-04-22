package com.ft.jenkins.cluster

enum Region implements Serializable {
  EU("eu"), US("us"), ALL("all"), UNKNOWN("UNKNOWN-REGION")

  String name
  String awsName

  Region(String name) {
    this.name = name
  }

  static final Region toRegion(String name) {
    values().find { it.name == name }
  }

  static final String toJenkinsChoiceValues(List<Region> availableRegions) {
    availableRegions.inject(ALL.name) {
      str, item -> str + "\n" + item.name
    }
  }
}
