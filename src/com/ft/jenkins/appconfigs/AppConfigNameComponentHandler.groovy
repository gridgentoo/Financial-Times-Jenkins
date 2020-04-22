package com.ft.jenkins.appconfigs

interface AppConfigNameComponentHandler {
  int handle(String[] nameComponents, AppConfig appConfig, int currentIndex)
}
