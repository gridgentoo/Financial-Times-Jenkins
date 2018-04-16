package com.ft.jenkins;

import org.junit.jupiter.api.Test;

import static com.ft.jenkins.EnvsRegistry.getEnvironmentByFullName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EnvsRegistryUnitTest {

  @Test
  void testGetEnvByClusterFullName_forNull() {
    assertNull(getEnvironmentByFullName(null));
  }

  @Test
  void testGetEnvByClusterFullName_forNonExistingEng() {
    assertNull(getEnvironmentByFullName("not-existing"));
  }

  @Test
  void testGetEnvByClusterFullName_forProd() {
    assertEquals(EnvsRegistry.getEnvironment(Environment.PROD_NAME),
                 getEnvironmentByFullName("upp-prod-publish-eu"));
  }
  @Test
  void testGetEnvByClusterFullName_forStaging() {
    assertEquals(EnvsRegistry.getEnvironment(Environment.STAGING_NAME),
                 getEnvironmentByFullName("upp-staging-delivery-eu"));
  }
}
