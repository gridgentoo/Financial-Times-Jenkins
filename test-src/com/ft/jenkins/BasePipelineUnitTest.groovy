package com.ft.jenkins

import com.lesfurets.jenkins.unit.BasePipelineTest
import com.lesfurets.jenkins.unit.MethodCall

import org.jenkinsci.plugins.workflow.cps.CpsScript

import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Base pipeline unit test tailored to our repository structure.
 * <p>
 * See <a href="https://github.com/jenkinsci/JenkinsPipelineUnit">JenkinsPipelineUnit</a> documentation for general usage.
 */
public class BasePipelineUnitTest extends BasePipelineTest {

  @Override
  void setUp() throws Exception {
    this.setBaseScriptRoot('k8s-pipeline-library')
    this.setScriptRoots('src', './.')
    this.setScriptExtension('groovy')

    super.setUp()

    helper.scriptBaseClass = CpsScript.class // Fix 'No such property: docker'
    helper.init()
  }

  public static void assertCallIsMade(List<MethodCall> methodCalls, String method, Object... expectedArgs) {
    assertTrue(methodCalls.findAll { call ->
      return call.methodName == method && call.args == expectedArgs
    }.any(), "Method call ${method}(${expectedArgs}) was not called")
  }

  /**
   * Helper for adding a environment value in tests
   */
  protected void addEnvVar(String name, String val) {
    if (!binding.hasVariable('env')) {
      binding.setVariable('env', new Expando(getProperty: { p -> this[p] }, setProperty: { p, v -> this[p] = v }))
    }
    def env = binding.getVariable('env') as Expando
    env[name] = val
  }
}


