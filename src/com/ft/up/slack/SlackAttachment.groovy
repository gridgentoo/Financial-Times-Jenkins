package com.ft.up.slack

/**
 * Replicates the structure from here: SlackAttachment attachment = new SlackAttachment()
 */
class SlackAttachment implements Serializable {
  String text
  String preText = ""
  String title = ""
  String titleUrl = ""
  String color = "good"
  String footer = ""
  String footerIcon = ""
  String imageUrl = ""
  String authorName = ""
  String authorLink = ""
  String authorIcon = ""

  boolean includeTimestamp = false
}
