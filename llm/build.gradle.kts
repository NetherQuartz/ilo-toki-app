import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

/**
 * llama.cpp and the shared C core, built into one static archive per iOS target.
 * The script is idempotent and skips targets whose archive is already up to date.
 */
val llamaAppleDir: Provider<Directory> = layout.buildDirectory.dir("llama-apple")

val buildLlamaApple by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds llama.cpp as static libraries for the iOS targets"

    val script = rootProject.file("scripts/build-llama-apple.sh")
    inputs.file(script)
    inputs.dir(layout.projectDirectory.dir("src/nativeCommon/cpp"))
    inputs.file(rootProject.file("llama.cpp/CMakeLists.txt"))
    outputs.dir(llamaAppleDir)

    commandLine(script.absolutePath, llamaAppleDir.get().asFile.absolutePath)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64() to "ios-device",
        iosSimulatorArm64() to "ios-simulator",
    ).forEach { (iosTarget, flavor) ->
        iosTarget.compilations.getByName("main").cinterops.create("ilotokiLlm") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/ilotoki_llm.def"))
            includeDirs(layout.projectDirectory.dir("src/nativeCommon/cpp"))
            // The merged archive is packed into the klib, so consumers do not need
            // to know where it lives on disk.
            extraOpts(
                "-libraryPath", llamaAppleDir.get().dir("$flavor/lib").asFile.absolutePath,
                "-staticLibrary", "libilotoki_llm.a",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * Hands ILOTOKI_TEST_MODEL to the simulator test runner. Simulator processes are
 * spawned through simctl, which only forwards variables prefixed SIMCTL_CHILD_.
 */
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    val modelPath = providers.environmentVariable("ILOTOKI_TEST_MODEL").getOrElse("")
    environment("SIMCTL_CHILD_ILOTOKI_TEST_MODEL", modelPath)
}

tasks.withType<CInteropProcess>().configureEach {
    dependsOn(buildLlamaApple)
}

android {
    namespace = "one.larkin.ilotoki.llm"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = libs.versions.android.ndk.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        // The model is 2 GB: it cannot be mapped on a 32-bit device, and x86 Android
        // phones do not exist. Apple-silicon emulators run arm64-v8a too, so this
        // covers every device that can realistically run the app.
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // Always optimize the native code: an unoptimized llama.cpp is
                // unusably slow even in a debug build of the app.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                // Allow compiling 16 KB page-aligned shared libraries.
                // https://developer.android.com/guide/practices/page-sizes#compile-r27
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
