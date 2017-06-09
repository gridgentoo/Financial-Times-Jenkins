import com.ft.jenkins.BuildConfig
import com.ft.jenkins.DeploymentUtils
import com.ft.jenkins.git.GitUtils
import com.ft.jenkins.git.GitUtilsConstants
import com.ft.jenkins.git.GithubReleaseInfo

/**
 * Entry point that decides which pipeline to execute, based on the Github release type to build for tags and on branch name for branches.
 * For Github pre-releases it will trigger the pipeline that automatically deploys to team environments,
 * and for GH releases it will trigger the pipeline for continuous delivery in upper environments.
 * For branches that start with "deploy-on-push/{env}/{feature}" it will trigger the deployment in the team environment.
 *
 * This multibranch pipeline is designed to be triggered only for tags coming from Github releases and for branches named "deploy-on-push/..".
 */
def call(BuildConfig config) {
  GitUtils gitUtils = new GitUtils()
  String currentBranch = (String) env.BRANCH_NAME
  DeploymentUtils deployUtils = new DeploymentUtils()

  if (gitUtils.isTag(currentBranch)) {
    GithubReleaseInfo releaseInfo = getReleaseInfoForCurrentTag(currentBranch)

    if (releaseInfo.isPreRelease) {
      String envToDeploy = deployUtils.getTeamFromReleaseCandidateTag(releaseInfo.getTagName())
      devBuildAndDeploy(config, releaseInfo.tagName, envToDeploy)
    } else {
      releaseBuildAndDeploy(config, releaseInfo)
    }
  } else if (gitUtils.isDeployOnPushForBranch(currentBranch)) {
    devBuildAndDeploy(config, deployUtils.getDockerImageVersion(currentBranch), deployUtils.getEnvironmentName(currentBranch))
  }

  echo "Skipping branch ${currentBranch} as it is not a tag and it doesn't start with ${GitUtilsConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX}"
}

public GithubReleaseInfo getReleaseInfoForCurrentTag(String currentBranch) {
  GitUtils gitUtils = new GitUtils()
  String tagName = gitUtils.getTagNameFromBranchName(currentBranch)
  String currentRepoName = gitUtils.getCurrentRepoName()

  GithubReleaseInfo releaseInfo = gitUtils.getGithubReleaseInfo(tagName, currentRepoName)
  return releaseInfo
}
