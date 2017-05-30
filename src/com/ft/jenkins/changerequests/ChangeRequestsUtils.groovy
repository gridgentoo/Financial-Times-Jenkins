package com.ft.jenkins.changerequests

import groovy.json.JsonSlurper

import static com.ft.jenkins.changerequests.CRConstants.DEFAULT_CREDENTIALS

final class CRConstants {
  public static final String DEFAULT_CREDENTIALS = "ft.cr-api.key"
}

public String open(ChangeRequestOpenData crData, String credentialId = DEFAULT_CREDENTIALS) {
  String bodyJson = """
{
  "ownerEmailAddress": "${crData.ownerEmail}",
  "summaryOfChange": "${crData.summary}",
  "changeDescription": "${crData.description}",
  "reasonForChangeDetails": "${crData.details}",
  "scheduledStartDate": "${getUkFormattedDate(crData.scheduledStartDate)}",
  "scheduledEndDate": "${getUkFormattedDate(crData.scheduledEndDate)}",
  "changeCategory": "${crData.changeCategory}",
  "riskProfile": "${crData.riskProfile}",
  "environment": "${crData.environment.name()}",
  "willThereBeAnOutage": "${crData.willThereBeAnOutage}",
  "resourceOne": "${crData.resourceOne}",
  "serviceIds": ["${crData.serviceIds.join("\",\"")}"],
  "notify": "${crData.notify}",
  "notifyChannel": "${crData.notifyChannel}"
}
"""

  echo "Opening CR with body ${bodyJson}"
  def response
  withCredentials([string(credentialsId: credentialId, variable: 'CR_API_KEY')]) {
    response = httpRequest(httpMode: 'POST',
                           url: 'https://cr-api.in.ft.com/v2/releaselog',
                           customHeaders: [[maskValue: true, name: 'x-api-key', value: env.CR_API_KEY],
                                           [maskValue: false, name: 'content-type', value: 'application/json']],
                           timeout: 10,
                           consoleLogResponseBody: true,
                           requestBody: bodyJson)
  }
  def responseJson = new JsonSlurper().parseText(response.content)
  return responseJson.changeRequests[0].name
}

public void close(ChangeRequestCloseData crData, String credentialId = DEFAULT_CREDENTIALS) {
  String bodyJson = """
{
  "closedByEmailAddress": "${crData.closedByEmailAddress}",
  "id": "${crData.id}",
  "actualStartDate": "${getUkFormattedDate(crData.getActualStartDate())}",
  "actualEndDate": "${getUkFormattedDate(crData.getActualEndDate())}",
  "closeCategory": "${crData.closeCategory}",
  "notify": "${crData.notify}",
  "notifyChannel": "${crData.notifyChannel}"
}
"""
  echo "Closing CR with body ${bodyJson}"
  withCredentials([string(credentialsId: credentialId, variable: 'CR_API_KEY')]) {
    httpRequest(httpMode: 'POST',
                url: 'https://cr-api.in.ft.com/v2/close',
                customHeaders: [[maskValue: true, name: 'x-api-key', value: env.CR_API_KEY],
                                [maskValue: false, name: 'content-type', value: 'application/json']],
                timeout: 10,
                consoleLogResponseBody: true,
                requestBody: bodyJson)
  }
}

private String getUkFormattedDate(Date date) {
  if (date) {
    return date.format("yyyy-MM-dd HH:mm", TimeZone.getTimeZone("Europe/London"))
  }
  return ""
}