package com.ft.up.slack

import com.ft.up.EnvsRegistry

import groovy.json.JsonBuilder

import static com.ft.up.slack.SlackConstants.BOT_USERNAME
import static com.ft.up.slack.SlackConstants.DEFAULT_CREDENTIALS

/**
 * Utils for Slack
 */

final class SlackConstants {
  public static final String DEFAULT_CREDENTIALS = "ft.slack.bot-credentials"
  public static final String BOT_USERNAME = "jenkins-bot"
}

public void sendEnvSlackNotification(String environment, String message) {
  sendSlackNotification(EnvsRegistry.getSlackChannelForEnv(environment), message)
}

/**
 * Sends a slack notification using the https://api.slack.com/methods/chat.postMessage method.
 *
 * @param channel the channel where to send the notification. Example: @username, #upp-tech
 * @param message The message to send
 * @param credentialId the id of Jenkins credentials to use. By default it will use 'ft.slack.bot-credentials'
 */
public void sendSlackNotification(String channel, String message, String credentialId = DEFAULT_CREDENTIALS) {
  String encodedChannel = URLEncoder.encode(channel, "UTF-8")
  String encodedMessage = URLEncoder.encode(message, "UTF-8")
  withCredentials([[$class: 'StringBinding', credentialsId: credentialId, variable: 'SLACK_TOKEN']]) {
    sh """
    curl -X POST -s \\
  https://slack.com/api/chat.postMessage \\
  -H 'cache-control: no-cache' \\
  -H 'content-type: application/x-www-form-urlencoded' \\
  -d 'text=${encodedMessage}&token=${env.SLACK_TOKEN}&channel=${encodedChannel}&username=${BOT_USERNAME}'
  """
  }
}

public void sendEnhancedSlackNotification(String channel, SlackAttachment attachment,
                                          String credentialId = DEFAULT_CREDENTIALS) {
  JsonBuilder attachmentBuilder = new JsonBuilder()
  attachmentBuilder {
    pretext(attachment.preText)
    author_name(attachment.authorName)
    author_link(attachment.authorLink)
    author_icon(attachment.authorIcon)
    title(attachment.title)
    title_link(attachment.titleUrl)
    text(attachment.text)
    image_url(attachment.imageUrl)
    footer(attachment.footer)
    footer_icon(attachment.footerIcon)
    color(attachment.color)
    mrkdwn_in("text", "pretext")
    if (attachment.includeTimestamp) {
      ts(System.currentTimeMillis())
    }
  }
  String encodedChannel = URLEncoder.encode(channel, "UTF-8")
  String encodedAttachment = URLEncoder.encode("[${attachmentBuilder.toString()}]", "UTF-8")
  withCredentials([[$class: 'StringBinding', credentialsId: credentialId, variable: 'SLACK_TOKEN']]) {
    String requestBody = "token=${env.SLACK_TOKEN}&attattachments=${encodedAttachment}&channel=${encodedChannel}&username=${BOT_USERNAME}"
    httpRequest(contentType: 'APPLICATION_FORM',
                httpMode: 'POST',
                responseHandle: 'NONE',
                url: 'https://slack.com/api/chat.postMessage',
                requestBody: requestBody)
  }
}
