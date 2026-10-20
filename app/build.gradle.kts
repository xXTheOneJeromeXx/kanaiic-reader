import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing lives outside the repo, in ~/.gradle/gradle.properties:
//   tbce.storeFile, tbce.storePassword, tbce.keyAlias, tbce.keyPassword
val signing = Properties().apply {
    val f = File(System.getProperty("user.home"), ".gradle/gradle.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.tbce.calc"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.9.1"
    }

    // Two editions from one codebase. They behave the same; the application id differs, and it
    // must: a disguised direct install must never point at the public Play listing. So do the
    // app-level name and icon (Settings > Apps, the installer): Dictionary on direct, Kanaiic
    // Reader on Play, and the face a fresh install starts on.
    flavorDimensions += "edition"
    productFlavors {
        // GitHub / kanaiic.com. Its id appears on no store.
        create("direct") {
            dimension = "edition"
            applicationId = "com.tbce.calc"
            manifestPlaceholders["appLabel"] = "@string/label_dictionary"
            manifestPlaceholders["appIcon"] = "@drawable/ic_dictionary"
            // A fresh install starts disguised, as the Dictionary.
            manifestPlaceholders["readerFace"] = "false"
            manifestPlaceholders["dictionaryFace"] = "true"
        }
        // Google Play. Its id leads to the public listing, which describes the disguise.
        create("play") {
            dimension = "edition"
            applicationId = "com.kanaiic.reader"
            manifestPlaceholders["appLabel"] = "@string/label_reader"
            manifestPlaceholders["appIcon"] = "@drawable/ic_reader"
            // A fresh install starts as itself; the Dictionary is offered after setup.
            manifestPlaceholders["readerFace"] = "true"
            manifestPlaceholders["dictionaryFace"] = "false"
        }
    }

    signingConfigs {
        if (signing.getProperty("tbce.storeFile") != null) {
            create("release") {
                storeFile = File(signing.getProperty("tbce.storeFile"))
                storePassword = signing.getProperty("tbce.storePassword")
                keyAlias = signing.getProperty("tbce.keyAlias")
                keyPassword = signing.getProperty("tbce.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("META-INF/**", "**/*.properties", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
