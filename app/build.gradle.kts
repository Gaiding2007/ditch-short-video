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
        versionCode = 11
        versionName = "0.1.1"
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

dependencies {
}


