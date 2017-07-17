package com.ft.jenkins.slack

import com.ft.jenkins.Cluster
import com.ft.jenkins.Environment

import static com.ft.jenkins.slack.SlackConstants.BOT_USERNAME
import static com.ft.jenkins.slack.SlackConstants.DEFAULT_CREDENTIALS

/**
 * Utils for Slack
 */

final class SlackConstants {
  public static final String DEFAULT_CREDENTIALS = "ft.slack.bot-credentials"
  public static final String BOT_USERNAME = "jenkins-bot"
}

public String getHealthUrl(Environment environment, Cluster cluster, String region = null) {
  String entryPointURL = environment.getEntryPointUrl(cluster, region)
  String fullClusterName = environment.getFullClusterName(cluster, region)
  return "<${entryPointURL}/__health|${fullClusterName}>"
}


public void sendEnhancedSlackNotification(String channel, SlackAttachment attachment,
                                          String credentialId = DEFAULT_CREDENTIALS) {

  /*  not using the JsonBuilder as we need NonCPS for that, and seems it doesn't play well with looking up credentials */
  String attachmentJson="""[{
    "pretext": "${attachment.preText}",
    "author_name": "${attachment.authorName}",
    "author_link": "${attachment.authorLink}",
    "author_icon": "${attachment.authorIcon}",
    "title": "${attachment.title}",
    "title_link": "${attachment.titleUrl}",
    "text": "${attachment.text}",
    "image_url": "${attachment.imageUrl}",
    "thumb_url": "${attachment.thumbUrl}",
    "footer": "${attachment.footer}",
    "footer_icon": "${attachment.footerIcon}",
    "color": "${attachment.color}",
    "mrkdwn_in": ["text", "pretext"]
    ${attachment.timestamp ? ', "ts": ' + attachment.timestamp : "" }
  }]"""

  String encodedChannel = URLEncoder.encode(channel, "UTF-8")
  String encodedAttachment = URLEncoder.encode(attachmentJson, "UTF-8")
  echo "Sending attachment to slack: ${attachmentJson}"
  withCredentials([[$class: 'StringBinding', credentialsId: credentialId, variable: 'SLACK_TOKEN']]) {
    String requestBody = "token=${env.SLACK_TOKEN}&attachments=${encodedAttachment}&channel=${encodedChannel}&username=${BOT_USERNAME}"
    echo "Whole request body: ${requestBody}"
    httpRequest(httpMode: 'POST',
                url: 'https://slack.com/api/chat.postMessage',
                customHeaders: [[maskValue: false, name: 'content-type', value: 'application/x-www-form-urlencoded']],
                timeout: 10,
                consoleLogResponseBody: true,
                requestBody: requestBody)
  }
}
