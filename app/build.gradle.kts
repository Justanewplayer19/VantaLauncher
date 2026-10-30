import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The signing key lives outside the repo, so a release build still works without it: it
// just comes out unsigned rather than failing. Keep keystore.jks and keystore.properties
// somewhere safe. A new key cannot install over an app signed with the old one.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.maria.homelauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maria.homelauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.11"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1 is only needed below API 24 and this app starts at 26. v3 is what
                // lets the key be rotated later without every install breaking.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Code shrinking is on; resource shrinking is not.
            //
            // It used to be off on the grounds that icon packs look resources up by name
            // through getIdentifier. That was a misreading: those lookups run against the
            // *pack's* resources, loaded out of another installed app, which nothing we
            // do to our own build can reach. Our own resources are a handful of icons and
            // strings, so there is nothing there worth shrinking and a reason not to.
            //
            // What the shrinker does buy is a smaller dex, and a smaller dex is less for
            // a slow phone to verify and load every time the launcher is started again
            // after being killed, which on a phone with little memory is all day.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
