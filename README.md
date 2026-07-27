# ilo toki

A translator between [Toki Pona](https://tokipona.org) and English, Russian and
Vietnamese that runs entirely on the phone. No network is used after the model has
been downloaded, and nothing you type leaves the device.

The default model is [ilo-toki-MiLMMT-46-1b-merged][model], a fine-tune of Xiaomi's
46-language translation model, quantized to GGUF and executed with
[llama.cpp](https://github.com/ggml-org/llama.cpp).

Android and iOS share the UI, the download logic, the view model and the inference
core; the platform-specific code is a few dozen lines on each side.

[model]: https://huggingface.co/NetherQuartz/ilo-toki-MiLMMT-46-1b-merged

## How it works

These models are fine-tuned on plain completion prompts, not chat exchanges, so no
chat template is involved. `translationPrompt()` builds the format the selected
model expects, which for the default one is:

```
Translate this from Toki Pona to English:
Toki Pona: jan li moku e kili
English:
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
best match for the device at startup. Only `arm64-v8a` is built — mapping a model
this size rules out 32-bit devices, and x86 Android phones do not exist.

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

## Models

[`ModelCatalog`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/model/ModelCatalog.kt)
lists everything the app can download. Users pick between them, and each entry
carries its own prompt format — a fine-tune only answers to the format it was
trained on, and sending the wrong one does not fail loudly: the model keeps
producing fluent text while quietly ignoring the requested target language. There
are tests pinning both formats for that reason.

Retiring a model means deleting its entry. Files in the models directory that no
entry claims are removed on the next launch, which is how a superseded download
stops occupying a couple of gigabytes forever. Mark an entry `deprecated` first and
drop it a release later, so anyone who already has it is not left without a
translator in the meantime.

The download writes to a `.part` file and resumes with a Range request, so an
interrupted transfer picks up where it stopped instead of starting over. Dropped
connections are retried automatically; the app only gives up when an attempt fails
without moving a single byte.

### Memory is the binding constraint

Generation is limited by memory bandwidth, so throughput tracks how much of the
model the system is willing to keep resident. Measured on a Pixel 6 (8 GB), the
1.29 GB default model runs at 4.5–7.9 tok/s once its pages are in — but a run
that has to fault them back in drops below 1 tok/s:

| Major page faults during the translation | tok/s |
|---|---|
| 3–579 | 4.5–7.9 |
| 128 000 | 0.5 |

Its 2.15 GB predecessor managed about 4 tok/s at best on the same device, and on
a 2 GB emulator — where it could not fit at all — 0.34 tok/s.

So model size buys speed twice over: fewer bytes to stream per token, and fewer
occasions for the system to evict the model in the first place. That is why a
smaller model beats a GPU backend here.

These numbers move a lot with whatever else is running; the ones above were taken
on a freshly rebooted phone.

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
