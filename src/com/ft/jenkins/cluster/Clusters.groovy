package com.ft.jenkins.cluster

import com.cloudbees.groovy.cps.NonCPS

import static EnvClusterMapEntry.newEksEntry
import static EnvClusterMapEntry.newEntry
import static com.ft.jenkins.cluster.Environment.*

class Clusters implements Serializable {

  private static final String SLACK_CHANNEL = "#upp-changes"

  @NonCPS
  static Cluster initDeliveryCluster() {
    Cluster deliveryCluster = new Cluster(ClusterType.DELIVERY)
    Environment devEnv = new Environment(DEV_NAME, deliveryCluster)
    devEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU]
      associatedClusterTypes = [ClusterType.DELIVERY, ClusterType.PUBLISHING]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu.upp.ft.com"
      ]
    }

    Environment k8sEnv = new Environment("k8s", deliveryCluster)
    k8sEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU]
      associatedClusterTypes = [ClusterType.DELIVERY, ClusterType.PUBLISHING]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-k8s-dev-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-k8s-dev-delivery-eu.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu.upp.ft.com"
      ]
    }

    Environment stagingEnv = new Environment(STAGING_NAME, deliveryCluster)
    stagingEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.DELIVERY, ClusterType.PUBLISHING]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-staging-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-staging-delivery-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-staging-delivery-us-api.upp.ft.com",
                      publicEndpoint: "https://upp-staging-delivery-us.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-staging-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-staging-delivery-glb.upp.ft.com"
      ]
    }

    Environment prodEnv = new Environment(PROD_NAME, deliveryCluster)
    prodEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.DELIVERY, ClusterType.PUBLISHING]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-prod-delivery-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-prod-delivery-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.DELIVERY}".toString()): newEntry(
                      apiServer: "https://upp-prod-delivery-us-api.upp.ft.com",
                      publicEndpoint: "https://upp-prod-delivery-us.upp.ft.com",
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-prod-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-prod-delivery-glb.upp.ft.com"
      ]
    }

    deliveryCluster.environments = [devEnv, k8sEnv, stagingEnv, prodEnv]
    deliveryCluster
  }

  @NonCPS
  static Cluster initPublishingCluster() {
    Cluster publishingCluster = new Cluster(ClusterType.PUBLISHING)
    Environment devEnv = new Environment(DEV_NAME, publishingCluster)
    devEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU]
      associatedClusterTypes = [ClusterType.PUBLISHING, ClusterType.DELIVERY]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PUBLISHING}".toString()): newEksEntry(
                      eksClusterName: "eks-publish-dev-eu",
                      apiServer: "https://AB728E3942288F827ACE2853A581F4CD.sk1.eu-west-1.eks.amazonaws.com/",
                      publicEndpoint: "https://upp-k8s-dev-publish-eu.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu.upp.ft.com"
      ]
    }

    Environment k8sEnv = new Environment("k8s", publishingCluster)
    k8sEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU]
      associatedClusterTypes = [ClusterType.PUBLISHING, ClusterType.DELIVERY]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PUBLISHING}".toString()): newEksEntry(
                      eksClusterName: "eks-publish-dev-eu",
                      apiServer: "https://AB728E3942288F827ACE2853A581F4CD.sk1.eu-west-1.eks.amazonaws.com/",
                      publicEndpoint: "https://upp-k8s-dev-publish-eu.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-k8s-dev-publish-eu.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-k8s-dev-delivery-eu.upp.ft.com"
      ]
    }

    Environment stagingEnv = new Environment(STAGING_NAME, publishingCluster)
    stagingEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.PUBLISHING, ClusterType.DELIVERY]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PUBLISHING}".toString()): newEntry(
                      apiServer: "https://upp-staging-publish-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-staging-publish-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.PUBLISHING}".toString()): newEntry(
                      apiServer: "https://upp-staging-publish-us-api.upp.ft.com",
                      publicEndpoint: "https://upp-staging-publish-us.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-staging-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-staging-delivery-glb.upp.ft.com"
      ]
    }

    Environment prodEnv = new Environment(PROD_NAME, publishingCluster)
    prodEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.PUBLISHING, ClusterType.DELIVERY]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PUBLISHING}".toString()): newEntry(
                      apiServer: "https://upp-prod-publish-eu-api.upp.ft.com",
                      publicEndpoint: "https://upp-prod-publish-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.PUBLISHING}".toString()): newEntry(
                      apiServer: "https://upp-prod-publish-us-api.upp.ft.com",
                      publicEndpoint: "https://upp-prod-publish-us.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-prod-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-prod-delivery-glb.upp.ft.com"
      ]
    }
    publishingCluster.environments = [devEnv, k8sEnv, stagingEnv, prodEnv]
    publishingCluster
  }

  @NonCPS
  static Cluster initPacCluster() {
    Cluster pacCluster = new Cluster(ClusterType.PAC)
    Environment stagingEnv = new Environment(STAGING_NAME, pacCluster)
    stagingEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.PAC]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PAC}".toString()): newEksEntry(
                      eksClusterName: "eks-pac-staging-eu",
                      apiServer: "https://865A92BB63716CCB8BDBB6EC14BEF6D0.sk1.eu-west-1.eks.amazonaws.com/",
                      publicEndpoint: "https://pac-staging-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.PAC}".toString()): newEksEntry(
                      eksClusterName: "eks-pac-staging-us",
                      apiServer: "https://b8fa2f079fa1c44a2819dfae9062ee7b.gr7.us-east-1.eks.amazonaws.com/",
                      publicEndpoint: "https://pac-staging-us.upp.ft.com"
              )
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-staging-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-staging-delivery-glb.upp.ft.com"
      ]
    }

    Environment prodEnv = new Environment(PROD_NAME, pacCluster)
    prodEnv.with {
      slackChannel = SLACK_CHANNEL
      regions = [Region.EU, Region.US]
      associatedClusterTypes = [ClusterType.PAC]
      clusterToApiServerMap = [
              ("${Region.EU}-${ClusterType.PAC}".toString()): newEntry(
                      apiServer: "https://pac-prod-eu-api.upp.ft.com",
                      publicEndpoint: "https://pac-prod-eu.upp.ft.com"
              ),
              ("${Region.US}-${ClusterType.PAC}".toString()): newEntry(
                      apiServer: "https://pac-prod-us-api.upp.ft.com",
                      publicEndpoint: "https://pac-prod-us.upp.ft.com"
              ),
      ]
      glbMap = [
              (ClusterType.PUBLISHING.toString()): "https://upp-prod-publish-glb.upp.ft.com",
              (ClusterType.DELIVERY.toString())  : "https://upp-prod-delivery-glb.upp.ft.com"
      ]
    }
    pacCluster.environments = [stagingEnv, prodEnv]
    pacCluster
  }
}
