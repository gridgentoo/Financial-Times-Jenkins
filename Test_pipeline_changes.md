# How to test and roll out pipeline changes

We'll be using the [versioning feature](https://jenkins.io/doc/book/pipeline/shared-libraries/#library-versions) of the Jenkins shared library plus the [Cloudbees folder plugin](https://wiki.jenkins.io/display/JENKINS/CloudBees+Folders+Plugin) to make sure that the changes to the pipeline code doesn't affect the current Jobs using it, and that we can control when these changes will be applied to all jobs.

A tipical flow for making changes to the pipeline is the following:

1. Create a new feature branch in this Git repository
2. Make the code changes to the Pipeline.
3. Create unit tests for the pipeline.
4. Commit and push all the code to the feature branch.

Now you have to test the pipeline changes in isolation.
There are some methods to do this depending on what was affected:
### The pipeline flow was affected (deploy to upper envs, deploy to team envs)
1. Choose an app that is not mission critical & can be restarted at will. Such an app is the [system-healthcheck](https://github.com/Financial-Times/coco-system-healthcheck) or the [coreos-version-checker](https://github.com/Financial-Times/coreos-version-checker).
2. Overwrite the shared pipeline version used by the multibranch pipeline of this job to use the newly pushed branch code. The inherited version of the pipeline is set on the parent Jenkins folder [k8s-deployment](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/) and can be overwritten per subfolder or job.
    1. Open the configuration page for the multibranch pipeline: [system-healthcheck example](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/apps-deployment/job/system-healthcheck-auto-deploy/configure)
    1. In the **Pipeline libraries** part define the overwritting library
    ![Add pipeline definition](https://user-images.githubusercontent.com/8848332/44658096-23177400-aa08-11e8-8607-af78a4288d06.png)
    Use the following details for the library:
          - Name: must be **k8s-pipeline-lib**
          - Default version: the name of your feature multibranch
          - Make sure you fill in the details of Git repository where the library code exists.
    1. Save the job configuration
3. Test that both the team envs & upper envs deploy work by either creating new temporary releases or triggering the existing jobs for current releases.
4. After testing is done and everything is fine, please remove the Pipeline library from the configuration of the job.
5. See [After testing the pipeline changes](#after-testing-the-pipeline-changes)

### The common deployment of the Helm chart was affected
As explained in the [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit?pli=1#heading=h.j1fmzpz5wefh), we're using a single common job for doing all the deployments. Since this is a pipeline script job, the version is specified directly in the source code of the job config pipeline script as the following code line:
  ```@Library('k8s-pipeline-lib@master') ```

To test the changes to this part in isolation do the following:
1. Create a copy of this job and set the new version on the job:
    1. Go to the [Jenkins test folder](https://upp-k8s-jenkins.in.ft.com/job/k8s-deployment/job/test/)
    2. Click on the "New Item" and input the following:
![  copy](https://user-images.githubusercontent.com/8848332/44659662-a8515780-aa0d-11e8-8592-c215b67922cc.png)
        - Copy from : **k8s-deployment/utils/deploy-upp-helm-chart**
        - Item name: any name would do here
    1. Afterwards you are taken to the configuration screen of the new job. You must adjust the version of the library that is used by putting the name of your branch in the script code:
![script](https://user-images.githubusercontent.com/8848332/44659677-b43d1980-aa0d-11e8-8288-51c446d06bf3.png)
    1. Click Save
1. Manually trigger this new job for testing the introduced functionality
2. After you're done with the testing and happy with the result, delete this job.
3. See [After testing the pipeline changes](#after-testing-the-pipeline-changes)

### Both the pipeline flow and the common deployment were affected
If this is the case and all the flow must be tested in isolation you can do the following:
1. Create a copy of the deploy job and set the new version on the job. See above for details.
2. Create a new temporary branch that will use this new deployment job:
    1. Start a new branch from your feature branch
    2. In the [DeploymentUtilsConstants script](src/com/ft/jenkins/DeploymentUtilsConstants.groovy#L22) adjust the contant ```GENERIC_DEPLOY_JOB``` to point to this new job.
    3. Commit and push this new temp branch.
3. Choose an app that is not mission critical & can be restarted at will and overwrite the shared pipeline version used by the multibranch pipeline of this job to use the newly pushed branch code. See the first case above for details on how to do this.
4. After you're done and happy with the result make sure you delete the temporary branch & the Pipeline library from the configuration of the job.

### After testing the pipeline changes
For rolling out the pipeline changes you have to do the following:
1. Create a Github Pull Request from your feature branch to master
2. After you get the approval, merge the branch to master
3. Merge master into the "auto-deploy-stable" branch that is used for UPP. See [spec](https://docs.google.com/document/d/1eNOczq8tEG8Q2boqKqjFKis9qMIRdQi6vDLgrWy4Akk/edit?pli=1#heading=h.6zp9bpxvm5uh) for details.
