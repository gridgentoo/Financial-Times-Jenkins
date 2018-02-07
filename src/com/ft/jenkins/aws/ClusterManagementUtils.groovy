package com.ft.jenkins.aws

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR

public void updateCluster(String awsRegion, String clusterName, String clusterEnv, String envType,
                          String platform, String vaultPass, String gitBranch) {
  prepareK8SCliCredentials(getFullClusterName(awsRegion, clusterEnv, envType, platform))
  String currentDir = pwd()
  GString dockerRunArgs =
          "-v ${currentDir}/${CREDENTIALS_DIR}:/ansible/credentials " +
          "-e 'AWS_REGION=${awsRegion}' " +
          "-e 'CLUSTER_NAME=${clusterName}' " +
          "-e 'CLUSTER_ENVIRONMENT=${clusterEnv}' " +
          "-e 'ENVIRONMENT_TYPE=${envType}' " +
          "-e 'PLATFORM=${platform}' " +
          "-e 'VAULT_PASS=${vaultPass}' "

  docker.image("k8s-provisioner:${gitBranch}").inside(dockerRunArgs) {
    sh "printenv"
//      sh "update.sh"
  }
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

private void prepareK8SCliCredentials(String fullClusterName) {
  withCredentials([
          [$class: 'FileBinding', credentialsId: "ft.k8s-provision.${fullClusterName}.credentials", variable: 'CREDENTIALS'],
  ]) {
    sh """
      mkdir -p ${CREDENTIALS_DIR}
      rm -f ${CREDENTIALS_DIR}/*
      unzip ${env.CREDENTIALS} -d ${CREDENTIALS_DIR}
    """
  }
}

return this