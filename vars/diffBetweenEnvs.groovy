import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry

def call(String firstEnvName, String secondEnvName, String clusterName) {
  echo "Diff between envs with params: [envToSyncFrom: ${firstEnvName}, envToBeSynced: ${secondEnvName}, cluster: ${clusterName}]"
  Environment envToSyncFrom = EnvsRegistry.getEnvironment(firstEnvName)
  Environment envToBeSynced = EnvsRegistry.getEnvironment(secondEnvName)
  Cluster cluster = clusterName.toUpperCase()

  Map<String, String> firstEnvCharts
  Map<String, String> secondEnvCharts
  List<String> addedCharts
  List<String> modifiedCharts
  List<String> removedCharts
  Map<String, Boolean> choosenParamsForAddedCharts, choosenParamsForModifiedCharts, choosenParamsForRemovedCharts

  node('') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins

        stage('diff-envs') {
          echo "Diff the clusters."
          firstEnvCharts = getChartsFromEnv(envToSyncFrom, cluster)
          secondEnvCharts = getChartsFromEnv(envToBeSynced, cluster)
          
          removedCharts = diffBetweenEnvs(firstEnvCharts, secondEnvCharts)
          addedCharts = diffBetweenEnvs(secondEnvCharts, firstEnvCharts)
          modifiedCharts = getModifiedCharts(firstEnvCharts, secondEnvCharts)
        }

        stage('select-charts-to-be-synced') {
          choosenParamsForAddedCharts = getUserInputs(addedCharts, firstEnvCharts, secondEnvCharts, "Services to be added")
          choosenParamsForModifiedCharts = getUserInputs(modifiedCharts, firstEnvCharts, secondEnvCharts, "Services to be modified")
          choosenParamsForRemovedCharts = getUserInputs(removedCharts, firstEnvCharts, secondEnvCharts, "Services to be removed")
        }

        stage('sync-services') {
          updateCharts(choosenParamsForAddedCharts, HelmAction.CREATE)
          updateCharts(choosenParamsForModifiedCharts, HelmAction.UPDATE)
          updateCharts(choosenParamsForRemovedCharts, HelmAction.DELETE)
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

private void updateCharts(Map<String, Boolean> choosenParams, HelmAction helmAction) {
  echo "Syncing services started by ${choosenParams.get('approver')}"
  choosenParams.remove('approver')
  if (choosenParams.size() == 0) {
    echo "There are no charts for helm action ${helmAction}"
  }

  Set<String> chartsToBeSynced = choosenParams.keySet()
  for (int i = 0; i < chartsToBeSynced.size(); i++) {
    String chartToBeSynced = chartsToBeSynced.getAt(i)
    if (choosenParams.get(chartToBeSynced)) {
      echo "syncing service ${chartToBeSynced}. Performing helm action: ${helmAction}"
    } else {
      echo "skipping service with name ${chartToBeSynced} from syncing"
    }
  }
}

private Map<String, Boolean> getUserInputs(List<String> charts, Map<String, String> firstEnvCharts,
                                           Map<String, String> secondEnvCharts, String inputMessage) {
  List<String> checkboxes = []
  for (int i = 0; i < charts.size(); i++) {
    String chartName = charts.get(i)
    String checkboxDescription = getCheckboxDescription(secondEnvCharts.get(chartName), firstEnvCharts.get(chartName))
    checkboxes.add(booleanParam(defaultValue: false,
                                description: checkboxDescription,
                                name: chartName))
  }

  Map<String, Boolean> choosenParams
  if (checkboxes.isEmpty()) {
    return new HashMap<>()
  }

  choosenParams = input(message: inputMessage,
                        parameters: checkboxes,
                        submitterParameter: 'approver',
                        ok: "Sync services")

  return choosenParams
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
  Map<String, String> envCharts
  DeploymentUtils deploymentUtils = new DeploymentUtils()
  deploymentUtils.runWithK8SCliTools(env, cluster, {
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > tmpCharts"
    String charts = readFile 'tmpCharts'
    envCharts = parseChartsIntoMap(charts)
  })

  return envCharts
}

private Map<String, String> parseChartsIntoMap(String charts) {
  Map<String, String> chartsMap = new HashMap<>()
  String[] chartsArray = charts.split("\\r?\\n")
  for (int i = 0; i < chartsArray.length; i++) {
    String chart = chartsArray[i]
    String chartVersion = chart.find(DeploymentUtilsConstants.CHART_VERSION_REGEX)
    String chartName = chart.replace(chartVersion, "")
    chartName = chartName.substring(0, chartName.length() - 1)
    chartsMap.put(chartName, chartVersion)
  }

  return chartsMap
}

private List<String> getModifiedCharts(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  Set<String> firstEnvCharts = firstEnv.keySet()
  List<String> modifiedCharts = new ArrayList<>()

  for (int i = 0; i < firstEnvCharts.size(); i++) {
    String chartName = firstEnvCharts[i]
    String chartVersion = firstEnv.get(chartName)

    if (secondEnv[chartName] != null && chartVersion != secondEnv[chartName]) {
      modifiedCharts.add(chartName)
    }
  }

  return modifiedCharts
}

private List<String> diffBetweenEnvs(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  Set<String> secondEnvCharts = secondEnv.keySet()
  List<String> removedCharts = []
  for (int i = 0; i <= secondEnvCharts.size(); i++) {
    String chartName = secondEnvCharts[i]
    if (chartName != null && !firstEnv.containsKey(chartName)) {
      removedCharts.add(chartName)
    }
  }

  return removedCharts
}

enum HelmAction {
  CREATE, UPDATE, DELETE
}
