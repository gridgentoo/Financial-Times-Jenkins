package com.ft.jenkins.deployment

import com.ft.jenkins.cluster.Cluster
import com.ft.jenkins.cluster.ClusterType
import com.ft.jenkins.cluster.Environment
import com.ft.jenkins.cluster.Region
import com.ft.jenkins.git.GitHelperConstants
import spock.lang.Specification

import static com.ft.jenkins.cluster.EnvClusterMapEntry.newEntry
import static com.ft.jenkins.cluster.Environment.DEV_NAME

class DeploymentsSpec extends Specification {

  def "should get cluster urls as helm values no region"() {
    given:
    Cluster cluster = new Cluster(ClusterType.DELIVERY)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [Region.EU]
      clusterToApiServerMap = [
              (ClusterType.DELIVERY.toString())      : newEntry(
                      apiServer: "https://delivery-api.ft.com",
                      publicEndpoint: "https://delivery.ft.com"
              ),
              ("${ClusterType.DELIVERY}1".toString()): newEntry(
                      apiServer: "https://delivery1-api.ft.com",
                      publicEndpoint: "https://delivery1.ft.com"
              )
      ]
    }
    when:
    String actualOutput = Deployments.getClusterUrlsAsHelmValues(env, env.cluster.clusterType, null)
    String expectedOutput = " --set cluster.delivery.url=https://delivery.ft.com"
    then:
    actualOutput == expectedOutput
  }

  def "should get cluster urls as helm values with region"() {
    given:
    Cluster cluster = new Cluster(ClusterType.DELIVERY)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      regions = [Region.EU, Region.US]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://delivery-dev-eu-api.ft.com",
                      publicEndpoint: "https://delivery-dev-eu.ft.com"
              ),
              ("${Region.US}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://delivery-dev-us-api.ft.com",
                      publicEndpoint: "https://delivery-dev-us.ft.com"
              )
      ]
    }
    when:
    String actualOutputEu = Deployments.getClusterUrlsAsHelmValues(env, env.cluster.clusterType, Region.EU)
    String expectedOutputEu = " --set cluster.delivery.url=https://delivery-dev-eu.ft.com"

    String actualOutputUs = Deployments.getClusterUrlsAsHelmValues(env, env.cluster.clusterType, Region.US)
    String expectedOutputUs = " --set cluster.delivery.url=https://delivery-dev-us.ft.com"
    then:
    actualOutputEu == expectedOutputEu
    actualOutputUs == expectedOutputUs
  }

  def "should return glb urls as helm values"() {
    given:
    Cluster cluster = new Cluster(ClusterType.PAC)
    Environment env = new Environment(DEV_NAME, cluster)
    env.with {
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-test-publishing.ft.com",
      ]
    }
    when:
    String actualOutput = Deployments.getGlbUrlsAsHelmValues(env)
    String expectedOutput = " --set glb.publishing.url=https://upp-test-publishing.ft.com"
    then:
    actualOutput == expectedOutput
  }

  def "should ignore missing glb urls when converting to helm values"() {
    given:
    Cluster cluster = new Cluster(ClusterType.PAC)
    Environment env = new Environment(DEV_NAME, cluster)
    when:
    String actualOutput = Deployments.getGlbUrlsAsHelmValues(env)
    then:
    actualOutput.isEmpty()
  }

  def "should throw exception when no environment defined"() {
    when:
    Deployments.getEnvironmentName(GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX + "myfeature")
    then:
    thrown IllegalArgumentException
  }

  def "should get environment name from branch name with slash"() {
    given:
    def expectedEnv = DEV_NAME
    def branchName = "${GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX}${expectedEnv}/mytopic/subtopic".toString()
    when:
    def actualEnv = Deployments.getEnvironmentName(branchName)
    then:
    actualEnv == expectedEnv
  }

  def "should get environment name from normal branch name"() {
    given:
    def expectedEnv = DEV_NAME
    def branchName = "${GitHelperConstants.DEPLOY_ON_PUSH_BRANCHES_PREFIX}${expectedEnv}/mytopic".toString()
    when:
    def actualEnv = Deployments.getEnvironmentName(branchName)
    then:
    actualEnv == expectedEnv
  }
}
