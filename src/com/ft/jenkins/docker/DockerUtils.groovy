package com.ft.jenkins.docker

import static com.ft.jenkins.docker.DockerUtilsConstants.DOCKERHUB_CREDENTIALS
import static com.ft.jenkins.docker.DockerUtilsConstants.DOCKERHUB_URL
import static com.ft.jenkins.docker.DockerUtilsConstants.FT_DOCKER_REGISTRY_CREDENTIALS
import static com.ft.jenkins.docker.DockerUtilsConstants.FT_DOCKER_REGISTRY_NAME


final class DockerUtilsConstants {
  public static final String DOCKERHUB_CREDENTIALS = "ft.dh.credentials"
  public static final String DOCKERHUB_URL = "" // For Jenkins to connect to Dockerhub, it needs the URL empty.
  public static final String FT_DOCKER_REGISTRY_NAME = "nexus.in.ft.com:5000"
  public static final String FT_DOCKER_REGISTRY_CREDENTIALS = "ft.docker_internal.credentials"
}

public void pushImageToDockerReg(image, String dockerRegistryUrl, String credentials = null) {
  docker.withRegistry(dockerRegistryUrl, credentials) {
    image.push()
  }
}

public def buildImage(String dockerTag, String folder = ".") {
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
  if (imageExists(dockerTag)) {
    /*  do not overwrite */
    echo "Docker image ${dockerTag} already exists. Skip building it ..."
    return
  }

  def image = buildImage(dockerTag)

  securityScan(dockerTag)

  boolean useInternalDockerReg = dockerTag.startsWith(FT_DOCKER_REGISTRY_NAME)
  if (useInternalDockerReg) {
    pushImageToDockerReg(image, "https://${FT_DOCKER_REGISTRY_NAME}", FT_DOCKER_REGISTRY_CREDENTIALS)
  } else {
    pushImageToDockerReg(image, DOCKERHUB_URL, DOCKERHUB_CREDENTIALS)
  }
  /*  remove the image after push */
  sh "docker rmi ${image.id}"
}

public boolean imageExists(String tag) {
  try {
    docker.image(tag).pull()
    return true
  } catch (e) {
    return false
  }
}

public void securityScan(String tag) {
    echo "Running security scan with Clair..."
    sh "curl \"https://github.com/arminc/clair-scanner/releases/download/v8/clair-scanner_linux_amd64\" > clair-scanner"
    sh "chmod 750 clair-scanner"
    sh "./clair-scanner --clair=\"http://10.172.43.22:6060\" --ip 10.172.43.22 ${tag}"
    sh "rm clair-scanner"
}

return this // We're returning the script in order to allow it to be loaded in a variable and executed on demand (check DockerUtilsTest for an example)
