import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.slack.SlackAttachment
import com.ft.jenkins.slack.SlackUtils

import static com.ft.jenkins.DeploymentUtilsConstants.APPROVER_INPUT

def call(String firstEnvName, String secondEnvName, String clusterName) {
  echo "Diff between envs with params: [envToSyncFrom: ${firstEnvName}, envToBeSynced: ${secondEnvName}, cluster: ${clusterName}]"
  Environment sourceEnv = EnvsRegistry.getEnvironment(firstEnvName)
  Environment targetEnv = EnvsRegistry.getEnvironment(secondEnvName)
  Cluster cluster = Cluster.valueOfLabel(clusterName)

  Map<String, String> sourceChartsVersions, targetChartsVersions
  List<String> addedCharts, modifiedCharts, removedCharts
  List<String> selectedChartsForAdding, selectedChartsForUpdating, selectedChartsForRemoving
  String addApprover, updateApprover, removalApprover

  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins

        stage('Compute diff between envs') {
          echo "Diff the clusters"
          sourceChartsVersions = getChartsFromEnv(sourceEnv, cluster)
          targetChartsVersions = getChartsFromEnv(targetEnv, cluster)

          removedCharts = getRemovedCharts(sourceChartsVersions, targetChartsVersions)
          addedCharts = getAddedCharts(targetChartsVersions, sourceChartsVersions)
          modifiedCharts = getModifiedCharts(sourceChartsVersions, targetChartsVersions)

          logDiffSummary(sourceEnv, targetEnv, sourceChartsVersions, targetChartsVersions, addedCharts, modifiedCharts,
                         removedCharts)

          sendSlackMessageForSyncSummary(sourceEnv, targetEnv, sourceChartsVersions, targetChartsVersions, addedCharts,
                                         modifiedCharts, removedCharts)
        }

        stage('Select charts to be added') {
          Map<String, Object> inputsForAdding =
              getUserInputs(addedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be added in ${targetEnv.name}",
                            "Add services to ${targetEnv.name}")
          addApprover = extractApprover(inputsForAdding)
          selectedChartsForAdding = getSelectedValues(inputsForAdding)
          echo "The following charts were selected for adding: ${selectedChartsForAdding}"
        }

        stage('Select charts to be updated') {
          Map<String, Object> inputsForUpdating =
              getUserInputs(modifiedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be updated in ${targetEnv.name}",
                            "Update services in ${targetEnv.name}")
          updateApprover = extractApprover(inputsForUpdating)
          selectedChartsForUpdating = getSelectedValues(inputsForUpdating)
          echo "The following charts were selected for updating: ${selectedChartsForUpdating}"
        }

        stage('Select charts to be deleted') {
          Map<String, Object> inputsForRemoving =
              getUserInputs(removedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be removed from ${targetEnv.name}",
                            "Remove the services from ${targetEnv.name}")
          removalApprover = extractApprover(inputsForRemoving)
          selectedChartsForRemoving = getSelectedValues(inputsForRemoving)
          echo "The following charts were selected for removing: ${selectedChartsForRemoving}"
        }

        echo "Starting sync between envs ...."

        //  todo [SB] do things in parallel
        stage('Install added charts') {
          installSelectedCharts(selectedChartsForAdding, sourceChartsVersions, targetEnv, cluster)
        }

        stage('Install updated charts') {
          installSelectedCharts(selectedChartsForUpdating, sourceChartsVersions, targetEnv, cluster)
        }

        stage('Uninstall removed charts') {
          removeSelectedCharts(selectedChartsForRemoving, targetChartsVersions, targetEnv, cluster)
        }
      }
    }

    catchError {
      sendNotifications(sourceEnv, targetEnv, sourceChartsVersions, targetChartsVersions, selectedChartsForAdding,
                        selectedChartsForUpdating, selectedChartsForRemoving)
    }

    stage("cleanup") {
      cleanWs()
    }
  }
}


private Map<String, Object> getUserInputs(List<String> charts, Map<String, String> sourceVersions,
                                          Map<String, String> targetVersions, String inputMessage, String okButton) {
  List checkboxes = []
  for (int i = 0; i < charts.size(); i++) {
    String chartName = charts.get(i)
    String checkboxDescription = getCheckboxDescription(targetVersions.get(chartName), sourceVersions.get(chartName))
    checkboxes.add(booleanParam(defaultValue: false,
                                description: checkboxDescription,
                                name: chartName))
  }

  if (checkboxes.isEmpty()) {
    return new HashMap<>()
  }

  return input(message: inputMessage,
               parameters: checkboxes,
               submitterParameter: APPROVER_INPUT,
               ok: okButton) as Map<String, Object>
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

private Map<String, String> getChartsFromEnv(Environment env, Cluster cluster) {
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

String extractApprover(Map<String, Object> userInputs) {
  String approver = userInputs.get(APPROVER_INPUT)
  userInputs.remove(APPROVER_INPUT)
  return approver
}

void installSelectedCharts(List<String> selectedCharts, Map<String, String> sourceChatsVersions,
                           Environment targetEnv, Cluster cluster) {
  for (String selectedChart : selectedCharts) {
    /*  trigger the generic job for deployment */
    String version = sourceChatsVersions.get(selectedChart)
    echo "Installing chart ${selectedChart}:${version} in ${targetEnv.getFullClusterName(cluster)} "

    build job: DeploymentUtilsConstants.GENERIC_DEPLOY_JOB,
          parameters: [
              string(name: 'Chart', value: selectedChart),
              string(name: 'Version', value: version),
              string(name: 'Environment', value: targetEnv.name),
              string(name: 'Cluster', value: cluster.label),
              string(name: 'Region', value: 'all'),
              booleanParam(name: 'Send success notifications', value: false)]
  }

}

void removeSelectedCharts(List<String> selectedCharts, Map<String, String> targetChartsVersions, Environment targetEnv,
                          Cluster cluster) {
  DeploymentUtils deploymentUtils = new DeploymentUtils()
  for (String selectedChart : selectedCharts) {
    String chartVersion = targetChartsVersions.get(selectedChart)
    deploymentUtils.removeAppsInChartWithHelm(selectedChart, chartVersion, targetEnv, cluster)
  }
}

private void logDiffSummary(Environment sourceEnv, Environment targetEnv, Map<String, String> sourceChartsVersions,
                            Map<String, String> targetChartsVersions, List<String> addedCharts,
                            List<String> modifiedCharts, List<String> removedCharts) {
  echo(""" Diff summary between source: ${sourceEnv.name} and target ${targetEnv.name}. 
            Modifications will be applied on target ${targetEnv.name}
            Added charts (${addedCharts.size()}): ${getChartsWithVersion(addedCharts, sourceChartsVersions)}
            Updated charts (${modifiedCharts.size()}): ${getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)}
            Removed charts (${removedCharts.size()}): ${getChartsWithVersion(removedCharts, targetChartsVersions)} 
          """)
}

void sendNotifications(Environment sourceEnv, Environment targetEnv,
                                    Map<String, String> sourceChartsVersions, Map<String, String> targetChartsVersions,
                                    List<String> addedCharts, List<String> modifiedCharts, List<String> removedCharts) {
  stage("notifications") {
    if (currentBuild.resultIsBetterOrEqualTo("SUCCESS")) {
      sendSyncSuccessNotification(sourceEnv, targetEnv, sourceChartsVersions, targetChartsVersions, addedCharts,
                                  modifiedCharts, removedCharts)

    } else {
      //  todo [sb] enable notifications
//      sendSyncFailureNotification(sourceEnv, targetEnv)
    }
  }
}

void sendSyncSuccessNotification(Environment sourceEnv, Environment targetEnv,
                       Map<String, String> sourceChartsVersions, Map<String, String> targetChartsVersions,
                       List<String> addedCharts, List<String> modifiedCharts, List<String> removedCharts) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${targetEnv.name} from ${sourceEnv.name} done"
  attachment.titleUrl = "${env.BUILD_URL}"

  attachment.text =
      """ Sync summary between source: `${sourceEnv.name}` and target `${targetEnv.name}`. 
            Modifications were applied on target `${targetEnv.name}`
            Selected added charts (${addedCharts.size()}): ${getChartsWithVersion(addedCharts, sourceChartsVersions)}
            Selected updated charts (${modifiedCharts.size()}): ${getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)}
            Selected removed charts (${removedCharts.size()}): ${getChartsWithVersion(removedCharts, targetChartsVersions)} 
          """
  attachment.color = "good"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

void sendSyncFailureNotification(Environment sourceEnv, Environment targetEnv) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Sync in ${targetEnv.name} from ${sourceEnv.name} failed !!!. Click for logs"
  attachment.titleUrl = "${env.BUILD_URL}/console"

  attachment.color = "danger"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

void sendSlackMessageForSyncSummary(Environment sourceEnv, Environment targetEnv,
                                    Map<String, String> sourceChartsVersions, Map<String, String> targetChartsVersions,
                                    List<String> addedCharts, List<String> modifiedCharts, List<String> removedCharts) {
  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: select charts for syncing in ${targetEnv.name} from ${sourceEnv.name}"
  attachment.titleUrl = "${env.BUILD_URL}input"

  attachment.text =
      """ Diff summary between source: `${sourceEnv.name}` and target `${targetEnv.name}`. 
            Modifications will be applied on target `${targetEnv.name}`
            Added charts (${addedCharts.size()}): ${getChartsWithVersion(addedCharts, sourceChartsVersions)}
            Updated charts (${modifiedCharts.size()}): ${getChartsDiffVersion(modifiedCharts, targetChartsVersions, sourceChartsVersions)}
            Removed charts (${removedCharts.size()}): ${getChartsWithVersion(removedCharts, targetChartsVersions)} 
          """
  attachment.color = "warning"

  SlackUtils slackUtils = new SlackUtils()
  slackUtils.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
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

