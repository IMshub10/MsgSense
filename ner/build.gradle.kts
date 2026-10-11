import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hiltAndroid)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.summer.ner"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
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
    val rustDir = file("src/main/java/com/summer/ner/tokenizer/rust_tokenizer")
    sourceFiles.from(fileTree(rustDir) { include("src/**", "Cargo.toml", "Cargo.lock") })
    rustDirectory.set(rustDir)
    outputDirectory.set(layout.projectDirectory.dir("src/main/jniLibs"))
    ndkDirectory.set(providers.environmentVariable("ANDROID_NDK_HOME").orElse(installedNdk))
    cargoExecutable.set("${System.getProperty("user.home")}/.cargo/bin/cargo")
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

    implementation(libs.onnxruntime.android.vlatestrelease)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.runtime)
}
