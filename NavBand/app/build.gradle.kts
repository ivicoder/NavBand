plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.navband.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.navband.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("NAVBAND_VERSION_CODE").orElse("1").get().toInt()
        versionName = "1.0"
    }

    // In CI, use the persistent keystore supplied through GitHub Actions secrets.
    // Local builds without these environment variables continue to use the standard debug key.
    val stableStorePath = System.getenv("NAVBAND_KEYSTORE_PATH")
    val stableStorePassword = System.getenv("NAVBAND_KEYSTORE_PASSWORD")
    val stableKeyAlias = System.getenv("NAVBAND_KEY_ALIAS")
    val stableKeyPassword = System.getenv("NAVBAND_KEY_PASSWORD")

    signingConfigs {
        if (
            !stableStorePath.isNullOrBlank() &&
            !stableStorePassword.isNullOrBlank() &&
            !stableKeyAlias.isNullOrBlank() &&
            !stableKeyPassword.isNullOrBlank()
        ) {
            create("stable") {
                storeFile = file(stableStorePath)
                storePassword = stableStorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.findByName("stable") != null) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}
