package com.ft.jenkins.diffsync

import com.ft.jenkins.Cluster
import com.ft.jenkins.Environment

class DiffInfo implements Serializable {
  Environment sourceEnv, targetEnv
  Map<String, String> sourceChartsVersions, targetChartsVersions
  List<String> addedCharts, modifiedCharts, removedCharts
  Cluster cluster

  public boolean areEnvsInSync() {
    return addedCharts.isEmpty() && modifiedCharts.isEmpty() && removedCharts.isEmpty()
  }

  public String addedChartsVersions() {
    return getChartsWithVersion(addedCharts, sourceChartsVersions)
  }

  public String modifiedChartsVersions() {
    return getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)
  }

  public String removedChartsVersions() {
    getChartsWithVersion(removedCharts, targetChartsVersions)
  }

  public List<String> getChartsWithVersion(List<String> charts, Map<String, String> chartVersions) {
    List<String> result = []
    for (String chart : charts) {
      result.add("${chart}:${chartVersions.get(chart)}")
    }
    return result
  }

  public List<String> getChartsDiffVersion(List<String> charts, Map<String, String> initialVersions, Map<String, String> updatedVersions) {
    List<String> result = []
    for (String chart : charts) {
      result.add("${chart}:${initialVersions.get(chart)}->${updatedVersions.get(chart)}")
    }
    return result
  }

}