import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Environment

/**
 * Entry point to be used in UPP repositories.
 *
 * It uses the generic entry point and configures it to deploy to UPP environments.
 */

def call() {
  BuildConfig config = new BuildConfig()
  config.setPreprodEnvName(Environment.PRE_PROD_NAME)
  config.setProdEnvName(Environment.PROD_NAME)

  genericEntryPointForJenkinsfile(config)
}
