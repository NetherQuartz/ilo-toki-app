import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * The app's version, read from git: the nearest semver tag, so a build of the tag
 * itself is `1.1.1`, one three commits past it `1.1.1-3-gabc1234`, and one with
 * uncommitted changes carries `-dirty`. Before the first tag there is nothing to be
 * a version of, so it is `0.0.0-` and the commit. The about card shows exactly this
 * string on both platforms, and Android's versionName is the same string.
 */
fun git(vararg args: String): Provider<String> = providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }

val appVersionName: Provider<String> =
    git("describe", "--tags", "--match", "[0-9]*.[0-9]*.[0-9]*", "--dirty")
        .filter { it.isNotEmpty() }
        .orElse(
            git("rev-parse", "--short", "HEAD")
                .filter { it.isNotEmpty() }
                .map { "0.0.0-$it" },
        )
        .orElse("0.0.0-dev")

/**
 * 1.4.2 → 10402. Android refuses to install over a higher code, so it has to grow
 * with every release; two digits each for minor and patch keep it monotonic, and
 * the release workflow refuses a tag that would overflow them.
 */
val appVersionCode: Provider<Int> = appVersionName.map { name ->
    val (major, minor, patch) = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(name)
        ?.destructured?.toList()?.map(String::toInt) ?: listOf(0, 0, 0)
    (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

val generateAppVersion by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/appVersion/kotlin")
    val version = appVersionName
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().asFile.resolve("one/larkin/ilotoki/AppVersion.kt")
        file.parentFile.mkdirs()
        file.writeText(
            "// Generated from git by :composeApp:generateAppVersion. Do not edit.\n" +
                "package one.larkin.ilotoki\n\n" +
                "internal const val APP_VERSION = \"${version.get()}\"\n",
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(projects.llm)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    packageOfResClass = "one.larkin.ilotoki.resources"
}

android {
    namespace = "one.larkin.ilotoki"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "one.larkin.ilotoki"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()

        // Must match :llm — without this the APK would install on x86 or 32-bit
        // devices that have no libilotoki_llm.so to load.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // ggml discovers its CPU backends by scanning the native library
            // directory, which only works if the installer unpacks them to disk
            // instead of leaving them inside the APK.
            useLegacyPackaging = true
        }
    }
    // The release key lives outside the repository. CI decodes it from a secret;
    // locally, export the same four variables to sign a release build. Without them
    // assembleRelease still works and produces an unsigned APK, as before.
    val releaseKeystore = providers.environmentVariable("ILOTOKI_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("ILOTOKI_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ILOTOKI_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ILOTOKI_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}
