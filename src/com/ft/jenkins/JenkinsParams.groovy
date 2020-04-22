package com.ft.jenkins

String getRequiredParameterValue(String paramName) {
  String paramValue = params[paramName]
  if (!paramValue) {
    throw new IllegalArgumentException("${paramName} is required. Please provide a value to build")
  }
  paramValue
}

String getJenkinsBuildAuthor() {
  currentBuild.rawBuild?.getCause(Cause.UserIdCause)?.getUserId()
}
