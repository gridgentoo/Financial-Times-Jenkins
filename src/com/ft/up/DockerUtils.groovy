package com.ft.up

public void pushImageToDH(image, String credentials = "ft.dh.credentials") {
  docker.withRegistry("", credentials) {
    image.push()
  }
}

public def buildImage(String dockerTag, String folder = ".") {
  def image = docker.build(dockerTag, folder)
  return image
}

