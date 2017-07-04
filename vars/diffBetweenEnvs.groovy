import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry

def call(String firstEnvName, String secondEnvName, String clusterName) {
  echo "Diff between envs with params: [envToSyncFrom: ${firstEnvName}, envToBeSynced: ${secondEnvName}, cluster: ${clusterName}]"
  Environment envToSyncFrom = EnvsRegistry.getEnvironment(firstEnvName)
  Environment envToBeSynced = EnvsRegistry.getEnvironment(secondEnvName)
  Cluster cluster = new Cluster(clusterName)

  Map<String, String> outdatedServices
  def selectedServicesToBeSynced

  node('') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins

        stage('diff-envs') {
          outdatedServices = doDiff(envToSyncFrom, envToBeSynced, cluster)
        }

        stage('select-services-to-be-synced') {
          String syncServicesMessage = "Added services"
          def syncChoices = []
          Set<String> servicesToBeSynced = outdatedServices.keySet()
          for (int i = 0; i < servicesToBeSynced.size(); i++) {
            syncChoices[i] = booleanParam(defaultValue: false,
                                          description: '',
                                          name: servicesToBeSynced.getAt(i))
          }

          selectedServicesToBeSynced = input(message: syncServicesMessage,
                                             parameters: syncChoices,
                                             ok: "Sync services")
          echo "selectedServicesToBeSynced: ${selectedServicesToBeSynced}"
        }

        stage('sync-services') {
          for (int i = 0; i < selectedServicesToBeSynced.length; i++) {
            echo "syncing service ${selectedServicesToBeSynced[i]}"
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
  }
}

public Map<String, String> doDiff(Environment firstEnv, Environment secondEnv, Cluster cluster) {
  echo "Diff the clusters."
  Map<String, String> firstEnvCharts, secondEnvCharts
  DeploymentUtils deploymentUtils = new DeploymentUtils()
  deploymentUtils.runWithK8SCliTools(firstEnv, cluster, {
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > tmpCharts"
    String charts = readFile 'tmpCharts'
    firstEnvCharts = parseChartsIntoMap(charts)
  })

  deploymentUtils.runWithK8SCliTools(secondEnv, cluster, {
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > tmpCharts"
    String charts = readFile 'tmpCharts'
    secondEnvCharts = parseChartsIntoMap(charts)
  })

  getRemovedServices(firstEnvCharts, secondEnvCharts)
  return getModifiedServices(firstEnvCharts, secondEnvCharts)

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

private Map<String, String> getModifiedServices(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  Set<String> firstEnvCharts = firstEnv.keySet()
  Map<String, String> modifiedCharts = new HashMap<>()
  //todo: delete me
  secondEnv.put("delivery-varnish", "1.2.23")
  for (int i = 0; i < firstEnvCharts.size(); i++) {
    String k = firstEnvCharts[i]
    String v = firstEnv.get(k)
    if (!secondEnv.containsKey(k)) {
      modifiedCharts.put(k, "Service added")
      println "${k}: service added"
    }

    if (v != secondEnv[k]) {
      modifiedCharts.put(k, "diff between versions: ${v} -- ${secondEnv[k]}")
      println "${k}: diff between versions: ${v} -- ${secondEnv[k]}"
    }
  }

  return modifiedCharts
}

private void getRemovedServices(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  Set<String> secondEnvCharts = secondEnv.keySet()
  for (int i = 0; i <= secondEnvCharts.size(); i++) {
    String k = secondEnvCharts[i]
    if (!firstEnv.containsKey(k)) {
      println "${k} service removed"
    }
  }
}
