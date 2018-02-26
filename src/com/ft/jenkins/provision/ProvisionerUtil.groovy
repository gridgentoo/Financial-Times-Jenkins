package com.ft.jenkins.provision

import com.ft.jenkins.EnvType
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.ParamUtils
import com.ft.jenkins.changerequests.ChangeRequestCloseData
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR

public void updateCluster(String fullClusterName, String gitBranch, String updateReason) {
  String credentialsDir = unzipTlsCredentialsForCluster(fullClusterName)
  echo "Unzipped the TLS credentials used when the cluster ${fullClusterName} was created in folder ${credentialsDir}"

  ClusterUpdateInfo updateInfo = getClusterUpdateInfo(fullClusterName)
  echo "For cluster ${fullClusterName} determined update info: ${updateInfo.toString()}"

  Environment updatedEnv = EnvsRegistry.getEnvironmentByFullName(fullClusterName)
  String crId = openChangeRequest(updateInfo, fullClusterName, updateReason, updatedEnv)

  catchError { // don't propagate error, so that we can close the CR
    echo "Starting update for cluster ${fullClusterName} ... "

    performUpdateCluster(updateInfo, credentialsDir, fullClusterName, gitBranch)
    echo "Ended update for cluster ${fullClusterName}"
  }

  closeChangeRequest(crId, updatedEnv)
}

private String openChangeRequest(ClusterUpdateInfo updateInfo, String fullClusterName, String updateReason,
                                 Environment updatedEnv) {
  /*  do not open change requests for Development environments. */
  if (updateInfo.envType == EnvType.DEVELOPMENT) {
    return null
  }

  try {
    ChangeRequestOpenData data = new ChangeRequestOpenData()
    String buildAuthor = new ParamUtils().jenkinsBuildAuthor
    data.ownerEmail = buildAuthor ? "${buildAuthor}@ft.com" : "universal.publishing.platform@ft.com"
    data.summary = "Update Kubernetes cluster ${fullClusterName}"
    data.description = updateReason
    data.environment = updateInfo.envType == EnvType.PROD ? ChangeRequestEnvironment.Production :
                       ChangeRequestEnvironment.Test
    data.notifyChannel = updatedEnv.slackChannel
    data.notify = true

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    return crUtils.open(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while opening CR for cluster ${fullClusterName} update: ${e.message} "
  }
}

private void closeChangeRequest(String crId, Environment environment) {
  if (crId == null) {
    return
  }

  try {
    ChangeRequestCloseData data = new ChangeRequestCloseData()
    data.notifyChannel = environment.slackChannel
    data.id = crId

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    crUtils.close(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while closing CR ${crId}: ${e.message} "
  }
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

    docker.image("${ProvisionConstants.DOCKER_IMAGE}:${gitBranch}").inside(dockerRunArgs) {
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