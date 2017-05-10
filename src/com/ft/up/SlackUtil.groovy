package com.ft.up

import java.nio.charset.StandardCharsets

/**
 * Utis for Slack
 */

void sendSlackNotification(String channel, String message, String credentialId = "ft.slack.bot-credentials") {

  String encodedChannel = URLEncoder.encode(channel, StandardCharsets.UTF_8.name())
  String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
  withCredentials([[$class: 'StringBinding', credentialsId: credentialId, variable: 'SLACK_TOKEN']]) {
    sh """
    curl -X POST \\
  https://slack.com/api/chat.postMessage \\
  -H 'cache-control: no-cache' \\
  -H 'content-type: application/x-www-form-urlencoded' \\
  -d 'text=${encodedMessage}&token=${env.SLACK_TOKEN}&channel=${encodedChannel}'
  """
  }
}
