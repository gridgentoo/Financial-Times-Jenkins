# k8s-pipeline-library

## Description
Jenkins shared pipeline library to be used for deployment in Kubernetes clusters.

## Documentation
[Deployment in k8s](https://docs.google.com/a/ft.com/document/d/15ecubJwkszH1B360Ah31uXy2UekpWlgfEmQeH9_wko8/edit?usp=sharing)

## What to do when adding a new environment
When provisioning a new environment, Jenkins needs to "see" it, in order to be able to deploy to it.
Here are the steps needed in order for Jenkins to "see" it.
1.  Create a new branch for this repository
1. Add the definition of the new environment in the EnvsRegistry.groovy. Here's an example:

        Environment preProd = new Environment()
        preProd.name = Environment.PRE_PROD_NAME
        preProd.slackChannel = "#k8s-pipeline-notif"
        preProd.regions = ["eu", "us"]
        preProd.clusterToApiServerMap = [
            ("eu-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
            ("us-" + Cluster.DELIVERY)  : "https://k8s-delivery-upp-eu-api.ft.com",
            ("eu-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com",
            ("us-" + Cluster.PUBLISHING): "https://k8s-pub-upp-eu-api.ft.com"
        ]
        
    An Environment has a name and a notifications slack channel. He might be deployed in multiple regions, and in each 
region, it might have multiple clusters. For each cluster we must define the URL of the K8S APi server.

1.  Define in Jenkins the credentials needed for accessing the K8S API servers. 
For each of the API servers in the environment Jenkins needs 3 keys in order to access it, therefore you need to create 3 Jenkins credentials / environment that are of type "Secret File" with the following ids
    1. ft.k8s-auth.${cluster_label}-${env_name}[-${region}].ca-cert -> this is the certificate of the CA used when generating the certificates -> ca.pem from the kubeconfig credentials
    1. ft.k8s-auth.${cluster_label}-${env_name}[-${region}].client-certificate -> this is the certificate of the user used to authenticate in the k8s cluster -> admin.pem from the kubeconfig credentials
    1. ft.k8s-auth.${cluster_label}-${env_name}[-${region}].client-key -> this is the private key of the user used to authenticate in the k8s cluster -> admin-key.pem from the kubeconfig credentials
    
1. Push the branch and create a Pull Request.
    
