package com.ft.jenkins

public String getRequiredParameterValue(String paramName) {
  String paramValue = params[paramName]
  if (!paramValue) {
    throw new IllegalArgumentException("${paramName} is required. Please provide a value to build")
  }
  return paramValue
}