plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ezstats.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ezstats.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "2.5.3"
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the code and drops unused resources: a much smaller, faster-starting app.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the same key as the earlier builds, so this installs over them and keeps your logins.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // Settings shows the version
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Material 3 Expressive is still marked experimental; opt in once here instead of on every function.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Material 3 Expressive is only public in the 1.5 line; 1.5.0-alpha14 is the last one that runs on Compose 1.10
    // (later ones need Compose 1.12, which needs AGP 9 and compileSdk 37).
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    // The icon set stopped being updated (and left the BOM) at 1.7.8; R8 strips the icons that aren't used.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
