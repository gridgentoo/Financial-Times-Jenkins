package com.ft.jenkins.slack

/**
 * Replicates the structure from here: https://api.slack.com/docs/message-attachments#attachment_structure
 */
class SlackAttachment implements Serializable {
  String text = ""
  String preText = ""
  String title = ""
  String titleUrl = ""

  /*  According to https://api.slack.com/docs/message-attachments#attachment_structure the color can be good, warning, danger, or any other hex color code */
  String color = "good"
  String footer = ""
  String footerIcon = ""
  String imageUrl = ""
  String thumbUrl = "http://www.tothenew.com/blog/wp-content/uploads/2016/08/w.png"
  String authorName = ""
  String authorLink = ""
  String authorIcon = ""
  Long timestamp = null
}
