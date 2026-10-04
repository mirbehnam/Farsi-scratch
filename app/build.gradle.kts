plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""

android {
    namespace = "ir.behnamapps.fascratch"
    compileSdk = 37

    defaultConfig {
        applicationId = "ir.behnamapps.fascratch"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2026-12-01 00:00:00 Asia/Tehran
        buildConfigField("long", "EXPIRATION_TIME_MILLIS", "1796070600000L")
        buildConfigField("String", "COURSE_API_BASE", quoted("https://api.behnamapp.ir/scratch/v1"))
        buildConfigField("String", "COURSE_SKU", quoted("scratch_basic"))
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("myket") {
            dimension = "distribution"
            buildConfigField("String", "BILLING_PROVIDER", quoted("myket"))
            buildConfigField("String", "BILLING_PUBLIC_KEY", quoted(providers.gradleProperty("MYKET_BILLING_PUBLIC_KEY").orNull ?: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCPhSMvlAz4EBnRsLadFGMTNRjZq27j6ciwBHMGonUcYaq68ImipsACEHnw6TLQnkaIbtN5rIVg4WLaWRQi2Jc5oxV4/1y1+D68tET6bQwlrJTMC+Ua5DESp9fm857915upuN0yn9mmRcpJZfkUi6XUaOU/nBT8qlHM/yu712O/xwIDAQAB"))
            manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
            manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"
            manifestPlaceholders["sdkVersion"] = "6"
            buildConfigField("String", "UPDATE_SOURCE", "\"مایکت\"")
        }
        create("bazaar") {
            dimension = "distribution"
            buildConfigField("String", "BILLING_PROVIDER", quoted("cafebazaar"))
            buildConfigField("String", "BILLING_PUBLIC_KEY", quoted(providers.gradleProperty("BAZAAR_BILLING_PUBLIC_KEY").orNull ?: "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwCWdn31moCUB/Zd28AujClnrvdozknucNPW3dmCMRZhXcgqXrZ6WgVIXDRrCdeFRALnKEWpDO3MgqM+v1cgKqRwZ1z2aiLj+cAPcKU5UKxZlEUytFIcBYKv41vMPEzPQZpg9FjVdWkpgLI7XKcUITXZcm9IB+RDinyKp0b+ouR4TDL5iGYNCvQYAjZfXVsPFxIY9W8fK/CzC3aLpXxG7SJ1SRK0IJ4N4JTIb+kgajUCAwEAAQ=="))
            buildConfigField("String", "UPDATE_SOURCE", "\"کافه‌بازار\"")
        }
        create("website") {
            dimension = "distribution"
            buildConfigField("String", "BILLING_PROVIDER", quoted("website"))
            buildConfigField("String", "BILLING_PUBLIC_KEY", quoted(""))
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
            signingConfig = signingConfigs.getByName("debug")
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
    "bazaarImplementation"("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
    "myketImplementation"("com.github.myketstore:myket-billing-client:1.19")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
