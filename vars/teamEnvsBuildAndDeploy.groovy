import com.ft.jenkins.BuildConfig
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.DeploymentUtilsConstants
import com.ft.jenkins.Environment
import com.ft.jenkins.EnvsRegistry
import com.ft.jenkins.docker.DockerUtils
import com.ft.jenkins.git.GitUtils

def call(BuildConfig buildConfig, String targetEnvName, String releaseName, boolean branchRelease) {
  /*  check if the build is allowed for the target environment */
  Environment targetEnv = EnvsRegistry.getEnvironment(targetEnvName)
  if (!targetEnv.clusters.containsAll(buildConfig.allowedClusters)) {
    stage("Target env `${targetEnvName}`doesn't have ${buildConfig.allowedClusters} clusters => Not deployed"){
      echo "This pipeline deploys only to ${buildConfig.allowedClusters} clusters. The release ${releaseName} is for env ${targetEnvName} that doesn't contain these clusters. ${targetEnvName} contains only the clusters ${targetEnv.clusters}"
    }
    return
  }

  DeploymentUtils deployUtil = new DeploymentUtils()
  DockerUtils dockerUtils = new DockerUtils()

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
            String dockerRepository = deployUtil.getDockerImageRepository()
            dockerUtils.buildAndPushImage("${dockerRepository}:${appVersion}")
          }
        }

        stage('publish chart') {
          chartName = deployUtil.publishHelmChart(appVersion)
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

    println " ============== Starting GENERIC_DEPLOY_JOB ================="
    println "with parameters"

    print "chartName"
    println chartName

    print "appVersion"
    println appVersion

    print "Environment"
    println targetEnvName

    println " ----------------- END ---------------------"

    build job: DeploymentUtilsConstants.GENERIC_DEPLOY_JOB,
          parameters: [
              string(name: 'Chart', value: chartName),
              string(name: 'Version', value: appVersion),
              string(name: 'Environment', value: targetEnvName),
              string(name: 'Cluster', value: 'all-in-chart'),
              string(name: 'Region', value: 'all'),
              booleanParam(name: 'Send success notifications', value: true)]
  }
}

private String getImageVersion(String releaseName, boolean branchRelease) {
  if (branchRelease) {
    /*  if we're releasing from a branch we need a valid semver, thus we'll be using the most recent git tag in the branch */
    GitUtils gitUtils = new GitUtils()
    String latestGitTag = gitUtils.getMostRecentGitTag()

    /* using the latest commit so that we generate unique image names for different commits on the same branch */
    String latestCommit = gitUtils.getShortLatestCommit()
    return "${latestGitTag}-${latestCommit}-${releaseName}"
  }

  return releaseName
}

void setCurrentBuildProps(String appVersion) {
  GitUtils gitUtils = new GitUtils()
  currentBuild.displayName="${env.BUILD_NUMBER} - ${gitUtils.getShortLatestCommit()}"
  currentBuild.description="version: ${appVersion}"
}