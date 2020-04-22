package com.ft.jenkins.deployment

interface HelmCommandGenerator {
  GString generateV3(Map params)

  GString generateV2(Map params)
}
