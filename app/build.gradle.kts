import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Release signing secrets come from local.properties (gitignored) or environment variables.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(key: String): String? = localProps.getProperty(key) ?: System.getenv(key.uppercase().replace('.', '_'))

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ua.mynotes"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.mynotes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    val storePass = secret("release.storePassword")
    signingConfigs {
        if (storePass != null) create("release") {
            storeFile = file(secret("release.storeFile") ?: "release.jks")
            storePassword = storePass
            keyAlias = secret("release.keyAlias") ?: "mynotes"
            keyPassword = secret("release.keyPassword") ?: storePass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without secrets the release APK is built unsigned instead of failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    testImplementation("junit:junit:4.13.2")
    // android.jar only has stubs of org.json; the real implementation is needed for JVM tests
    testImplementation("org.json:json:20240303")
}
