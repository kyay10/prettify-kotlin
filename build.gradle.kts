import arrow.optics.plugin.arrowOptics
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  alias(libs.plugins.kotlin)
  id("org.jetbrains.intellij.platform")
  alias(libs.plugins.changelog)
  alias(libs.plugins.arrow.optics)
}

dependencies {
  testImplementation(libs.junit)

  // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
  intellijPlatform {
    intellijIdea("2025.1")
    bundledPlugins(listOf("org.jetbrains.kotlin"))
    testFramework(TestFrameworkType.Platform)
  }
}

kotlin {
  compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
  dependencies { implementation(libs.arrow.core) }
  arrowOptics()
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
  pluginConfiguration {
    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"

      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    }

    val changelog = project.changelog // local variable for configuration cache compatibility
    // Get the latest available change notes from the changelog file
    changeNotes = version.map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }
  }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
  groups.empty()
  repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
  versionPrefix = ""
}

tasks {
  publishPlugin {
    dependsOn(patchChangelog)
  }
}