package com.ft.jenkins.provision

import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.EnvType
import com.ft.jenkins.cluster.Region
import spock.lang.Specification

class ProvisionersSpec extends Specification {

  def "should get null when cluster update info is null"() {
    expect:
    Provisioners.getClusterUpdateInfo(null) == null
  }

  def "should throw exception when cluster full name is incomplete"() {
    when:
    Provisioners.getClusterUpdateInfo("upp-eu")
    then:
    thrown IllegalArgumentException
  }

  def "should get cluster update info for test environment"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "upp"
      region = Region.EU
      envName = "k8s-dev"
      envType = EnvType.DEVELOPMENT
      clusterType = ClusterType.DELIVERY
      oidcIssuerUrl = "https://upp-k8s-dev-delivery-eu-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("upp-k8s-dev-delivery-eu")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }

  def "should get cluster update info for prod environment"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "pac"
      region = Region.EU
      envName = "prod"
      envType = EnvType.PROD
      clusterType = ClusterType.DELIVERY
      oidcIssuerUrl = "https://pac-prod-delivery-eu-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("pac-prod-delivery-eu")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }

  def "should get cluster update info for staging environment"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "upp"
      region = Region.EU
      envName = "staging"
      envType = EnvType.TEST
      clusterType = ClusterType.DELIVERY
      oidcIssuerUrl = "https://upp-staging-delivery-eu-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("upp-staging-delivery-eu")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }

  def "should get cluster update info for simple test environment"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "upp"
      region = Region.US
      envName = "devcj"
      envType = EnvType.DEVELOPMENT
      clusterType = ClusterType.PUBLISHING
      oidcIssuerUrl = "https://upp-devcj-publish-us-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("upp-devcj-publish-us")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }

  def "should get cluster update info for environment without cluster"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "pac"
      region = Region.US
      envName = "staging-pac"
      envType = EnvType.DEVELOPMENT
      oidcIssuerUrl = "https://pac-staging-us-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("pac-staging-us")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }

  def "should get cluster update info for environment without cluster and composed name"() {
    given:
    ClusterUpdateInfo expectedClusterUpdateInfo = new ClusterUpdateInfo()
    expectedClusterUpdateInfo.with {
      platform = "pac"
      region = Region.US
      envName = "k8s-dev"
      envType = EnvType.DEVELOPMENT
      oidcIssuerUrl = "https://pac-k8s-dev-test-us-dex.upp.ft.com"
    }
    when:
    ClusterUpdateInfo actualClusterUpdateInfo = Provisioners.getClusterUpdateInfo("pac-k8s-dev-test-us")
    then:
    actualClusterUpdateInfo == expectedClusterUpdateInfo
  }
}
