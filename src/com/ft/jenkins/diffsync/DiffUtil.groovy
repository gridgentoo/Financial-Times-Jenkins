package com.ft.jenkins.diffsync

import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment

public void logDiffSummary(DiffInfo diffInfo) {
  echo(""" Diff summary between source: ${diffInfo.sourceEnv.name} and target ${diffInfo.targetEnv.name}. 
            Modifications will be applied on target ${diffInfo.targetEnv.name}
            Added charts (${diffInfo.addedCharts.size()}): ${diffInfo.addedChartsVersions()}
            Updated charts (${diffInfo.modifiedCharts.size()}): ${diffInfo.modifiedChartsVersions()}
            Removed charts (${diffInfo.removedCharts.size()}): ${diffInfo.removedChartsVersions()} 
          """)
}

public void computeDiffBetweenEnvs(Environment sourceEnv, Environment targetEnv, Cluster cluster) {
  DiffInfo diffInfo = new DiffInfo()
  diffInfo.sourceEnv = sourceEnv
  diffInfo.targetEnv = targetEnv
  diffInfo.cluster = cluster

  diffInfo.sourceChartsVersions = getChartVersionsFromEnv(sourceEnv, cluster)
  diffInfo.targetChartsVersions = getChartVersionsFromEnv(targetEnv, cluster)

  diffInfo.removedCharts = getRemovedCharts(diffInfo.sourceChartsVersions, diffInfo.targetChartsVersions)
  diffInfo.addedCharts = getAddedCharts(diffInfo.targetChartsVersions, diffInfo.sourceChartsVersions)
  diffInfo.modifiedCharts = getModifiedCharts(diffInfo.sourceChartsVersions, diffInfo.targetChartsVersions)
}

private Map<String, String> getChartVersionsFromEnv(Environment env, Cluster cluster) {
  DeploymentUtils deploymentUtils = new DeploymentUtils()
  String tempFile = "tmpCharts_${System.currentTimeMillis()}"

  deploymentUtils.runWithK8SCliTools(env, cluster, {
    /*  get the chart versions from the cluster */
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > ${tempFile}"
  })

  String charts = readFile(tempFile)
  return parseHelmChartOutputIntoMap(charts)
}

/**
 * Parses the helm output of charts into a mapping between chart name and version.
 *
 * The output of helm is composed of lines like 'annotations-rw-neo4j-2.0.0-k8s-helm-migration-rc1', so the format is chartName-chartVersion
 *
 * @param chartsOutputText aggregated lines produced by 'helm list'
 * @return
 */
private Map<String, String> parseHelmChartOutputIntoMap(String chartsOutputText) {
  echo "Got charts raw output from helm: ${chartsOutputText}. Parsing it ..."
  Map<String, String> chartsMap = new HashMap<>()
  String[] chartOutputLines = chartsOutputText.split("\\r?\\n")
  for (String chartOutput : chartOutputLines) {
    String chartVersion = chartOutput.find(DeploymentUtilsConstants.CHART_VERSION_REGEX)
    String chartName = chartOutput.substring(0, chartOutput.length() - chartVersion.length())
    chartsMap.put(chartName, chartVersion)
  }

  return chartsMap
}

private List<String> getModifiedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  List<String> modifiedCharts = new ArrayList<>()

  sourceEnvCharts.each { String chartName, String chartVersion ->
    if (targetEnvCharts.containsKey(chartName) && chartVersion != targetEnvCharts[chartName]) {
      modifiedCharts.add(chartName)
    }
  }

  return modifiedCharts
}

private List<String> getAddedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  List<String> removedCharts = []
  sourceEnvCharts.keySet().each { String chartName ->
    if (chartName != null && !targetEnvCharts.containsKey(chartName)) {
      removedCharts.add(chartName)
    }
  }

  return removedCharts
}

private List<String> getRemovedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  return getAddedCharts(targetEnvCharts, sourceEnvCharts)
}


