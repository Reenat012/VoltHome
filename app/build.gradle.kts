import java.util.Properties

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
        versionCode = 29
        versionName = "3.0"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "YANDEX_CLIENT_ID",
            "\"${project.findProperty("YANDEX_CLIENT_ID") ?: ""}\""
        )
        manifestPlaceholders["YANDEX_CLIENT_ID"] =
            project.findProperty("YANDEX_CLIENT_ID") as String? ?: ""

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("API_BASE_URL") ?: ""}\""
        )

        // ---------------------------------------------------------
        // 🔥 COMMIT 2 — FEATURE FLAG ДЛЯ REAL PRODUCT LOADING
        // ---------------------------------------------------------
        // true = используем RuStore SDK для загрузки продуктов
        // false = fallback UI режим (без возможности покупки)
        buildConfigField(
            "boolean",
            "BILLING_REAL_PRODUCT_LOADING_ENABLED",
            "true"
        )

        buildConfigField(
            "boolean",
            "BILLING_PENDING_CONFIRM_ENABLED",
            "true"
        )

        // ---------------------------------------------------------
        // 🔥 COMMIT 5 — FEATURE FLAGS ДЛЯ RESTORE / RECOVERY
        // ---------------------------------------------------------
        // true = ручное восстановление покупок через SDK разрешено
        // false = кнопка restore и recovery path должны быть отключены
        buildConfigField(
            "boolean",
            "BILLING_RESTORE_ENABLED",
            "true"
        )

        // true = разрешаем контролируемый auto-restore на старте / после логина
        // false = auto-restore выключен, доступен только manual restore
        // На rollout держим false, чтобы не словить лишние recovery-гонки.
        buildConfigField(
            "boolean",
            "BILLING_AUTO_RESTORE_ON_START_ENABLED",
            "false"
        )

//        addManifestPlaceholders(
//            mapOf(
//                "VKIDClientID" to providers.gradleProperty("VKIDClientID").get(),
//                "VKIDClientSecret" to providers.gradleProperty("VKIDClientSecret").get(),
//                "VKIDRedirectHost" to "vk.com",
//                "VKIDRedirectScheme" to "vk${providers.gradleProperty("VKIDClientID").get()}"
//            )
//        )
    }

    signingConfigs {
        val properties = Properties()
        val file = rootProject.file("keystore.properties")
        if (file.exists()) properties.load(file.inputStream())

        create("release") {
            storeFile = file("/Users/mugalimovrinat/Documents/VoltHome/Публикация/Key/upload_key")
            storePassword = properties.getProperty("storePassword", "")
            keyAlias = "upload_key"
            keyPassword = properties.getProperty("keyPassword", "")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            // Подключаем конфигурацию подписи
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.foundation.layout)
//    implementation(libs.compose.material3)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.litert.support.api)
    implementation(libs.androidx.storage)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
//    implementation(libs.androidx.foundation.desktop)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    kapt("androidx.room:room-compiler:2.7.2")
    implementation(libs.androidx.room.ktx)

    // Hilt (опционально, но рекомендуется)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.gson)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg) // Для поддержки SVG

    // Lottie для векторных анимаций
    implementation("com.airbnb.android:lottie-compose:6.3.0")

    // Карусель
    implementation("com.google.accompanist:accompanist-pager:0.34.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.34.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WebView для Compose
    implementation("androidx.webkit:webkit:1.14.0")

    implementation("com.google.accompanist:accompanist-flowlayout:0.32.0")

//    implementation (libs.blurview)

    implementation("com.yandex.android:mobmetricalib:5.3.7")

//    implementation("com.vk.id:vkid:2.3.2")
    implementation("androidx.security:security-crypto:1.1.0")

//    implementation("com.yandex.android:mobmetricalib:5.3.7") {
//        exclude(group = "com.yandex.android", module = "authsdk")
//    }
    implementation("com.yandex.android:authsdk:3.1.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore (Proto/Custom Serializer)
    implementation("androidx.datastore:datastore-core:1.1.1")

// Шифрование (Tink + Android Keystore)
    implementation("com.google.crypto.tink:tink-android:1.12.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt ("androidx.hilt:hilt-compiler:1.2.0")

    // Если снова не найдёт капчу — ВРЕМЕННО добавь явные зависимости:
//     implementation("com.vk.id.captcha:okhttp-interceptors:0.0.4")
//     implementation("com.vk.id.captcha:vkid-captcha:0.0.4")

    // RuStore Pay SDK
    implementation(platform(libs.rustore.sdk.bom))
    implementation(libs.rustore.sdk.pay)

    //m3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

configurations.all {
    resolutionStrategy.force("androidx.browser:browser:1.8.0")
}

configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:4.12.0")
        force("com.squareup.okhttp3:logging-interceptor:4.12.0")
        force("com.yandex.android:authsdk:3.1.4")
    }
}