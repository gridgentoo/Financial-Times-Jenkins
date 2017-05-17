package com.ft.up

import static com.ft.up.DockerUtilsConstants.*

final class DockerUtilsConstants {

  public static final String DOCKERHUB_CREDENTIALS = "ft.dh.credentials"
  public static final String DOCKERHUB_URL = ""; // For Jenkins to connect to Dockerhub, it needs the URL empty.
  public static final String FT_DOCKER_REGISTRY_URL = "https://up-registry.ft.com"
}

private void pushImageToDockerReg(image, String dockerRegistryUrl, String credentials = null) {
  docker.withRegistry(dockerRegistryUrl, credentials) {
    image.push()
  }
}

private def buildImage(String dockerTag, String folder = ".") {
  def image = docker.build(dockerTag, folder)
  return image
}

public void buildAndPushImage(String dockerTag, boolean useInternalDockerReg) {
  def image = buildImage(dockerTag)
  if (!useInternalDockerReg) {
    pushImageToDockerReg(image, DOCKERHUB_URL, DOCKERHUB_CREDENTIALS)
  }
  else {
    pushImageToDockerReg(image, FT_DOCKER_REGISTRY_URL)
  }
}