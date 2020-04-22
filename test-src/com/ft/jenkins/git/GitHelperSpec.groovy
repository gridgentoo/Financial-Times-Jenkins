package com.ft.jenkins.git

import spock.lang.Specification

class GitHelperSpec extends Specification {

  def "should assert false when branch name is not from deploy-on-push type"() {
    expect:
    !GitHelper.isDeployOnPushForBranch("my-test")
  }

  def "should assert true when branch name is from deploy-on-push type"() {
    expect:
    GitHelper.isDeployOnPushForBranch("deploy-on-push/my-test")
  }
}
