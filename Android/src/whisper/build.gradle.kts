plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.google.ai.edge.gallery.whisper"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 30
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf(
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                    "-DWHISPER_CURL=OFF",
                    "-DWHISPER_SDL2=OFF",
                    "-DWHISPER_COREML=OFF",
                    "-DWHISPER_OPENVINO=OFF",
                    "-DGGML_BUILD_TESTS=OFF",
                    "-DGGML_BUILD_EXAMPLES=OFF",
                    "-DGGML_METAL=OFF",
                    "-DGGML_CUDA=OFF",
                    "-DGGML_VULKAN=OFF",
                    "-DGGML_OPENCL=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DBUILD_SHARED_LIBS=OFF",
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
