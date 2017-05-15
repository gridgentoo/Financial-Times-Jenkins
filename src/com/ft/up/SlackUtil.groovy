package com.ft.up
/**
 * Utils for Slack
 */

void sendTeamSlackNotification(String team, String message) {
  sendSlackNotification(TeamsRegistry.getSlackChannelForTeam(team), message)
}

/**
 * Sends a slack notification using the https://api.slack.com/methods/chat.postMessage method.
 *
 * @param channel the channel where to send the notification. Example: @username, #upp-tech
 * @param message The message to send
 * @param credentialId the id of Jenkins credentials to use. By default it will use 'ft.slack.bot-credentials'
 */
void sendSlackNotification(String channel, String message, String credentialId = "ft.slack.bot-credentials") {
  String encodedChannel = URLEncoder.encode(channel, "UTF-8")
  String encodedMessage = URLEncoder.encode(message, "UTF-8")
  withCredentials([[$class: 'StringBinding', credentialsId: credentialId, variable: 'SLACK_TOKEN']]) {
    sh """
    curl -X POST -s \\
  https://slack.com/api/chat.postMessage \\
  -H 'cache-control: no-cache' \\
  -H 'content-type: application/x-www-form-urlencoded' \\
  -d 'text=${encodedMessage}&token=${env.SLACK_TOKEN}&channel=${encodedChannel}&username=jenkins-bot'
  """
  }
}
