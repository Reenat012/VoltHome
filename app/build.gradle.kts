plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    kotlin("kapt")

}

android {
    namespace = "ru.mugalimov.volthome"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.mugalimov.volthome"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    implementation (libs.androidx.core.ktx.v1120)
    implementation (libs.androidx.activity.compose.v182)
    implementation (platform(libs.androidx.compose.bom.v20240200))
    implementation( libs.androidx.compose.material3.material3)
    implementation (libs.material.icons.extended)
    implementation (libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    implementation (libs.androidx.room.runtime)
    kapt ("androidx.room:room-compiler:2.6.1")
    implementation (libs.androidx.room.ktx)

    // Hilt (опционально, но рекомендуется)
    implementation (libs.hilt.android)
    kapt (libs.hilt.compiler)
    implementation (libs.androidx.hilt.navigation.compose)

    implementation (libs.androidx.runtime)
    implementation (libs.ui)
    implementation (libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation ("androidx.compose.runtime:runtime:$1.6.1")
    implementation ("androidx.compose.runtime:runtime-livedata:$1.6.1")

    implementation (libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.ui.v154)
    implementation(libs.androidx.material3.v112)

    implementation (libs.gson)

}