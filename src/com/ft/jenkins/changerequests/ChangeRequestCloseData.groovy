package com.ft.jenkins.changerequests

class ChangeRequestCloseData implements Serializable {
  String id
  String closedByEmailAddress = "universal.publishing.platform@ft.com"
  Date actualStartDate
  Date actualEndDate
  String closeCategory = "Implemented"
  boolean notify = true
  String notifyChannel
}
