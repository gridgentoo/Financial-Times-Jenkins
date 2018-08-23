package com.ft.jenkins

import org.junit.jupiter.api.BeforeEach

public class DeploymentUtilIntegTest extends BaseIntegrationTest {

  private Object script

  @Override
  @BeforeEach
  void setUp() throws Exception {
    super.setUp()

    helper.registerAllowedMethod('readYaml', [String.class], null)
    script = loadScript("com/ft/jenkins/DeploymentUtils.groovy")
  }


}
