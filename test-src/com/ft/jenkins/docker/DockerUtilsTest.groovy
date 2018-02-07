package com.ft.jenkins.docker

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.jenkinsci.plugins.docker.workflow.DockerDSL
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito

class DockerUtilsTest extends BasePipelineTest {
  private static final String DOCKER_TEST_IMAGE_AND_TAG = "coco/test-image:test-tag"

  private Object script
  private Object dockerMock

  @Override
  @BeforeEach
  void setUp() throws Exception {
    this.setBaseScriptRoot('k8s-pipeline-library')
    this.setScriptRoots('src/com/ft/jenkins/docker')
    this.setScriptExtension('groovy')
    helper.with {
      it.scriptRoots = this.scriptRoots
      it.scriptExtension = this.scriptExtension
      it.baseClassloader = this.baseClassLoader
      it.scriptBaseClass = CpsScript.class // Fix 'No such property: docker'
      it.registerAllowedMethod('withCredentials', [Map.class, Closure.class], null)
      it.registerAllowedMethod('withCredentials', [List.class, Closure.class], null)
      it.registerAllowedMethod("usernamePassword", [Map.class], { creds -> "bcc19744" })
      it.registerAllowedMethod('docker', [String.class], null)
      it.registerAllowedMethod('docker.build', [String.class], null)
      it.baseScriptRoot = this.baseScriptRoot
      return it
    }.init()

    script = loadScript("DockerUtils.groovy")
    Object docker = new DockerDSL().getValue(script) // Fix 'No such property: docker'
    dockerMock = Mockito.mock(docker.class, Answers.RETURNS_DEEP_STUBS)
    binding.setVariable('docker', dockerMock) // Fix 'No such property: docker'

    addEnvVar('NEXUS_USERNAME', 'placeholder_username')
    addEnvVar('NEXUS_PASSWORD', 'placeholder_password')

    super.setUp()
  }

  @Test
  void isExecutingWithoutBuilding() throws Exception {
    script.buildAndPushImage(DOCKER_TEST_IMAGE_AND_TAG)

    Assert.assertFalse(helper.callStack.findAll { call ->
      call.methodName == 'withCredentials' // Checking if the withCredentials method is called. If it is, then the image is built. Check DockerUtils for more.
    }.any())
  }

  @Test
  void isExecutingWithBuilding() throws Exception {
    Mockito.when(dockerMock.image(Mockito.any())).thenThrow(new RuntimeException())

    script.buildAndPushImage(DOCKER_TEST_IMAGE_AND_TAG)
    Assert.assertTrue(helper.callStack.findAll { call ->
      call.methodName == 'withCredentials' // Checking if the withCredentials method is called. If it is, then the image is built. Check DockerUtils for more.
    }.any())
  }

  /**
   * Helper for adding a environment value in tests
   */
  void addEnvVar(String name, String val) {
    if (!binding.hasVariable('env')) {
      binding.setVariable('env', new Expando(getProperty: { p -> this[p] }, setProperty: { p, v -> this[p] = v }))
    }
    def env = binding.getVariable('env') as Expando
    env[name] = val
  }
}
