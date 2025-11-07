plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.safehands"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.safehands"
        minSdk = 33
        targetSdk = 36
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

    // Assuming standard AndroidX and Google library coordinates
    // YOU MUST VERIFY THESE AGAINST YOUR LIBS.VERSIONS.TOML
    implementation("androidx.core:core-ktx:1.12.0") // Example for libs.androidx.core.ktx or libs.core.ktx
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // Example for libs.androidx.lifecycle.runtime.ktx
    implementation("androidx.activity:activity-compose:1.8.0") // Example, check version for libs.androidx.activity.compose
    implementation(platform("androidx.compose:compose-bom:2024.02.02")) // Example, check version for libs.androidx.compose.bom
    implementation("androidx.compose.ui:ui") // from libs.androidx.ui
    implementation("androidx.compose.ui:ui-graphics") // from libs.androidx.ui.graphics
    implementation("androidx.compose.ui:ui-tooling-preview") // from libs.androidx.ui.tooling.preview
    implementation("androidx.compose.material3:material3:1.2.0") // Example, check version for libs.androidx.material3

    // Traditional AndroidX Libraries (some might be duplicates of above if names are similar in your catalog)
    implementation("androidx.appcompat:appcompat:1.6.1") // Example for libs.appcompat or libs.androidx.appcompat
    implementation("com.google.android.material:material:1.11.0") // You already had this, and it's also for libs.material or libs.google.material
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Example for libs.constraintlayout or libs.androidx.constraintlayout
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0") // Example for libs.lifecycle.livedata.ktx or libs.androidx.lifecycle.livedata.ktx
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Example for libs.lifecycle.viewmodel.ktx or libs.androidx.lifecycle.viewmodel.ktx
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") // Example for libs.navigation.fragment.ktx or libs.androidx.navigation.fragment.ktx
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation(libs.androidx.recyclerview)
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")

    // Test Implementations
    testImplementation("junit:junit:4.13.2") // Example for libs.junit
    androidTestImplementation("androidx.test.ext:junit:1.1.5") // Example for libs.androidx.junit
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Example for libs.androidx.espresso.core
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02")) // Match version with the implementation platform
    androidTestImplementation("androidx.compose.ui:ui-test-junit4") // from libs.androidx.ui.test.junit4

    // Debug Implementations
    debugImplementation("androidx.compose.ui:ui-tooling") // from libs.androidx.ui.tooling
    debugImplementation("androidx.compose.ui:ui-test-manifest") // from libs.androidx.ui.test.manifest
}
