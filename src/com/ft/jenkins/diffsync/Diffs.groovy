package com.ft.jenkins.diffsync

import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.deployment.Deployments
import com.ft.jenkins.deployment.HelmConstants

static List<String> getChartsDiffVersion(List<String> charts, Map<String, String> initialVersions,
                                         Map<String, String> updatedVersions) {
  def result = []
  for (String chart : charts) {
    result.add("${chart}:${initialVersions.get(chart)}->${updatedVersions.get(chart)}")
  }
  result
}

static List<String> getChartsWithVersion(List<String> charts, Map<String, String> chartVersions) {
  List<String> result = []
  for (String chart : charts) {
    result.add("${chart}:${chartVersions.get(chart)}")
  }
  result
}

void logDiffSummary(DiffInfo diffInfo) {
  echo(""" Diff summary between source: ${diffInfo.sourceFullName()} and target ${diffInfo.targetFullName()}. 
            Modifications will be applied on target ${diffInfo.targetFullName()}
            Added charts (${diffInfo.addedCharts.size()}): ${diffInfo.addedChartsVersions()}
            Updated charts (${diffInfo.modifiedCharts.size()}): ${diffInfo.modifiedChartsVersions()}
            Removed charts (${diffInfo.removedCharts.size()}): ${diffInfo.removedChartsVersions()} 
          """)
}

DiffInfo computeDiffBetweenEnvs(Environment sourceEnv, Region sourceRegion, Environment targetEnv,
                                Region targetRegion, ClusterType cluster) {
  DiffInfo diffInfo = new DiffInfo()
  diffInfo.sourceEnv = sourceEnv
  diffInfo.targetEnv = targetEnv
  diffInfo.cluster = cluster
  diffInfo.sourceRegion = sourceRegion
  diffInfo.targetRegion = targetRegion

  diffInfo.sourceChartsVersions = getChartVersionsFromEnv(sourceEnv, cluster, sourceRegion)
  diffInfo.targetChartsVersions = getChartVersionsFromEnv(targetEnv, cluster, targetRegion)

  diffInfo.addedCharts = getAddedCharts(diffInfo.sourceChartsVersions, diffInfo.targetChartsVersions)
  diffInfo.modifiedCharts = getModifiedCharts(diffInfo.sourceChartsVersions, diffInfo.targetChartsVersions)
  diffInfo.removedCharts = getRemovedCharts(diffInfo.sourceChartsVersions, diffInfo.targetChartsVersions)

  diffInfo
}

private Map<String, String> getChartVersionsFromEnv(Environment env, ClusterType cluster, Region region) {
  Deployments deployments = new Deployments()

  String chartsOutput
  deployments.runWithK8SCliTools(env, cluster, region, {
    /*  get the chart versions from the cluster */
    chartsOutput = sh(returnStdout: true, script: "helm list --deployed --col-width=180 | awk 'NR>1 {print \$9}'")
  })

  echo "Got charts raw output from helm: ${chartsOutput}. Parsing it ..."
  def chartVersions = parseHelmChartOutputIntoMap(chartsOutput)
  chartVersions
}

/**
 * Parses the helm output of charts into a mapping between chart name and version.
 *
 * The output of helm is composed of lines like 'annotations-rw-neo4j-2.0.0-k8s-helm-migration-rc1', so the format is chartName-chartVersion
 *
 * @param chartsOutputText aggregated lines produced by 'helm list'
 * @return
 */
static Map<String, String> parseHelmChartOutputIntoMap(String chartsOutputText) {
  Map<String, String> chartsMap = new HashMap<>()

  if (!chartsOutputText?.trim()) {
    return chartsMap
  }

  String[] chartOutputLines = chartsOutputText.split("\\r?\\n")
  for (String chartOutput : chartOutputLines) {
    String chartVersion = chartOutput.find(HelmConstants.CHART_VERSION_REGEX)
    String chartName = chartOutput.substring(0, chartOutput.length() - chartVersion.length() - 1)
    chartsMap.put(chartName, chartVersion)
  }
  return chartsMap
}

private static List<String> getModifiedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  List<String> modifiedCharts = new ArrayList<>()

  sourceEnvCharts.each { String chartName, String chartVersion ->
    if (targetEnvCharts.containsKey(chartName) && chartVersion != targetEnvCharts[chartName]) {
      modifiedCharts.add(chartName)
    }
  }
  modifiedCharts
}

private static List<String> getAddedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  List<String> addedCharts = []
  sourceEnvCharts.keySet().each { String chartName ->
    if (chartName != null && !targetEnvCharts.containsKey(chartName)) {
      addedCharts.add(chartName)
    }
  }
  addedCharts
}

private static List<String> getRemovedCharts(Map<String, String> sourceEnvCharts, Map<String, String> targetEnvCharts) {
  getAddedCharts(targetEnvCharts, sourceEnvCharts)
}


