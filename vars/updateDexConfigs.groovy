import com.ft.jenkins.Cluster
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.provision.ProvisionerUtil
import groovy.json.JsonSlurper

node("docker") {
    stage("everything") {
        String clusterName = "upp-k8s-dev-delivery-eu"
        ProvisionerUtil provisionerUtil = new ProvisionerUtil()
        def clusterUpdateInfo = provisionerUtil.getClusterUpdateInfo(clusterName)
        echo clusterUpdateInfo.toString()

        DeploymentUtils deploymentUtils = new DeploymentUtils()
        Cluster targetCluster = Cluster.valueOfLabel(clusterUpdateInfo.cluster)
        echo "Target cluster: " + targetCluster

        String targetRegion = clusterUpdateInfo.region
        echo "Target region: " + targetRegion

        Environment targetEnv
        for (Environment env in EnvsRegistry.envs) {
            if (clusterName == env.getClusterSubDomain(targetCluster, targetRegion)) {
                targetEnv = env
                break
            }
        }
        if (targetEnv == null) {
            echo "Can't find target env for cluster" + clusterName
            return
        }
        echo "Target env: " + targetEnv.name

        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(env."Dex config")

        String app = "upp-dex-config"
        String chartFolderLocation = "upp-dex-config/upp-dex-config"

        String ghClientId = object."upp-k8s-dev-delivery-eu"."github.client.id"
        String ghClientSecret = object."upp-k8s-dev-delivery-eu"."github.client.secret"
        String kcLoginSecret = object."upp-k8s-dev-delivery-eu"."kubectl.login.secret"
        String ldapHost = object."upp-k8s-dev-delivery-eu"."ldap.host"
        String ldapBindDN = object."upp-k8s-dev-delivery-eu"."ldap.bindDN"
        String ldapBindPW = object."upp-k8s-dev-delivery-eu"."ldap.bindPW"

        echo "ghClientId" + ghClientId
        echo "ghClientSecret" + ghClientSecret
        echo "kcLoginSecret" + kcLoginSecret
        echo "ldapHost" + ldapHost
        echo "ldapBindDN" + ldapBindDN
        echo "ldapBindPW" + ldapBindPW

        deploymentUtils.runWithK8SCliTools(targetEnv, targetCluster, targetRegion, {
            sh "kubectl get nodes"
            checkout([$class           : 'GitSCM',
                      branches         : [[name: "dex-config"]],
                      userRemoteConfigs: [[url: "git@github.com:Financial-Times/upp-dex-config.git", credentialsId: "ft-upp-team"]]
            ])

            String additionalHelmValues =
                    "--set github.client.id=${ghClientId} "
            "--set github.client.secret=${ghClientSecret} "
            "--set kubectl.login.secret=${kcLoginSecret} "
            "--set cluster.name=${clusterName} "
            "--set ldap.host=${ldapHost} "
            "--set ldap.bindDN=${ldapBindDN} "
            "--set ldap.bindPW=${ldapBindPW} "
            sh "helm upgrade ${app} ${chartFolderLocation} -i --timeout 1200 ${additionalHelmValues}"

        })
    }
}
