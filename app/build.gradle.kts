import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

val localSecrets = Properties().apply {
    rootProject.file("secrets.properties")
        .takeIf { it.isFile }
        ?.reader(Charsets.UTF_8)
        ?.use(::load)
}

fun configurationValue(name: String): String =
    providers.environmentVariable(name).orNull?.trim().takeUnless { it.isNullOrEmpty() }
        ?: providers.gradleProperty(name).orNull?.trim().takeUnless { it.isNullOrEmpty() }
        ?: localSecrets.getProperty(name)?.trim().orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val appMetricaApiKey = configurationValue("APP_METRICA_API_KEY")

android {
    namespace = "ru.mugalimov.volthome"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.mugalimov.volthome"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 35
        versionCode = 35
        versionName = "3.6"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "YANDEX_CLIENT_ID",
            "\"${project.findProperty("YANDEX_CLIENT_ID") ?: ""}\""
        )
        buildConfigField(
            "String",
            "APP_METRICA_API_KEY",
            appMetricaApiKey.asBuildConfigString()
        )
        manifestPlaceholders["YANDEX_CLIENT_ID"] =
            project.findProperty("YANDEX_CLIENT_ID") as String? ?: ""

        // true = ручное восстановление покупок через SDK разрешено
        // false = кнопка restore и recovery path должны быть отключены
        buildConfigField(
            "boolean",
            "BILLING_RESTORE_ENABLED",
            "true"
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
        if (file.exists()) file.reader(Charsets.UTF_8).use(properties::load)

        val releaseStorePath = configurationValue("VOLTHOME_KEYSTORE_PATH")
            .ifBlank { properties.getProperty("storeFile", "") }
            .takeIf { it.isNotBlank() }
        val releaseStorePassword = configurationValue("VOLTHOME_KEYSTORE_PASSWORD")
            .ifBlank { properties.getProperty("storePassword", "") }
        val releaseKeyAlias = configurationValue("VOLTHOME_KEY_ALIAS")
            .ifBlank { properties.getProperty("keyAlias", "upload_key") }
        val releaseKeyPassword = configurationValue("VOLTHOME_KEY_PASSWORD")
            .ifBlank { properties.getProperty("keyPassword", "") }

        create("release") {
            storeFile = releaseStorePath?.let(::file)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
//    implementation(libs.compose.material3)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
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
    kapt("androidx.room:room-compiler:2.8.0")
    implementation(libs.androidx.room.ktx)
    androidTestImplementation("androidx.room:room-testing:2.8.0")

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

    // DataStore (Proto/Custom Serializer)
    implementation("androidx.datastore:datastore-core:1.1.1")

// Шифрование (Tink + Android Keystore)
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
        force("com.yandex.android:authsdk:3.1.4")
    }
}

// room-testing 2.8.x использует json 1.8.x; выравниваем только test APK,
// иначе старый serialization-core из Compose падает до запуска миграции.
configurations.matching { it.name.contains("AndroidTest", ignoreCase = true) }.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1"
    )
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(appMetricaApiKey.isNotBlank()) {
            "APP_METRICA_API_KEY is required for a release build. " +
                "Set it in secrets.properties, ~/.gradle/gradle.properties or the environment."
        }
    }
}
