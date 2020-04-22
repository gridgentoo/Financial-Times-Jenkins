package com.ft.jenkins.diffsync

import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region

class DiffInfo implements Serializable {
  Environment sourceEnv, targetEnv
  Region sourceRegion, targetRegion

  Map<String, String> sourceChartsVersions, targetChartsVersions
  List<String> addedCharts, modifiedCharts, removedCharts
  ClusterType cluster

  boolean areEnvsInSync() {
    addedCharts.isEmpty() && modifiedCharts.isEmpty() && removedCharts.isEmpty()
  }

  String addedChartsVersions() {
    Diffs.getChartsWithVersion(addedCharts, sourceChartsVersions)
  }

  String modifiedChartsVersions() {
    Diffs.getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)
  }

  String removedChartsVersions() {
    Diffs.getChartsWithVersion(removedCharts, targetChartsVersions)
  }

  String targetFullName() {
    targetEnv.getFullClusterName(cluster, targetRegion)
  }

  String sourceFullName() {
    sourceEnv.getFullClusterName(cluster, sourceRegion)
  }
}
