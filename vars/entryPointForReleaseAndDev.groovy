import com.ft.up.BuildConfig

def call(BuildConfig config) {
  String branchName = env.BRANCH_NAME

  if (isTag(branchName)) {
    releaseBuildAndDeploy(config)
  }
  else {
    devBuildAndDeploy(config)
  }
}

boolean isTag(String checkedOutBranchName) {
  return checkedOutBranchName.startsWith("tag/")
}