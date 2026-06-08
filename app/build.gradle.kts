import java.util.Properties
import java.security.MessageDigest

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

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
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
        buildConfigField("String", "NER_BENCHMARK_MODEL_ID", "\"unsupported\"")
    }

    flavorDimensions += "nerBenchmarkModel"
    productFlavors {
        create("albertV50") {
            dimension = "nerBenchmarkModel"
            applicationIdSuffix = ".benchmark.albertv50"
            buildConfigField("String", "NER_BENCHMARK_MODEL_ID", "\"albert-v50\"")
        }
        create("bertBaseV50") {
            dimension = "nerBenchmarkModel"
            applicationIdSuffix = ".benchmark.bertbasev50"
            buildConfigField("String", "NER_BENCHMARK_MODEL_ID", "\"bert-base-v50\"")
        }
        create("distilbertV50") {
            dimension = "nerBenchmarkModel"
            applicationIdSuffix = ".benchmark.distilbertv50"
            buildConfigField("String", "NER_BENCHMARK_MODEL_ID", "\"distilbert-v50\"")
        }
        create("mobilebertV50") {
            dimension = "nerBenchmarkModel"
            applicationIdSuffix = ".benchmark.mobilebertv50"
            buildConfigField("String", "NER_BENCHMARK_MODEL_ID", "\"mobilebert-v50\"")
        }
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
        buildConfig = true
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
    val installedNdk = file("${android.sdkDirectory}/ndk")
        .listFiles()
        ?.filter { it.isDirectory }
        ?.maxByOrNull { it.name }
        ?.absolutePath
        .orEmpty()
    environment("ANDROID_NDK_HOME",
        providers.environmentVariable("ANDROID_NDK_HOME").orElse(
            installedNdk
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

mapOf(
    "AlbertV50" to "albertV50",
    "BertBaseV50" to "bertBaseV50",
    "DistilbertV50" to "distilbertV50",
    "MobilebertV50" to "mobilebertV50",
).forEach { (variantPrefix, flavor) ->
    val validateTask = tasks.register("validate${variantPrefix}NerBenchmarkAssets") {
        val assetDir = file("src/$flavor/assets/ner_benchmark")
        inputs.dir(assetDir)
        doLast {
            val required = listOf(
                "model.onnx",
                "tokenizer.json",
                "config.json",
                "export_info.json",
                "fixture.jsonl",
                "golden.jsonl",
                "performance-selection.json",
                "manifest.sha256",
            )
            required.forEach { name ->
                check(file("$assetDir/$name").isFile) {
                    "Missing $flavor benchmark asset $name. Run scripts/sync_ner_benchmark_assets.sh."
                }
            }
            val expectedHashes = file("$assetDir/manifest.sha256").readLines()
                .filter { it.isNotBlank() }
                .associate { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    check(parts.size == 2) { "Malformed $flavor manifest line: $line" }
                    parts[1].removePrefix("*") to parts[0]
                }
            required.filterNot { it == "manifest.sha256" }.forEach { name ->
                val digest = MessageDigest.getInstance("SHA-256")
                file("$assetDir/$name").inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                check(expectedHashes[name] == actual) {
                    "SHA-256 mismatch for $flavor/$name. Run scripts/sync_ner_benchmark_assets.sh."
                }
            }
            for (name in listOf("fixture.jsonl", "golden.jsonl")) {
                val count = file("$assetDir/$name").useLines { it.count() }
                check(count == 6445) { "$flavor/$name must contain 6445 rows; found $count" }
            }
        }
    }
    tasks.matching { it.name.startsWith("pre$variantPrefix") && it.name.endsWith("Build") }
        .configureEach { dependsOn(validateTask) }
    tasks.matching { it.name.startsWith("process$variantPrefix") && it.name.endsWith("GoogleServices") }
        .configureEach { enabled = false }
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.converter.gson)
    testImplementation(libs.onnxruntime)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.firebase.crashlytics)

    // Hilt Dependencies
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.hilt.compiler)

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
