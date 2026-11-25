import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releasePropsFile = rootProject.file("keystore/release.properties")
val releaseProps = Properties().apply {
    if (releasePropsFile.exists()) {
        releasePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.gaiding.ditchshortvideo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gaiding.ditchshortvideo"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.2.3"
    }

    signingConfigs {
        create("release") {
            if (releasePropsFile.exists()) {
                storeFile = rootProject.file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
        create("debugLocal") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugLocal")
        }
        release {
            if (releasePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }
}

afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        tasks.named("assemble$variant").configure {
            doLast {
                val folder = layout.buildDirectory.dir("outputs/apk/${variant.lowercase()}").get().asFile
                val source = folder.resolve("app-${variant.lowercase()}.apk")
                if (source.exists()) {
                    val suffix = if (variant == "Debug") "-debug" else ""
                    source.copyTo(
                        folder.resolve("ditch-short-video-${android.defaultConfig.versionName}$suffix.apk"),
                        overwrite = true
                    )
                }
            }
        }
    }
}

dependencies {
}


