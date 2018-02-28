package com.ft.jenkins.provision

/**
 * There is currently no way to define these constants directly in the ProvisionerUtil script, so putting them in a separate class.
 */
final class ProvisionConstants {

  public static final String REPO_URL = 'https://github.com/Financial-Times/k8s-aws-delivery-poc.git'
  public static final String DOCKER_IMAGE = 'k8s-provisioner'
}
