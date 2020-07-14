# k8s-pipeline-library

## Description
Jenkins [shared pipeline library](https://jenkins.io/doc/book/pipeline/shared-libraries/) to be used for deployment in Kubernetes clusters.

## Documentation
[Deployment in k8s - spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk)

### Main global steps
1. [Generic entry point for Jenkinsfile](vars/genericEntryPointForJenkinsfile.groovy) - the generic entry point for the continuous delivery pipelines. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.3dr93sgi36h)
1. [UPP entry point for Jenkinsfile](/vars/uppEntryPointForJenkinsfile.groovy) main entry point for Continuous Delivery in UPP clusters. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.rcnsi4cl7nnz)
1. [PAC entry point for Jenkinsfile](/vars/pacEntryPointForJenkinsfile.groovy) main entry point for Continuous Delivery in UPP clusters. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.rcnsi4cl7nnz)
1. [Install Helm chart](vars/installHelmChart.groovy) - this is the step used by the generic job for installing a Helm chart. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.j1fmzpz5wefh)
1. [Build and deploy in team envs](vars/teamEnvsBuildAndDeploy.groovy) - this is the step that handles the building and deployment into the team envs. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.3dr93sgi36h)
1. [Build and deploy in upper envs](vars/upperEnvsBuildAndDeploy.groovy) - this is the step that handles the building and deployment into the upper envs (staging and prod). See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.3dr93sgi36h)

### Utility global steps
1. [Diff & sync 2 envs](vars/diffBetweenEnvs.groovy): - this is the main step used in the [Diff & Sync 2 k8s envs](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/diff-between-envs/). This job can be used to keep the team envs in sync with the prod ones, or when provisioning a new environment.
1. [Update cluster using the provisioner](vars/updateCluster.groovy) - this is the main step used in the [Update a Kubernetes cluster using the Provisioner](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/update-cluster/). This job can be used for updating the CoreOS version in a cluster.
1. [Update Dex configs](vars/updateDexConfigs.groovy) - this is the main step used in the job [Update Dex Config](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/utils/job/update-dex-config/) that updates the Dex configurations in multiple clusters at once. For more information on Dex, see [Content auth](https://github.com/Financial-Times/content-auth)

### Pipeline Workflow
1. Build Pipeline
    1. Build project docker image with `git` tag as a docker tag.
    1. Package docker image to a `helm` chart.
    1. Update `helm` repository index.
    1. Upload packaged `helm` chart to UPP helm repository S3 bucket.
1. Deploy Pipeline
    1. Fetch `helm` chart based on current version tag.
    1. Extract files from chart's `app-configs` directory.
    1. Filter app configs based on environment and/or cluster type.
    1. Filter again based on most specific deployments, e.g. with specified environment and/or region.
    1. Run `helm upgrade` with configured parameters for each of the filtered app configs. NOTE: The version of `helm` used is determined based on deploying on an EKS cluster or not.

### Helm integration
On every helm install/upgrade the following values are automatically inserted:

1. `region`: the region where the targeted cluster lives. Example: `eu`, `us`
1. `target_env`: the name of the environment as defined in the Environment registry. Example: `k8s`, `prod`
1. `__ext.target_cluster.sub_domain`: the DNS subdomain of the targeted cluster. This is computed from the mapped API server declared in the EnvsRegistry. Example: `upp-prod-publish-us`, `pac-prod-eu`
1. For every cluster in the targeted environment, the URLs are exposed with the values `cluster.${cluster_label}.url`. Example: `--set cluster.delivery.url=https://upp-k8s-dev-delivery-eu.ft.com --set cluster.publishing.url=https://upp-k8s-dev-publish-eu.ft.com`

*NOTE*: in the future all these values will be moved under the `__ext` namespace to avoid clashes with other developer introduced values.

## What to do when adding a new environment
When provisioning a new environment, Jenkins needs to "see" it, in order to be able to deploy to it.
Here are the steps needed in order for Jenkins to "see" it.

1.  Create a new branch for this repository
1. Add the definition of the new environment in the Clusters.groovy. Here's an example:
    ```
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
    ```
    Here are the characteristics of an Environment:
    1. It must be attached to an initialized `Cluster` (must be listed in `<cluster_instance>.environments = [...]`).
    1. It must have a Slack channel, e.g. `#upp-changes`.
    1. It must have specified regions, e.g. `EU`, `US`.
    1. It must contain entries consisting of `<region>-<cluster_type>` as key and EKS cluster connection details as value.

    The name of the environment is very important as it is correlated with the envs name from [the Helm chart app-configs folder](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit#heading=h.u09xl9x334yk) and with the ones in the Github releases for team environments.
    This is why this name must contain only `alphanumeric` characters. `-` and `_` are not allowed in the name. Valid names may be: k8s, xp, myteam, rjteam
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
    This page generates snippets that you can paste into your script.
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

## Permissive script security
By default the Groovy pipelines run in Jenkins in a Sandbox that limits the methods and objects you can use in the code.
This is done by the [Script Security Plugin](https://wiki.jenkins.io/display/JENKINS/Script+Security+Plugin).
Since this is annoying and devs might not know how to overcome this, we decided to disable this behavior by using the [Permissive Script Security Plugin](https://wiki.jenkins.io/display/JENKINS/Permissive+Script+Security+Plugin).

## How to create a job for a new repo
When a new app is created that needs Continuous Delivery on our Kubernetes clusters, you can enable this by creating a new multibranch job in Jenkins following these steps:

1. Login into [Jenkins](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/) using the AD credentials
1. Go to the appropriate folder for the platform. For UPP go to `UPP: Pipelines for application deployments` and for Pac go to `PAC: Pipelines for application deployments`
1. Click the “New Item” link in the left side
1. A template job is already defined in Jenkins, so in the new item dialog fill the following:
    - Item name: `{replace_with_app_name}`-auto-deploy
    - copy from: k8s-deployment/deploy-pipeline-template
![1](https://user-images.githubusercontent.com/8848332/45297184-3993f400-b50d-11e8-8cef-6bb9721119cc.png)
1. Configure the job: fill in the display name with “{replace-with-app-name} dev and release pipeline” and the 2 Git branch sources.
![2](https://user-images.githubusercontent.com/8848332/45297461-1d448700-b50e-11e8-86db-5f1b067d1fb7.png)
1. Click save

## How to trigger the pipeline job
If you've just created a new branch or a new tag or a new commit on a branch that should be picked up by Jenkins you have 2 options:
1. wait for Jenkins to pick it up. It is set to scan all repos each 2 mins.
2. Trigger the scanning of the multibranch manually. Go to the multibranch pipeline job like [aggregate-concept-transformer job](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/apps-deployment/job/aggregate-concept-transformer-auto-deploy/)
and click `Scan Multibranch Pipeline Now` from the left hand side of the screen.

