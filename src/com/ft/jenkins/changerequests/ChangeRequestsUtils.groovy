package com.ft.jenkins.changerequests

import groovy.json.JsonSlurper

import static com.ft.jenkins.changerequests.CRConstants.DEFAULT_CREDENTIALS

final class CRConstants {
  public static final String DEFAULT_CREDENTIALS = "ft.change-api.key"
}

/*
Mapping the ChangeAPI to Jenkins variables as follows:
environment - environment
systemCode - systemCode
ownerEmailAddress - "user": {"email": "${user}@ft.com"}
scheduledStartDate - timestamp
summaryOfChange - extraProperties: "changeDescription": "releasing v7 which includes a security patch"
*/
public String open(ChangeRequestOpenData crData, String credentialId = DEFAULT_CREDENTIALS) {
  String bodyJson = """
{
  "user": {
	  "email": "${crData.ownerEmail}"
	},
	"environment": "${crData.environment.name()}",
	"systemCode": "${crData.systemCode}",
	"notifications": {
		"slackChannels": ["${crData.notifyChannel}"]
	},
  "extraProperties": {
		"changeDescription": ["${crData.summary}"]
	}
}
"""

  echo "Opening CR with body ${bodyJson}"
  def response
  withCredentials([string(credentialsId: credentialId, variable: 'UPP_CHANGE_API_KEY')]) {
    response = httpRequest(httpMode: 'POST',
                           url: 'https://api.ft.com/change-log/v1/create',
                           customHeaders: [[maskValue: true, name: 'x-api-key', value: env.UPP_CHANGE_API_KEY],
                                           [maskValue: false, name: 'content-type', value: 'application/json']],
                           timeout: 60,
                           consoleLogResponseBody: true,
                           requestBody: bodyJson)
  }
  def responseJson = new JsonSlurper().parseText(response.content)
  return responseJson.changeRequests?.getAt(0)?.name
}

private String getUkFormattedDate(Date date) {
  if (date) {
    return date.format("yyyy-MM-dd HH:mm", TimeZone.getTimeZone("Europe/London"))
  }
  return ""
}