import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry

import static com.ft.jenkins.DeploymentUtilsConstants.APPROVER_INPUT

def call(String firstEnvName, String secondEnvName, String clusterName) {
  echo "Diff between envs with params: [envToSyncFrom: ${firstEnvName}, envToBeSynced: ${secondEnvName}, cluster: ${clusterName}]"
  Environment sourceEnv = EnvsRegistry.getEnvironment(firstEnvName)
  Environment targetEnv = EnvsRegistry.getEnvironment(secondEnvName)
  Cluster cluster = clusterName.toUpperCase()

  Map<String, String> sourceChartsVersions
  Map<String, String> targetChartsVersions
  List<String> addedCharts
  List<String> modifiedCharts
  List<String> removedCharts
  Map<String, Object> inputsForAdding, inputsForUpdating, inputsForRemoving
  String addApprover
  String updateApprover
  String removalApprover

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
        }

        stage('Select charts to be added') {
          inputsForAdding =
              getUserInputs(addedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be added in ${targetEnv.name}",
                            "Add services to ${targetEnv.name}")
          addApprover = extractApprover(inputsForAdding)

        }

        stage('Select charts to be updated') {
          inputsForUpdating =
              getUserInputs(modifiedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be updated in ${targetEnv.name}",
                            "Update services in ${targetEnv.name}")
          updateApprover = extractApprover(inputsForUpdating)
        }

        stage('Select charts to be deleted') {
          inputsForRemoving =
              getUserInputs(removedCharts, sourceChartsVersions, targetChartsVersions,
                            "Services to be removed from ${targetEnv.name}",
                            "Remove the services from ${targetEnv.name}")
          removalApprover = extractApprover(inputsForRemoving)
        }

        stage('Install added charts') {
          installSelectedCharts(getSelectedValues(inputsForAdding), sourceChartsVersions, targetEnv, cluster)
        }

        stage('Install updated charts') {
          installSelectedCharts(getSelectedValues(inputsForUpdating), sourceChartsVersions, targetEnv, cluster)
        }

        stage('Uninstall removed charts') {
          removeSelectedCharts(getSelectedValues(inputsForRemoving))
        }




      }
    }

    catchError {
      echo "An error occurred in the pipeline."
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
    echo "Installing chart ${selectedChart}:${version} in ${targetEnv.name}"

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

void removeSelectedCharts(List<String> selectedCharts, Environment targetEnv, Cluster cluster) {
  for (String selectedChart : selectedCharts) {

  }
}