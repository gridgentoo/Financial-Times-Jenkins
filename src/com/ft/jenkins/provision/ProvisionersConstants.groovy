package com.ft.jenkins.provision

/**
 * There is currently no way to define these constants directly in the Provisioners script, so putting them in a separate class.
 */
final class ProvisionersConstants {
  public static final String REPO_URL = 'https://github.com/Financial-Times/content-k8s-provisioner.git'
  public static final String DOCKER_IMAGE = 'k8s-provisioner'
}
