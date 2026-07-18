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
    alias(libs.plugins.roborazzi)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
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

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += "arm64-v8a"
            }
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
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    androidResources {
        noCompress += "onnx"
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

roborazzi {
    outputDir.set(file("src/test/snapshots/images"))
}

val installedNdk = providers.provider {
    file("${android.sdkDirectory}/ndk").listFiles()
        ?.filter(File::isDirectory)
        ?.maxByOrNull(File::getName)
        ?.absolutePath
        ?: throw GradleException("Install an Android NDK before rebuilding the tokenizer JNI library.")
}

tasks.register<BuildRustTokenizerAndroidTask>("buildRustTokenizerAndroid") {
    group = "build setup"
    description = "Cross-compile Rust HF tokenizer JNI for Android (arm64-v8a + x86_64)"
    val rustDir = file("src/main/java/com/summer/notifai/banking_ner/rust_tokenizer")
    sourceFiles.from(fileTree(rustDir) { include("src/**", "Cargo.toml", "Cargo.lock") })
    rustDirectory.set(rustDir)
    outputDirectory.set(layout.projectDirectory.dir("src/main/jniLibs"))
    ndkDirectory.set(providers.environmentVariable("ANDROID_NDK_HOME").orElse(installedNdk))
    cargoExecutable.set("${System.getProperty("user.home")}/.cargo/bin/cargo")
}

tasks.register<VerifyBankRegistryTask>("verifyBankRegistry") {
    group = "verification"
    description = "Verify local bank registry coverage and actionable-service safety"
    registrySource.set(rootProject.layout.projectDirectory.file("core/src/main/java/com/summer/core/banking/BankRegistry.kt"))
    logosDirectory.set(rootProject.layout.projectDirectory.dir("logos"))
    reportFile.set(layout.buildDirectory.file("reports/bank-registry.txt"))
}

val msgSenseRoot = providers.provider {
    providers.gradleProperty("msgsenseRoot").orNull
        ?: providers.environmentVariable("MSGSENSE_ROOT").orNull
        ?: localProperties.getProperty("msgsense.root")
        ?: throw GradleException(
            "Set msgsense.root in local.properties, MSGSENSE_ROOT, or -PmsgsenseRoot " +
                "to package the production MobileBERT assets."
        )
}

val syncProductionNerAssets by tasks.registering(SyncProductionNerAssetsTask::class) {
    group = "build setup"
    description = "Verify and package production MobileBERT NER assets"
    modelFile.set(layout.file(msgSenseRoot.map {
        file("$it/ner/exports/v50/mobilebert-v50/mobilebert-v50.onnx")
    }))
    tokenizerFile.set(layout.file(msgSenseRoot.map {
        file("$it/ner/models/prod/mobilebert-v50/model/tokenizer.json")
    }))
    expectedModelSha256.set("4e60212adf09ed5cbc0eb9659bd707373cf6b5f1015b139b4fc73b1765151c4f")
    expectedTokenizerSha256.set("da0e79933b9ed51798a3ae27893d3c5fa4a201126cef75586296df9b4d2c62a0")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncProductionNerAssets,
            SyncProductionNerAssetsTask::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(libs.onnxruntime.android.vlatestrelease)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.hilt.compiler)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    androidTestImplementation(libs.androidx.navigation.testing)

    implementation(libs.lottie)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
}
