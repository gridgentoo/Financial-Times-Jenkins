package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.Environment

import static com.ft.jenkins.deployment.HelmConstants.*

enum HelmCommand implements HelmCommandGenerator {
  FETCH(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3FetchCommand(params) }

    GString generateV2(Map params) { v2FetchCommand(params) }
  }),
  DELETE(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3DeleteCommand(params) }

    GString generateV2(Map params) { v2DeleteCommand(params) }
  }),
  PACKAGE(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3PackageCommand(params) }

    GString generateV2(Map params) { v2PackageCommand(params) }
  }),
  UPDATE_REPO_INDEX(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3UpdateRepoIndexCommand() }

    GString generateV2(Map params) { v2UpdateRepoIndexCommand() }
  }),
  INIT(new HelmCommandGenerator() {
    GString generateV3(Map params) { "" as GString }

    GString generateV2(Map params) { v2InitCommand() }
  }),
  ADD_REPO(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3AddRepoCommand(params) }

    GString generateV2(Map params) { v2AddRepoCommand(params) }
  }),
  UPDATE_DEPENDENCY(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3UpdateDependencyCommand(params) }

    GString generateV2(Map params) { v2UpdateDependencyCommand(params) }
  }),
  UPGRADE(new HelmCommandGenerator() {
    GString generateV3(Map params) { v3UpgradeCommand(params) }

    GString generateV2(Map params) { v2UpgradeCommand(params) }
  })

  HelmCommandGenerator command

  HelmCommand(HelmCommandGenerator command) {
    this.command = command
  }

  static GString v2UpgradeCommand(Map params) {
    "upgrade ${params[CHART_RELEASE_NAME_COMMAND_PARAM]} ${params[CHART_NAME_COMMAND_PARAM]} " +
            "--values ${params[VALUES_FILE_PATH_COMMAND_PARAM]} " +
            "--set region=${params[REGION_COMMAND_PARAM]} " +
            "--set __ext.target_cluster.sub_domain=${params[TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM]} " +
            "--set target_env=${params[TARGET_ENV_COMMAND_PARAM]} " +
            "--timeout 1200 --install " +
            "${params[CLUSTER_URLS_COMMAND_PARAM]} " +
            "${params[GLB_URLS_COMMAND_PARAM]}"
  }

  static GString v3UpgradeCommand(Map params) {
    "upgrade ${params[CHART_RELEASE_NAME_COMMAND_PARAM]} ${params[CHART_NAME_COMMAND_PARAM]} " +
            // set namespace to "default when non in explicitly set"
            "--namespace ${params[TARGET_ENV_NAMESPACE_PARAM] ?: Environment.DEFAULT_KUBE_NAMESPACE} " +
            "--values ${params[VALUES_FILE_PATH_COMMAND_PARAM]} " +
            "--set region=${params[REGION_COMMAND_PARAM]} " +
            "--set __ext.target_cluster.sub_domain=${params[TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM]} " +
            "--set target_env=${params[TARGET_ENV_COMMAND_PARAM]} " +
            "--timeout 20m --install " +
            "${params[CLUSTER_URLS_COMMAND_PARAM]} " +
            "${params[GLB_URLS_COMMAND_PARAM]}"
  }

  static GString v2UpdateDependencyCommand(Map params) {
    "dependency update ${params[CHART_NAME_COMMAND_PARAM]}"
  }

  static GString v3UpdateDependencyCommand(Map params) {
    "dependency update ${params[CHART_NAME_COMMAND_PARAM]}"
  }

  static GString v2AddRepoCommand(Map params) {
    if (params.isEmpty()) {
      params = [(LOCAL_REPO_NAME_PARAM): HELM_LOCAL_REPO_NAME, (REPO_URL_PARAM): HELM_REPO_URL]
    }
    "repo add ${params[LOCAL_REPO_NAME_PARAM]} ${params[REPO_URL_PARAM]}"
  }

  static GString v3AddRepoCommand(Map params) {
    if (params.isEmpty()) {
      params = [(LOCAL_REPO_NAME_PARAM): HELM_LOCAL_REPO_NAME, (REPO_URL_PARAM): HELM_REPO_URL]
    }
    "repo add ${params[LOCAL_REPO_NAME_PARAM]} ${params[REPO_URL_PARAM]}"
  }

  static GString v2InitCommand() {
    "init -c" as GString
  }

  static GString v2UpdateRepoIndexCommand() {
    "repo index --merge ${HELM_REPO_INDEX_FILE_PATH} --url ${HELM_REPO_URL} ."
  }

  static GString v3UpdateRepoIndexCommand() {
    "repo index --merge ${HELM_REPO_INDEX_FILE_PATH} --url ${HELM_REPO_URL} ."
  }

  static GString v2PackageCommand(Map params) {
    "package --version ${params[CHART_VERSION_COMMAND_PARAM]} ${HELM_CONFIG_FOLDER}/${params[CHART_NAME_COMMAND_PARAM]}"
  }

  static GString v3PackageCommand(Map params) {
    "package --version ${params[CHART_VERSION_COMMAND_PARAM]} ${HELM_CONFIG_FOLDER}/${params[CHART_NAME_COMMAND_PARAM]}"
  }

  static GString v2DeleteCommand(Map params) {
    "delete ${params[CHART_RELEASE_NAME_COMMAND_PARAM]}"
  }

  static GString v3DeleteCommand(Map params) {
    "uninstall ${params[CHART_RELEASE_NAME_COMMAND_PARAM]}"
  }

  static GString v2FetchCommand(Map params) {
    "fetch --untar ${HELM_LOCAL_REPO_NAME}/${params[CHART_NAME_COMMAND_PARAM]} " +
            "--version ${params[CHART_VERSION_COMMAND_PARAM]}"
  }

  static GString v3FetchCommand(Map params) {
    "fetch --untar ${HELM_LOCAL_REPO_NAME}/${params[CHART_NAME_COMMAND_PARAM]} " +
            "--version ${params[CHART_VERSION_COMMAND_PARAM]}"
  }

  @Override
  GString generateV3(Map params) {
    throw new UnsupportedOperationException()
  }

  @Override
  GString generateV2(Map params) {
    throw new UnsupportedOperationException()
  }
}
