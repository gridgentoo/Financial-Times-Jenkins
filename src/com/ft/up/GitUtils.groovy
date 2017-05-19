package com.ft.up

import static com.ft.up.GitUtilsConstants.*
final class GitUtilsConstants {
  public static final String TAG_BRANCHES_PREFIX = "tags/"
}

public boolean isTag(String checkedOutBranchName) {
  return checkedOutBranchName.startsWith(TAG_BRANCHES_PREFIX)
}

public String getTagNameFromBranchName(String checkedOutBranchName) {
  String[] values = checkedOutBranchName.split('/')
  return values[values.length - 1]
}

public GitTagInfo getTagInfo(String tagName) {
  String gitTempFile = "__tagDescription_${System.currentTimeMillis()}"
  try {
    sh "git show ${tagName}  > ${gitTempFile}"
    String fileText = readFile(gitTempFile)
    GitTagInfo info = new GitTagInfo()
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
