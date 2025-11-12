import arrow.optics.plugin.arrowOptics
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
  id("java")
  alias(libs.plugins.kotlin)
  alias(libs.plugins.intellij.platform)
  alias(libs.plugins.changelog)
  alias(libs.plugins.qodana)
  alias(libs.plugins.kover)
  alias(libs.plugins.arrow.optics)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.opentest4j)
  intellijPlatform {
    create(properties("platformType"), properties("platformVersion"))
    properties("platformBundledPlugins").map { it.split(',') }.let(::bundledPlugins)
    properties("platformPlugins").map { it.split(',') }.let(::plugins)
    testFramework(TestFrameworkType.Platform)
  }
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-parameters")
  }
  dependencies {
    implementation(libs.arrow.core)
  }
  jvmToolchain(17)
  arrowOptics()
}

intellijPlatform {
  pluginConfiguration {
    name = properties("pluginName")
    version = properties("pluginVersion")

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
    changeNotes = properties("pluginVersion").map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }
    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
    }
  }
  signing {
    certificateChain = environment("CERTIFICATE_CHAIN")
    privateKey = environment("PRIVATE_KEY")
    password = environment("PRIVATE_KEY_PASSWORD")
  }
  publishing {
    token = properties("intellijPublishToken")
    channels = properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
  }
  pluginVerification {
    ides {
      recommended()
    }
  }
}

changelog {
  groups.empty()
  repositoryUrl = properties("pluginRepositoryUrl")
}

kover {
  reports {
    total {
      xml {
        onCheck = true
      }
    }
  }
}

tasks {
  wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
  }

  publishPlugin {
    dependsOn(patchChangelog)
  }
}

intellijPlatformTesting {
  runIde {
    register("runIdeForUiTests") {
      task {
        jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
            "-Drobot-server.port=8082",
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
          )
        }
      }

      plugins {
        robotServerPlugin()
      }
    }
  }
}