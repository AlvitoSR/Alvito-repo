// Baseado no template oficial recloudstream/cloudstream-extensions-template
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21")
        // Plugin do CloudStream que faz tudo funcionar
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: com.lagradost.cloudstream3.gradle.CloudstreamExtension.() -> Unit) =
    extensions.getByName<com.lagradost.cloudstream3.gradle.CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: com.android.build.gradle.BaseExtension.() -> Unit) =
    extensions.getByName<com.android.build.gradle.BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "seu-usuario/embedtv-cloudstream")
    }

    android {
        compileSdkVersion(33)
        defaultConfig {
            minSdk = 21
            targetSdk = 33
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs +
                        "-Xno-call-assertions" +
                        "-Xno-param-assertions" +
                        "-Xno-receiver-assertions"
            }
        }
    }

    dependencies {
        val apk by configurations
        val implementation by configurations

        // Stubs do CloudStream (necessário para compilar)
        apk("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP
        implementation("org.jsoup:jsoup:1.18.3")              // HTML parser
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
