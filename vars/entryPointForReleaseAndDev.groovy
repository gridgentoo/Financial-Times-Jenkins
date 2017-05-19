import com.ft.up.BuildConfig
import com.ft.up.GitUtils

def call(BuildConfig config) {
  GitUtils gitUtils = new GitUtils()

  if (gitUtils.isTag(env.BRANCH_NAME)) {
    releaseBuildAndDeploy(config)
  }
  else {
    devBuildAndDeploy(config)
  }
}
