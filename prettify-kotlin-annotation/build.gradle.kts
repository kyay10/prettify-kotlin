plugins {
    kotlin("multiplatform") version "1.9.20"
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    signing
}

group = "io.github.kyay10"
version = "1.1"

repositories {
    mavenCentral()
}
val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

kotlin {
    jvm()
    js(IR)
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" -> linuxX64()
        isMingwX64 -> mingwX64()
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
}

publishing {
    publications {
        publications.withType<MavenPublication> {
            artifact(javadocJar)
        }
    }
}

fun getExtraString(name: String) = properties[name]?.toString()

publishing {
    // Configure maven central repository
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("sonatypeUsername")
                password = getExtraString("sonatypePassword")
            }
        }
    }

    // Configure all publications
    publications.withType<MavenPublication> {
        // Provide artifacts information requited by Maven Central
        pom {
            name.set("Prettify Kotlin Annotation")
            description.set("Provides a @Pretty annotation for Kotlin")
            url.set("https://github.com/kyay10/prettify-kotlin")

            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("kyay10")
                    name.set("Youssef Shoaib")
                    email.set("canonballt@gmail.com")
                }
            }
            scm {
                url.set("https://github.com/kyay10/prettify-kotlin.git")
            }
        }
    }
}

// Signing artifacts. Signing.* extra properties values will be used

signing {
    sign(publishing.publications)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
  val signingTasks = tasks.withType<Sign>()
  mustRunAfter(signingTasks)
}