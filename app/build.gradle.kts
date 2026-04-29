import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

// Load local.properties for private server URLs (not committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localBool(key: String, default: Boolean): Boolean {
    return localProps.getProperty(key)?.trim()?.lowercase()?.let {
        it == "true" || it == "1" || it == "yes" || it == "on"
    } ?: default
}

// Play Store upload key lives in keystore.properties at the repo root. File
// is gitignored; a template lives at playstore/keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning: Boolean = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

// CI builds encode GitHub's run number into versionCode so the app's build
// number matches the release tag (v1.0.<run_number>). Local dev builds get 1.
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersionName: String = "1.0.$ciRunNumber"

android {
    namespace = "com.charles.livecaptionn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.charles.livecaptionn"
        minSdk = 29
        targetSdk = 35
        versionCode = ciRunNumber
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Inject server URLs from local.properties; falls back to localhost
        buildConfigField("String", "DEFAULT_TRANSLATE_URL",
            "\"${localProps.getProperty("translate.url", "http://localhost:3006")}\"")
        buildConfigField("String", "DEFAULT_STT_URL",
            "\"${localProps.getProperty("stt.url", "http://localhost:9000/asr?output=json")}\"")
        buildConfigField("boolean", "ADS_ENABLED", localBool("ads.enabled", true).toString())
        buildConfigField(
            "String",
            "ADMOB_APP_ID",
            "\"${localProps.getProperty("ads.admob.app.id", "ca-app-pub-3940256099942544~3347511713")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_APP_OPEN_ID_DEBUG",
            "\"${localProps.getProperty("ads.admob.app.open.id.debug", "ca-app-pub-3940256099942544/9257395921")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_APP_OPEN_ID_RELEASE",
            "\"${localProps.getProperty("ads.admob.app.open.id.release", "")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_BANNER_ID_DEBUG",
            "\"${localProps.getProperty("ads.admob.banner.id.debug", "ca-app-pub-3940256099942544/9214589741")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_BANNER_ID_RELEASE",
            "\"${localProps.getProperty("ads.admob.banner.id.release", "")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_NATIVE_ID_DEBUG",
            "\"${localProps.getProperty("ads.admob.native.id.debug", "ca-app-pub-3940256099942544/2247696110")}\""
        )
        buildConfigField(
            "String",
            "ADMOB_NATIVE_ID_RELEASE",
            "\"${localProps.getProperty("ads.admob.native.id.release", "")}\""
        )
        manifestPlaceholders["admobAppId"] =
            localProps.getProperty("ads.admob.app.id", "ca-app-pub-3940256099942544~3347511713")

        // GitHub repo that the in-app update checker queries for new releases.
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"chartmann1590\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"LiveTranscribe-Android\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val storeFileName = keystoreProps.getProperty("storeFile")
                val resolved = rootProject.file(storeFileName)
                storeFile = if (resolved.exists()) resolved else file(storeFileName)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // On-device translation (Google Translate models cached offline).
    implementation("com.google.mlkit:translate:17.0.3")

    // Firebase BoM pins compatible versions of every Firebase SDK below.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")

    // Google Mobile Ads (AdMob). Powers the banner at the bottom of the
    // main UI and the app-open ad.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // ProcessLifecycleOwner — used by AppOpenAdManager to detect when the
    // app comes to the foreground so the app-open ad can be shown.
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
