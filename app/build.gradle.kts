import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lingji.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lingji.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 45
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = rootProject.file("local.properties")
            if (localProperties.exists()) {
                val props = Properties()
                localProperties.inputStream().use { props.load(it) }
                storeFile = file(props.getProperty("RELEASE_STORE_FILE") ?: "../release.keystore")
                storePassword = props.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = props.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = props.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.gson)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.markwon.core)
    implementation(libs.markwon.image)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.markwon.latex)
    implementation(libs.markwon.strikethrough)

    implementation(libs.coil.compose)
    implementation(libs.haze.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

/**
 * 发版前校验：内置更新日志的最新条目必须与 versionName 一致。
 *
 * 「设置 → 应用 → 历代更新日志」显示的是随包内置的 res/raw/changelog.json，
 * 忘了更新时用户装到新版却看不到本版的更新说明。校验挂在 release 构建的
 * preReleaseBuild 上，漏写日志就无法产出安装包。
 *
 * 修复方式（发布流程第 3 步）：
 *   1. 把 docs/changelog/current.md 的标题改成 `# <versionName>` 并重写本版内容；
 *   2. 执行 python tools/gen_changelog.py。
 *
 * 只是打自测包、确实不需要更新日志时，可加 -PskipChangelogCheck=true 跳过。
 */
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    if ((project.findProperty("skipChangelogCheck") as String?) == "true") return@configureEach
    doFirst {
        val versionName = android.defaultConfig.versionName?.toString().orEmpty()
        val changelog = file("src/main/res/raw/changelog.json")
        if (!changelog.exists()) {
            throw GradleException(
                "内置更新日志缺失：${changelog.path}\n" +
                "请先写好 docs/changelog/current.md，再执行 python tools/gen_changelog.py" +
                "（自测包可加 -PskipChangelogCheck=true 跳过）"
            )
        }
        // 顶层第一个 version 就是最新版本，只读开头即可，不必解析整个 json
        val latest = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
            .find(changelog.readText().take(400))?.groupValues?.get(1)
        if (latest != versionName) {
            throw GradleException(
                "内置更新日志的最新版本是「${latest ?: "未知"}」，与 versionName「$versionName」不一致。\n" +
                "请按发布流程第 3 步：先把 docs/changelog/current.md 的标题改成 # $versionName 并重写本版内容，" +
                "再执行 python tools/gen_changelog.py。\n" +
                "（只是自测包、确实不需要日志时，可加 -PskipChangelogCheck=true）"
            )
        }
    }
}
