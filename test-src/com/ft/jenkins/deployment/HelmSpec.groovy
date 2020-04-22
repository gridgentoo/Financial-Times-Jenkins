package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.Cluster
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region
import spock.lang.Specification

import static com.ft.jenkins.cluster.EnvClusterMapEntry.newEksEntry
import static com.ft.jenkins.cluster.EnvClusterMapEntry.newEntry
import static com.ft.jenkins.cluster.Environment.DEV_NAME
import static com.ft.jenkins.cluster.Environment.STAGING_NAME
import static com.ft.jenkins.deployment.HelmConstants.*

class HelmSpec extends Specification {
  def "should generate correct helm v3 commands when helm version is explicitly set"(HelmCommand helmCommand, Map params, GString expectedCommand) {
    when:
    def actualCommand = Helm.generateCommand(helmCommand, params, HelmVersion.V3)
    expectedCommand = "${HELM_CLI_TOOL} ${expectedCommand}"
    then:
    actualCommand == expectedCommand
    where:
    helmCommand                   | params                                                                                    | expectedCommand
    HelmCommand.FETCH             | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v3FetchCommand(params)
    HelmCommand.PACKAGE           | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v3PackageCommand(params)
    HelmCommand.DELETE            | [(CHART_RELEASE_NAME_COMMAND_PARAM): "draft-content-api"]                                 | HelmCommand.v3DeleteCommand(params)
    HelmCommand.UPDATE_DEPENDENCY | [(CHART_NAME_COMMAND_PARAM): "draft-content-api"]                                         | HelmCommand.v3UpdateDependencyCommand(params)

    // without TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'"
    ]                                                                                                                         | HelmCommand.v3UpgradeCommand(params)

    // with TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_ENV_NAMESPACE_PARAM)             : "alabala",
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'"
    ]                                                                                                                         | HelmCommand.v3UpgradeCommand(params)

    HelmCommand.ADD_REPO          | [:]                                                                                       | HelmCommand.v3AddRepoCommand(params)
    HelmCommand.UPDATE_REPO_INDEX | [:]                                                                                       | HelmCommand.v3UpdateRepoIndexCommand()
  }

  def "should generate correct helm v2 commands when helm version is explicitly set"(HelmCommand helmCommand, Map params, GString expectedCommand) {
    when:
    def actualCommand = Helm.generateCommand(helmCommand, params, HelmVersion.V2)
    expectedCommand = "${HELM_CLI_TOOL} ${expectedCommand}"
    then:
    actualCommand == expectedCommand
    where:
    helmCommand                   | params                                                                                    | expectedCommand
    HelmCommand.FETCH             | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v2FetchCommand(params)
    HelmCommand.PACKAGE           | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v2PackageCommand(params)
    HelmCommand.DELETE            | [(CHART_RELEASE_NAME_COMMAND_PARAM): "draft-content-api"]                                 | HelmCommand.v2DeleteCommand(params)
    HelmCommand.UPDATE_DEPENDENCY | [(CHART_NAME_COMMAND_PARAM): "draft-content-api"]                                         | HelmCommand.v2UpdateDependencyCommand(params)

    // without TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'"
    ]                                                                                                                         | HelmCommand.v2UpgradeCommand(params)

    // with TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_ENV_NAMESPACE_PARAM)             : "alabala",
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'",

    ]                                                                                                                         | HelmCommand.v2UpgradeCommand(params)

    HelmCommand.ADD_REPO          | [:]                                                                                       | HelmCommand.v2AddRepoCommand(params)
    HelmCommand.UPDATE_REPO_INDEX | [:]                                                                                       | HelmCommand.v2UpdateRepoIndexCommand()
  }

  def "should generate correct helm v3 commands based on environment and region"(HelmCommand helmCommand, Map params, GString expectedCommand) {
    given:
    Cluster cluster = new Cluster(ClusterType.PUBLISHING)
    Environment env = new Environment(STAGING_NAME, cluster)
    env.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PUBLISHING}".toString()): newEksEntry(
                      eksClusterName: "eks-publishing-staging-eu",
                      apiServer: "https://447d575a05454241fa65efd66af1bf48.sk1.eu-west-1.eks.amazonaws.com",
                      publicEndpoint: "https://upp-staging-publish-eu.upp.ft.com"
              )
      ]
    }
    when:
    def actualCommand = Helm.generateCommand(helmCommand, params, env, Region.EU)
    expectedCommand = "${HELM_CLI_TOOL} ${expectedCommand}"
    then:
    actualCommand == expectedCommand
    where:
    helmCommand                   | params                                                                                    | expectedCommand
    HelmCommand.FETCH             | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v3FetchCommand(params)
    HelmCommand.PACKAGE           | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v3PackageCommand(params)
    HelmCommand.DELETE            | [(CHART_RELEASE_NAME_COMMAND_PARAM): "draft-content-api"]                                 | HelmCommand.v3DeleteCommand(params)
    HelmCommand.UPDATE_DEPENDENCY | [(CHART_NAME_COMMAND_PARAM): "draft-content-api"]                                         | HelmCommand.v3UpdateDependencyCommand(params)

    // without TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'"
    ]                                                                                                                         | HelmCommand.v3UpgradeCommand(params)

    // with TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_ENV_NAMESPACE_PARAM)             : "alabala",
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'",

    ]                                                                                                                         | HelmCommand.v3UpgradeCommand(params)

    HelmCommand.ADD_REPO          | [:]                                                                                       | HelmCommand.v3AddRepoCommand(params)
    HelmCommand.UPDATE_REPO_INDEX | [:]                                                                                       | HelmCommand.v3UpdateRepoIndexCommand()
  }

  def "should generate correct helm v2 commands based on environment and region"(HelmCommand helmCommand, Map params, GString expectedCommand) {
    given:
    Cluster cluster = new Cluster(ClusterType.DELIVERY)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
    }
    when:
    def actualCommand = Helm.generateCommand(helmCommand, params, env, Region.EU)
    expectedCommand = "${HELM_CLI_TOOL} ${expectedCommand}"
    then:
    actualCommand == expectedCommand
    where:
    helmCommand                   | params                                                                                    | expectedCommand
    HelmCommand.FETCH             | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v2FetchCommand(params)
    HelmCommand.PACKAGE           | [(CHART_NAME_COMMAND_PARAM): "draft-content-api", (CHART_VERSION_COMMAND_PARAM): "1.0.0"] | HelmCommand.v2PackageCommand(params)
    HelmCommand.DELETE            | [(CHART_RELEASE_NAME_COMMAND_PARAM): "draft-content-api"]                                 | HelmCommand.v2DeleteCommand(params)
    HelmCommand.UPDATE_DEPENDENCY | [(CHART_NAME_COMMAND_PARAM): "draft-content-api"]                                         | HelmCommand.v2UpdateDependencyCommand(params)

    // without TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'"
    ]                                                                                                                         | HelmCommand.v2UpgradeCommand(params)

    // with TARGET_ENV_NAMESPACE_PARAM
    HelmCommand.UPGRADE           | [(CHART_NAME_COMMAND_PARAM)               : "draft-content-api",
                                     (CHART_RELEASE_NAME_COMMAND_PARAM)       : "draft-content-api",
                                     (VALUES_FILE_PATH_COMMAND_PARAM)         : "draft-content-api/app-configs/draft-content-api_pac.yaml",
                                     (REGION_COMMAND_PARAM)                   : "eu",
                                     (TARGET_ENV_COMMAND_PARAM)               : STAGING_NAME,
                                     (TARGET_ENV_NAMESPACE_PARAM)             : "alabala",
                                     (TARGET_CLUSTER_SUB_DOMAIN_COMMAND_PARAM): "pac-staging-eu",
                                     (CLUSTER_URLS_COMMAND_PARAM)             : "--set 'cluster.pac.url=https://pac-staging-eu.upp.ft.com'",
                                     (GLB_URLS_COMMAND_PARAM)                 : "--set 'glb.publishing.url=https://upp-staging-publish-glb.upp.ft.com' --set 'glb.delivery.url=https://upp-staging-delivery-glb.upp.ft.com'",

    ]                                                                                                                         | HelmCommand.v2UpgradeCommand(params)

    HelmCommand.ADD_REPO          | [:]                                                                                       | HelmCommand.v2AddRepoCommand(params)
    HelmCommand.UPDATE_REPO_INDEX | [:]                                                                                       | HelmCommand.v2UpdateRepoIndexCommand()
  }
}
