package com.ft.jenkins

import com.lesfurets.jenkins.unit.BasePipelineTest

import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Base integration test tailored to our repository structure.
 * <p>
 * See <a href="https://github.com/jenkinsci/JenkinsPipelineUnit">JenkinsPipelineUnit</a> documentation for general usage.
 */
public class BaseIntegrationTest extends BasePipelineTest {

  @Override
  void setUp() throws Exception {
    this.setBaseScriptRoot('k8s-pipeline-library')
    this.setScriptRoots('src', './.')
    this.setScriptExtension('groovy')

    super.setUp()

    helper.scriptBaseClass = CpsScript.class // Fix 'No such property: docker'
    helper.init()
  }
}
