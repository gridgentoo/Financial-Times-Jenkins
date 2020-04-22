import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.EnvsRegistry
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.deployment.Deployments
import com.ft.jenkins.deployment.DeploymentsConstants
import com.ft.jenkins.diffsync.DiffInfo
import com.ft.jenkins.diffsync.Diffs
import com.ft.jenkins.diffsync.SyncInfo
import com.ft.jenkins.slack.Slack
import com.ft.jenkins.slack.SlackAttachment

import static com.ft.jenkins.deployment.DeploymentsConstants.APPROVER_INPUT

def call() {
  String sourceEnvName = env."Source env"
  Region sourceRegion = Region.toRegion(env."Source region")
  String targetEnvName = env."Target env"
  Region targetRegion = Region.toRegion(env."Target region")
  String clusterName = env."Cluster"
  boolean selectInputsByDefault = Boolean.valueOf(env."Select all by default")

  echo "Diff between envs with params: [sourceEnv: ${sourceEnvName}, sourceRegion: ${sourceRegion.name}, targetEnv: ${targetEnvName}, targetRegion: ${targetRegion.name}, cluster: ${clusterName}]"
  Environment sourceEnv = EnvsRegistry.getEnvironment(clusterName, sourceEnvName)
  Environment targetEnv = EnvsRegistry.getEnvironment(clusterName, targetEnvName)
  if (!sourceRegion) {
    sourceRegion = null
  }

  if (!targetRegion) {
    targetRegion = null
  }

  ClusterType cluster = ClusterType.toClusterType(clusterName)

  currentBuild.displayName = "${sourceEnv.getFullClusterName(cluster, sourceRegion)} -> ${targetEnv.getFullClusterName(cluster, targetRegion)}"

  DiffInfo diffInfo
  SyncInfo syncInfo = new SyncInfo()
  Diffs diffUtil = new Diffs()

  node('docker') {
    catchError {
      timeout(60) { //  timeout after 30 mins to not block jenkins

        stage('Compute diff between envs') {
          echo "Diff the clusters"
          diffInfo = diffUtil.computeDiffBetweenEnvs(sourceEnv, sourceRegion, targetEnv, targetRegion, cluster)
          diffUtil.logDiffSummary(diffInfo)
          sendSlackNotificationOnDiff(diffInfo)
        }

        if (diffInfo.areEnvsInSync()) {
          return  // do not continue if diff is empty.
        }

        syncInfo.diffInfo = diffInfo
        stage('Select charts to be added') {
          if (diffInfo.addedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForAdding = getSelectedUserInputs(diffInfo.addedCharts, diffInfo,
                  "Services to be added in ${diffInfo.targetFullName()}",
                  "Add services to ${diffInfo.targetFullName()}", selectInputsByDefault)
          echo "The following charts were selected for adding: ${syncInfo.selectedChartsForAdding}"
        }

        stage('Select charts to be updated') {
          if (diffInfo.modifiedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForUpdating = getSelectedUserInputs(diffInfo.modifiedCharts, diffInfo,
                  "Services to be updated in ${diffInfo.targetFullName()}",
                  "Update services in ${diffInfo.targetFullName()}", selectInputsByDefault)
          echo "The following charts were selected for updating: ${syncInfo.selectedChartsForUpdating}"
        }

        stage('Select charts to be deleted') {
          if (diffInfo.removedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForRemoving = getSelectedUserInputs(diffInfo.removedCharts, diffInfo,
                  "Services to be removed from ${diffInfo.targetFullName()}",
                  "Remove the services from ${diffInfo.targetFullName()}", selectInputsByDefault)
          echo "The following charts were selected for removing: ${syncInfo.selectedChartsForRemoving}"
        }


        echo "Starting sync between envs ...."

        //  todo [SB] do things in parallel
        stage('Install added charts') {
          installSelectedCharts(syncInfo.selectedChartsForAdding, diffInfo)
        }

        stage('Install updated charts') {
          installSelectedCharts(syncInfo.selectedChartsForUpdating, diffInfo)
        }

        stage('Uninstall removed charts') {
          removeSelectedCharts(syncInfo.selectedChartsForRemoving, diffInfo)
        }
      }
    }

    catchError {
      stage("notifications") {
        if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
          sendSyncSuccessNotification(diffInfo, syncInfo)
        } else if (currentBuild.currentResult != "ABORTED") {
          sendSyncFailureNotification(sourceEnv, targetEnv)
        }
      }
    }

    stage("cleanup") {
      cleanWs()
    }
  }
}

private List<String> getSelectedUserInputs(List<String> charts, DiffInfo diffInfo, String inputMessage,
                                           String okButton, boolean selectInputsByDefault) {
  List checkboxes = []
  charts.sort()
  for (int i = 0; i < charts.size(); i++) {
    String chartName = charts.get(i)
    String checkboxDescription = getCheckboxDescription(diffInfo.targetChartsVersions.get(chartName),
            diffInfo.sourceChartsVersions.get(chartName))
    checkboxes.add(booleanParam(defaultValue: selectInputsByDefault,
            description: checkboxDescription,
            name: chartName))
  }

  if (checkboxes.isEmpty()) {
    return []
  }

  /*  adding also the approver, although we're extracting it afterwards, as if only one input is given, the input method
      will return a single object, and not a map, which would be inconvenient */
  Map<String, Object> rawUserInputs = input(message: inputMessage,
          parameters: checkboxes,
          submitterParameter: APPROVER_INPUT,
          ok: okButton) as Map<String, Object>
  extractApprover(rawUserInputs)

  return getSelectedValues(rawUserInputs)
}

private List<String> getSelectedValues(Map<String, Object> userInputs) {
  List<String> selectedValues = []
  userInputs.each { String value, Boolean isSelected ->
    if (isSelected) {
      selectedValues.add(value)
    }
  }
  selectedValues
}

private static String getCheckboxDescription(String oldChartVersion, String newChartVersion) {
  if (newChartVersion == null) {
    return ""
  }

  if (oldChartVersion == null) {
    return "New version: ${newChartVersion}"
  }

  return "Old version: ${oldChartVersion}, new version: ${newChartVersion}"
}

static String extractApprover(Map<String, Object> userInputs) {
  String approver = userInputs.get(APPROVER_INPUT)
  userInputs.remove(APPROVER_INPUT)
  approver
}

void installSelectedCharts(List<String> selectedCharts, DiffInfo diffInfo) {
  for (String selectedChart : selectedCharts) {
    /*  trigger the generic job for deployment */
    String version = diffInfo.sourceChartsVersions.get(selectedChart)
    echo "Installing chart ${selectedChart}:${version} in ${diffInfo.targetFullName()} "

    build job: DeploymentsConstants.GENERIC_DEPLOY_JOB,
            parameters: [
                    string(name: 'Chart', value: selectedChart),
                    string(name: 'Version', value: version),
                    string(name: 'Environment', value: diffInfo.targetEnv.name),
                    string(name: 'Cluster', value: diffInfo.cluster.label),
                    string(name: 'Region', value: diffInfo.targetRegion ? diffInfo.targetRegion : "all"),
                    booleanParam(name: 'Send success notifications', value: false)]
  }

}

void removeSelectedCharts(List<String> selectedCharts, DiffInfo diffInfo) {
  Deployments deployments = new Deployments()
  for (String selectedChart : selectedCharts) {
    String chartVersion = diffInfo.targetChartsVersions.get(selectedChart)
    deployments.removeAppsInChartWithHelm(selectedChart, chartVersion, diffInfo.targetEnv, diffInfo.cluster)
  }
}

void sendSyncSuccessNotification(DiffInfo diffInfo, SyncInfo syncInfo) {
  if (diffInfo.areEnvsInSync()) {
    return; //  if envs are in sync nothing to do here
  }

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${diffInfo.targetFullName()} from ${diffInfo.sourceFullName()} done"
  attachment.titleUrl = "${env.BUILD_URL}"

  attachment.text = """ 
Sync summary between source: `${diffInfo.sourceFullName()}` and target `${diffInfo.targetFullName()}`. 
Modifications were applied on target `${diffInfo.targetFullName()}`
1. Selected added charts (${syncInfo.selectedChartsForAdding.size()}): ${syncInfo.addedChartsVersions()}
2. Selected updated charts (${syncInfo.selectedChartsForUpdating.size()}): ${syncInfo.modifiedChartsVersions()}
3. Selected removed charts (${syncInfo.getSelectedChartsForRemoving().size()}): ${syncInfo.removedChartsVersions()} 
"""
  attachment.color = "good"

  Slack slackUtils = new Slack()
  slackUtils.sendEnhancedSlackNotification(diffInfo.targetEnv.slackChannel, attachment)
}

void sendSyncFailureNotification(Environment sourceEnv, Environment targetEnv) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${targetEnv.name} from ${sourceEnv.name} failed !!!. Click for logs"
  attachment.titleUrl = "${env.BUILD_URL}/console"

  attachment.color = "danger"

  Slack slack = new Slack()
  slack.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

void sendSlackNotificationOnDiff(DiffInfo diffInfo) {
  if (diffInfo.areEnvsInSync()) {
    sendSlackNotificationForEnvsInSync(diffInfo)
  } else {
    sendSlackNotificationForDiffSummary(diffInfo)
  }
}

void sendSlackNotificationForDiffSummary(DiffInfo diffInfo) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: select charts for syncing from ${diffInfo.sourceFullName()} in ${diffInfo.targetFullName()}"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = """
Diff summary between source: `${diffInfo.sourceFullName()}` and target `${diffInfo.targetFullName()}`. 
Modifications will be applied on target `${diffInfo.targetFullName()}`
1. Added charts (${diffInfo.addedCharts.size()}): ${diffInfo.addedChartsVersions()}
2. Updated charts (${diffInfo.modifiedCharts.size()}): ${diffInfo.modifiedChartsVersions()}
3. Removed charts (${diffInfo.removedCharts.size()}): ${diffInfo.removedChartsVersions()} 
"""
  attachment.color = "warning"

  Slack slackUtils = new Slack()
  slackUtils.sendEnhancedSlackNotification(diffInfo.targetEnv.slackChannel, attachment)
}

void sendSlackNotificationForEnvsInSync(DiffInfo diffInfo) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "The environments ${diffInfo.sourceFullName()} and ${diffInfo.targetFullName()} are in sync"
  attachment.text = """
The environments : `${diffInfo.sourceFullName()}` and target `${diffInfo.targetFullName()}` do not have differences. 
Nothing to sync. 
"""
  attachment.color = "good"

  Slack slack = new Slack()
  slack.sendEnhancedSlackNotification(diffInfo.targetEnv.slackChannel, attachment)
}
