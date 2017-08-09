package com.ft.jenkins.diffsync

import com.ft.jenkins.Cluster
import com.ft.jenkins.Environment

class DiffInfo implements Serializable {
  Environment sourceEnv, targetEnv
  String sourceRegion, targetRegion

  Map<String, String> sourceChartsVersions, targetChartsVersions
  List<String> addedCharts, modifiedCharts, removedCharts
  Cluster cluster

  public boolean areEnvsInSync() {
    return addedCharts.isEmpty() && modifiedCharts.isEmpty() && removedCharts.isEmpty()
  }

  public String addedChartsVersions() {
    return DiffUtil.getChartsWithVersion(addedCharts, sourceChartsVersions)
  }

  public String modifiedChartsVersions() {
    return DiffUtil.getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)
  }

  public String removedChartsVersions() {
    DiffUtil.getChartsWithVersion(removedCharts, targetChartsVersions)
  }

  public String targetFullName() {
    targetEnv.getFullClusterName(cluster, targetRegion)
  }

  public String sourceFullName() {
    sourceEnv.getFullClusterName(cluster, sourceRegion)
  }


}