package com.ft.up.slack

class SlackAttachment implements Serializable {
  String text
  String preText = ""
  String title = ""
  String titleUrl = ""
  String color = "green"
  String footer = ""
  String footerIcon = ""
  String imageUrl = ""
  String authorName = ""
  String authorLink = ""
  String authorIcon = ""

  String includeTimestamp
}
