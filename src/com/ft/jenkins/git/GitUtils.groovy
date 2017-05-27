package com.ft.jenkins.git

import groovy.json.JsonSlurper

import static com.ft.jenkins.git.GitUtilsConstants.TAG_BRANCHES_PREFIX

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

public GithubReleaseInfo getGithubReleaseInfo(String tagName) {
  /*  fetch the release info*/
  def releaseResponse = httpRequest(acceptType: 'APPLICATION_JSON',
                                    authentication: 'ft.github.credentials',
                                    url: "https://api.github.com/repos/Financial-Times/people-rw-neo4j/releases/tags/${tagName}")

  def releaseInfoJson = new JsonSlurper().parseText(releaseResponse.content)
  GithubReleaseInfo releaseInfo = new GithubReleaseInfo()
  releaseInfo.title = releaseInfoJson.name
  releaseInfo.description = releaseInfoJson.body
  releaseInfo.url = releaseInfoJson.html_url
  releaseInfo.authorName = releaseInfoJson.author.login
  releaseInfo.authorUrl = releaseInfoJson.author.html_url
  releaseInfo.authorAvatar = releaseInfoJson.author.avatar_url
  releaseInfo.tagName = tagName
  return releaseInfo
}

private String getAuthorEmailAddress(String fileText) {
  return (fileText =~ /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,4}/)[0]
}

@NonCPS
private String getSummaryOfChange(String fileText) {
  String description = ""
  fileText.eachLine { line ->
    if (!(line =~ /Author:/) && !(line =~ /Date:/) && !(line =~ /Merge:/) && line != "") {
      description = description + line
    }
  }

  return description
}
