# k8s-pipeline-library

## Description
Jenkins shared pipeline library to be used for deployment in Kubernetes clusters.

## Documentation
[Deployment in k8s - spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk)

### Helm integration
On every helm install/upgrade the following values are automatically inserted:

1. `region`: the reigon where the targeted cluster lives. Example: `eu`, `us`
1. `target_env`: the name of the environment as defined in the Environment registry. Example: `k8s`, `prod`
1. `__ext.target_cluster.sub_domain`: the DNS subdomain of the targeted cluster. This is computed from the mapped API server declared in the EnvsRegistry. Example: `upp-prod-publish-us`, `pac-prod-eu`
1. For every cluster in the targeted environment, the URLs are exposed with the values `cluster.${cluster_label}.url`. Example: `--set cluster.delivery.url=https://upp-k8s-dev-delivery-eu.ft.com --set cluster.publishing.url=https://upp-k8s-dev-publish-eu.ft.com`

*NOTE*: in the future all these values will be moved under the `__ext` namespace to avoid clashes with other developer introduced values.

## What to do when adding a new environment
When provisioning a new environment, Jenkins needs to "see" it, in order to be able to deploy to it.
Here are the steps needed in order for Jenkins to "see" it.

1.  Create a new branch for this repository
1. Add the definition of the new environment in the EnvsRegistry.groovy. Here's an example:
    ```
          Environment prod = new Environment()
          prod.name = Environment.PROD_NAME
          prod.slackChannel = "#k8s-pipeline-notif"
          prod.regions = ["eu", "us"]
          prod.clusters = [Cluster.DELIVERY, Cluster.PUBLISHING, Cluster.NEO4J]
          prod.clusterToApiServerMap = [
              ("eu-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-eu-api.ft.com",
              ("us-" + Cluster.DELIVERY)  : "https://upp-prod-delivery-us-api.ft.com",
              ("eu-" + Cluster.PUBLISHING): "https://upp-prod-publish-eu-api.ft.com",
              ("us-" + Cluster.PUBLISHING): "https://upp-prod-publish-us-api.ft.com",
              ("eu-" + Cluster.NEO4J): "https://upp-prod-neo4j-eu-api.ft.com",
              ("us-" + Cluster.NEO4J): "https://upp-prod-neo4j-us-api.ft.com"
          ]
    ```
    Here are the characteristics of an Environment:
      1. It has a name and a notifications slack channel.
      1. It might be spread across multiple AWS regions
      1. In each region, it might have multiple clusters (stacks).
      1. For each cluster(stack) we must define the URL of the K8S APi server.
1. Don't forget to add the newly defined environment to the `envs` list in the EnvsRegistry class.
1. Define in [Jenkins](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/) the credentials needed for accessing the K8S API servers.
For each of the API servers in the environment Jenkins needs 1 key in order to access it, therefore you need to create 1 Jenkins credential / cluster that are of type `Secret Text` with the following ids
    1. `ft.k8s-auth.${full-cluster-name}.token` (example `ft.k8s-auth.upp-k8s-dev-delivery-eu.token`) -> this is the token of the Jenkins service account from the Kubernetes cluster.
1. Define in [Jenkins](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/) the credentials with the TLS assets of the cluster.
   This will be used when updating the kubernetes cluster using (this Jenkins job)[Update a Kubernetes cluster](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/update-cluster/)
   The credential must be named `ft.k8s-provision.${full-cluster-name}.credentials`. Example `ft.k8s-provision.upp-k8s-dev-delivery-eu.credentials`.
   The type must be `Secret file` and the zip should be fetched from Last Pass.
1. Push the branch and create a Pull Request.
1. After merge, add the new environment to the Jenkins jobs:
    1. [Deploys a helm chart from the upp repo](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/deploy-upp-helm-chart/)
    1. [Update a Kubernetes cluster](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/update-cluster/)

# Developer documentation
## Intellij Idea project setup
Steps:

1. Make sure you have Groovy language support plugin enabled in Intellij
1. Import the project from the maven POM: File -> New -> Project from existing sources -> go to project folder & select -> choose External model -> Maven
1. Set `var` and `intellij-gdsl` as Source folders

With this setup you will have completion in groovy files for out of the box functions injected by pipeline plugins in Jenkins.
This might help you in some cases.

## Pipeline development tips & tricks
### How do I know what functions are available OOTB ?
You have 2 options:

1. Checkout the pipeline syntax page. Go to any pipeline job & click the "Pipeline syntax". [Here is a link](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/diff-between-envs/pipeline-syntax/) to such page.
    This page generates snipets that you can paste into your script.
1. Use Intellij with GDSL (see setup above). This might not be useful sometimes, as the parameters are maps.
### Recommendations

1. Prefer using docker images that you can control over Jenkins plugins. Depending on Jenkins plugins makes Jenkins hard to upgrade.
   The pipeline steps support running logic inside docker containers, so they are recommended, especially if you need command line tools.
   As an example, we're using the [k8s-cli-utils](https://github.com/Financial-Times/k8s-cli-utils) docker image for making *kubectl* or *helm calls* instead of relying on a plugin that installs these utilities on the Jenkins slaves.
2.  Always declare types and avoid using “def”
3. Use the *@NonCPS* annotation for methods that use objects that are not serializable. See the docs [here](https://github.com/jenkinsci/workflow-cps-plugin/blob/master/README.md) and a practical example in [this stackoverflow question](https://stackoverflow.com/questions/42295921/what-is-the-effect-on-noncps-in-a-jenkins-pipeline-script).
5.  In order to test some code you can “*Replay*” a job run and place the code changes directly in the window.


## How to test and roll out pipeline changes
See [How to test and roll out pipeline changes](Test_pipeline_changes.md)
## Pipeline integration points
The pipeline has several integration points to achieving its goals. For this it keeps the secret data (API keys, username & passwords) as [Jenkins credentials
](https://jenkins.io/doc/book/using/using-credentials/). The whole list of credentials set in Jenkins can be accessed [here
](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/)

See [Pipeline Integration points](Pipeline_integration_points.md) for details.

## Used Jenkins plugins
The pipeline code tries to keep to a minimum the used plugins, and uses docker images for command line tools.
The used plugins by the pipeline code are:

1. [HTTP Request Plugin](http://wiki.jenkins-ci.org/display/JENKINS/HTTP+Request+Plugin) for making HTTP requests from variuos integrations, like Slack.
1. [Lockable Resources Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Lockable+Resources+Plugin) for updating the `index.yaml` file of the Helm repository.
1. [Mask Passwords Plugin](http://wiki.jenkins-ci.org/display/JENKINS/Mask+Passwords+Plugin) for masking sensitive input data in the logs.