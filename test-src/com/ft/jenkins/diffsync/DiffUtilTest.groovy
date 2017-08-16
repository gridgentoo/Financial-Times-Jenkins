package com.ft.jenkins.diffsync

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class DiffUtilTest {

  DiffUtil diffUtil = new DiffUtil()

  @Test
  void parseHelmChartOutputIntoMapWithEmpty() {
    Map<String, String> result = diffUtil.parseHelmChartOutputIntoMap("")
    assertTrue(result.isEmpty())
  }

  @Test
  void parseHelmChartOutputIntoMapWithNumberInName() {
    String chartName = "generic-rw-s3"
    String version = "1.4.0-k8s-helm-integration-rc3"
    Map<String, String> result = diffUtil.parseHelmChartOutputIntoMap("${chartName}-${version}")

    assertFalse(result.isEmpty())
    assertTrue(result.containsKey(chartName), "The chart version was parsed correctly")
    assertEquals(version, result.get(chartName))
  }

  @Test
  void parseHelmChartOutputWithMultipleLines() {
    String chartName1 = "generic-rw"
    String version1 = "1.4.0-k8s-helm-integration-rc3"
    String chartName2 = "api-policy-component"
    String version2 = "2.0.1"

    String textToParse = "${chartName1}-${version1}\n${chartName2}-${version2}"
    Map<String, String> result = diffUtil.parseHelmChartOutputIntoMap(textToParse)

    assertFalse(result.isEmpty())
    assertTrue(result.containsKey(chartName1), "The chart version was parsed correctly")
    assertEquals(version1, result.get(chartName1))
    assertTrue(result.containsKey(chartName2), "The chart version was parsed correctly")
    assertEquals(version2, result.get(chartName2))
  }


}
