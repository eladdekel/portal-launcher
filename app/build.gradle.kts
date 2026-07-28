plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.iblu01.portallauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iblu01.portallauncher"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric needs merged resources/manifest
        }
    }
}

androidComponents {
    // These are variant-agnostic Robolectric/JVM unit tests; running them twice (once per
    // build type) is redundant. More importantly, the Compose test manifest that provides
    // androidx.activity.ComponentActivity (needed by createComposeRule()/ActivityScenarioRule)
    // is only declared as debugImplementation(libs.compose.ui.test.manifest) so it merges into
    // the debug variant's manifest but never ships in the release APK, as intended. Robolectric
    // resolves the unit-test manifest from each build type's own main manifest (testImplementation
    // manifests are not merged in for JVM unit tests), so testReleaseUnitTest can never resolve
    // that activity. Disable the release unit-test variant rather than duplicating test-only
    // manifest content into a release-shipped path.
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.enableUnitTest = false
    }
}

dependencies {
    implementation(libs.paho.mqtt)
    implementation(libs.okhttp)
    implementation(libs.nanohttpd)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)

    implementation(libs.coil.compose)

    // MAD architecture: structured concurrency + StateFlow/ViewModel + immutable collections.
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.collections.immutable)

    // Encrypted storage for HA token / MQTT password.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt dependency injection.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    // Headless Android/Compose behavior tests (JVM, no emulator).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
