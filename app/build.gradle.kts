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

        // GitHub API config for in-app feedback reporter. Read from
        // local.properties; fall back to empty strings so the app compiles
        // but shows a UI error when config is missing.
        buildConfigField("String", "GITHUB_API_TOKEN",
            "\"${localProps.getProperty("github.api.token", "")}\"")
        buildConfigField("String", "GITHUB_REPO_OWNER",
            "\"${localProps.getProperty("github.repo.owner", "")}\"")
        buildConfigField("String", "GITHUB_REPO_NAME",
            "\"${localProps.getProperty("github.repo.name", "")}\"")
        buildConfigField("String", "FEEDBACK_ASSETS_DIR", "\"feedback-assets\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "true")
            // Cloudflare Worker (Stripe billing backend) config. Not committed —
            // sourced from local.properties, same pattern as translate.url/stt.url.
            buildConfigField("String", "PREMIUM_WORKER_BASE_URL",
                "\"${localProps.getProperty("premium.worker.url", "")}\"")
            buildConfigField("String", "STRIPE_PRICE_AD_FREE",
                "\"${localProps.getProperty("premium.stripe.price.ad_free", "")}\"")
            buildConfigField("String", "STRIPE_PRICE_PRO",
                "\"${localProps.getProperty("premium.stripe.price.pro", "")}\"")
            // Owner-only free-access key. Blank in public/CI builds; only ever set
            // in the developer's own local.properties. Must match the Worker's
            // OWNER_ACCESS_KEY secret for the OWNER_ALLOWLIST bypass to apply —
            // knowing the owner's email alone is not enough (see worker.js).
            buildConfigField("String", "OWNER_ACCESS_KEY",
                "\"${localProps.getProperty("premium.owner.access_key", "")}\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_SELF_UPDATE_ENABLED", "false")
            // Play Billing subscription product IDs, configured once in Play Console.
            // Not secrets, so hardcoded here rather than sourced from local.properties.
            buildConfigField("String", "PLAY_PRODUCT_AD_FREE", "\"ad_free_monthly\"")
            buildConfigField("String", "PLAY_PRODUCT_PRO", "\"pro_monthly\"")
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
        jniLibs {
            useLegacyPackaging = false
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
    // firebase-messaging and firebase-config were removed — not used.

    // Google Mobile Ads (AdMob). Powers the banner at the bottom of the
    // main UI and the app-open ad.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // ProcessLifecycleOwner — used by AppOpenAdManager to detect when the
    // app comes to the foreground so the app-open ad can be shown.
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.11.0")

    // Play Billing — playstore flavor only. Gradle auto-creates the
    // "playstoreImplementation" configuration from the flavor name; this is
    // what guarantees zero Play Billing code ships in the github flavor APK.
    "playstoreImplementation"("com.android.billingclient:billing-ktx:7.1.1")

    // Chrome Custom Tabs — github flavor only, used to open Stripe Checkout /
    // Customer Portal URLs in-browser. No Stripe SDK, keys, or card UI is ever
    // compiled into this app; Stripe code lives entirely in the Cloudflare Worker.
    "githubImplementation"("androidx.browser:browser:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
