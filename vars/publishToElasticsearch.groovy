import com.ft.jenkins.*

import static com.ft.jenkins.DeploymentUtilsConstants.CREDENTIALS_DIR
import static com.ft.jenkins.DeploymentUtilsConstants.K8S_CLI_IMAGE

def call() {
  ParamUtils paramUtils = new ParamUtils()
  String environmentInput = paramUtils.getRequiredParameterValue("Environment")
  String regionInput = paramUtils.getRequiredParameterValue("Region")
  String cmsInput = paramUtils.getRequiredParameterValue("CMS")
  String indexInput = paramUtils.getRequiredParameterValue("Index")

  Environment targetEnv = computeTargetEnvironment(environmentInput)
  DeploymentUtils deployUtil = new DeploymentUtils()

  String GET_MONGO_CONTAINER_CMD = "kubectl get pods | grep mongodb | awk '{print \$1}' | head -1"

  String GET_METHODE_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FTCOM-METHODE/ },\"type\": {\$nin: [\"ContentPackage\",\"Content\",\"ImageSet\"]}}, {_id: false, uuid: 1}).forEach(function(o) { print(o.uuid)})' --quiet > uuids.txt"
  String GET_WORDPRESS_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"mediaType\":null,\"identifiers.authority\": {\$regex: /FT-LABS/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' > uuids.txt"
  String GET_VIDEO_UUIDS_CMD = "mongo upp-store -eval 'rs.slaveOk(); db.content.find({\"identifiers.authority\": {\$regex: /NEXT-VIDEO-EDITOR/ }}, {_id: false, uuid: 1}).forEach(function(o) { printjson(o)})' --quiet | jq -r '.uuid' >> uuids.txt"

  Closure SERVICE_SHUTDOWN = { sh "kubectl scale --replicas=0 deployments/content-rw-elasticsearch" }
  Closure INDEX_DELETION = {
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/elasticsearch-index-zapper && " +
            "elasticsearch-index-zapper --aws-access-key=`kubectl get secret global-secrets -o yaml | grep aws.access_key_id | head -1 | awk '{print \$2}' | base64 -d` " +
            "--aws-secret-access-key=`kubectl get secret global-secrets -o yaml | grep aws.secret_access_key | head -1 | awk '{print \$2}' | base64 -d` " +
            "--elasticsearch-endpoint=`kubectl get configmap global-config -o yaml | grep aws.content.elasticsearch.endpoint | awk '{print \$2}'` " +
            "--elasticsearch-index='${indexInput}'"
  }
  Closure SERVICE_STARTUP = { sh "kubectl scale --replicas=2 deployments/content-rw-elasticsearch" }

  Closure GET_METHODE_UUIDS = {
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/endpoint-hitter && " +
            "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_METHODE_UUIDS_CMD} && " +
            "endpoint-hitter --target-url=${getDeliveryClusterUrl(environmentInput, regionInput)}/__post-publication-combiner/{uuid}"
  }
  Closure GET_WORDPRESS_UUIDS = {
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/endpoint-hitter && " +
            "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_WORDPRESS_UUIDS_CMD} && " +
            "endpoint-hitter --target-url=${getDeliveryClusterUrl(environmentInput, regionInput)}/__post-publication-combiner/{uuid}"
  }
  Closure GET_VIDEO_UUIDS = {
    sh 'export GOROOT=/usr/lib/go && export GOPATH=/gopath && export GOBIN=/gopath/bin && export PATH=$PATH:$GOROOT/bin:$GOPATH/bin && ' +
            "go get -u github.com/Financial-Times/endpoint-hitter && " +
            "kubectl exec -it `${GET_MONGO_CONTAINER_CMD}` -- ${GET_VIDEO_UUIDS_CMD} && " +
            "endpoint-hitter --target-url=${getDeliveryClusterUrl(environmentInput, regionInput)}/__post-publication-combiner/{uuid}"
  }

  node('docker') {
    catchError {
      stage('Disable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_SHUTDOWN)
        sleep(5) // Wait 5s for content-rw-elasticsearch to be disabled
      }

      stage('Delete ES index') {
        runGoWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, INDEX_DELETION)
      }

      stage('Enable content-rw-elasticsearch') {
        deployUtil.runWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, SERVICE_STARTUP)
      }

      stage('Get UUIDs and publish content') {
        switch (cmsInput) {
          case "Methode":
            runGoWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_METHODE_UUIDS)
            break
          case "Wordpress":
            runGoWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_WORDPRESS_UUIDS)
            break
          case "Video":
            runGoWithK8SCliTools(targetEnv, Cluster.DELIVERY, regionInput, GET_VIDEO_UUIDS)
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
