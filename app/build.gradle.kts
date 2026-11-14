plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.papermill.app"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.papermill.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            // M0 阶段先关 R8：出问题时日志可读，且包体不是当前矛盾
            isMinifyEnabled = false
            isShrinkResources = false
            // 复用 debug keystore：和手机上其它调试包同签名，可覆盖安装、数据不丢
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        // AGP 8 起默认不再生成 BuildConfig，「检查更新」要读 VERSION_NAME，必须显式打开
        buildConfig = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
