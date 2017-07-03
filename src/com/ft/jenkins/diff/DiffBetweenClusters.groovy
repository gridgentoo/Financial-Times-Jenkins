package com.ft.jenkins.diff

import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment

public void steps(Environment firstEnv, Environment secondEnv, Cluster cluster) {
  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins

        stage('diff-envs') {
          doDiff(firstEnv, secondEnv, cluster)
        }

        stage('select-services-to-be-synced') {
          String syncServicesMessage = "Added services"
          String choices = "Sync \n Don't sync"
          String approver = input(message: syncServicesMessage,
                                  ok: "Deploy to test")
          for (int i=0; i<=10; i++) {
            def syncChoice = choice(choices: choices,
                                    description: '',
                                    name: "serviceName")
          }
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

public void doDiff(Environment firstEnv, Environment secondEnv, Cluster cluster) {
  echo "Diff the clusters."
  Map<String, String> firstEnvCharts, secondEnvCharts
  DeploymentUtils.runWithK8SCliTools(firstEnv, cluster, {
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > tmpCharts"
    String charts = readFile 'tmpCharts'
    firstEnvCharts = parseChartsIntoMap(charts)
  })

  DeploymentUtils.runWithK8SCliTools(secondEnv, cluster, {
    sh "helm list --deployed | awk 'NR>1 {print \$9}' > tmpCharts"
    String charts = readFile 'tmpCharts'
    secondEnvCharts = parseChartsIntoMap(charts)
  })
}

private Map<String, String> parseChartsIntoMap(String charts) {
  Map<String, String> chartsMap = new HashMap<>()
  charts.eachLine { chart ->
    String chartVersion = chart.find(DiffBetweenClustersConstants.CHART_VERSION_REGEX)
    String chartName = chart.replace(chartVersion, "")
    chartName = chartName.substring(0, chartName.length() - 1)
    chartsMap.put(chartName, chartVersion)
    return chartsMap
  }
}

private void difference(Map<String, String> firstEnv, Map<String, String> secondEnv) {
  firstEnv.each { k, v ->
    if (!secondEnv.containsKey(k)) {
      println "${k}: service added"
      return
    }

    if (v != secondEnv[k]) {
      println "${k}: diff between versions: ${v} -- ${secondEnv[k]}"
      return
    }
  }

  secondEnv.each { k, v ->
    if (!firstEnv.containsKey(k)) {
      println "${k} service removed"
    }
  }
}
