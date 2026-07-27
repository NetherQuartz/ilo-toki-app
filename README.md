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

**Nothing is downloaded until you ask for it.** Launching the app loads the model
if it is already on the device and otherwise leaves the screen fully usable with
the target plate saying so; the slab is what starts the transfer. A download runs
in the background behind a 12 dp line under the header, can be paused, and resumes
from its `.part` file afterwards.

## The interface

The look is called **«sitelen»**: bone paper, a 2 dp ink outline on everything, a
hard blur-free offset shadow, and exactly one saturated colour. That colour —
`jelo`, the yellow — means one thing and only one thing at a time: *the target of
the translation, or the active choice*. Filling a second surface with it breaks the
code that lets the screen be read at a glance, so [`Plate`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/Primitives.kt)
takes a background rather than defining variants.

The screen is two stacked plates — source above, target below — with a swap knob on
the seam and the primary action as a slab at the foot. Settings, the model list, the
history stack and the about card each have their own place; none of them interrupts
translating.

Everything visual is built from the primitives in
[`ui/Primitives.kt`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/Primitives.kt)
and the tokens in [`ui/theme/Theme.kt`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/theme/Theme.kt);
the icons are drawn as `ImageVector`s from rectangles, triangles and circles on a
100×100 grid in [`ui/IloTokiIcons.kt`](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/IloTokiIcons.kt).
Material 3 is still on the classpath, but nothing in the app looks like it.

Two type families are used. Space Grotesk carries the UI and translated text.
**sitelen pona is a ligature font**: the app writes plain latin toki pona
(`sina pona tawa mi`) and turns on standard and contextual ligatures, and the
glyphs appear on their own — no glyph is ever drawn or looked up by hand. The
script is flipped from a pill on whichever plate holds toki pona, and there is
deliberately no setting for it: it is a property of the text, not a preference.

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

To produce the files a catalog entry points at:

```shell
scripts/merge-and-quantize.sh <base-repo-or-dir> <adapter-repo-or-dir> <name>
```

It merges the adapter, converts to GGUF and writes the quantization ladder into
`build/models`. Read [`scripts/merge_lora.py`](scripts/merge_lora.py) before
merging one of these adapters by hand — a plain `merge_and_unload()` produces a
model that repeats a single token, and the reason is not obvious.

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

### Sampling

Decoding is greedy. Translation has one right answer per input, so sampling can
only walk away from it, and the same sentence translating differently on a second
try reads as a bug. Sweeping the temperature over a fixed set of sentences bore
that out: 0.2 wobbled only where the model was already unsure, and 0.5 turned
`jan li moku e kili` into "Apple is eaten" and "Яблоко съедено" — never into
anything better. It also happens to be what the base model's authors recommend,
and it skips a sort over a 262k-token vocabulary on every token, which is worth a
few per cent.

Where the model is simply wrong — it renders `jan` as "Player", probably a mark
left by a Minecraft translation corpus — greedy is wrong deterministically. That
is a model matter, not a sampling one.

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

Both bundled fonts are under the SIL Open Font License: [Space Grotesk][grotesk]
(text of the licence in [`licenses/`](licenses)) and sitelen pona pona.

[gemma]: https://ai.google.dev/gemma/terms
[grotesk]: https://github.com/floriankarsten/space-grotesk
