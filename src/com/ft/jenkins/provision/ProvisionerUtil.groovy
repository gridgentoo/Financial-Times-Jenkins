package com.ft.jenkins.provision

import com.ft.jenkins.Cluster
import com.ft.jenkins.EnvType
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.ParamUtils
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR

public void updateCluster(String fullClusterName, String gitBranch, String updateReason) {
  String credentialsDir = unzipTlsCredentialsForCluster(fullClusterName)
  echo "Unzipped the TLS credentials used when the cluster ${fullClusterName} was created in folder ${credentialsDir}"

  ClusterUpdateInfo updateInfo = getClusterUpdateInfo(fullClusterName)
  echo "For cluster ${fullClusterName} determined update info: ${updateInfo.toString()}"

  Environment updatedEnv = EnvsRegistry.getEnvironmentByFullName(fullClusterName)
  String crId = openChangeRequest(gitBranch, updateInfo, fullClusterName, updateReason, updatedEnv)

  catchError { // don't propagate error, so that we can close the CR
    echo "Starting update for cluster ${fullClusterName} ... "

    sendStartUpdateNotification(fullClusterName, updateReason)

    catchError {
      performUpdateCluster(updateInfo, credentialsDir, gitBranch)
    }
    echo "Ended update for cluster ${fullClusterName}"
    sendFinishUpdateNotification(fullClusterName, updateReason)
  }

}

private void sendStartUpdateNotification(String fullClusterName, String updateReason) {
  String buildAuthor = new ParamUtils().jenkinsBuildAuthor

  if (!buildAuthor) {
    return
  }

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Starting update of cluster '${fullClusterName}' for reason: '${updateReason}'"
  attachment.titleUrl = "${env.BUILD_URL}"

  attachment.text = """You can get a closer look at the update by watching the AWS CloudFormation Stack events.
    Login into AWS at https://awslogin.in.ft.com -> CloudFormation -> filter for stack ${fullClusterName}.
    """
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  try {
    slackUtils.sendEnhancedSlackNotification("@${buildAuthor}", attachment)
  }
  catch (e) { //  do not fail if slack notification fails
    echo "Error while sending slack notification: ${e.message}"
  }
}

private void sendFinishUpdateNotification(String fullClusterName, String updateReason) {
  String buildAuthor = new ParamUtils().jenkinsBuildAuthor

  if (!buildAuthor) {
    return
  }

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Finished update of cluster '${fullClusterName}' for reason: '${updateReason}'"
  attachment.titleUrl = "${env.BUILD_URL}"

  if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
    attachment.text = "Success"
    attachment.color = "good"
  } else {
    attachment.text = "A failure has occurred during update. Check the console logs and the CloudFormation stack events."
    attachment.color = "danger"
  }

  SlackUtils slackUtils = new SlackUtils()
  try {
    slackUtils.sendEnhancedSlackNotification("@${buildAuthor}", attachment)
  }
  catch (e) { //  do not fail if slack notification fails
    echo "Error while sending slack notification: ${e.message}"
  }
}

private String openChangeRequest(String gitBranch, ClusterUpdateInfo updateInfo, String fullClusterName, String updateReason,
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
    if (fullClusterName.contains("PAC") || fullClusterName.contains("pac")) {
      data.systemCode = "pac"
    } else  {
      data.systemCode = "upp"
    }
    //data.systemCode = "${fullClusterName}"

    data.gitTagOrCommit = "commit"
    
    data.gitReleaseTagOrCommit = checkCommitID(gitBranch)
    data.gitRepositoryName = "https://github.com/Financial-Times/content-k8s-provisioner"

    data.environment = updateInfo.envType == EnvType.PROD ? ChangeRequestEnvironment.Production :
                       ChangeRequestEnvironment.Test
    data.notifyChannel = updatedEnv.slackChannel
    data.clusterFullName = "${fullClusterName}"

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    return crUtils.open(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while opening CR for cluster ${fullClusterName} update: ${e.message} "
  }
}

private String checkCommitID(String branch) {
  git url: "https://github.com/Financial-Times/content-k8s-provisioner", credentialsId: "ft-upp-team"
  def output = sh(returnStdout: true, script: "git ls-remote git@github.com:Financial-Times/content-k8s-provisioner.git | grep refs/heads/${gitBranch} | cut -f 1")
  print "Latest commit hash of content-k8s-provisioner branch ${branch} is ${output}"
  return output
}
private void performUpdateCluster(ClusterUpdateInfo updateInfo, credentialsDir, String gitBranch) {
  GString vaultCredentialsId = "ft.k8s-provision.${updateInfo.platform}.env-type-${updateInfo.envType.shortName}.vault.pass"

  withCredentials([string(credentialsId: vaultCredentialsId, variable: 'VAULT_PASS')]) {
    wrap([$class: 'MaskPasswordsBuildWrapper']) { //  mask the password params
      String dockerRunArgs =
          "-u root " +
          "-v ${credentialsDir.trim()}:/ansible/credentials " +
          "-e 'AWS_REGION=${updateInfo.region}' " +
          "-e 'AWS_ACCESS_KEY=${env."Upp provisioning user AWS Access Key Id"}' " +
          "-e 'AWS_SECRET_ACCESS_KEY=${env."Upp provisioning user AWS Secret Access Key"}' " +
          "-e 'CLUSTER_NAME=${updateInfo.envName}' " +
          "-e 'CLUSTER_ENVIRONMENT=${updateInfo.cluster}' " +
          "-e 'ENVIRONMENT_TYPE=${updateInfo.envType.shortName}' " +
          "-e 'OIDC_ISSUER_URL=${updateInfo.oidcIssuerUrl}' " +
          "-e 'PLATFORM=${updateInfo.platform}' " +
          "-e 'VAULT_PASS=${env.VAULT_PASS}' "

      docker.image("${ProvisionConstants.DOCKER_IMAGE}:${gitBranch}").inside(dockerRunArgs) {
        sh "/update.sh"
      }
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
  if (componentsNum < 3) {
    throw new IllegalArgumentException("The full cluser name: ${clusterFullName} is incomplete")
  }

  /*  the full name looks like upp-k8s-dev-delivery-eu, and might have or not a cluster name.
      PAC doesn't have a cluster name. PAC clusters have the full name like pac-staging-eu.
      First determine if we have a cluster component */
  if (doesNameHasClusterComponent(components)) {
    info.cluster = components[componentsNum - 2]
    /*  in between sits the env name */
    info.envName = components[1..componentsNum - 3].join("-")
  } else {
    info.cluster = ""
    /*  in between sits the env name */
    info.envName = components[1..componentsNum - 2].join("-")

  }
  info.platform = components[0]
  info.region = components[componentsNum - 1]

  info.envType = Environment.getEnvTypeForName(info.envName)
  info.oidcIssuerUrl = "https://${clusterFullName}-dex.ft.com"

  return info
}

private boolean doesNameHasClusterComponent(String[] components) {
  String possibleClusterComponent = components[components.length - 2]
  Cluster determinedCluster = Cluster.valueOfLabel(possibleClusterComponent)
  return determinedCluster != null
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