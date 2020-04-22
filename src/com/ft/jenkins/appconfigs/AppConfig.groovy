package com.ft.jenkins.appconfigs

import com.cloudbees.groovy.cps.NonCPS
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.EnvClusterMapEntry
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region

class AppConfig implements Serializable {
  String appName
  String origConfigFileName
  ClusterType clusterType
  Environment environment
  Region region
  AppConfig child
  boolean withAliasedClusterType
  boolean isEks
  boolean isInvalidClusterType
  boolean isInvalidEnvironment
  boolean isInvalidRegion
  boolean ignoredDeployment
  String ignoreCauseMessage

  @NonCPS
  String toConfigFileName() {
    String appConfigName = ""

    if (appName) {
      appConfigName += appName
    }

    if (isEks) {
      appConfigName += "_eks"
    }

    if (clusterType != ClusterType.UNKNOWN) {
      // looking for configuration file for all envs, e.g app_name-publishing
      String label = withAliasedClusterType ? clusterType.alias : clusterType.label
      appConfigName += "_${label}"
    } else if (isInvalidClusterType) {
      appConfigName += "_${ClusterType.UNKNOWN.label}"
    }

    // looking for configuration file for a specific env, e.g. app_name-publishing-prod
    if (environment) {
      appConfigName += "_${environment.name}"
    } else if (isInvalidEnvironment) {
      appConfigName += "_${Environment.UNKNOWN_ENV}"
    }

    // if region is specified looking for configuration file for a specific env for specific region, e.g. app_name-publishing-prod_us
    if (region) {
      appConfigName += "_${region.name}"
    } else if (isInvalidRegion) {
      appConfigName += "_${Region.UNKNOWN.name}"
    }

    appConfigName
  }

  Map shouldBeIgnoredForDeployment(Environment targetEnv, ClusterType clusterType, Region region) {
    Environment currentEnv = environment ?: targetEnv
    EnvClusterMapEntry clusterMapEntry = currentEnv.getClusterMapEntry(clusterType, region)
    boolean environmentIsNotOnEks = !clusterMapEntry?.isEks
    boolean environmentIsOnEks = clusterMapEntry?.isEks
    String configFileName = toConfigFileName()
    Map result
    if (isEks && environmentIsNotOnEks) {
      ignoredDeployment = true
      ignoreCauseMessage = "No Available EKS cluster"
      result = [
              ignored: true,
              message: "Ignoring ${configFileName}, because there is currently no deployed EKS cluster on this environment..."
      ]
    } else if (!isEks && environmentIsOnEks) {
      ignoredDeployment = true
      ignoreCauseMessage = "No available EKS app config"
      result = [
              ignored: true,
              message: "Ignoring ${configFileName}, because there is currently no available EKS app config to be deployed on this EKS environment..."
      ]
    } else {
      ignoredDeployment = false
      result = [
              ignored: false,
              message: "Proceeding with deployment of ${configFileName}..."
      ]
    }
    result
  }
}
