package com.ft.jenkins.deployment


import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region

static String generateCommand(HelmCommand helmCommand, Map params, Environment env, Region region) {
  def helmVersion = HelmVersion.discoverVersion(env, region)
  def cmd = generateCommand(helmCommand, params, helmVersion)
  cmd
}

static String generateCommand(HelmCommand helmCommand, Map params, HelmVersion helmVersion) {
  if (helmVersion == HelmVersion.V3) {
    return "${HelmConstants.HELM_CLI_TOOL} ${helmCommand.command.generateV3(params)}".toString()
  }
  return "${HelmConstants.HELM_CLI_TOOL} ${helmCommand.command.generateV2(params)}".toString()
}
