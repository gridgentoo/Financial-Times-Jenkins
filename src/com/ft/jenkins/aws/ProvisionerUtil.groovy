package com.ft.jenkins.aws

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR

public void updateCluster(String awsRegion, String clusterName, String clusterEnv, String envType,
                          String platform, String gitBranch) {
  String credentialsDir = prepareK8SCliCredentials(getFullClusterName(awsRegion, clusterEnv, envType, platform))
  withCredentials([
      [$class: 'StringBinding', credentialsId: "ft.k8s-provision.content-${envType}.vault.pass", variable: 'VAULT_PASS']]) {

    String dockerRunArgs =
        "-u root " +
        "-v ${credentialsDir.trim()}:/ansible/credentials " +
        "-e 'AWS_REGION=${awsRegion}' " +
        "-e 'CLUSTER_NAME=${clusterName}' " +
        "-e 'CLUSTER_ENVIRONMENT=${clusterEnv}' " +
        "-e 'ENVIRONMENT_TYPE=${envType}' " +
        "-e 'PLATFORM=${platform}' " +
        "-e 'VAULT_PASS=${env.VAULT_PASS}' "

    docker.image("k8s-provisioner:${gitBranch}").inside(dockerRunArgs) {
      sh "/update.sh"
    }
  }
}

public ClusterUpdateInfo getClusterUpdateInfo(String clusterFullName) {

}

public static String getFullEnvironmentType(String envType) {
  if (envType == 'd') {
    return "test"
  } else if (envType == 't' || envType == 'p') {
    return "prod"
  }
  return envType
}

private static String getFullClusterName(String awsRegion, String clusterEnv, String envType, String platform) {
  return "${platform}-${getFullEnvironmentType(envType)}-${clusterEnv}-${awsRegion}"
}

private String prepareK8SCliCredentials(String fullClusterName) {
  /*  unzip the TLS assets of the cluster stored as credentials in Jenkins */
  withCredentials([
      [$class: 'FileBinding', credentialsId: "ft.k8s-provision.${fullClusterName}.credentials", variable: 'CREDENTIALS'],
  ]) {
    sh ("""
      mkdir -p ${CREDENTIALS_DIR}
      rm -rf ${CREDENTIALS_DIR}/*
      unzip ${env.CREDENTIALS} -d ${CREDENTIALS_DIR}
    """)
  }

  //  get the absolute path of the parent dir of the ca.pem file
  return sh (returnStdout: true, script: """
      cafile=\$(find ${CREDENTIALS_DIR} -name ca.pem)
      echo "\$(cd \$(dirname \${cafile}); pwd -P)" 
    """)
}

return this