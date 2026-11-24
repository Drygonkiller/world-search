plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.drygonkiller.wordsearch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.drygonkiller.wordsearch"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/9214589741")
            resValue("string", "admob_interstitial_id", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "admob_rewarded_id", "ca-app-pub-3940256099942544/5224354917")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-2899987990324695~3945516141"
            resValue("string", "admob_banner_id", "ca-app-pub-2899987990324695/3439083832")
            resValue("string", "admob_interstitial_id", "ca-app-pub-2899987990324695/6816503321")
            resValue("string", "admob_rewarded_id", "ca-app-pub-2899987990324695/3470873423")
        }
    }

    buildFeatures { compose = true }
    sourceSets.getByName("test").resources.srcDir("src/main/assets")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.google.android.gms:play-services-ads:24.7.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    testImplementation("junit:junit:4.13.2")
}
