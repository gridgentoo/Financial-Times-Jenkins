import com.ft.jenkins.*

def call() {
  ParamUtils paramUtils = new ParamUtils()
  String environmentInput = paramUtils.getRequiredParameterValue("Environment")
  String regionInput = paramUtils.getRequiredParameterValue("Region")
  String cmsInput = paramUtils.getRequiredParameterValue("CMS")
  String indexInput = paramUtils.getRequiredParameterValue("Index")

  Environment targetEnv = computeTargetEnvironment(environmentInput)
  DeploymentUtils deployUtil = new DeploymentUtils()

  String GET_MONGO_CONTAINER_CMD = "kubectl get pods | grep mongodb | awk '{print \$1}' | head -1"

  String GET_METHODE_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FTCOM-METHODE/ },\"type\": {\$nin: [\"ContentPackage\",\"Content\",\"ImageSet\"]}}, {_id: false, uuid: 1}).forEach(function(o) { print(o.uuid)})' --quiet > uuids.txt "
  String GET_WORDPRESS_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FT-LABS/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' > uuids.txt"
  String GET_VIDEO_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"identifiers.authority\": {\$regex: /NEXT-VIDEO-EDITOR/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' >> uuids.txt"

  Closure SERVICE_SHUTDOWN = { sh "kubectl scale --replicas=0 deployments/content-rw-elasticsearch" }
  Closure INDEX_DELETION = {
    sh "apk add go"
    sh "go get -u github.com/Financial-Times/elasticsearch-index-zapper"
    sh "elasticsearch-index-zapper --aws-access-key=`kubectl get secret global-secrets -o yaml | grep aws.access_key_id | head -1 | awk '{print \$2}' | base64 --decode` " +
            "--aws-secret-access-key=`kubectl get secret global-secrets -o yaml | grep aws.secret_access_key | head -1 | awk '{print \$2}' | base64 --decode` " +
            "--elasticsearch-endpoint=`kubectl get configmap global-config -o yaml | grep aws.content.elasticsearch.endpoint | awk '{print \$2}'`" +
            "--elasticsearch-index='${indexInput}'"
  }
  Closure SERVICE_STARTUP = { sh "kubectl scale --replicas=2 deployments/content-rw-elasticsearch" }

  Closure SETUP_ENDPOINT_HITTER = {
    sh "apk add go"
    sh "go get -u github.com/Financial-Times/endpoint-hitter"
  }
  Closure CALL_ENDPOINT_HITTER = {
    sh "endpoint-hitter --target-url=${targetEnv.getFullClusterName(Cluster.DELIVERY, regionInput)}"
  }
  Closure GET_METHODE_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_METHODE_UUIDS_CMD}" }
  Closure GET_WORDPRESS_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_WORDPRESS_UUIDS_CMD}" }
  Closure GET_VIDEO_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_VIDEO_UUIDS_CMD}" }

  node('docker') {
    catchError {
      stage('Disable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_SHUTDOWN)
        sleep(5) // Wait 5s for content-rw-elasticsearch to be disabled
      }

      stage('Delete ES index') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, INDEX_DELETION)
      }

      stage('Enable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_STARTUP)
      }

      stage('Get UUIDs and publish content') {
        switch (cmsInput) {
          case "Methode":
            def methodeCall = SETUP_ENDPOINT_HITTER >> GET_METHODE_UUIDS >> CALL_ENDPOINT_HITTER
            deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, methodeCall)
            break
          case "Wordpress":
            def wordpressCall = SETUP_ENDPOINT_HITTER >> GET_WORDPRESS_UUIDS >> CALL_ENDPOINT_HITTER
            deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, wordpressCall)
            break
          case "Video":
            def videoCall = SETUP_ENDPOINT_HITTER >> GET_VIDEO_UUIDS >> CALL_ENDPOINT_HITTER
            deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, videoCall)
            break
          default: error("CMS parameter value is unknown!")
        }
      }
    }

    stage('cleanup') {
      cleanWs()
    }
  }
}

private static Environment computeTargetEnvironment(String environmentInput) {
  Environment targetEnv = EnvsRegistry.getEnvironment(environmentInput)
  if (targetEnv == null) {
    throw new IllegalArgumentException("Unknown environment ${environmentInput}. The environment is required.")
  }
  return targetEnv
}