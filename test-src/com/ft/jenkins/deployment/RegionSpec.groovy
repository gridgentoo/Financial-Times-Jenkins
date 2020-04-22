package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.Region
import spock.lang.Specification

class RegionSpec extends Specification {

  def "should parse existing region name to correct region enum object"() {
    expect:
    Region.toRegion("eu") == Region.EU
  }

  def "should return null to when a non existing region name is passed"() {
    expect:
    Region.toRegion("non-existing") == null
  }

  def "should return null to when a null region name is passed"() {
    expect:
    Region.toRegion(null) == null
  }

  def "should generate correct jenkins choices parameter values format"() {
    expect:
    Region.toJenkinsChoiceValues([Region.EU, Region.US]) == "all\neu\nus"
    Region.toJenkinsChoiceValues([Region.EU]) == "all\neu"
    Region.toJenkinsChoiceValues([Region.US]) == "all\nus"
  }

  def "should return default value for available region when no regions are passed"() {
    expect:
    Region.toJenkinsChoiceValues([]) == "all"
    Region.toJenkinsChoiceValues(null) == "all"
  }
}
