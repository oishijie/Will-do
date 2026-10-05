import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // 🔥 启用序列化插件
    kotlin("plugin.serialization")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun localProperty(name: String): String {
    return (localProperties.getProperty(name) ?: "")
        .trim()
        .removeSurrounding("\"")
}

fun signingProperty(name: String): String {
    return (providers.gradleProperty(name).orNull ?: localProperty(name))
        .trim()
        .removeSurrounding("\"")
}

fun escapeBuildConfigString(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.antgskds.calendarassistant"
    compileSdkVersion("android-37.0")

    defaultConfig {
        applicationId = "com.antgskds.calendarassistant"
        minSdk = 33
        targetSdk = 36
        versionCode = 175
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "LOCAL_MODEL_EDITION", "false")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Build fingerprint for anti-piracy verification
        buildConfigField("String", "CODE_AUTHOR", "\"AIXINJUELUOAI\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField(
            "String",
            "PROMPT_UPDATE_URL",
            "\"${escapeBuildConfigString(localProperty("PROMPT_UPDATE_URL"))}\""
        )
        buildConfigField(
            "String",
            "APP_UPDATE_URL",
            "\"${escapeBuildConfigString(localProperty("APP_UPDATE_URL"))}\""
        )
    }

    signingConfigs {
        val releaseStoreFile = signingProperty("RELEASE_STORE_FILE")
        val releaseStorePassword = signingProperty("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = signingProperty("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = signingProperty("RELEASE_KEY_PASSWORD")
        if (
            releaseStoreFile.isNotBlank() &&
            releaseStorePassword.isNotBlank() &&
            releaseKeyAlias.isNotBlank() &&
            releaseKeyPassword.isNotBlank()
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "ui"
    productFlavors {
        create("native") {
            dimension = "ui"
        }
    }

    compileOptions {
        // 🔥 开启脱糖，支持 Java 8 时间 API
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":location"))

    // === 基础库 (使用默认生成的引用) ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")

    // ✅✅✅ 新增关键依赖：修复 MainActivity 中的 viewModel() 报错
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // 补充图标库
    implementation("androidx.compose.material:material-icons-extended")

    // === 脱糖库 (Time API 必需) ===
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // === JSON 序列化 (核心数据地基) ===
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // === Ktor 网络库 (AI 请求用) ===
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")

    // === 本地二维码/条码扫描（独立于图文 AI 识别） ===
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // === QR 码生成 ===
    implementation("com.google.zxing:core:3.5.3")

    // === 离线语音转写 ===
    implementation("com.bihe0832.android:lib-sherpa-onnx:6.25.21")

    // === 测试库 ===
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    implementation("androidx.navigation:navigation-compose:2.8.0")

    // === Shizuku (静默提权) ===
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("dev.rikka.shizuku:aidl:13.1.5")

    // 小米 HyperOS 灵动岛（LSPosed/Xposed）支持已下线，随之移除：
    //   io.github.d4viddf:hyperisland_kit —— 灵动岛 UI 库，仅 MiuiIslandManager 使用
    //   de.robv.android.xposed:api     —— Xposed 桥接接口，仅 SelfHook/DispatcherHook 使用
    // 记账功能已下线：org.smali:dexlib2（原 AutoAccounting upstream DEX adaptation engine）随之移除。

    // === Room 数据库 ===
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // === WorkManager ===
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // 与 AndroidX Test 1.7 的原子依赖组对齐，避免 instrumentation 编译/运行类路径版本冲突。
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // Markdown (便签渲染) —— io.noties.markwon 六个 artifact 已随 feature/note 的
    // MarkdownText 与 editor/markdown 下的 EditHandler 一并移除：编辑器实际使用的是
    // PlainNoteEditor 自身的实现，这套 Markwon 方案从未接入，属废弃代码。

}
