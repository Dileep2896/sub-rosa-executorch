plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.subrosa.app"
    compileSdk = 34
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.subrosa.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Target the S25 Ultra and the Apple-Silicon emulator. arm64-v8a only keeps the
        // APK lean and matches the ExecuTorch / whisper.cpp native libs we ship later.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }
    // Native on-device ASR: builds libwhisper.so (whisper.cpp + ggml, CPU) from vendored sources.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            // Extract native libs to nativeLibraryDir on install so the app can (a) exec the
            // qnn_llama_runner binary — shipped as libqnn_llama_runner.so — as a subprocess, and
            // (b) let FastRPC find the Hexagon Skel (libQnnHtpV79Skel.so) as a real file via ADSP_LIBRARY_PATH.
            useLegacyPackaging = true
            // The QNN AAR also bundles libqnn_executorch_backend.so; prefer our jniLibs copy, which
            // matches the runner + the QNN .pte (same Colab/QNN version).
            pickFirsts += "lib/arm64-v8a/libqnn_executorch_backend.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment) // align fragment with activity 1.9.x (ActivityResult 16-bit fix)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // On-device LLM: ExecuTorch 1.3.1 QNN AAR (vendored). Bundles XNNPACK (CPU) + QNN/Hexagon (NPU), the
    // 1.3.1 LlmModule API (matches our code), and 16 KB-aligned .so. Runs our XNNPACK .pte on CPU today;
    // drop a QNN-exported .pte + the Qualcomm HTP .so libs (jniLibs/) to flip the LLM to the Hexagon NPU.
    implementation(files("libs/executorch-qnn-1.3.1.aar"))
    implementation(libs.facebook.fbjni)
    implementation(libs.facebook.soloader)

    // On-device speaker diarization: sherpa-onnx (ONNX Runtime bundled). Vendored AAR; its arm64-v8a
    // .so files are 16 KB page-aligned, so they load on the S25 (Android 16). Models pushed at runtime.
    implementation(files("libs/sherpa-onnx-1.13.3.aar"))

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
