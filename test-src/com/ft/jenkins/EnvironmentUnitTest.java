package com.ft.jenkins;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnvironmentUnitTest {

  @Test
  void testGetClusterDnsName_happy() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY, Cluster.PUBLISHING));
    env.setRegions(Arrays.asList("eu"));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    env.setClusterToApiServerMap(clusterToApiServerMap);
    clusterToApiServerMap.put("eu-" + Cluster.DELIVERY.toString(), "https://upp-k8s-dev-delivery-eu-api.ft.com");
    clusterToApiServerMap.put("eu-" + Cluster.PUBLISHING.toString(), "https://upp-k8s-dev-publish-eu-api.ft.com");

    assertAll(
        () -> assertEquals("upp-k8s-dev-delivery-eu", env.getClusterSubDomain(Cluster.DELIVERY, "eu")),
        () -> assertEquals("upp-k8s-dev-publish-eu", env.getClusterSubDomain(Cluster.PUBLISHING, "eu"))
    );
  }

  @Test
  void testGetClusterDnsName_noRegion() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    env.setClusterToApiServerMap(clusterToApiServerMap);
    clusterToApiServerMap.put(Cluster.DELIVERY.toString(), "https://upp-k8s-dev-delivery-api.ft.com");

    assertEquals("upp-k8s-dev-delivery", env.getClusterSubDomain(Cluster.DELIVERY));
  }

  @Test
  void testGetClusterDnsName_wrongCluster() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    env.setClusterToApiServerMap(clusterToApiServerMap);
    clusterToApiServerMap.put(Cluster.DELIVERY.toString(), "https://upp-k8s-dev-delivery-api.ft.com");

    assertNull(env.getClusterSubDomain(Cluster.PUBLISHING));
  }

  @Test
  void testGetClusterDnsName_noMappingForCluster() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY));
    env.setRegions(Arrays.asList("eu"));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    env.setClusterToApiServerMap(clusterToApiServerMap);

    assertNull(env.getClusterSubDomain(Cluster.DELIVERY));
  }

  @Test
  public void thatGlbNameIsReturned() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY, Cluster.PUBLISHING));
    env.setRegions(Arrays.asList("eu"));

    HashMap<String, String> glbMap = new HashMap<>();
    glbMap.put(Cluster.PUBLISHING.toString(), "https://upp-test-publish.ft.com");
    env.setGlbMap(glbMap);

    assertEquals("https://upp-test-publish.ft.com", env.getGlbUrl(Cluster.PUBLISHING));
  }

  @Test
  public void thatMissingGlbNameIsHandled() {
    Environment env = new Environment();
    env.setName("k8s");
    env.setClusters(Arrays.asList(Cluster.DELIVERY, Cluster.PUBLISHING));
    env.setRegions(Arrays.asList("eu"));

    assertNull(env.getGlbUrl(Cluster.PUBLISHING));
  }
}
