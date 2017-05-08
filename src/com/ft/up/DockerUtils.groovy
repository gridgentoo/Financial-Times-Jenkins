package com.ft.up


class DockerUtils {

  private static final String DH_CREDENTIALS = 'ft.dh.credentials'

  public void pushImageToDH(image) {
    docker.withRegistry("", DH_CREDENTIALS) {
      image.push()
    }
  }

  public def buildImage(String dockerTag, String folder = ".") {
    def image = docker.build(dockerTag, folder)
    return image
  }

}
