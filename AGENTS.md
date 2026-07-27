# Working notes

Things that are expensive to rediscover. [README.md](README.md) describes what the
project is and how to build it — this file is the traps, the workflows and the
current state.

**Keep this file current.** When you learn something here that cost more than a few
minutes to establish — a trap, a measurement, a decision and its reason — add it in
the same change that acted on it, and delete what has stopped being true. A stale
note is worse than none: it will be trusted. Re-check the "Current state" section
at the start of a session and correct it before relying on it.

## Traps

**Merging this LoRA the obvious way produces a broken model.** The adapter touches
`embed_tokens` and gemma3 ties `lm_head` to the same tensor, so a plain
`merge_and_unload()` yields a model that repeats one token forever. See
[scripts/merge_lora.py](scripts/merge_lora.py) for the fix and the reasoning. Always
generate from a merged model before shipping it — the failure is total but silent
until you look.

**Prompt format is per-model and fails silently.** A fine-tune answers only to the
format it was trained on. Send the wrong one and the model still produces fluent
text, it just ignores the requested target language. `PromptStyle` in
[Translation.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/Translation.kt)
carries it per catalog entry; tests pin both strings. Never assume two fine-tunes
share a format, even from the same author.

**Gradle needs JDK 21.** With a newer JDK the whole error message is the version
number. Android Studio's bundled JBR is fine; from a shell:
`export JAVA_HOME=/Users/vlarkin/Library/Java/JavaVirtualMachines/corretto-21.0.3/Contents/Home`

**Hugging Face transfers wedge on Xet.** Multi-gigabyte downloads stall with an idle
socket and no error. `export HF_HUB_DISABLE_XET=1` and retry in a loop — plain HTTP
is slower but finishes, and it drops connections often enough that the retry matters.

**Do not hand-list llama.cpp sources.** Upstream split every architecture into its
own translation unit (168 files under `src/`). The Android build delegates to
llama.cpp's own CMake for this reason; a hand-maintained list rotted on the first
submodule update.

## Workflows

**Producing a model** — base + adapter to the GGUF files the app downloads:

```shell
scripts/merge-and-quantize.sh <base-repo-or-dir> <adapter-repo-or-dir> <name>
hf upload NetherQuartz/<repo> build/models .
```

Then add or update the entry in
[ModelCatalog.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/model/ModelCatalog.kt).
Get the exact byte size from the URL rather than guessing:
`curl -sIL <url> | grep -i x-linked-size`.

**Verifying on a device.** The emulator and simulator both lie about performance —
memory pressure dominates, and neither reproduces a real phone's. For anything about
speed use a physical device, and check `MemAvailable` and `SwapFree` in
`/proc/meminfo` first: measurements taken while swap is exhausted are meaningless.
Throughput tracks major page faults per translation (field 12 of `/proc/<pid>/stat`)
more than anything else.

Driving the app over adb: `adb shell input tap` with coordinates from a screenshot.
On iOS use the simulator control tool; its coordinate space is points, not pixels.

**Checking a model without the app** — build the C core against a host llama.cpp and
run prompts through it. That is the same code path the app uses, so it catches
merge and format problems that a transformers-only check would not.

## Decisions worth not relitigating

- **llama.cpp, not MLX or ExecuTorch.** Only cross-platform option that runs GGUF on
  both targets.
- **No GPU backend.** OpenCL upstream targets Adreno and the test device is Mali;
  Vulkan has no Android story upstream and loses the zero-copy mmap that keeps
  memory viable. Model size buys more than either would.
- **Greedy decoding.** Measured: sampling never improved a translation, only added
  noise. See the Sampling section in the README.
- **Q8_0 as the shipped quantization.** With a 262k-token vocabulary the embedding
  matrix dominates, so Q8_0 costs 0.35 GB over Q4_K_M and gives up nothing.

## Current state

*Last updated: 2026-07-27.*

- Model: [ilo-toki-MiLMMT-46-1b-merged](https://huggingface.co/NetherQuartz/ilo-toki-MiLMMT-46-1b-merged),
  one repository holding the merged weights and four quantizations.
- Verified on a Pixel 6 and the iOS simulator: download, resume, model switching,
  deletion, translation in all three languages both ways.
- `gh` is installed and authenticated; the HF CLI is logged in as NetherQuartz.

Open:

- **The model needs retraining.** `jan` comes back as "Player" — almost certainly the
  Minecraft translation corpus, and `jan` is in half of all sentences. Longer inputs
  lose clauses: `soweli lili li lape lon tomo` gives "Rabbit sleeps". Translation
  *into* Toki Pona looks clean; the damage is one-directional.
- The picker could offer the other quantizations; the machinery is there, it needs
  entries in the catalog.
- Desktop targets are maybe a day for macOS: four expect/actuals, and the JNI bridge
  works unchanged on a desktop JVM. Cross-compiling the natives is the real cost.
