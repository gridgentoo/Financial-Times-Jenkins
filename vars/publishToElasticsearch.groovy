import com.ft.jenkins.*

def call() {
  Environment targetEnv = computeTargetEnvironment()
  DeploymentUtils deployUtil = new DeploymentUtils()

  String GET_MONGO_CONTAINER_CMD = "kubectl get pods | grep mongodb | awk '{print \$1}' | head -1"

  String GET_METHODE_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {$regex: /FTCOM-METHODE/ },\"type\": {$nin: [\"ContentPackage\",\"Content\",\"ImageSet\"]}}, {_id: false, uuid: 1}).forEach(function(o) { print(o.uuid)})' --quiet > uuids.txt "
  String GET_WORDPRESS_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {$regex: /FT-LABS/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet > uuids.txt"
  String GET_VIDEO_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"identifiers.authority\": {$regex: /NEXT-VIDEO-EDITOR/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet > uuids.txt"

  Closure SERVICE_SHUTDOWN = { sh "kubectl --scale=0 content-rw-elasticsearch" }
  Closure SERVICE_STARTUP = { sh "kubectl --scale=2 content-rw-elasticsearch" }

  Closure SETUP_ENDPOINT_HITTER = {
    sh "apk add go"
    sh "go get -u github.com/Financial-Times/endpoint-hitter"
  }
  Closure CALL_ENDPOINT_HITTER = { sh "endpoint-hitter --target-url=${targetEnv.name}" }
  Closure GET_METHODE_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_METHODE_UUIDS_CMD}" }
  Closure GET_WORDPRESS_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_WORDPRESS_UUIDS_CMD}" }
  Closure GET_VIDEO_UUIDS = { sh "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_VIDEO_UUIDS_CMD}" }

  node('docker') {
    stage('Disable content-rw-elasticsearch') {
      deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, null, SERVICE_SHUTDOWN)
      sleep(3000) // Wait for content-rw-elasticsearch to be disabled
    }
    stage('Delete ES index') {
//      sh "curl -XDELETE 'localhost:9200/twitter?pretty'"
    }
    stage('Enable content-rw-elasticsearch') {
      deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, null, SERVICE_STARTUP)
    }
    stage('Get UUIDs and publish content') {
      switch (CMS) {
        case "Methode":
          def methodeCall = SETUP_ENDPOINT_HITTER >> GET_METHODE_UUIDS >> CALL_ENDPOINT_HITTER
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, null, methodeCall)
          break
        case "Wordpress":
          def wordpressCall = SETUP_ENDPOINT_HITTER >> GET_WORDPRESS_UUIDS >> CALL_ENDPOINT_HITTER
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, null, wordpressCall)
          break
        case "Video":
          def videoCall = SETUP_ENDPOINT_HITTER >> GET_VIDEO_UUIDS >> CALL_ENDPOINT_HITTER
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, null, videoCall)
          break
        default: error("CMS parameter value is unknown!")
      }
    }
  }
}

private static Environment computeTargetEnvironment() {
  ParamUtils paramUtils = new ParamUtils()
  String environmentInput = paramUtils.getRequiredParameterValue("Environment")
  Environment targetEnv = EnvsRegistry.getEnvironment(environmentInput)
  if (targetEnv == null) {
    throw new IllegalArgumentException("Unknown environment ${environmentInput}. The environment is required.")
  }
  return targetEnv
}