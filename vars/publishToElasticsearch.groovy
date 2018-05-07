import com.ft.jenkins.*

def call() {
  ParamUtils paramUtils = new ParamUtils()
  String environmentInput = paramUtils.getRequiredParameterValue("Environment")
  String regionInput = paramUtils.getRequiredParameterValue("Region")
  String cmsInput = paramUtils.getRequiredParameterValue("CMS").replace('"', '')
  String indexInput = paramUtils.getRequiredParameterValue("Index")
  String zapIndexInput = paramUtils.getRequiredParameterValue("Zap index")

  Environment targetEnv = computeTargetEnvironment(environmentInput)
  DeploymentUtils deployUtil = new DeploymentUtils()

  String UUIDS_FILE_PATH = "uuids.txt"
  String INDEX_ZAPPER_APP_NAME = "elasticsearch-index-zapper"
  String GET_MONGO_CONTAINER_CMD = "kubectl get pods | grep mongodb | awk '{print \$1}' | head -1"
  String JQ_ALTERNATIVE_CMD = "sed '/uuid/!d' | sed s/\\\"uuid\\\"://g | sed s/\\\"//g | sed s/\\ //g | sed -e 's/[{}]//g' | awk -v RS=':' '{print \$1}'"

  String GET_METHODE_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); connection=db.getMongo(); connection.setReadPref(\"secondaryPreferred\"); connection.getDB(\"upp-store\").content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FTCOM-METHODE/ },\"type\": {\$nin: [\"ContentPackage\",\"Content\",\"ImageSet\"]}}, {_id: false, uuid: 1}).forEach(function(o) { print(o.uuid)})' --quiet > ${UUIDS_FILE_PATH}"
  String GET_WORDPRESS_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); connection=db.getMongo(); connection.setReadPref(\"secondaryPreferred\"); connection.getDB(\"upp-store\").content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FT-LABS/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | ${JQ_ALTERNATIVE_CMD} > ${UUIDS_FILE_PATH}"
  String GET_VIDEO_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); connection=db.getMongo(); connection.setReadPref(\"secondaryPreferred\"); connection.getDB(\"upp-store\").content.find({\"identifiers.authority\": {\$regex: /NEXT-VIDEO-EDITOR/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | ${JQ_ALTERNATIVE_CMD} > ${UUIDS_FILE_PATH}"

  String AWS_ACCESS_KEY
  String AWS_SECRET_KEY
  String ES_ENDPOINT
  String AUTH_USER
  String AUTH_PASSWORD

  Closure GET_CONFIGURATION = {
    AWS_ACCESS_KEY = sh(
            script: "kubectl get secret global-secrets -o yaml | grep aws.access_key_id | head -1 | awk '{print \$2}' | base64 -d",
            returnStdout: true
    ).trim()
    AWS_SECRET_KEY = sh(
            script: "kubectl get secret global-secrets -o yaml | grep aws.secret_access_key | head -1 | awk '{print \$2}' | base64 -d",
            returnStdout: true
    ).trim()
    ES_ENDPOINT = sh(
            script: "kubectl get configmap global-config -o yaml | grep aws.content.elasticsearch.endpoint | awk '{print \$2}'",
            returnStdout: true
    ).trim()
  }

  Closure GET_VARNISH_AUTH = {
    AUTH_USER = sh(
            script: "kubectl get secret varnish-auth -o yaml | grep htpasswd | head -1 | awk '{print \$2}' | base64 -d | grep ops- | cut -d':' -f1",
            returnStdout: true
    ).trim()
    AUTH_PASSWORD = sh(
            script: "kubectl get secret varnish-auth -o yaml | grep htpasswd | head -1 | awk '{print \$2}' | base64 -d | grep ops- | cut -d':' -f2",
            returnStdout: true
    ).trim()
  }

  Closure GET_METHODE_UUIDS = {
    sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_METHODE_UUIDS_CMD}"
  }
  Closure GET_WORDPRESS_UUIDS = {
    sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_WORDPRESS_UUIDS_CMD}"
  }
  Closure GET_VIDEO_UUIDS = {
    sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_VIDEO_UUIDS_CMD}"
  }

  Closure CALL_ENDPOINT_HITTER = {
    sh "go get -u github.com/Financial-Times/endpoint-hitter && " +
            "endpoint-hitter --target-url=${getDeliveryClusterUrl(environmentInput, regionInput)}/__post-publication-combiner/{uuid} --auth-user=${AUTH_USER} --auth-password=${AUTH_PASSWORD}"
  }

  node('docker') {
    catchError {
      stage('Disable content-rw-elasticsearch') {
        if (zapIndexInput == "true") {
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, stopElasticsearchReadWriter() as Closure)
          sleep(5) // Wait 5s for content-rw-elasticsearch to be disabled
        }
      }

      stage('Delete ES index') {
        if (zapIndexInput == "true") {
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_CONFIGURATION)
          runGo(deleteIndex(INDEX_ZAPPER_APP_NAME, AWS_ACCESS_KEY, AWS_SECRET_KEY, ES_ENDPOINT, indexInput) as Closure)
        } else {
          echo 'Skipping ES index zapping'
        }
      }

      stage('Enable content-rw-elasticsearch') {
        if (zapIndexInput == "true") {
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, startElasticsearchReadWriter() as Closure)
        }
      }

      stage('Get UUIDs and publish content') {
        String[] cmsArray = cmsInput.split(",")
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_VARNISH_AUTH)
        echo "Using varnish credentials for user ${AUTH_USER}"
        for (int i = 0; i < cmsArray.length; i++) {
          echo "CMS: ${cmsArray[i]}"
          sh "rm -f ${UUIDS_FILE_PATH}"
          switch (cmsArray[i]) {
            case "Methode":
              echo 'Publishing from Methode...'
              deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_METHODE_UUIDS)
              break
            case "Wordpress":
              echo 'Publishing from Wordpress...'
              deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_WORDPRESS_UUIDS)
              break
            case "Video":
              echo 'Publishing from Video...'
              deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_VIDEO_UUIDS)
              break
            default: error("CMS parameter value is unknown!")
          }
          runGo(CALL_ENDPOINT_HITTER)
        }
      }
    }

    stage('cleanup') {
      cleanWs()
    }
  }
}

private void runGo(Closure codeToRun) {
  String currentDir = pwd()

  GString dockerRunArgs = "-v ${currentDir}:/workspace " +
          "--user 0"

  docker.image("golang:1.9.5-alpine3.7").inside(dockerRunArgs) {
    sh "apk add --no-cache git jq"

    codeToRun.call()
  }
}

private def deleteIndex(String indexZapperAppName, String awsAccessKey, String awsSecretKey, String esEndpoint, String indexInput) {
  git credentialsId: 'ft-upp-team', url: 'git@github.com:Financial-Times/elasticsearch-index-zapper.git', branch: 'master'

  sh "mkdir -p \$GOPATH/src/${indexZapperAppName} && mv ./* \$GOPATH/src/${indexZapperAppName} && " +
          "cd \$GOPATH/src/${indexZapperAppName} && " +
          'go get -u github.com/kardianos/govendor && ' +
          'govendor sync && ' +
          'go install && ' +
          "${indexZapperAppName} --aws-access-key=${awsAccessKey} " +
          "--aws-secret-access-key=${awsSecretKey} " +
          "--elasticsearch-endpoint=${esEndpoint} " +
          "--elasticsearch-index=${indexInput}"
}

private def stopElasticsearchReadWriter() {
  sh "kubectl scale --replicas=0 deployments/content-rw-elasticsearch"
}

private def startElasticsearchReadWriter() {
  sh "kubectl scale --replicas=2 deployments/content-rw-elasticsearch"
}

private static Environment computeTargetEnvironment(String environmentInput) {
  Environment targetEnv = EnvsRegistry.getEnvironment(environmentInput)
  if (targetEnv == null) {
    throw new IllegalArgumentException("Unknown environment ${environmentInput}. The environment is required.")
  }
  return targetEnv
}

private String getDeliveryClusterUrl(String envName, String region) {
  switch (envName) {
    case "k8s": return "https://upp-k8s-dev-delivery-" + region + ".ft.com"
    case "staging": return "https://upp-staging-delivery-" + region + ".ft.com"
    case "prod": return "https://upp-prod-delivery-" + region + ".ft.com"
    default: throw new IllegalArgumentException("Unknown environment ${envName}. The environment is required.")
  }
}
