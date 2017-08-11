package com.ft.jenkins.git

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test

class GitUtilsTest {

  GitUtils gitUtils = new GitUtils()

  @Test
  public void isDeployOnPushForBranch() {
    assertFalse(gitUtils.isDeployOnPushForBranch("my-test"))
  }
}
