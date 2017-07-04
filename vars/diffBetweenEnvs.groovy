import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.diff.DiffBetweenClustersConstants

def call() {
  Environment firstEnv = EnvsRegistry.getEnvironment("k8s")
  Environment secondEnv = EnvsRegistry.getEnvironment("k8s")
  Cluster cluster = Cluster.DELIVERY
  Map<String,String> servicesToBeSyncedMap
  node('') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins

        stage('diff-envs') {
          servicesToBeSyncedMap = doDiff(firstEnv, secondEnv, cluster)
        }

        stage('select-services-to-be-synced') {
          String syncServicesMessage = "Added services"
          def syncChoices = []
          //todo: delete me
          echo "teeest: ${servicesToBeSyncedMap}"
          Set<String> servicesToBeSynced = servicesToBeSyncedMap.keySet()
          for (int i = 0; i < servicesToBeSynced.size(); i++) {
            syncChoices[i] = booleanParam(defaultValue: false,
                                          description: '',
                                          name: servicesToBeSynced.getAt(i))
          }

          String[] servicesToSync = input(message: syncServicesMessage,
                                        parameters: syncChoices,
                                        ok: "Sync services")
          echo "servicesToSync: ${servicesToSync}"
        }

        stage('sync-services') {

        }

        catchError {
          echo "An error occurred in the pipeline."
        }
      }
    }
  }
}

public Map<String,String> doDiff(Environment firstEnv, Environment secondEnv, Cluster cluster) {
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
    String chartVersion = chart.find(DiffBetweenClustersConstants.CHART_VERSION_REGEX)
    String chartName = chart.replace(chartVersion, "")
    chartName = chartName.substring(0, chartName.length() - 1)
    chartsMap.put(chartName, chartVersion)
  }

  return chartsMap
}

private Map<String, String> getModifiedServices(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  Set<String> firstEnvCharts = firstEnv.keySet()
  Map<String, String> modifiedCharts = new HashMap<>()
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
