package com.ft.jenkins.changerequests

class ChangeRequestOpenData implements Serializable {
  String ownerEmail
  String summary
  String description
  String details
  ChangeRequestEnvironment environment
  String changeCategory
  String riskProfile = "Low"
  String willThereBeAnOutage = "NO"
  Date scheduledStartDate
  Date scheduledEndDate
  String resourceOne = "universal.publishing.platform@ft.com"
  List<String> serviceIds = ['ContentAPI']
  boolean notify = true
  String notifyChannel
}
