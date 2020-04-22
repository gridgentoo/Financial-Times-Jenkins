import com.ft.jenkins.cluster.BuildConfig
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment

/**
 * Entry point to be used in PAC repositories.
 *
 * It uses the generic entry point and configures it to deploy to PAC environments.
 */

def call() {
  BuildConfig config = new BuildConfig()
  config.setPreprodEnvName(Environment.STAGING_NAME)
  config.setProdEnvName(Environment.PROD_NAME)
  config.allowedClusterTypes = [ClusterType.PAC]

  genericEntryPointForJenkinsfile(config)
}
