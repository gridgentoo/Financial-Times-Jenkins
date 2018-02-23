package com.ft.jenkins.aws;


import com.ft.jenkins.EnvType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProvisionerUtilUnitTest {

  ProvisionerUtil provisionerUtil = new ProvisionerUtil();

  @Test
  public void testClusterUpdateInfoForNull() {
    assertNull(provisionerUtil.getClusterUpdateInfo(null));
  }

  @Test
  public void testClusterUpdateInfoForInCompleteName() {
    assertThrows(IllegalArgumentException.class, () -> provisionerUtil.getClusterUpdateInfo("upp-delivery-eu"));
  }

  @Test
  public void testClusterUpdateInfoForTestEnv() {
    ClusterUpdateInfo updateInfo = provisionerUtil.getClusterUpdateInfo("upp-k8s-dev-delivery-eu");
    assertAll("update info properties",
              () -> assertEquals("upp", updateInfo.getPlatform()),
              () -> assertEquals("eu", updateInfo.getRegion()),
              () -> assertEquals(EnvType.DEVELOPMENT, updateInfo.getEnvType()),
              () -> assertEquals("k8s-dev", updateInfo.getEnvName()),
              () -> assertEquals("delivery", updateInfo.getCluster())
    );
  }

  @Test
  public void testClusterUpdateInfoForProdEnv() {
    ClusterUpdateInfo updateInfo = provisionerUtil.getClusterUpdateInfo("pac-prod-delivery-eu");
    assertAll("update info properties",
              () -> assertEquals("pac", updateInfo.getPlatform()),
              () -> assertEquals("eu", updateInfo.getRegion()),
              () -> assertEquals(EnvType.PROD, updateInfo.getEnvType()),
              () -> assertEquals("prod", updateInfo.getEnvName()),
              () -> assertEquals("delivery", updateInfo.getCluster())
    );
  }

  @Test
  public void testClusterUpdateInfoForStagingEnv() {
    ClusterUpdateInfo updateInfo = provisionerUtil.getClusterUpdateInfo("pac-staging-neo4j-eu");
    assertAll("update info properties",
              () -> assertEquals("pac", updateInfo.getPlatform()),
              () -> assertEquals("eu", updateInfo.getRegion()),
              () -> assertEquals(EnvType.TEST, updateInfo.getEnvType()),
              () -> assertEquals("staging", updateInfo.getEnvName()),
              () -> assertEquals("neo4j", updateInfo.getCluster())
    );
  }

  @Test
  public void testClusterUpdateInfoForSimpleTestEnv() {
    ClusterUpdateInfo updateInfo = provisionerUtil.getClusterUpdateInfo("upp-devcj-publish-us");
    assertAll("update info properties",
              () -> assertEquals("upp", updateInfo.getPlatform()),
              () -> assertEquals("us", updateInfo.getRegion()),
              () -> assertEquals(EnvType.DEVELOPMENT, updateInfo.getEnvType()),
              () -> assertEquals("devcj", updateInfo.getEnvName()),
              () -> assertEquals("publish", updateInfo.getCluster())
    );
  }
}
