### AWS Helm repo integration
We're keeping all the UPP helm charts in an S3 bucket exposed as an HTTP static website restricted only for FT access.
The CloudFormation template for the S3 bucket is [here](https://github.com/Financial-Times/content-k8s-provisioner/blob/master/helm-repo-stack/helm-s3-repo.yaml).
For uploading Helm charts to this [upp-helm-repo](https://s3.console.aws.amazon.com/s3/buckets/upp-helm-repo/?region=eu-west-1&tab=overview) S3 bucket, the pipeline uses the AWS user [upp-helm-repo-access.prod](https://console.aws.amazon.com/iam/home?region=eu-west-1#/users/upp-helm-repo-access.prod)

**Jenkins credential:** ***[ft.helm-repo.aws-credentials
](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/ft.helm-repo.aws-credentials/)***

#### Maintaining the index.yaml
One sensible part of maintaining the Helm repo is to make sure the [index file](https://github.com/helm/helm/blob/master/docs/chart_repository.md#the-index-file) is maintained according to the repo.
In order to do this, concurrent updates of this file are restricted by using the [Lockable Resources Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Lockable+Resources+Plugin) for locking down the file until the following operations are complete:

- the new chart is uploaded to the repo
- the index.yaml is updated with the new chart
- the new version of index.yaml is uploaded to the repo.

See DeploymentUtils#publishHelmChart.

### Slack integration
The pipeline uses the newly created [jenkins-bot](https://financialtimes.slack.com/services/180766093394) in order to make API calls to Slack.

**Jenkins credential:** ***[ft.slack.bot-credentials](https://upp-k8s-jenkins.in.ft.com/credentials/store/system/domain/_/credential/ft.slack.bot-credentials/)*** .

### Github integration
For accessing the Github API for obtaining information about releases the pipeline uses the *ft-upp-team* GH user.

**Jenkins credential:** ***[ft.github.credentials](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/ft.github.credentials/)***

### Dockerhub integration
For pushing docker images to DockerHub the pipeline uses the DH user *universalpublishingplatform*.

**Jenkins credential:** ***[ft.dh.credentials](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/ft.dh.credentials/)***

### FT internal Docker registry integration
For pushing docker images of private repos to the FT internal Docker Registry, currently hosted as a *Nexus* instance at [nexus.in.ft.com:5000
](nexus.in.ft.com:5000), the pipeline uses the user *upp-docker*.

**Jenkins credential:** ***[ft.docker_internal.credentials](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/ft.docker_internal.credentials/)***

### Maven repo integration
Some of java apps integrate with the FT's maven repository hosted in a Nexus instance. For accessing this repository the pipeline uses the user *upp-nexus*.

**Jenkins credential:** ***[nexus.credentials
](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/nexus.credentials/)***

### Change API integration
For automatically managing Change Requests, the pipeline integrates with the Change API.

**Jenkins credential:** ***[ft.change-api.key
](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/credentials/store/folder/domain/_/credential/ft.change-api.key/)***
