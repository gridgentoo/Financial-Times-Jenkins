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
          
          removedCharts = diffBetweenMaps(firstEnvCharts, secondEnvCharts)
          addedCharts = diffBetweenMaps(secondEnvCharts, firstEnvCharts)
          modifiedCharts = getModifiedCharts(firstEnvCharts, secondEnvCharts)
        }

        stage('select-charts-to-be-synced') {
          choosenParamsForAddedCharts = getUserInputs(addedCharts)
          choosenParamsForModifiedCharts = getUserInputs(modifiedCharts)
          choosenParamsForRemovedCharts = getUserInputs(removedCharts)
        }

        stage('sync-services') {
          updateCharts(choosenParamsForAddedCharts)
          updateCharts(choosenParamsForModifiedCharts)
          updateCharts(choosenParamsForRemovedCharts)
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

private void updateCharts(Map<String, Boolean> choosenParams) {
  echo "Syncing services started by ${choosenParams.get('approver')}"
  choosenParams.remove('approver')
  Set<String> chartsToBeSynced = choosenParams.keySet()
  for (int i = 0; i < chartsToBeSynced.size(); i++) {
    String chartToBeSynced = chartsToBeSynced.getAt(i)
    if (choosenParams.get(chartToBeSynced)) {
      echo "syncing service ${chartToBeSynced}"
    } else {
      echo "skipping service with name ${chartToBeSynced} from syncing"
    }
  }
}

private Map<String, Boolean> getUserInputs(List<String> charts) {
  List<String> checkboxesForAddedCharts = []
  for (int i = 0; i < charts.size(); i++) {
    String addedChart = charts.get(i)
    checkboxesForAddedCharts.add(booleanParam(defaultValue: false,
                                              description: "Service added",
                                              name: addedChart))
  }

  Map<String, Boolean> choosenParams = input(message: "Charts to be added",
                                             parameters: checkboxesForAddedCharts,
                                             submitterParameter: 'approver',
                                             ok: "Sync services")

  return choosenParams
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
      echo "${chartName}: updated chart: ${chartVersion} -- ${secondEnv[chartName]}"
    }
  }

  return modifiedCharts
}

private List<String> diffBetweenMaps(Map<String, String> firstEnv, Map<String, String> secondEnv) {
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
