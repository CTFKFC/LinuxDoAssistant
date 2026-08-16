import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // ⚠️ 不要加 org.jetbrains.kotlin.android：AGP 9.0 起已内置 Kotlin 支持，
    //    再声明会直接构建失败。见 https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 签名配置
//
// 读 keystore/keystore.properties；文件不存在时静默回退到 debug 签名，
// 保证任何人 clone 下来都能直接构建，不会因为缺密钥而失败。
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null &&
    rootProject.file(keystoreProps.getProperty("storeFile")).exists()

// ---------------------------------------------------------------------------
// R8 混淆开关
//
// 默认**关闭**，因为 R8 相当吃内存，在低配机器（≤ 4GB）上容易把系统拖到疯狂换页。
// 关闭只影响 APK 体积与符号混淆，**不影响任何功能**。
//
// 内存充裕时建议打开，APK 能小不少：
//   ./gradlew :app:assembleRelease -Plinuxdo.minify=true
// 同时把 gradle.properties 的 org.gradle.jvmargs 提到 3072m 以上。
// ---------------------------------------------------------------------------
val enableMinify = (project.findProperty("linuxdo.minify") as String?)?.toBoolean() ?: false

android {
    namespace = "com.ydm.linuxdo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // build-tools 版本默认交给 AGP 自动选择。
    //
    // 只有在「SDK 目录只读 / 无法自动安装 AGP 期望的版本」时才需要手动指定，
    // 比如构建时报 "Failed to install build-tools;xx.x.x ... licences have not been accepted"。
    // 这时用命令行参数指定你本地已安装的版本即可：
    //   ./gradlew assembleDebug -Plinuxdo.buildTools=36.0.0
    (project.findProperty("linuxdo.buildTools") as String?)?.let { buildToolsVersion = it }

    defaultConfig {
        applicationId = "com.ydm.linuxdo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 6
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = enableMinify
            isShrinkResources = enableMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("⚠ 未找到 keystore/keystore.properties，release 构建回退为 debug 签名")
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 本项目统一用 src/main/kotlin，而不是 AGP 默认的 src/main/java
    sourceSets {
        getByName("main") {
            kotlin.directories.add("src/main/kotlin")
        }
        getByName("test") {
            kotlin.directories.add("src/test/kotlin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":automation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
