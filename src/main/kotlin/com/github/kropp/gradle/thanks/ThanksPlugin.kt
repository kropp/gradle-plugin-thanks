package com.github.kropp.gradle.thanks

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.task
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class ThanksPlugin : Plugin<ProjectInternal> {
  private val ENV_VAR = "GITHUB_TOKEN"

  private val pluginRepos = mutableSetOf<String>()

  override fun apply(project: ProjectInternal) {
    project.pluginManager.pluginContainer.all {
      val pluginClassName = this::class.qualifiedName
      if (pluginClassName != null) {
        resolvePluginRepo(pluginClassName)?.let { pluginRepos += it }
      }
    }

    project.task("thanks") {
      doLast {
        withToken(project) { token ->
          project.allprojects.findDependenciesAndStar(token) { allDependencies }
//          project.allprojects.findDependenciesAndStar(token) { root.dependencies }
        }
      }
    }
  }

  private fun resolvePluginRepo(className: String): String? {
    return when(className) {
      "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin" -> "gradle/kotlin-dsl"
      "com.github.kropp.gradle.thanks.ThanksPlugin" -> "kropp/gradle-plugin-thanks"
      else -> if (className.startsWith("org.gradle")) "gradle/gradle" else null
    }
  }

  private fun withToken(project: Project, action: (String) -> Unit) {
    val token = project.properties["GithubToken"] as? String ?: System.getenv(ENV_VAR)
    if (token.isNullOrEmpty()) {
      println("Github API token not found. Please set -PGithubToken parameter or environment variable $ENV_VAR")
    } else {
      action(token)
    }
  }

  private fun Set<Project>.findDependenciesAndStar(token: String, dependencies: ResolutionResult.() -> Set<DependencyResult>) {
    val repositories = pluginRepos + this.flatMap { it.getRepositories(dependencies) }.toSet()
    if (repositories.any()) {
      println("Starring Github repositories from dependencies and plugins")
    } else {
      println("No Github repositories found in dependencies and plugins")
    }

    loop@ for (repository in repositories) {
      try {
        if (repository.isStarred(token)) {
          println(" \u2b50 $repository")
        } else {
          val response = repository.star(token)
          when (response) {
            401 -> { println("Authentication failed. Please check Github token."); break@loop }
            204 -> println(" \uD83C\uDF1F $repository")
            else -> println(" \u274c $repository ($response)")
          }
        }
      } catch (e: IOException) {
        println(" \u274c $repository (${e.message})")
      }
    }
  }

  private fun Project.getRepositories(dependencies: ResolutionResult.() -> Set<DependencyResult>): List<String> {
    val dependencyIds = configurations.filter { it.isCanBeResolved }.flatMap { it.incoming.resolutionResult.dependencies() }
        .filterIsInstance<ResolvedDependencyResult>().map { it.selected.id }
    return getPoms(this, dependencyIds).mapNotNull { readGithubProjectsFromPom(it) }
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
    val url = scm.childNodes.asSequence().filter { it.textContent.contains("github.com") }.firstOrNull()?.textContent
    return url?.substringAfter("github.com")?.removePrefix("/")?.removePrefix(":")?.removeSuffix(".git")?.removeSuffix("/issues")
  }

  private val GITHUB_API = "https://api.github.com"
  private fun String.isStarred(token: String) = httpRequestResponse("GET", "$GITHUB_API/user/starred/$this", token) == 204
  private fun String.star(token: String) = httpRequestResponse("PUT", "$GITHUB_API/user/starred/$this", token)

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