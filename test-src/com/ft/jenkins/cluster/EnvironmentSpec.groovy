package com.ft.jenkins.cluster

import spock.lang.Specification

import static EnvClusterMapEntry.newEntry
import static com.ft.jenkins.cluster.Environment.DEV_NAME

class EnvironmentSpec extends Specification {

  def "should get cluster with region dns name"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
    }
    when:
    String actualOutput = devEnv.getClusterSubDomain(ClusterType.DELIVERY, Region.EU)
    String expectedOutput = "upp-k8s-dev-delivery-eu"

    then:
    actualOutput == expectedOutput
  }

  def "should get correct api server url for cluster"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
    }
    when:
    String actualOutput = devEnv.getClusterMapEntry(ClusterType.DELIVERY, Region.EU)?.apiServer
    String expectedOutput = "https://upp-k8s-dev-delivery-eu-api.upp.ft.com"

    then:
    actualOutput == expectedOutput
  }

  def "should get cluster without region dns name"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      clusterToApiServerMap = [
              (ClusterType.DELIVERY.toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery.upp.ft.com"
              )
      ]
    }
    when:
    String actualOutput = devEnv.getClusterSubDomain(ClusterType.DELIVERY)
    String expectedOutput = "upp-k8s-dev-delivery"
    then:
    actualOutput == expectedOutput
  }

  def "should not get wrong cluster dns name"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      clusterToApiServerMap = [
              (ClusterType.DELIVERY.toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery.upp.ft.com"
              )
      ]
    }
    when:
    String actualOutput = devEnv.getClusterSubDomain(ClusterType.PUBLISHING)
    then:
    actualOutput == null
  }

  def "should not get cluster without environment mapping dns name"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      regions = [Region.EU]
      clusterToApiServerMap = [:]
    }
    when:
    String actualOutput = devEnv.getClusterSubDomain(ClusterType.DELIVERY, Region.EU)
    then:
    actualOutput == null
  }

  def "should get glb url"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      regions = [Region.EU]
      glbMap = [(ClusterType.PUBLISHING.toString()): "https://upp-test-publish.ft.com"]
    }
    when:
    String actualOutput = devEnv.getGlbUrl(ClusterType.PUBLISHING)
    String expectedOutput = "https://upp-test-publish.ft.com"
    then:
    actualOutput == expectedOutput
  }

  def "should not get missing glb url"() {
    given:
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      regions = [Region.EU]
    }
    when:
    String actualOutput = devEnv.getGlbUrl(ClusterType.PUBLISHING)
    then:
    actualOutput == null
  }
}
