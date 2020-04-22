package com.ft.jenkins.diffsync

import spock.lang.Specification

class DiffsSpec extends Specification {

  def "should empty helm chart output text be parsed into a map"() {
    when:
    def result = Diffs.parseHelmChartOutputIntoMap("")
    then:
    result.isEmpty()
  }

  def "should helm chart output text be parsed into a map with a number in name"() {
    given:
    def chartName = "generic-rw-s3"
    def version = "1.4.0-k8s-helm-integration-rc3"
    when:
    def result = Diffs.parseHelmChartOutputIntoMap("${chartName}-${version}")
    then:
    !result.isEmpty()
    result.containsKey(chartName)
    version == result.get(chartName)
  }

  def "should helm chart output with multiple lines be parsed into a map"() {
    given:
    def chartName1 = "generic-rw"
    def version1 = "1.4.0-k8s-helm-integration-rc3"
    def chartName2 = "api-policy-component"
    def version2 = "2.0.1"
    def textToParse = "${chartName1}-${version1}\n${chartName2}-${version2}"
    when:
    def result = Diffs.parseHelmChartOutputIntoMap(textToParse)
    then:
    !result.isEmpty()
    result.containsKey(chartName1)
    version1 == result.get(chartName1)
    result.containsKey(chartName2)
    version2 == result.get(chartName2)
  }
}
