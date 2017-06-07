import com.ft.jenkins.BuildConfig
import com.ft.jenkins.git.GitUtils
import com.ft.jenkins.git.GithubReleaseInfo

/**
 * Entry point that decides which pipeline to execute, based on the Github release type to build.
 * For Github pre-releases it will trigger the pipeline that automatically deploys to team environments,
 * and for GH releases it will trigger the pipeline for continuous delivery in upper environments.
 *
 * This multibranch pipeline is designed to be triggered only for tags comming from Github releases.
 */
def call(BuildConfig config) {
  GitUtils gitUtils = new GitUtils()

  if (!gitUtils.isTag(env.BRANCH_NAME)) {
    echo "Skipping branch ${env.BRANCH_NAME} as it is not a tag"
    return
  }

  String tagName = gitUtils.getTagNameFromBranchName(env.BRANCH_NAME)
  String currentRepoName = gitUtils.getCurrentRepoName()
  GithubReleaseInfo releaseInfo = gitUtils.getGithubReleaseInfo(tagName, currentRepoName)

  if (releaseInfo.isPreRelease) {
    devBuildAndDeploy(config, releaseInfo)
  }
  else {
    releaseBuildAndDeploy(config, releaseInfo)
  }
}
