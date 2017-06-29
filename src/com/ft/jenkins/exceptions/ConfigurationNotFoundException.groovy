package com.ft.jenkins.exceptions

/**
 * Exception to be thrown when the configuration file for a service was not found under apps-config folder, either
 * because it does not respect the naming convention or it does not exist at all.
 * Naming convention for files under apps-config folder:
 * <SERVICE_NAME>_<CLUSTER_NAME>[_<ENVIRONMENT_NAME>].yaml.
 * Examples:
 *  - publish-availability-monitor_publishing_k8s.yaml
 */
class ConfigurationNotFoundException extends Exception {
  ConfigurationNotFoundException(String var1) {
    super(var1)
  }
}
