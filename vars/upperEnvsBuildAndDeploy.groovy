import com.ft.jenkins.appconfigs.AppConfig
import com.ft.jenkins.appconfigs.AppConfigs
import com.ft.jenkins.changerequests.ChangeRequestEnvironment
import com.ft.jenkins.changerequests.ChangeRequestOpenData
import com.ft.jenkins.changerequests.ChangeRequestPopulateSystemCode
import com.ft.jenkins.changerequests.ChangeRequestsUtils
import com.ft.jenkins.cluster.*
import com.ft.jenkins.deployment.Deployments
import com.ft.jenkins.deployment.DeploymentsConstants
import com.ft.jenkins.docker.Docker
import com.ft.jenkins.git.GithubReleaseInfo
import com.ft.jenkins.slack.Slack
import com.ft.jenkins.slack.SlackAttachment
import com.google.common.collect.Multimap
import com.google.common.collect.Sets

def call(GithubReleaseInfo releaseInfo, BuildConfig buildConfig) {
  Deployments deployments = new Deployments()
  AppConfigs appConfigs = new AppConfigs()

  Multimap<ClusterType, AppConfig> appsInRepo = null
  String tagName = releaseInfo.tagName
  String appVersion = tagName
  Docker docker = new Docker()
  String chartName

  catchError {
    node('docker') {
      timeout(30) { //  timeout after 30 mins to not block jenkins
        stage('checkout') {
          checkout scm
        }

        if (fileExists("Dockerfile")) { //  build Docker image only if we have a Dockerfile
          stage('build image') {
            String dockerRepository = deployments.getDockerImageRepository()
            docker.buildAndPushImage("${dockerRepository}:${appVersion}")
          }
        }

        stage('publish chart') {
          chartName = deployments.publishHelmChart(appVersion)
        }
      }
      deployments.fetchChart(chartName, appVersion)
      appsInRepo = appConfigs.getClusterTypeToAppConfigPairsFromAppsInChart(chartName)

    }
    autoDeployToEnvironment(Environment.DEV_NAME, chartName, appVersion, appsInRepo, 1)

    initiateDeploymentToEnvironment(buildConfig.preprodEnvName, chartName, appVersion, releaseInfo, appsInRepo, 1)
    initiateDeploymentToEnvironment(buildConfig.prodEnvName, chartName, appVersion, releaseInfo, appsInRepo, 7)
  }

  stage("cleanup") {
    node("docker") {
      cleanWs()
    }
  }
}

void autoDeployToEnvironment(String targetEnvName, String chartName,
                             String version, Multimap<ClusterType, AppConfig> appsPerCluster,
                             int daysForTheDeployment) {
  Set<ClusterType> availableClusterTypes = appsPerCluster.keySet().findAll { it != ClusterType.UNKNOWN }

  // get first non null environment
  Environment environment
  for (ClusterType clusterType : availableClusterTypes) {
    environment = EnvsRegistry.getEnvironment(clusterType, targetEnvName)
    if (environment) {
      break
    }
  }

  if (environment) {
    stage("autodeploy to ${environment.name}") {
      timeout(time: daysForTheDeployment, unit: 'DAYS') {
        deployAppsToEnvironmentRegions(environment.regions, chartName, version, environment)
      }
    }
  }
}

void initiateDeploymentToEnvironment(String targetEnvName, String chartName,
                                     String version,
                                     GithubReleaseInfo releaseInfo, Multimap<ClusterType, AppConfig> appsPerCluster,
                                     int daysForTheDeployment) {
  Set<ClusterType> availableClusterTypes = appsPerCluster.keySet().findAll { it != ClusterType.UNKNOWN }
  List<Environment> availableEnvironments = availableClusterTypes.collect { EnvsRegistry.getEnvironment(it, targetEnvName) }
  // get first non null environment
  Environment environment = availableEnvironments.find { it }
  stage("deploy to ${targetEnvName} in ${availableClusterTypes} clusters") {
    timeout(time: daysForTheDeployment, unit: 'DAYS') {

      //  todo [sb] use a template engine for the Strings. See http://docs.groovy-lang.org/next/html/documentation/template-engines.html#_simpletemplateengine
      sendSlackMessageForDeployReady(releaseInfo, chartName, environment, appsPerCluster)

      List<Region> remainingRegionsToDeployTo = environment.regions
      String crId = null
      String deployInitiator = null
      while (!remainingRegionsToDeployTo.isEmpty()) {

        if (deployInitiator != null) {
          sendSlackMessageForIntermediaryDeploy(releaseInfo, environment, appsPerCluster, remainingRegionsToDeployTo,
                  deployInitiator, chartName)
        }

        JenkinsDeployInput deployInput = displayJenkinsInputForDeploy(releaseInfo, environment, appsPerCluster,
                remainingRegionsToDeployTo)

        deployInitiator = deployInput.approver

        if (crId == null) {
          crId = openCr(deployInitiator, releaseInfo, environment, appsPerCluster, chartName)
        }

        List<Region> regionsToDeployTo = []
        if (deployInput.selectedRegion == Region.ALL) {
          regionsToDeployTo.addAll(remainingRegionsToDeployTo)
        } else {
          regionsToDeployTo.add(deployInput.getSelectedRegion())
        }

        deployAppsToEnvironmentRegions(regionsToDeployTo, chartName, version, environment)

        remainingRegionsToDeployTo.removeAll(regionsToDeployTo)

        stage("validate apps in ${environment.getNamesWithRegions(regionsToDeployTo)}") {
          sendSlackMessageForValidation(releaseInfo, environment, appsPerCluster, regionsToDeployTo,
                  deployInitiator, chartName)
          displayJenkinsInputForValidation(releaseInfo, environment, appsPerCluster, regionsToDeployTo)
        }
      }
    }
  }
}

void deployAppsToEnvironmentRegions(List<Region> regionsToDeployTo, String chartName, String imageVersion,
                                    Environment environment) {

  /*  deploy at the same time in all clusters */
  /*  todo [sb] - if more than one cluster, do it one at a time */
  for (Region region : regionsToDeployTo) {
    build job: DeploymentsConstants.GENERIC_DEPLOY_JOB,
            parameters: [
                    string(name: 'Chart', value: chartName),
                    string(name: 'Version', value: imageVersion),
                    string(name: 'Environment', value: environment.name),
                    string(name: 'Cluster', value: ClusterType.ALL_IN_CHART.label),
                    string(name: 'Region', value: region.name),
                    booleanParam(name: 'Send success notifications', value: false)]

  }
}

void sendSlackMessageForDeployReady(GithubReleaseInfo releaseInfo, String chartName,
                                    Environment targetEnv, Multimap<ClusterType, AppConfig> appsPerCluster) {

  SlackAttachment attachment = new SlackAttachment()
  attachment.title = "Click for manual decision: [${chartName}]:${releaseInfo.tagName} ready to deploy in '${targetEnv.name}'"
  attachment.titleUrl = "${env.BUILD_URL}input"

  String appsText = computeSlackTextForAppsToDeploy(appsPerCluster)

  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${appsText}, is ready to deploy in `${targetEnv.name}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  Slack slack = new Slack()
  slack.sendEnhancedSlackNotification(targetEnv.slackChannel, attachment)
}

static String computeSlackTextForAppsToDeploy(Multimap<ClusterType, AppConfig> appsPerCluster) {
  String appsText
  if (AppConfigs.areSameAppsInAllClusters(appsPerCluster)) {
    List<String> apps = AppConfigs.getAppsInFirstCluster(appsPerCluster).collect { it.appName }
    appsText = "`${apps}` in clusters `${ClusterType.toLabels(appsPerCluster.keySet())}`"
  } else {
    List<String> messages = []
    appsPerCluster.asMap().each { ClusterType cluster, Collection<AppConfig> appsInCluster ->
      Set<String> apps = Sets.newHashSet(appsInCluster.collect { it.appName })
      messages.add("`${apps}` in cluster `${cluster.label}`")
    }
    appsText = messages.join(" and ")
  }
  appsText
}

static String computeSimpleTextForAppsToDeploy(Multimap<ClusterType, AppConfig> appsPerCluster) {
  String appsText
  if (AppConfigs.areSameAppsInAllClusters(appsPerCluster)) {
    List<String> apps = AppConfigs.getAppsInFirstCluster(appsPerCluster).collect { it.appName }
    appsText = "${apps} in clusters ${ClusterType.toLabels(appsPerCluster.keySet())}"
  } else {
    List<String> messages = []
    appsPerCluster.asMap().each { ClusterType cluster, Collection<AppConfig> appsInCluster ->
      Set<String> apps = Sets.newHashSet(appsInCluster.collect { it.appName })
      messages.add("${apps} in cluster ${cluster.label}")
    }
    appsText = messages.join(" and ")
  }
  appsText
}

JenkinsDeployInput displayJenkinsInputForDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                                Multimap<ClusterType, AppConfig> appsPerCluster,
                                                List<Region> availableRegions) {
  String envWithRegions = targetEnv.getNamesWithRegions(availableRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} is ready to deploy in '${envWithRegions}'."
  if (availableRegions.size() == 1) {
    Region regionToDeploy = availableRegions.get(0)
    releaseMessage = releaseMessage + "It will be deployed in region ${regionToDeploy.name}"
    String approver = input(message: releaseMessage, submitterParameter: 'approver',
            ok: "Deploy to ${targetEnv.getNamesWithRegions(availableRegions)}".toString())
    return new JenkinsDeployInput(approver, regionToDeploy)
  } else {
    String choices = Region.toJenkinsChoiceValues(availableRegions)
    String regionParamName = "Region to deploy to"
    def regionChoice = choice(choices: choices,
            description: 'Please choose in what region(s) do you want to deploy first. If you choose only one region, you will be presented later with the deploy option for the other one',
            name: regionParamName)
    def chosenParams = input(message: releaseMessage, parameters: [regionChoice], submitterParameter: 'approver',
            ok: "Deploy to ${targetEnv.name}".toString())

    def approver = chosenParams.get('approver') as String
    def selectedRegion = Region.toRegion(chosenParams.get(regionParamName) as String)
    def deployInput = new JenkinsDeployInput(approver, selectedRegion)
    deployInput
  }
}

void sendSlackMessageForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                   Multimap<ClusterType, AppConfig> appsPerCluster,
                                   List<Region> deployedInRegions, String approver, String chartName) {
  Slack slack = new Slack()

  List<String> healthURLs = []
  for (ClusterType cluster : appsPerCluster.keySet()) {
    for (Region region : deployedInRegions) {
      healthURLs.add(slack.getHealthUrl(targetEnv, cluster, region))
    }
  }

  SlackAttachment attachment = new SlackAttachment()
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)
  attachment.title = "Click for manual decision: ${chartName}:${releaseInfo.tagName} was deployed and waits validation in '${envWithRegions}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${computeSlackTextForAppsToDeploy(appsPerCluster)}, was deployed successfully and is waiting validation in ${healthURLs}."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar

  /* send notification only to approver */
  slack.sendEnhancedSlackNotification("@${approver}", attachment)
}

String displayJenkinsInputForValidation(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                        Multimap<ClusterType, AppConfig> appsPerCluster,
                                        List<Region> deployedInRegions) {
  String envWithRegions = targetEnv.getNamesWithRegions(deployedInRegions)

  String releaseMessage = "The release ${releaseInfo.tagName} of apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} was deployed in '${envWithRegions}' and needs validation. Is this release valid?"
  String approver = input(message: releaseMessage, submitterParameter: 'approver', ok: "Release is valid in ${envWithRegions}".toString())
  approver
}

private String openCr(String approver, GithubReleaseInfo releaseInfo, Environment environment,
                      Multimap<ClusterType, AppConfig> appsPerCluster, String chartName) {
  try {
    ChangeRequestOpenData data = new ChangeRequestOpenData()
    data.ownerEmail = "${approver}@ft.com"
    String clusterAndAppName = computeSimpleTextForAppsToDeploy(appsPerCluster)
    data.clusterFullName = "${clusterAndAppName}"

    ChangeRequestPopulateSystemCode evaluateSystemCode = new ChangeRequestPopulateSystemCode()
    //Check if systemCode is in the list of different HelmChartName to systemCode mappings
    String evaluatedSystemCode = evaluateSystemCode.populateSystemCode(chartName)
    //Check if systemCode actually exists. If it does not, assign upp to it instead.
    String existingEvaluatedSystemCode = evaluateSystemCode.checkSystemCode(evaluatedSystemCode)
    data.systemCode = existingEvaluatedSystemCode
    data.gitTagOrCommitType = "gitReleaseTag"
    data.gitReleaseTagOrCommit = releaseInfo.tagName
    data.gitRepositoryName = releaseInfo.url

    data.summary = "Deploying chart ${chartName}:${releaseInfo.tagName} with apps ${computeSimpleTextForAppsToDeploy(appsPerCluster)} in ${environment.name}"
    if (environment.name == Environment.PROD_NAME || environment.name == "prodpac") {
      data.environment = ChangeRequestEnvironment.Production
    } else {
      data.environment = ChangeRequestEnvironment.Test
    }
    data.notifyChannel = environment.slackChannel

    ChangeRequestsUtils crUtils = new ChangeRequestsUtils()
    return crUtils.open(data)
  }
  catch (e) { //  do not fail if the CR interaction fail
    echo "Error while opening CR for release ${releaseInfo.getTagName()}: ${e.message} "
  }
}

final class JenkinsDeployInput implements Serializable {

  String approver
  Region selectedRegion

  JenkinsDeployInput(String approver, Region selectedRegion) {
    this.approver = approver
    this.selectedRegion = selectedRegion
  }
}

void sendSlackMessageForIntermediaryDeploy(GithubReleaseInfo releaseInfo, Environment targetEnv,
                                           Multimap<ClusterType, AppConfig> appsPerCluster, List<Region> remainingRegions,
                                           String initiator, String chartName) {
  SlackAttachment attachment = new SlackAttachment()
  String envWithRegionNames = targetEnv.getNamesWithRegions(remainingRegions)
  attachment.title = "Click for manual decision: ${chartName}:${releaseInfo.tagName} deploy in remaining regions '${envWithRegionNames}'"
  attachment.titleUrl = "${env.BUILD_URL}input"
  String validatedEnvWithRegionNames = targetEnv.getNamesWithRegions(targetEnv.getValidatedRegions(remainingRegions))
  attachment.text = "The release <${releaseInfo.url}|${releaseInfo.tagName}> of apps ${computeSlackTextForAppsToDeploy(appsPerCluster)}, was *validated in ${validatedEnvWithRegionNames}* and is ready to deploy in `${envWithRegionNames}`."
  attachment.authorName = releaseInfo.authorName
  attachment.authorLink = releaseInfo.authorUrl
  attachment.authorIcon = releaseInfo.authorAvatar
  attachment.color = "warning"

  Slack slack = new Slack()
  slack.sendEnhancedSlackNotification("@${initiator}", attachment)
}
