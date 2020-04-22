package com.ft.jenkins.diffsync

class SyncInfo implements Serializable {
  List<String> selectedChartsForAdding = [], selectedChartsForUpdating = [], selectedChartsForRemoving = []
  DiffInfo diffInfo

  String addedChartsVersions() {
    return Diffs.getChartsWithVersion(selectedChartsForAdding, diffInfo.sourceChartsVersions)
  }

  String modifiedChartsVersions() {
    return Diffs.getChartsDiffVersion(selectedChartsForUpdating, diffInfo.targetChartsVersions, diffInfo.sourceChartsVersions)
  }

  String removedChartsVersions() {
    Diffs.getChartsWithVersion(selectedChartsForRemoving, diffInfo.targetChartsVersions)
  }
}
