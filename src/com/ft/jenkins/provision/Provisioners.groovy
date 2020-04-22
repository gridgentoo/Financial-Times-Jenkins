package com.ft.jenkins.provision

import com.ft.jenkins.JenkinsParams
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.EnvType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.EnvsRegistry
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.git.GitHelper
import com.ft.jenkins.slack.Slack
import com.ft.jenkins.slack.SlackAttachment

import static com.ft.jenkins.deployment.DeploymentsConstants.CREDENTIALS_DIR

void updateCluster(String fullClusterName, String gitBranch, String updateReason) {
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
  String buildAuthor = new JenkinsParams().jenkinsBuildAuthor
  if (!buildAuthor) {
    return
  }
  SlackAttachment attachment = buildSlackAttachmentTitleInfo(fullClusterName, updateReason)
  attachment.text = """You can get a closer look at the update by watching the AWS CloudFormation Stack events.
    Login into AWS at https://awslogin.in.ft.com -> CloudFormation -> filter for stack ${fullClusterName}.
    """
  attachment.color = "warning"

  Slack slack = new Slack()
  try {
    slack.sendEnhancedSlackNotification("@${buildAuthor}", attachment)
  }
  catch (e) { //  do not fail if slack notification fails
    echo "Error while sending slack notification: ${e.message}"
  }
}

private void sendFinishUpdateNotification(String fullClusterName, String updateReason) {
  String buildAuthor = new JenkinsParams().jenkinsBuildAuthor
  if (!buildAuthor) {
    return
  }
  SlackAttachment attachment = buildSlackAttachmentTitleInfo(fullClusterName, updateReason)
  if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
    attachment.text = "Success"
    attachment.color = "good"
  } else {
    attachment.text = "A failure has occurred during update. Check the console logs and the CloudFormation stack events."
    attachment.color = "danger"
  }

  Slack slack = new Slack()
  try {
    slack.sendEnhancedSlackNotification("@${buildAuthor}", attachment)
  }
  catch (e) { //  do not fail if slack notification fails
    echo "Error while sending slack notification: ${e.message}"
  }
}

private SlackAttachment buildSlackAttachmentTitleInfo(String fullClusterName, String updateReason) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Finished update of cluster '${fullClusterName}' for reason: '${updateReason}'"
  attachment.titleUrl = "${env.BUILD_URL}"
  attachment
}

private String openChangeRequest(String gitBranch, ClusterUpdateInfo updateInfo, String fullClusterName, String updateReason,
                                 Environment updatedEnv) {
  /*  do not open change requests for Development environments. */
  if (updateInfo.envType == EnvType.DEVELOPMENT) {
    return null
  }

  try {
    ChangeRequestOpenData data = new ChangeRequestOpenData()
    String buildAuthor = new JenkinsParams().jenkinsBuildAuthor
    data.ownerEmail = buildAuthor ? "${buildAuthor}@ft.com" : "universal.publishing.platform@ft.com"
    data.summary = "Update Kubernetes cluster ${fullClusterName}"
    if (fullClusterName.contains("PAC") || fullClusterName.contains("pac")) {
      data.systemCode = "pac"
    } else {
      data.systemCode = "upp"
    }

    data.gitTagOrCommitType = "commit"

    data.gitReleaseTagOrCommit = new GitHelper().getGithubReleaseInfo(gitBranch, "content-k8s-provisioner").latestCommit
    data.gitRepositoryName = ProvisionersConstants.REPO_URL

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
                      "-e 'CLUSTER_ENVIRONMENT=${updateInfo.clusterType}' " +
                      "-e 'ENVIRONMENT_TYPE=${updateInfo.envType.shortName}' " +
                      "-e 'OIDC_ISSUER_URL=${updateInfo.oidcIssuerUrl}' " +
                      "-e 'PLATFORM=${updateInfo.platform}' " +
                      "-e 'VAULT_PASS=${env.VAULT_PASS}' "

      docker.image("${ProvisionersConstants.DOCKER_IMAGE}:${gitBranch}").inside(dockerRunArgs) {
        sh "/update.sh"
      }
    }
  }
}

static ClusterUpdateInfo getClusterUpdateInfo(String clusterFullName) {
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
    info.clusterType = ClusterType.toClusterType(components[componentsNum - 2])
    /*  in between sits the env name */
    info.envName = components[1..componentsNum - 3].join("-")
  } else {
    info.clusterType = ClusterType.UNKNOWN
    /*  in between sits the env name */
    info.envName = components[1..componentsNum - 2].join("-")
  }
  info.platform = components[0]
  info.region = Region.toRegion(components[componentsNum - 1])

  info.envType = Environment.getEnvTypeForName(info.envName)
  info.oidcIssuerUrl = "https://${clusterFullName}-dex.upp.ft.com"

  return info
}

private static boolean doesNameHasClusterComponent(String[] components) {
  String possibleClusterComponent = components[components.length - 2]
  ClusterType determinedCluster = ClusterType.toClusterType(possibleClusterComponent)
  determinedCluster != null
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
