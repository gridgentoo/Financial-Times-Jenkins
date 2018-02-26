package com.ft.jenkins.aws

import com.ft.jenkins.Environment

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR

public void updateCluster(String fullClusterName, String gitBranch) {
  String credentialsDir = unzipTlsCredentialsForCluster(fullClusterName)
  echo "Unzipped the TLS credentials used when the cluster ${fullClusterName} was created in folder ${credentialsDir}"

  ClusterUpdateInfo updateInfo = getClusterUpdateInfo(fullClusterName)
  echo "For cluster ${fullClusterName} determined update info: ${ClusterUpdateInfo} "

  echo "Starting update for cluster ${fullClusterName} ... "
  performUpdateCluster(updateInfo, credentialsDir, fullClusterName, gitBranch)
  echo "Ended update for cluster ${fullClusterName}"
}

private void performUpdateCluster(ClusterUpdateInfo updateInfo, credentialsDir, String clusterFullname,
                                  String gitBranch) {
  GString vaultCredentialsId = "ft.k8s-provision.env-type-${updateInfo.envType.shortName}.vault.pass"

  withCredentials([string(credentialsId: vaultCredentialsId, variable: 'VAULT_PASS')]) {
    String dockerRunArgs =
        "-u root " +
        "-v ${credentialsDir.trim()}:/ansible/credentials " +
        "-e 'AWS_REGION=${updateInfo.region}' " +
        "-e 'CLUSTER_NAME=${updateInfo.envName}' " +
        "-e 'CLUSTER_ENVIRONMENT=${updateInfo.cluster}' " +
        "-e 'ENVIRONMENT_TYPE=${updateInfo.envType.shortName}' " +
        "-e 'PLATFORM=${updateInfo.platform}' " +
        "-e 'VAULT_PASS=${env.VAULT_PASS}' "

    docker.image("k8s-provisioner:${gitBranch}").inside(dockerRunArgs) {
      sh "/update.sh"
    }
  }
}

public ClusterUpdateInfo getClusterUpdateInfo(String clusterFullName) {
  if (clusterFullName == null) {
    return null
  }

  ClusterUpdateInfo info = new ClusterUpdateInfo()
  String[] components = clusterFullName.split("-")
  int componentsNum = components.length
  if (componentsNum < 4) {
    throw new IllegalArgumentException("The full cluser name: ${clusterFullName} is incomplete")
  }
  /*  the full name looks like upp-k8s-dev-delivery-eu */
  info.platform = components[0]
  info.region = components[componentsNum - 1]
  info.cluster = components[componentsNum - 2]
  /*  in between sits the env name */
  info.envName = components[1..componentsNum - 3].join("-")

  info.envType = Environment.getEnvTypeForName(info.envName)
  return info
}

private String unzipTlsCredentialsForCluster(String fullClusterName) {
  withCredentials([file(credentialsId: "ft.k8s-provision.${fullClusterName}.credentials", variable: 'CREDENTIALS')]) {
    sh("""
      mkdir -p ${CREDENTIALS_DIR}
      rm -rf ${CREDENTIALS_DIR}/*
      unzip ${env.CREDENTIALS} -d ${CREDENTIALS_DIR}
    """)
  }

  //  get the absolute path of the parent dir of the ca.pem file
  return sh(returnStdout: true, script: """
      cafile=\$(find ${CREDENTIALS_DIR} -name ca.pem)
      echo "\$(cd \$(dirname \${cafile}); pwd -P)" 
    """)
}

return this