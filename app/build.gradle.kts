import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ─── Версия читается из version.properties ────────────────────────────────────
// Менять VERSION_MAJOR и VERSION_MINOR вручную в том файле.
// VERSION_PATCH инкрементируется автоматически в GitHub Actions при каждом коммите.
val versionProps = Properties().also { props ->
    val f = rootProject.file("version.properties")
    if (f.exists()) props.load(f.inputStream())
}
val vMajor = versionProps.getProperty("VERSION_MAJOR", "0").trim().toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR", "3").trim().toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH", "0").trim().toInt()
val appVersionName = "$vMajor.$vMinor.$vPatch"
val appVersionCode = vMajor * 10000 + vMinor * 100 + vPatch


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glucoplan.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.glucoplan.app"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")  // ✅ Правильно, использует release ключ
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true  // генерирует BuildConfig с versionName/versionCode из gradle
    }
    
    buildToolsVersion = "36.1.0"
    ndkVersion = "28.2.13676358"

    // ─── Переименование APK: GlucoPlan_0.3.1.apk ─────────────────────────────
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                if (variant.buildType.name == "release") {
                    output.outputFileName = "GlucoPlan_${variant.versionName}.apk"
                }
            }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Vico Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Gson
    implementation(libs.gson)
    
    // Timber (logging)
    implementation(libs.timber)

    // ─── Unit Testing ─────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)

    // ─── Instrumented Testing ──────────────────────────────────────────────────
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Debug for Compose testing
    debugImplementation(libs.compose.ui.test.manifest)
}
