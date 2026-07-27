# ilo toki

A translator between [Toki Pona](https://tokipona.org) and English, Russian and
Vietnamese that runs entirely on the phone. No network is used after the model has
been downloaded, and nothing you type leaves the device.

The model is [tatoeba-tok-multi-gemma-2-2b-merged][model], a gemma-2-2b fine-tune,
quantized to GGUF and executed with [llama.cpp](https://github.com/ggml-org/llama.cpp).

Android and iOS share the UI, the download logic, the view model and the inference
core; the platform-specific code is a few dozen lines on each side.

[model]: https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged

## How it works

The model was fine-tuned on plain completion prompts, not chat exchanges, so no chat
template is involved. `translationPrompt()` builds:

```
Translate Toki Pona to English.
Query: jan li moku e kili
Answer:
```

and the engine streams the completion back token by token. Each translation starts
from a cleared KV cache — there is no conversation state to carry over.

## Layout

| Path | What lives there |
|---|---|
| [`composeApp/`](composeApp/src) | The app: Compose Multiplatform UI, model download, translation view model |
| [`llm/`](llm/src) | The inference module: a plain-C façade over llama.cpp plus its Kotlin bindings |
| [`iosApp/`](iosApp/iosApp) | The Xcode project — it only hosts the Compose view controller |
| [`llama.cpp/`](llama.cpp) | Git submodule |
| [`scripts/`](scripts) | Build helpers |

Almost everything in `composeApp` is in `commonMain`. `androidMain` holds the
`Activity`, the `Application`, Material You colours and app-private storage;
`iosMain` holds the `UIViewController` entry point and the same three equivalents.

### The inference module

`llm/src/nativeCommon/cpp/ilotoki_llm.h` is a narrow C API — load, start, next,
stop, free — over llama.cpp. Both platforms use the same implementation:

- **Android** wraps it in JNI (`llm/src/androidMain/cpp/jni.cpp`).
- **iOS** binds it through cinterop (`llm/src/nativeInterop/cinterop`).

It is plain C, and no exception ever crosses the boundary: failures come back as
return codes plus an error buffer. Throwing through JNI or cinterop is undefined
behaviour, and both bindings would have to unwrap C++ exceptions anyway.

## Building

You will need:

- **JDK 21.** Newer JDKs are not supported by AGP 8.11 and fail with a bare version
  number as the whole error message. Android Studio's bundled JBR is fine; from a
  shell, set `JAVA_HOME` explicitly.
- **Android NDK 27.2.12479018** (pinned in the version catalog).
- **Xcode** with the iOS platform installed, for the iOS side.

Fetch the submodule first:

```shell
git submodule update --init --recursive
```

### Android

```shell
./gradlew :composeApp:assembleDebug
```

The build delegates to llama.cpp's own CMake with `GGML_CPU_ALL_VARIANTS`, so ggml
ships one CPU backend per Android baseline (armv8.0 through armv9.2) and picks the
best match for the device at startup. Only `arm64-v8a` is built — the model is over
2 GB, which rules out 32-bit devices, and x86 Android phones do not exist.

ggml discovers those backends by scanning the native library directory, which is why
the app sets `useLegacyPackaging = true`: the installer has to unpack them to disk
rather than leave them inside the APK.

### iOS

Open [`iosApp`](iosApp) in Xcode and run, or build the framework directly:

```shell
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

The Xcode build phase calls `:composeApp:embedAndSignAppleFrameworkForXcode`, which
in turn runs [`scripts/build-llama-apple.sh`](scripts/build-llama-apple.sh) to compile
llama.cpp and the C core into one static archive per target. Device builds use Metal;
the simulator falls back to the CPU backend, because ggml's Metal path is not usable
there. To rebuild those archives from scratch:

```shell
./scripts/build-llama-apple.sh --clean
```

The app ships the `increased-memory-limit` and `extended-virtual-addressing`
entitlements. Without them iOS terminates the process as soon as the weights are
mapped.

## Tests

```shell
./gradlew :composeApp:testDebugUnitTest
```

There is also an opt-in test that runs a real model through the cinterop path on a
simulator. It is skipped unless you point it at a GGUF file:

```shell
ILOTOKI_TEST_MODEL=/path/to/model.gguf ./gradlew :llm:iosSimulatorArm64Test
```

## The model

`ModelSpec` in [`ModelDownload.kt`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/model/ModelDownload.kt)
is the only place the file name and URL are defined, so switching quantization is a
one-line change.

The download writes to a `.part` file and resumes with a Range request, so an
interrupted transfer picks up where it stopped instead of starting the 2 GB over.
Dropped connections are retried automatically; the app only gives up when an attempt
fails without moving a single byte.

### Memory is the binding constraint

The current Q6_K weights are ~2.15 GB. Measured on a Pixel 6 (8 GB RAM), the whole
model stays resident, but Android still evicts and re-reads its pages constantly —
over 240 000 major page faults in one session — which is why the first translation
after launch is roughly half the speed of later ones.

Where the model does not fit at all, throughput collapses: on a 2 GB emulator the
same build managed 0.34 tok/s against 2.6 with enough RAM. A smaller quantization is
the single largest improvement available, ahead of any GPU backend.

### Threads

Decoding runs on the performance cores only. `performance_core_count()` reads
`cpufreq` and drops the slowest frequency group — on a Pixel 6 that means the four
Cortex-A55 cores are left out and the two A76 plus two X1 cores are used. Both
directions matter, measured on that device:

| Threads | tok/s |
|---|---|
| 2 (big cluster only) | 2.0 |
| **4 (chosen)** | **4.0** |
| 8 (every core) | 2.1 |

Work is split evenly and synchronised at every layer, so one thread on a slow core
holds up all the others.

## Licences

llama.cpp is MIT. The model inherits the [Gemma Terms of Use][gemma].

[gemma]: https://ai.google.dev/gemma/terms
