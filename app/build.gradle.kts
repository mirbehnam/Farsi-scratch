plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "ir.behnamapps.fascratch"
    compileSdk = 37

    defaultConfig {
        applicationId = "ir.behnamapps.fascratch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2026-10-01 00:00:00 Asia/Tehran
        buildConfigField("long", "EXPIRATION_TIME_MILLIS", "1790800200000L")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("myket") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_SOURCE", "\"مایکت\"")
        }
        create("bazaar") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_SOURCE", "\"کافه‌بازار\"")
        }
        create("website") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_SOURCE", "\"سایت\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.webkit:webkit:1.12.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
