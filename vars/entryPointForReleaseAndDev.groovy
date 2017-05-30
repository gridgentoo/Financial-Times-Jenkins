import com.ft.jenkins.BuildConfig
import com.ft.jenkins.git.GitUtils

/**
 * Entry point that decides which pipeline to execute, based on the branch type to build.
 * For regular branches it will trigger the pipeline that automatically deploys to team environments,
 * and for tags, meaning we're dealing with releases, it will trigger the pipeline for continuous delivery in upper environments.
 *
 * We need this entry point in order to be able to trigger builds when git tags are created in a multibranch fashion.
 * Since jenkins supports multibranch pipelines with a single Jenkinsfile entry point per git repository, I had to do this in order to
 * have 2 multibranch pipelines per repository. One will be for team environments deployments on every commit and one for release deployments from tags.
 */
def call(BuildConfig config) {
  GitUtils gitUtils = new GitUtils()

  if (gitUtils.isTag(env.BRANCH_NAME)) {
    releaseBuildAndDeploy(config)
  }
  else {
    devBuildAndDeploy(config)
  }
}
