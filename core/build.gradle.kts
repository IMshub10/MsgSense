plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hiltAndroid)
}

android {
    namespace = "com.summer.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        dataBinding = true
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.material)
    api(libs.androidx.databinding.runtime)

    api(libs.androidx.lifecycle.livedata)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.firebase.crashlytics)
    api(libs.androidx.hilt.common)
    api(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt Dependencies
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    //ML
    implementation(libs.onnxruntime.android.vlatestrelease)
    implementation(libs.opennlp.tools)  // or the latest version

    //Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    //Google Phone number
    implementation(libs.libphonenumber)

    //paging
    api(libs.androidx.room.paging)
    api(libs.androidx.paging.runtime)

    //WorkManager
    api(libs.androidx.work.runtime.ktx)

    //debugImplementation(libs.leakcanary.android)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.converter.gson)


}