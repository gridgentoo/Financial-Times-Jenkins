import com.ft.jenkins.BuildConfig
import com.ft.jenkins.Cluster

/**
 * Entry point to be used in PAC repositories.
 *
 * It uses the generic entry point and configures it to deploy to PAC environments.
 */

def call() {
  BuildConfig config = new BuildConfig()
  config.setPreprodEnvName("stagingpac")
  config.setProdEnvName("prodpac")
  config.allowedClusters = [Cluster.PAC]

  genericEntryPointForJenkinsfile(config)
}
