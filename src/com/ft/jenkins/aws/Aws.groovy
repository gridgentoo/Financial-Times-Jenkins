package com.ft.jenkins.aws

/**
 * Execution of aws-cli functionalities.
 */
void uploadS3Files(String s3Repo, String... filePaths) {
  for (int i = 0; i < filePaths.length; i++) {
    String filePath = filePaths[i];
    sh "aws s3 cp ${filePath} ${s3Repo}"
  }
}

// This is an experiment to extract EKS API Server endpoints dynamically.
// It is not used right now, instead we hardcode the API server endpoints.
String getEksApiServerEndpoint(String clusterName, String awsRegion) {
  String eksUrl = sh(script: "aws eks describe-cluster --region ${awsRegion} --name ${clusterName} --query cluster.endpoint | tr -d '\"'".toString(), returnStdout: true)
  eksUrl
}
