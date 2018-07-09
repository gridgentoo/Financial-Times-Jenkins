package com.ft.jenkins;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeploymentUtilTest {

  DeploymentUtils deploymentUtils = new DeploymentUtils();

  @Test
  public void testGetClusterUrlsAsHelmValuesNoRegion() {
    Environment environment = new Environment();
    environment.setClusters(Arrays.asList(Cluster.DELIVERY, Cluster.PUBLISHING));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    clusterToApiServerMap.put(Cluster.DELIVERY.toString(), "https://delivery-api.ft.com");
    clusterToApiServerMap.put(Cluster.PUBLISHING.toString(), "https://publishing-api.ft.com");
    environment.setClusterToApiServerMap(clusterToApiServerMap);
    String clusterUrlsAsHelmValues = deploymentUtils.getClusterUrlsAsHelmValues(environment, null);

    assertEquals(
        " --set cluster.delivery.url=https://delivery.ft.com --set cluster.publishing.url=https://publishing.ft.com",
        clusterUrlsAsHelmValues);

  }

  @Test
  public void testGetClusterUrlsAsHelmValuesWithRegion() {
    Environment environment = new Environment();
    environment.setClusters(Arrays.asList(Cluster.DELIVERY, Cluster.PUBLISHING));
    environment.setRegions(Arrays.asList("eu", "us"));

    HashMap<String, String> clusterToApiServerMap = new HashMap<>();
    clusterToApiServerMap.put("eu-" + Cluster.DELIVERY.toString(), "https://delivery-eu-api.ft.com");
    clusterToApiServerMap.put("eu-" + Cluster.PUBLISHING.toString(), "https://publishing-eu-api.ft.com");
    clusterToApiServerMap.put("us-" + Cluster.DELIVERY.toString(), "https://delivery-us-api.ft.com");
    clusterToApiServerMap.put("us-" + Cluster.PUBLISHING.toString(), "https://publishing-us-api.ft.com");
    environment.setClusterToApiServerMap(clusterToApiServerMap);

    assertAll(
        () -> {
          String clusterUrlsAsHelmValues = deploymentUtils.getClusterUrlsAsHelmValues(environment, "eu");
          assertEquals(
              " --set cluster.delivery.url=https://delivery-eu.ft.com --set cluster.publishing.url=https://publishing-eu.ft.com",
              clusterUrlsAsHelmValues);
        },
        () -> {
          String clusterUrlsAsHelmValues = deploymentUtils.getClusterUrlsAsHelmValues(environment, "us");
          assertEquals(
              " --set cluster.delivery.url=https://delivery-us.ft.com --set cluster.publishing.url=https://publishing-us.ft.com",
              clusterUrlsAsHelmValues);

        }
    );

  }

  @Test
  public void thatGlbUrlsAreReturnedAsHelmParams() {
    Environment environment = new Environment();
    environment.setClusters(Arrays.asList(Cluster.PAC));
    HashMap<String, String> glbMap = new HashMap<>();
    
    glbMap.put(Cluster.PUBLISHING.toString(), "https://upp-test-publishing.ft.com");
    environment.setGlbMap(glbMap);

    String actual = deploymentUtils.getGlbUrlsAsHelmValues(environment);

    assertEquals(
        " --set glb.publishing.url=https://upp-test-publishing.ft.com",
        actual);
  }

  @Test
  public void thatMissingGlbUrlsAreIgnored() {
    Environment environment = new Environment();
    environment.setClusters(Arrays.asList(Cluster.PAC));

    String actual = deploymentUtils.getGlbUrlsAsHelmValues(environment);

    assertEquals("", actual);
  }
}
