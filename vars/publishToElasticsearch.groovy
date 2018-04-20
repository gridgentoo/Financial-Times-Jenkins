import com.ft.jenkins.*

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR
import static com.ft.jenkins.DeploymentUtilsConstants.K8S_CLI_IMAGE

def call() {
  ParamUtils paramUtils = new ParamUtils()
  String environmentInput = paramUtils.getRequiredParameterValue("Environment")
  String regionInput = paramUtils.getRequiredParameterValue("Region")
  String cmsInput = paramUtils.getRequiredParameterValue("CMS")
  String indexInput = paramUtils.getRequiredParameterValue("Index")
  String zapIndexInput = paramUtils.getRequiredParameterValue("Zap index")

  Environment targetEnv = computeTargetEnvironment(environmentInput)
  DeploymentUtils deployUtil = new DeploymentUtils()

  String UUIDS_FILE_PATH = "uuids.txt"
  String GET_MONGO_CONTAINER_CMD = "kubectl get pods | grep mongodb | awk '{print \$1}' | head -1"

  String GET_METHODE_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FTCOM-METHODE/ },\"type\": {\$nin: [\"ContentPackage\",\"Content\",\"ImageSet\"]}}, {_id: false, uuid: 1}).forEach(function(o) { print(o.uuid)})' --quiet > ${UUIDS_FILE_PATH}"
  String GET_WORDPRESS_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FT-LABS/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' > ${UUIDS_FILE_PATH}"
  String GET_VIDEO_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"identifiers.authority\": {\$regex: /NEXT-VIDEO-EDITOR/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' > ${UUIDS_FILE_PATH}"

  Closure SERVICE_SHUTDOWN = { sh "kubectl scale --replicas=0 deployments/content-rw-elasticsearch" }

  String AWS_ACCESS_KEY
  String AWS_SECRET_KEY
  String ES_ENDPOINT

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

  Closure INDEX_DELETION = {
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/elasticsearch-index-zapper && " +
            "elasticsearch-index-zapper --aws-access-key=${AWS_ACCESS_KEY} " +
            "--aws-secret-access-key=${AWS_SECRET_KEY} " +
            "--elasticsearch-endpoint=${ES_ENDPOINT}` " +
            "--elasticsearch-index='${indexInput}'"
  }

  Closure SERVICE_STARTUP = { sh "kubectl scale --replicas=2 deployments/content-rw-elasticsearch" }

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
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/endpoint-hitter && " +
            "endpoint-hitter --target-url=${getDeliveryClusterUrl(environmentInput, regionInput)}/__post-publication-combiner/{uuid}"
  }

  node('docker') {
    catchError {
      stage('Disable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_SHUTDOWN)
        sleep(5) // Wait 5s for content-rw-elasticsearch to be disabled
      }

      stage('Delete ES index') {
        if(zapIndexInput) {
          deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_CONFIGURATION)
          echo "INDEX DELETION: ${INDEX_DELETION}"
//          runGo(INDEX_DELETION)
        } else {
          echo 'Skipping ES index zapping'
        }
      }

      stage('Enable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_STARTUP)
      }

      stage('Get UUIDs and publish content') {
        String[] cmsArray = cmsInput.split(",")
        for(int i = 0; i < cmsArray.length; i++) {
          sh "rm -f uuids.txt"
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

  GString dockerRunArgs = "-v ${currentDir}:/workspace"

  docker.image("1.9.5-alpine3.7").inside(dockerRunArgs) {
    sh "apk --update add git go libc-dev"

    codeToRun.call()
  }
}

private static Environment computeTargetEnvironment(String environmentInput) {
  Environment targetEnv = EnvsRegistry.getEnvironment(environmentInput)
  if (targetEnv == null) {
    throw new IllegalArgumentException("Unknown environment ${environmentInput}. The environment is required.")
  }
  return targetEnv
}

private void runGoWithK8SCliTools(Environment env, Cluster cluster, String region = null, Closure codeToRun) {
  prepareK8SCliCredentials(env, cluster, region)
  String currentDir = pwd()

  String apiServer = env.getApiServerForCluster(cluster, region)
  GString dockerRunArgs =
          "-v ${currentDir}/${CREDENTIALS_DIR}:/${CREDENTIALS_DIR} " +
                  "-e 'K8S_API_SERVER=${apiServer}' " +
                  "-e 'KUBECONFIG=${currentDir}/kubeconfig' -u root"

  docker.image(K8S_CLI_IMAGE).inside(dockerRunArgs) {
    sh "/docker-entrypoint.sh"
    sh "apk --update add git go libc-dev"

    codeToRun.call()
  }
}

private String getDeliveryClusterUrl(String envName, String region) {
  switch (envName) {
    case "k8s": return "https://upp-k8s-dev-delivery-" + region + ".ft.com"
    case "staging": return "https://upp-staging-delivery-" + region + ".ft.com"
    case "prod": return "https://upp-prod-delivery-" + region + ".ft.com"
    default: throw new IllegalArgumentException("Unknown environment ${envName}. The environment is required.")
  }
}

private void prepareK8SCliCredentials(Environment targetEnv, Cluster cluster, String region = null) {
  String fullClusterName = targetEnv.getFullClusterName(cluster, region)
  withCredentials([
          [$class: 'FileBinding', credentialsId: "ft.k8s-auth.${fullClusterName}.client-certificate", variable: 'CLIENT_CERT'],
          [$class: 'FileBinding', credentialsId: "ft.k8s-auth.${fullClusterName}.ca-cert", variable: 'CA_CERT'],
          [$class: 'FileBinding', credentialsId: "ft.k8s-auth.${fullClusterName}.client-key", variable: 'CLIENT_KEY']]) {
    sh """
      mkdir -p ${CREDENTIALS_DIR}
      rm -f ${CREDENTIALS_DIR}/*
      cp ${env.CLIENT_CERT} ${CREDENTIALS_DIR}/
      cp ${env.CLIENT_KEY} ${CREDENTIALS_DIR}/
      cp ${env.CA_CERT} ${CREDENTIALS_DIR}/
    """
  }
}
