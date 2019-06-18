package com.ft.jenkins.changerequests

class ChangeRequestOpenData implements Serializable {
  String ownerEmail
  String summary
  ChangeRequestEnvironment environment
  String notifyChannel
  String systemCode
  String fullClusterName
}
