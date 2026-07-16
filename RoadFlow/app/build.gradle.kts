import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.amko.roadflow"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.amko.roadflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_API_KEY", "\"${localProperties.getProperty("FIREBASE_API_KEY") ?: ""}\"")
        buildConfigField("String", "FIREBASE_BASE_URL", "\"${localProperties.getProperty("FIREBASE_BASE_URL") ?: ""}\"")
        buildConfigField("String", "MAP_API_KEY", "\"${localProperties.getProperty("MAP_API_KEY") ?: ""}\"")
        buildConfigField("String", "BASE_URL", "\"${localProperties.getProperty("BASE_URL") ?: ""}\"")
        buildConfigField("String", "Z_M_API_KEY", "\"${localProperties.getProperty("Z_M_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
configurations.all {
    resolutionStrategy {
        force("androidx.vectordrawable:vectordrawable:1.1.0")
        force("androidx.vectordrawable:vectordrawable-animated:1.1.0")
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.maplibre.gl:android-sdk:11.0.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}