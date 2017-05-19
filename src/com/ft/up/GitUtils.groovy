package com.ft.up

final class GitUtilsConstants {

}

public class TagInfo implements Serializable {
  String authorEmail
  String summary
}

public String getTagNameFromBranchName(String checkedOutBranchName) {
  String[] values = checkedOutBranchName.split('/')
  return values[values.length - 1]
}

public TagInfo getTagInfo(String tagName) {
  String gitTempFile = "__tagDescription_${System.currentTimeMillis()}"
  try {
    sh "git show ${tagName}  > ${gitTempFile}"
    String fileText = readFile(gitTempFile)
    TagInfo info = new TagInfo()
    info.authorEmail = getAuthorEmailAddress(fileText)
    info.summary = getSummaryOfChange(fileText)
    return info
  } finally {
    sh "rm -f ${gitTempFile}"
  }
}

private String getAuthorEmailAddress(String fileText) {
  return (fileText =~ /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,4}/)[0]
}

private String getSummaryOfChange(String fileText) {
  String description = ""
  fileText.eachLine { line ->
    if (!(line =~ /Author:/) && !(line =~ /Date:/) && !(line =~ /Merge:/) && line != "") {
      description = description + line
    }
  }

  return description
}
