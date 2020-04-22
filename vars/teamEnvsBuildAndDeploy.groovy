import com.ft.jenkins.cluster.BuildConfig
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.EnvsRegistry
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.deployment.Deployments
import com.ft.jenkins.deployment.DeploymentsConstants
import com.ft.jenkins.docker.Docker
import com.ft.jenkins.git.GitHelper

def call(BuildConfig buildConfig, String targetEnvName, String releaseName, boolean branchRelease) {
  /*  check if the build is allowed for the target environment */
  def allowedClusterTypes = buildConfig.allowedClusterTypes
  boolean hasAllowedClusterType = EnvsRegistry.hasAllowedClusterType(allowedClusterTypes, targetEnvName)
  if (!hasAllowedClusterType) {
    stage("Target env `${targetEnvName}` does not belong to any of the allowed clusters types: ${allowedClusterTypes} => Not deployed") {
      echo "This pipeline deploys only to ${allowedClusterTypes} clusters. The release ${releaseName} is for env ${targetEnvName} that doesn't contain these clusters."
    }
    return
  }

  Deployments deployments = new Deployments()
  Docker docker = new Docker()

  String appVersion
  String chartName

  node('docker') {
    catchError {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        appVersion = getImageVersion(releaseName, branchRelease)
        setCurrentBuildProps(appVersion)

        if (fileExists("Dockerfile")) { //  build Docker image only if we have a Dockerfile
          stage('build image') {
            String dockerRepository = deployments.getDockerImageRepository()
            docker.buildAndPushImage("${dockerRepository}:${appVersion}")
          }
        }

        stage('publish chart') {
          chartName = deployments.publishHelmChart(appVersion)
        }
      }
    }

    stage("cleanup") {
      cleanWs()
    }
  }

  /*  continue with deploy if successful so far */
  if (currentBuild.resultIsWorseOrEqualTo("FAILURE")) {
    return
  }
  /*  this is called outside of a node, so that the node is released, and so the executor is released during the deploy. */
  stage("deploy chart") {
    /*  trigger the generic job for deployment */
    build job: DeploymentsConstants.GENERIC_DEPLOY_JOB,
            parameters: [
                    string(name: 'Chart', value: chartName),
                    string(name: 'Version', value: appVersion),
                    string(name: 'Environment', value: targetEnvName),
                    string(name: 'Cluster', value: ClusterType.ALL_IN_CHART.label),
                    string(name: 'Region', value: Region.ALL.name),
                    string(name: 'Namespace', value: Environment.DEFAULT_KUBE_NAMESPACE),
                    booleanParam(name: 'Send success notifications', value: true)]
  }
}

private static String getImageVersion(String releaseName, boolean branchRelease) {
  if (branchRelease) {
    GitHelper gitHelper = new GitHelper()
    /*  if we're releasing from a branch we need a valid semver, thus we'll be using the most recent git tag in the branch */
    String latestGitTag = gitHelper.getMostRecentGitTag()

    /* using the latest commit so that we generate unique image names for different commits on the same branch */
    String latestCommit = gitHelper.getShortLatestCommit()
    String imageVersion = "${latestGitTag}-${latestCommit}-${releaseName}"
    imageVersion
  }
  releaseName
}

void setCurrentBuildProps(String appVersion) {
  currentBuild.displayName = "${env.BUILD_NUMBER} - ${new GitHelper().getShortLatestCommit()}"
  currentBuild.description = "version: ${appVersion}"
}
