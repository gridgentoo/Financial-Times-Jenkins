package com.ft.jenkins.diffsync

class SyncInfo implements Serializable {
  List<String> selectedChartsForAdding = [], selectedChartsForUpdating = [], selectedChartsForRemoving = []
  DiffInfo diffInfo

  public String addedChartsVersions() {
    return DiffUtil.getChartsWithVersion(selectedChartsForAdding, diffInfo.sourceChartsVersions)
  }

  public String modifiedChartsVersions() {
    return DiffUtil.getChartsDiffVersion(selectedChartsForUpdating, diffInfo.targetChartsVersions, diffInfo.sourceChartsVersions)
  }

  public String removedChartsVersions() {
    DiffUtil.getChartsWithVersion(selectedChartsForRemoving, diffInfo.targetChartsVersions)
  }
}
