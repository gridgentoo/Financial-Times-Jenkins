package com.ft.jenkins.exceptions

/**
 * Exception to be thrown if the name of a helm config file under `apps-config` does not meet the naming conventions:
 * <SERVICE_NAME>_<CLUSTER_NAME>[_<ENVIRONMENT_NAME>].yaml.
 * Examples:
 *  - publish-availability-monitor_publishing_k8s.yaml
 */
public class InvalidAppConfigFileNameException extends Exception {
  public InvalidAppConfigFileNameException(String message) {
    super(message);
  }
}
