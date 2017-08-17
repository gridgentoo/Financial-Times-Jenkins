import com.ft.jenkins.BuildConfig

/**
 * Entry point to be used in PAC repositories.
 *
 * It uses the generic entry point and configures it to deploy to PAC environments.
 */

def call() {
  BuildConfig config = new BuildConfig()
  //  todo [sb] adjust these once we have all the envs defined
  config.setPreprodEnvName("pac")
  config.setProdEnvName("pac")

  genericEntryPointForJenkinsfile(config)
}
