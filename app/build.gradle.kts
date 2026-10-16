import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 签名密钥由 CI/本地环境变量注入；缺失时 release 保持未签名
val keystorePath: String? = System.getenv("WXDI_KEYSTORE_FILE")

android {
    namespace = "com.johnny.wxdexinspector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.johnny.wxdexinspector"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.7"

        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val p = keystorePath
            if (!p.isNullOrBlank()) {
                val kf = file(p)
                if (kf.exists()) {
                    storeFile = kf
                    storePassword = System.getenv("WXDI_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("WXDI_KEY_ALIAS")
                    keyPassword = System.getenv("WXDI_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val relSc = signingConfigs.findByName("release")
            if (relSc?.storeFile != null) {
                signingConfig = relSc
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
        // 强制把 .so 解压安装（useLegacyPackaging=true）。
        // 个别 ROM / 第三方安装器无法直接 mmap APK 内未压缩的原生库，
        // 会导致 DexKit 报 "No implementation found ... nativeInitDexKit"。
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.google.material)
    implementation(libs.gson)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.dexkit)

    // 液态玻璃：开源库 Kyant0/AndroidLiquidGlass（Apache-2.0）
    implementation(libs.backdrop)

    debugImplementation(libs.androidx.ui.tooling)
}
