import com.ft.jenkins.cluster.BuildConfig
import com.ft.jenkins.deployment.Deployments
import com.ft.jenkins.git.GitHelper
import com.ft.jenkins.git.GitHelperConstants
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
  String currentBranch = (String) env.BRANCH_NAME
  String currentTag = (String) env.TAG_NAME

  GitHelper gitHelper = new GitHelper()

  if (GitHelper.isTag(currentTag)) {
    String tagName = env.TAG_NAME
    GithubReleaseInfo releaseInfo = gitHelper.getReleaseInfoForCurrentTag(tagName)

    if (releaseInfo == null || releaseInfo.isPreRelease) {
      String envToDeploy = Deployments.getTeamFromReleaseCandidateTag(tagName)
      teamEnvsBuildAndDeploy(config, envToDeploy, tagName, false)
    } else {
      upperEnvsBuildAndDeploy(releaseInfo, config)
    }
  } else if (GitHelper.isDeployOnPushForBranch(currentBranch)) {
    if (currentBranch.contains(config.preprodEnvName) || currentBranch.contains(config.prodEnvName)) {
      echo "Skipping branch ${currentBranch} as ${GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX} can't be used to push to upper environments."
    } else {
      String releaseCandidateName = Deployments.getReleaseCandidateName(currentBranch)
      teamEnvsBuildAndDeploy(config, Deployments.getEnvironmentName(currentBranch), releaseCandidateName, true)
    }
  } else {
    echo "Skipping branch ${currentBranch} as it is not a tag and it doesn't start with ${GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX}"
  }
}
