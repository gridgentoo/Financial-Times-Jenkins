package com.ft.jenkins.git

import groovy.json.JsonSlurper

import java.util.regex.Matcher

import static GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX


static boolean isTag(String checkedOutBranchName) { checkedOutBranchName?.trim() }

static boolean isDeployOnPushForBranch(String branchName) { branchName.startsWith(DEPLOY_ON_PUSH_BRANCHES_PREFIX) }

static String getTagNameFromBranchName(String checkedOutBranchName) {
  String[] values = checkedOutBranchName.split('/')
  String tagName = values[values.length - 1]
  tagName
}

String getCurrentRepoName(Object scm) {
  String gitUrl = scm.getUserRemoteConfigs()[0].url
  Matcher matcher = (gitUrl =~ /.*\/(.*).git/)
  /*  get the value matching the group */
  String currentRepoName = matcher[0][1]
  currentRepoName
}

String getMostRecentGitTag() {
  sh "git describe --abbrev=0 --tags >> git-version"
  String mostRecentGitTag = readFile 'git-version'

  /* remove any additional text from git version */
  String extractedGitVersion = (mostRecentGitTag =~ GitHelperConstants.GIT_VERSION_REGEX)[0]
  echo "Retrieved most recent git tag: ${extractedGitVersion}"
  extractedGitVersion
}

String getLatestCommit() { sh(returnStdout: true, script: 'git rev-parse HEAD').toString().trim() }

String getShortLatestCommit() { getLatestCommit().take(6) }

GithubReleaseInfo getGithubReleaseInfo(String tagName, String repoName) {
  /*  fetch the release info*/
  def releaseInfoJson = fetchGithubReleaseInfoJson(tagName, repoName)
  GithubReleaseInfo releaseInfo = new GithubReleaseInfo()
  releaseInfo.title = releaseInfoJson.name
  releaseInfo.description = releaseInfoJson.body
  releaseInfo.url = releaseInfoJson.html_url
  releaseInfo.authorName = releaseInfoJson.author.login
  releaseInfo.authorUrl = releaseInfoJson.author.html_url
  releaseInfo.authorAvatar = releaseInfoJson.author.avatar_url
  releaseInfo.latestCommit = releaseInfoJson.sha
  releaseInfo.isPreRelease = Boolean.valueOf(releaseInfoJson.prerelease)
  releaseInfo.tagName = tagName
  releaseInfo
}

private Object fetchGithubReleaseInfoJson(String tagName, String repoName) {
  def releaseResponse
  def requestedUrl = "https://api.github.com/repos/Financial-Times/${repoName}/releases/tags/${tagName}".toString()
  try {
    releaseResponse = httpRequest acceptType: 'APPLICATION_JSON', authentication: 'ft.github.credentials', url: requestedUrl
  } catch (IllegalStateException e) {
    echo "Release in GitHub could not be found at URL: ${requestedUrl}. Error: ${e.message}"
    return null
  }

  def releaseInfoJson = new JsonSlurper().parseText(releaseResponse.content)
  releaseInfoJson
}

GithubReleaseInfo getReleaseInfoForCurrentTag(String tagName) {
  String currentRepoName = getCurrentRepoName(scm)
  GithubReleaseInfo releaseInfo = getGithubReleaseInfo(tagName, currentRepoName)
  releaseInfo
}

