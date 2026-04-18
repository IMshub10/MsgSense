import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hiltAndroid)
    id("kotlin-kapt")
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.androidx.navigation.safe.args)
    alias(libs.plugins.baselineprofile)
}

// Load keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.summer.notifai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.utilities.msgsense"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore
        }
        create("release") {
//            storeFile = file(keystoreProperties["storeFile"] as String)
//            storePassword = keystoreProperties["storePassword"] as String
//            keyAlias = keystoreProperties["keyAlias"] as String
//            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    // Keep ML model files uncompressed in the APK so TFLite/ORT can mmap them
    // and so ONNX external-data offsets stay valid.
    androidResources {
        noCompress += listOf("tflite", "onnx", "onnx.data")
    }
}

baselineProfile {
    // Don't build on every build - generate manually
    automaticGenerationDuringBuild = false
}

// --- Rust HF tokenizer JNI build (shared banking_ner package) ---
tasks.register<Exec>("buildRustTokenizerAndroid") {
    description = "Cross-compile Rust HF tokenizer JNI for Android (arm64-v8a + x86_64)"
    val rustDir = file("src/main/java/com/summer/notifai/banking_ner/rust_tokenizer")
    val jniLibsDir = file("src/main/jniLibs")
    workingDir = rustDir
    environment("CARGO_TARGET_DIR", "./target")
    environment("ANDROID_NDK_HOME",
        providers.environmentVariable("ANDROID_NDK_HOME").orElse(
            "${android.sdkDirectory}/ndk/${file("${android.sdkDirectory}/ndk").list()?.maxOrNull() ?: ""}"
        ).get()
    )
    commandLine(
        "${System.getProperty("user.home")}/.cargo/bin/cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", jniLibsDir.absolutePath,
        "build", "--release"
    )
    inputs.files(fileTree(rustDir) { include("src/**", "Cargo.toml", "Cargo.lock") })
    outputs.dir(jniLibsDir)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(libs.converter.gson)
    implementation(libs.onnxruntime.android.vlatestrelease)
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
    testImplementation(libs.converter.gson)
    testImplementation(libs.onnxruntime)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.firebase.crashlytics)

    // Hilt Dependencies
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.dynamic.features.fragment)

    androidTestImplementation(libs.androidx.navigation.testing)

    //lottie
    implementation(libs.lottie)
    
    // Baseline Profile - improves cold start performance
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
}