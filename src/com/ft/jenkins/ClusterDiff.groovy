package com.ft.jenkins

Map<String, String> prodReleasesMap = parseReleasesIntoMap("annotations-rw-neo4j:0.2.0\n" +
                                                           "brands-rw-neo4j:0.2.0-integrate-helm\n" +
                                                           "burrow:1.0.3-jenkins-test2\n" +
                                                           "burrow22:1.0.3-jenkins-test2\n" +
                                                           "concept-rw-elasticsearch:0.0.14-k8s-jenkins-deploy-test-rc1\n" +
                                                           "concept-search-api:1.0.15-jenkins-test\n" +
                                                           "memberships-rw-neo4j:1.1.6-jenkins-test\n" +
                                                           "next-video-annotations-rw-neo4j:0.3.1-jenkins-test\n" +
                                                           "people-rw-neo4j:pipeline-test\n" +
                                                           "people-rw-neo4j-test:pipeline-test2\n" +
                                                           "synthetic-image-publication-monitor:37.0.2-jenkins-test\n" +
                                                           "v1-annotations-rw-neo4j:0.3.1-jenkins-test\n" +
                                                           "v2-annotations-rw-neo4j:0.3.1-jenkins-test")
Map<String, String> lowerEnvReleasesMap = parseReleasesIntoMap("annotations-rw-neo4j:0.2.0\n" +
                                                               "brands-rw-neo4j:0.2.0-integrate-helm\n" +
                                                               "burrow:1.0.3-jenkins-test2\n" +
                                                               "concept-rw-elasticsearch:0.0.14-k8s-jenkins-deploy-test-rc1\n" +
                                                               "concept-search-api:1.0.15-jenkins-test\n" +
                                                               "memberships-rw-neo4j:1.1.6-jenkins-test\n" +
                                                               "next-video-annotations-rw-neo4j:0.3.1-jenkins-test\n" +
                                                               "people-rw-neo4j:pipeline-test\n" +
                                                               "people-rw-neo4j-test:pipeline-test\n" +
                                                               "synthetic-image-publication-monitor:37.0.2-jenkins-test\n" +
                                                               "v1-annotations-rw-neo4j:0.3.1-jenkins-test\n" +
                                                               "v1-suggestor:0.1.3-jenkins-test\n" +
                                                               "v2-annotations-rw-neo4j:0.3.1-jenkins-test")
difference(prodReleasesMap, lowerEnvReleasesMap)

public Map<String, String> parseReleasesIntoMap(String releases) {
  Map<String, String> releasesMap = new HashMap<>()
  releases.eachLine { line ->
    String[] releaseAndVersion = line.split(":")
    if (releaseAndVersion.length == 2) {
      releasesMap.put(releaseAndVersion[0], releaseAndVersion[1])
    }
  }

  return releasesMap
}

public void difference(Map<String, String> first, Map<String, String> second) {
  first.each { k, v ->
    if (!second.containsKey(k)) {
      println "${k}: service added"
      return
    }

    if (v != second[k]) {
      println "${k}: diff between versions: ${v} -- ${second[k]}"
      return
    }
  }

  second.each { k, v ->
    if (!first.containsKey(k)) {
      println "${k} service removed"
    }
  }
}
