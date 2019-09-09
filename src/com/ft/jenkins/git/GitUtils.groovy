package com.ft.jenkins.git

import groovy.json.JsonSlurper

import java.util.regex.Matcher

import static com.ft.jenkins.git.GitUtilsConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX
import static com.ft.jenkins.git.GitUtilsConstants.TAG_BRANCHES_PREFIX

public boolean isTag(String checkedOutBranchName) {
  return checkedOutBranchName.startsWith(TAG_BRANCHES_PREFIX)
}

public boolean isDeployOnPushForBranch(String branchName) {
  return branchName.startsWith(DEPLOY_ON_PUSH_BRANCHES_PREFIX)
}

public String getTagNameFromBranchName(String checkedOutBranchName) {
  String[] values = checkedOutBranchName.split('/')
  return values[values.length - 1]
}

public String getCurrentRepoName(Object scm) {
  String gitUrl = scm.getUserRemoteConfigs()[0].url
  Matcher matcher = (gitUrl =~ /.*\/(.*).git/)
  /*  get the value matching the group */
  return matcher[0][1]
}

public GithubReleaseInfo getGithubReleaseInfo(String tagName, String repoName) {
  /*  fetch the release info*/
  def releaseResponse
  GString requestedUrl = "https://api.github.com/repos/Financial-Times/${repoName}/releases/tags/${tagName}"
  try {
    releaseResponse = httpRequest(acceptType: 'APPLICATION_JSON',
                                  authentication: 'ft.github.credentials',
                                  url: requestedUrl)
  } catch (IllegalStateException e) {
    echo"Release in GitHub could not be found at URL: ${requestedUrl}. Error: ${e.message}"
    return null
  }

  def releaseInfoJson = new JsonSlurper().parseText(releaseResponse.content)
  GithubReleaseInfo releaseInfo = new GithubReleaseInfo()
  releaseInfo.title = releaseInfoJson.name
  releaseInfo.description = releaseInfoJson.body
  releaseInfo.url = releaseInfoJson.html_url
  releaseInfo.authorName = releaseInfoJson.author.login
  releaseInfo.authorUrl = releaseInfoJson.author.html_url
  releaseInfo.authorAvatar = releaseInfoJson.author.avatar_url
  releaseInfo.isPreRelease = Boolean.valueOf(releaseInfoJson.prerelease)
  releaseInfo.tagName = tagName
  return releaseInfo
}

public String getMostRecentGitTag() {
  println "==========git tags start ==============="
  sh "git tags"
  sh "git describe --abbrev=0 --tags >> git-version"
  println "==========git tags end ==============="
  String mostRecentGitTag = readFile 'git-version'

  /* remove any additional text from git version */
  String extractedGitVersion = (mostRecentGitTag =~ GitUtilsConstants.GIT_VERSION_REGEX)[0]
  echo "Retrieved most recent git tag: ${extractedGitVersion}"
  return extractedGitVersion
}

public String getLatestCommit() {
  return sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
}

public String getShortLatestCommit() {
  return getLatestCommit().take(6)
}
