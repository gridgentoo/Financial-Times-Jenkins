package com.ft.jenkins.aws

/**
 * Utils for communicating with AWS
 */
public void uploadS3Files(String s3Repo, String awsCredentials, String ... filePaths) {
  withCredentials([usernamePassword(credentialsId: awsCredentials, passwordVariable: 'SECRET_ACCESS_KEY', usernameVariable: 'ACCESS_KEY_ID')]) {
    docker.image("mikesir87/aws-cli:1.11.125").inside("-e 'AWS_ACCESS_KEY_ID=${env.ACCESS_KEY_ID}' -e 'AWS_SECRET_ACCESS_KEY=${env.SECRET_ACCESS_KEY}'") {
      for (int i = 0; i < filePaths.length; i++) {
        String filePath = filePaths[i];
        sh "aws s3 cp ${filePath} ${s3Repo}"
      }
    }
  }
}