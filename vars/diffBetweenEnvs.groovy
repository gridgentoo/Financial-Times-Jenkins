import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.diffsync.DiffInfo
import com.ft.jenkins.diffsync.DiffUtil
import com.ft.jenkins.diffsync.SyncInfo
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

import static com.ft.jenkins.DeploymentUtilsConstants.APPROVER_INPUT

def call(String firstEnvName, String secondEnvName, String clusterName) {
  echo "Diff between envs with params: [envToSyncFrom: ${firstEnvName}, envToBeSynced: ${secondEnvName}, cluster: ${clusterName}]"
  Environment sourceEnv = EnvsRegistry.getEnvironment(firstEnvName)
  Environment targetEnv = EnvsRegistry.getEnvironment(secondEnvName)
  Cluster cluster = Cluster.valueOfLabel(clusterName)

  DiffInfo diffInfo
  SyncInfo syncInfo
  DiffUtil diffUtil = new DiffUtil()

  node('docker') {
    catchError {
      timeout(60) { //  timeout after 30 mins to not block jenkins

        stage('Compute diff between envs') {
          echo "Diff the clusters"
          diffInfo = diffUtil.computeDiffBetweenEnvs(sourceEnv, targetEnv, cluster)
          diffUtil.logDiffSummary(diffInfo)
          sendSlackMessageForDiffSummary(diffInfo)
        }

        if (diffInfo.areEnvsInSync()) {
          return  // do not continue if diff is empty.
        }

        stage('Select charts to be added') {
          if (diffInfo.addedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForAdding = getSelectedUserInputs(diffInfo.addedCharts, diffInfo,
                                                                   "Services to be added in ${targetEnv.name}",
                                                                   "Add services to ${targetEnv.name}")
          echo "The following charts were selected for adding: ${syncInfo.selectedChartsForAdding}"
        }

        stage('Select charts to be updated') {
          if (diffInfo.modifiedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForUpdating = getSelectedUserInputs(diffInfo.modifiedCharts, diffInfo,
                                                                     "Services to be updated in ${targetEnv.name}",
                                                                     "Update services in ${targetEnv.name}")
          echo "The following charts were selected for updating: ${syncInfo.selectedChartsForUpdating}"
        }

        stage('Select charts to be deleted') {
          if (diffInfo.removedCharts.isEmpty()) {
            return; //  don't do anything at this stage
          }

          syncInfo.selectedChartsForRemoving = getSelectedUserInputs(diffInfo.removedCharts, diffInfo,
                                                                     "Services to be removed from ${targetEnv.name}",
                                                                     "Remove the services from ${targetEnv.name}")
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
        } else {
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
                                           String okButton) {
  List checkboxes = []
  for (int i = 0; i < charts.size(); i++) {
    String chartName = charts.get(i)
    String checkboxDescription = getCheckboxDescription(diffInfo.targetChartsVersions.get(chartName),
                                                        diffInfo.sourceChartsVersions.get(chartName))
    checkboxes.add(booleanParam(defaultValue: false,
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
  return selectedValues
}

private String getCheckboxDescription(String oldChartVersion, String newChartVersion) {
  if (newChartVersion == null) {
    return ""
  }

  if (oldChartVersion == null) {
    return "New version: ${newChartVersion}"
  }

  return "Old version: ${oldChartVersion}, new version: ${newChartVersion}"
}

String extractApprover(Map<String, Object> userInputs) {
  String approver = userInputs.get(APPROVER_INPUT)
  userInputs.remove(APPROVER_INPUT)
  return approver
}

void installSelectedCharts(List<String> selectedCharts, DiffInfo diffInfo) {
  for (String selectedChart : selectedCharts) {
    /*  trigger the generic job for deployment */
    String version = diffInfo.sourceChartsVersions.get(selectedChart)
    echo "Installing chart ${selectedChart}:${version} in ${diffInfo.targetEnv.getFullClusterName(diffInfo.cluster)} "

    build job: DeploymentUtilsConstants.GENERIC_DEPLOY_JOB,
          parameters: [
              string(name: 'Chart', value: selectedChart),
              string(name: 'Version', value: version),
              string(name: 'Environment', value: diffInfo.targetEnv.name),
              string(name: 'Cluster', value: diffInfo.cluster.label),
              string(name: 'Region', value: 'all'),
              booleanParam(name: 'Send success notifications', value: false)]
  }

}

void removeSelectedCharts(List<String> selectedCharts, DiffInfo diffInfo) {
  DeploymentUtils deploymentUtils = new DeploymentUtils()
  for (String selectedChart : selectedCharts) {
    String chartVersion = diffInfo.targetChartsVersions.get(selectedChart)
    deploymentUtils.removeAppsInChartWithHelm(selectedChart, chartVersion, diffInfo.targetEnv, diffInfo.cluster)
  }
}

void sendSyncSuccessNotification(DiffInfo diffInfo, SyncInfo syncInfo) {
  if (diffInfo.areEnvsInSync()) {
    return; //  if envs are in sync nothing to do here
  }

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${diffInfo.targetEnv.name} from ${diffInfo.sourceEnv.name} done"
  attachment.titleUrl = "${env.BUILD_URL}"

  attachment.text = """ 
Sync summary between source: `${diffInfo.sourceEnv.name}` and target `${diffInfo.targetEnv.name}`. 
Modifications were applied on target `${diffInfo.targetEnv.name}`
Selected added charts (${syncInfo.selectedChartsForAdding.size()}): ${diffInfo.addedChartsVersions()}
Selected updated charts (${syncInfo.selectedChartsForUpdating.size()}): ${diffInfo.modifiedChartsVersions()}
Selected removed charts (${syncInfo.getSelectedChartsForRemoving().size()}): ${diffInfo.removedChartsVersions()} 
"""
  attachment.color = "good"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(diffInfo.targetEnv.slackChannel, attachment)
}

void sendSyncFailureNotification(Environment sourceEnv, Environment targetEnv) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${targetEnv.name} from ${sourceEnv.name} failed !!!. Click for logs"
  attachment.titleUrl = "${env.BUILD_URL}/console"

  attachment.color = "danger"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

void sendSlackMessageForDiffSummary(DiffInfo diffInfo) {
  if (diffInfo.areEnvsInSync()) {
    return;
  }

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: select charts for syncing in ${diffInfo.targetEnv.name} from ${diffInfo.sourceEnv.name}"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = """
Diff summary between source: `${diffInfo.sourceEnv.name}` and target `${diffInfo.targetEnv.name}`. 
Modifications will be applied on target `${diffInfo.targetEnv.name}`
Added charts (${diffInfo.addedCharts.size()}): ${diffInfo.addedChartsVersions()}
Updated charts (${diffInfo.modifiedCharts.size()}): ${diffInfo.modifiedChartsVersions()}
Removed charts (${diffInfo.removedCharts.size()}): ${diffInfo.removedChartsVersions()} 
"""
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(diffInfo.targetEnv.slackChannel, attachment)
}
