package com.github.kropp.gradle.thanks

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.*
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.task
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class ThanksPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.task("thanks") {
      doLast {
        project.findDependenciesAndStar { root.dependencies }
      }
    }
    project.task("thanksAll") {
      doLast {
        project.findDependenciesAndStar { allDependencies }
      }
    }
  }

  private fun Project.findDependenciesAndStar(dependencies: ResolutionResult.() -> Set<DependencyResult>) {
    val dependencyIds = this.configurations.toList().filter { it.isCanBeResolved }.flatMap { it.incoming.resolutionResult.dependencies() }
        .filterIsInstance<ResolvedDependencyResult>().map { it.selected.id }
    val repositories = getPoms(this, dependencyIds).mapNotNull { readGithubProjectsFromPom(it) }.toSet()
    if (repositories.any()) {
      println("Starring Github repositories from dependencies")
    } else {
      println("No Github repositories found in dependencies")
    }
    repositories
        .forEach {
          if (isStarred(it)) {
            println("\u2b50 $it")
          } else {
            star(it)
            println("\uD83C\uDF1F $it")
          }
        }
  }

  private fun getPoms(project: Project, ids: List<ComponentIdentifier>) =
      project.dependencies
          .createArtifactResolutionQuery()
          .forComponents(ids)
          .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
          .execute()
          .resolvedComponents
          .flatMap {
            it.getArtifacts(MavenPomArtifact::class.java)
                .filterIsInstance<ResolvedArtifactResult>()
                .map { it.file.absolutePath }
          }

  private fun readGithubProjectsFromPom(filename: String): String? {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(filename)
    val scm = document.getElementsByTagName("scm").asSequence().firstOrNull() ?: return null
    return scm.childNodes.asSequence().filter { it.textContent.contains("github.com") }.firstOrNull()?.textContent?.substringAfter("github.com/")
  }

  private val GITHUB_API = "https://api.github.com"
  private fun Project.isStarred(repo: String) = httpRequestResponse("GET", "$GITHUB_API/user/starred/$repo", properties["token"] as? String ?: System.getenv("GITHUB_TOKEN")) == 204
  private fun Project.star(repo: String) = httpRequestResponse("PUT", "$GITHUB_API/user/starred/$repo", properties["token"] as? String ?: System.getenv("GITHUB_TOKEN")) == 204

  private fun httpRequestResponse(method: String, url: String, token: String): Int? {
    val httpCon = URL(url).openConnection() as? HttpURLConnection ?: return null
    httpCon.setRequestProperty("Authorization", "token $token")
    httpCon.requestMethod = method
    httpCon.connectTimeout = 10_000
    httpCon.readTimeout = 10_000
    httpCon.connect()
    return httpCon.responseCode
  }
}