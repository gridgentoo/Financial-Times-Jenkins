import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.changerequests.ChangeRequestCloseData
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.git.GithubReleaseInfo
import com.ft.jenkins.provision.ClusterUpdateInfo
import com.ft.jenkins.provision.ProvisionerUtil

import static com.ft.jenkins.DeploymentUtilsConstants.APPROVER_INPUT

def call() {
    node('docker') {
        stage('cleanup') { cleanWs() }
        catchError { updateDexConfig() } //catch any errors to ensure cleanup is always executed
        stage('cleanup') { cleanWs() }
    }
}

private void updateDexConfig() {
    DeploymentUtils deploymentUtils = new DeploymentUtils()
    String app = "upp-dex-config"
    String chartFolderLocation = "helm/" + app
    String appVersion

    Map<String, Map<String, String>> configMap = readConfig()
    List<String> clusters = new ArrayList<>()
    clusters.addAll((HashSet<String>) configMap.keySet())
    List<String> selectedClusters = new ArrayList<>()
    String approver = ""

    stage('Select clusters to update') {
        (selectedClusters, approver) = getSelectedUserInputs(clusters,
                "Clusters to deploy dex-config to",
                "Update dex-config")
        echo "The following charts were selected for adding: ${selectedClusters}"
    }

    String crId = openCr(approver, app, environment)

    selectedClusters.each { String clusterName ->
        Map<String, String> secrets = configMap."$clusterName"

        stage(clusterName) {
            ProvisionerUtil provisionerUtil = new ProvisionerUtil()
            ClusterUpdateInfo clusterUpdateInfo = provisionerUtil.getClusterUpdateInfo(clusterName)
            if (clusterUpdateInfo == null || clusterUpdateInfo.cluster == null || clusterUpdateInfo.region == null) {
                throw new IllegalArgumentException("Cannot extract cluster info from cluster name " + clusterName)
            }
            Cluster targetCluster = Cluster.valueOfLabel(clusterUpdateInfo.cluster)
            if (targetCluster == null) {
                if (clusterName.contains("pac")) {
                    targetCluster = Cluster.PAC
                } else {
                    throw new IllegalArgumentException("Unknown cluster" + clusterUpdateInfo.cluster)
                }
            }
            String targetRegion = clusterUpdateInfo.region
            if (targetRegion == null) {
                throw new IllegalArgumentException("Cannot determine region from cluster name: " + clusterName)
            }
            Environment targetEnv
            for (Environment env in EnvsRegistry.envs) {
                if (clusterName == env.getClusterSubDomain(targetCluster, targetRegion)) {
                    targetEnv = env
                    break
                }
            }
            if (targetEnv == null) {
                throw new IllegalArgumentException("Cannot determine target env from cluster name: " + clusterName)
            }
            def scmVars = checkoutDexConfig(app)
            appVersion = scmVars.GIT_COMMIT.take(7)

            String valuesFile = "values.yaml"
            writeFile([file: valuesFile, text: buildHelmValues(secrets, clusterName)])

            String helmDryRunOutput = "output.txt"
            deploymentUtils.runWithK8SCliTools(targetEnv, targetCluster, targetRegion, {
                sh "helm upgrade --debug --dry-run ${app} ${chartFolderLocation} -i --timeout 1200 -f ${valuesFile} > ${helmDryRunOutput}"
            })

            String dexSecretFile = writeDexSecret(helmDryRunOutput)
            encodeDexSecrets(dexSecretFile)
            sh "rm ${chartFolderLocation}/templates/dex-config.yaml"
            sh "mv ${dexSecretFile} ${chartFolderLocation}/templates/dex-config.yaml"

            deploymentUtils.runWithK8SCliTools(targetEnv, targetCluster, targetRegion, {
                sh """
                        kubectl apply -f ${chartFolderLocation}/templates/dex-config.yaml --validate=false;
                        sleep 5; kubectl scale deployment content-auth-dex --replicas=0;
                        sleep 5; kubectl scale deployment content-auth-dex --replicas=2;
                        sleep 15; kubectl get pod --selector=app=content-auth-dex"""
            })
        }
    }
    if (selectedClusters.size() > 0) {
        currentBuild.description = "$app:$appVersion in ${selectedClusters.join(",")}"
    } else {
        currentBuild.description = "No update was performed."
    }
}

private String openCr(String approver, String chartName, Environment environment) {
    try {
        ChangeRequestOpenData data = new ChangeRequestOpenData()
        data.ownerEmail = "${approver}@ft.com"
        data.summary = "Deploying chart ${chartName}@master with apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} in ${environment.name}"
        data.description = releaseInfo.title
        data.details = releaseInfo.title
        data.environment = environment.name == Environment.PROD_NAME ? ChangeRequestEnvironment.Production :
                ChangeRequestEnvironment.Test
        data.notifyChannel = environment.slackChannel
        data.notify = true

        ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
        return crUtils.open(data)
    }
    catch (e) { //  do not fail if the CR interaction fail
        echo "Error while opening CR for release ${releaseInfo.getTagName()}: ${e.message} "
    }
}

private void closeCr(String crId, Environment environment) {
    if (crId == null) {
        return
    }

    try {
        ChangeRequestCloseData data = new ChangeRequestCloseData()
        data.notifyChannel = environment.slackChannel
        data.id = crId

        ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
        crUtils.close(data)
    }
    catch (e) { //  do not fail if the CR interaction fail
        echo "Error while closing CR ${crId}: ${e.message} "
    }
}

private Map<String, Map<String, String>> readConfig() {
    def configMap = readJSON text: env."Dex config"
    (Map<String, Map<String, String>>) configMap
}

private def getSelectedUserInputs(List<String> clusters, String inputMessage,
                                           String okButton) {
    List checkboxes = []
    clusters.sort()
    for (int i = 0; i < clusters.size(); i++) {
        String chartName = clusters.get(i)
        checkboxes.add(booleanParam(defaultValue: true, name: chartName))
    }

    if (checkboxes.isEmpty()) {
        return []
    }

    /*  adding also the approver, although we're extracting it afterwards, as if only one input is given, the input method
        will return a single object, and not a map, which would be inconvenient */
    Map<String, Object> rawUserInputs = input(message: inputMessage,
            parameters: checkboxes,
            submitterParameter: APPROVER_INPUT,
            ok: okButton) as Map<String, Object>


    return getSelectedValues(rawUserInputs), extractApprover(rawUserInputs)
}

private List<String> getSelectedValues(Map<String, Object> userInputs) {
    List<String> selectedValues = []
    userInputs.each { String value, Boolean isSelected ->
        if (isSelected) {
            selectedValues.add(value)
        }
    }
    return selectedValues
}

String extractApprover(Map<String, Object> userInputs) {
    String approver = userInputs.get(APPROVER_INPUT)
    userInputs.remove(APPROVER_INPUT)
    return approver
}


private void encodeDexSecrets(String dexSecretFile) {
    docker.image("ruby:2.5.1-slim-stretch").inside() {
        sh "gem install kube_secrets_encode"
        sh "kube_secrets --file=${dexSecretFile} --yes > /dev/null"
    }
}

private String writeDexSecret(String helmDryRunOutput) {
    String output = readFile(helmDryRunOutput)
    def dexSecret = ""
    def writeLine = false
    String[] lines = output.split("\n")
    lines.each { String line ->
        if (writeLine) {
            dexSecret = dexSecret + "\n" + line
        }
        if (line.contains("dex-config.yaml")) {
            writeLine = true
        }
        if (writeLine && line.contains("enablePasswordDB")) {
            writeLine = false
        }
    }
    String dexSecretFile = "secrets.txt"
    writeFile([file: dexSecretFile, text: dexSecret])
    dexSecretFile
}

private Object checkoutDexConfig(String app) {
    checkout([$class           : 'GitSCM',
              branches         : [[name: "master"]],
              userRemoteConfigs: [[url: "git@github.com:Financial-Times/${app}.git", credentialsId: "ft-upp-team"]]
    ])
}

private String buildHelmValues(Map<String, String> secrets, String clusterName) {
    String helmValues = "github:\n  client:\n    id: " + '"' + secrets."github.client.id" + '"' +
            "\n    secret: " + '"' + secrets."github.client.secret" + '"' + "\nkubectl:\n  login:\n    secret: " +
            '"' + secrets."kubectl.login.secret" + '"' + "\ncluster:\n  name: " + '"' + clusterName + '"' +
            "\nldap:\n  host: " + '"' + secrets."ldap.host" + '"' + "\n  bindDN: " + '"' + secrets."ldap.bindDN" + '"' +
            "\n  bindPW: " + '"' + secrets."ldap.bindPW" + '"' + "\n"
    return helmValues
}
