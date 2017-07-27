package com.ft.jenkins.docker

import static com.ft.jenkins.docker.DockerUtilsConstants.DOCKERHUB_CREDENTIALS
import static com.ft.jenkins.docker.DockerUtilsConstants.DOCKERHUB_URL
import static com.ft.jenkins.docker.DockerUtilsConstants.FT_DOCKER_REGISTRY_NAME

final class DockerUtilsConstants {
  public static final String DOCKERHUB_CREDENTIALS = "ft.dh.credentials"
  public static final String DOCKERHUB_URL = ""; // For Jenkins to connect to Dockerhub, it needs the URL empty.
  public static final String FT_DOCKER_REGISTRY_NAME = "up-registry.ft.com"
}

private void pushImageToDockerReg(image, String dockerRegistryUrl, String credentials = null) {
  docker.withRegistry(dockerRegistryUrl, credentials) {
    image.push()
  }
}

private def buildImage(String dockerTag, String folder = ".") {
  def image
  /*  adding the internal nexus credentials to support the maven apps connecting to Nexus */
  withCredentials([usernamePassword(credentialsId: "nexus.credentials", passwordVariable: 'NEXUS_PASSWORD',
                                    usernameVariable: 'NEXUS_USERNAME')]) {
    image = docker.build(dockerTag,
                         "--build-arg SONATYPE_USER=${env.NEXUS_USERNAME} --build-arg SONATYPE_PASSWORD=${env.NEXUS_PASSWORD} ${folder}")
  }
  return image
}

public void buildAndPushImage(String dockerTag) {
  def image = buildImage(dockerTag)
  boolean useInternalDockerReg = dockerTag.startsWith(FT_DOCKER_REGISTRY_NAME)
  if (useInternalDockerReg) {
    pushImageToDockerReg(image, "https://${FT_DOCKER_REGISTRY_NAME}")
  } else {
    pushImageToDockerReg(image, DOCKERHUB_URL, DOCKERHUB_CREDENTIALS)
  }
}