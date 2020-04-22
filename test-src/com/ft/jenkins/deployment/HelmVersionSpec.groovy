package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.Cluster
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region
import spock.lang.Specification

import static com.ft.jenkins.cluster.EnvClusterMapEntry.newEntry
import static com.ft.jenkins.cluster.Environment.DEV_NAME

class HelmVersionSpec extends Specification {

  def "should discover helm version based on environment and region when cluster url is hardcoded"() {
    given:
    Cluster cluster = new Cluster(ClusterType.DELIVERY)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
    }
    when:
    def actualHelmVersion = HelmVersion.discoverVersion(env, Region.EU)
    def expectedHelmVersion = HelmVersion.V2
    then:
    actualHelmVersion == expectedHelmVersion
  }

  def "should throw exception when based on environment and region there is no cluster url"() {
    given:
    def region = Region.EU
    def clusterType = ClusterType.DELIVERY
    Cluster cluster = new Cluster(clusterType)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [region]
      clusterToApiServerMap = [
              ("${region}-${clusterType}".toString()): newEntry(
                      apiServer: null,
                      publicEndpoint: null
              )
      ]
    }
    when:
    HelmVersion.discoverVersion(env, region)
    then:
    def exception = thrown IllegalArgumentException
    exception.message == "Cannot discover helm version for cluster type '${clusterType.label}' region '${region.name}' because the is no available cluster URL"
  }

  def "should throw exception when based on environment and region there is cluster to environments mapping"() {
    given:
    def region = Region.EU
    def clusterType = ClusterType.DELIVERY
    Cluster cluster = new Cluster(clusterType)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [region]
    }
    when:
    HelmVersion.discoverVersion(env, region)
    then:
    def exception = thrown IllegalArgumentException
    exception.message == "Cannot discover helm version for cluster type '${clusterType.label}' region '${region.name}' because the is no available cluster URL"
  }
}
