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

**The models directory also holds the app's data.** `settings` and `history` are
written next to the `.gguf` files. `ModelRepository.removeUnknownFiles()` used to
delete everything in there the catalog did not claim, which silently ate both on the
next launch; it now only removes files ending in `.gguf`/`.gguf.part`. Anything new
stored beside the models is safe, but a sweep that defaults to deleting is not —
leave that predicate alone.

**Space Grotesk is Latin-only and has no `‹` `›`.** Cyrillic falls through to the
platform font, which is fine and intended (Russian is one of the three languages).
The guillemets are not: typing them gets a mismatched fallback glyph, so every
chevron in the UI is `IloTokiIcons.Chevron`, drawn. Check any new punctuation against
the font before typing it into a string.

**The hard shadow is painted outside the element's bounds.** `Modifier.surface` draws
it, so a parent that clips, or a gap narrower than the offset, cuts it off. The
13–14 dp gaps in the layouts are what leave room for it. Compose's own
`Modifier.shadow` is not a substitute — it is blurred, which this design never is.
For the same reason the swap knob needs `requiredSize` and `zIndex`: it lives in a
14 dp seam it has to overflow, and over both plates rather than under them.

**SwiftUI insets the Compose view unless you tell it not to.** `ContentView` hosts
`MainViewController()`; without `.ignoresSafeArea()` on it SwiftUI keeps the Compose
surface inside the safe area, and the window's white shows above the status bar and
below the home indicator while the app's own background stops short of both. It
looks like a Compose layout bug and is not one — Compose already applies every inset
itself. Android's equivalent is the `enableEdgeToEdge()` in `MainActivity`.

**Every target-plate state needs a collapsed variant.** With the keyboard up the
target plate becomes a 76 dp strip, and anything laid out as a stacked column —
label, big number, buttons — is silently clipped mid-line rather than shrunk. The
states go through `CollapsedStrip`, one row, one line. Adding a new target state
means adding its collapsed form in the same change.

**A border drawn behind the content gets painted over.** Several surfaces are
filled by a *child* that reaches their own edge — the accent growing behind the
slab's label, the progress line, the active cell of a segmented control. With the
outline in a `drawBehind`, every one of them swallowed it. `Modifier.surface` uses
`drawWithContent` and draws the fill and shadow first, then the content, then the
2 dp line last. Anything new that fills to its own edge inherits the fix; anything
that draws its own divider (the segmented cells) must come *after* its background in
the modifier chain for the same reason.

**sitelen pona sits high in its line box.** The font carries ascent it never draws
into, so a single word centred as a mark inside a square — `olin` on a history card,
`pona` in the language list — lands visibly above centre. The `sitelen` and
`historySitelen` styles set `LineHeightStyle(Center, Trim.Both)`, which fixes it
everywhere at once. Do not nudge individual call sites with padding.

**`navigationBarsPadding().imePadding()` chained is not stable on Android.** The
pair relies on the first modifier consuming the inset before the second reads it,
and measured on a Pixel 9 the bottom inset flipped between 0 and the gesture bar's
63 px for roughly half a second at a time — the two plates visibly breathed, and the
rest of the time the gesture bar was not being cleared at all. Screens use
`Modifier.screenBottomInsets()`, one `WindowInsets.navigationBars.union(ime)`. To
check a suspicion like this, record with `adb shell screenrecord`, split with
`ffmpeg -vf fps=30` and track a plate edge down one pixel column — a single
screenshot cannot tell a layout wobble from a stray tap.

**An empty `models` list means «the disk has not been looked at yet».** The catalog
is never empty, so `models.none { it.downloaded }` on the initial value is not «no
model», it is «no answer yet» — and reading it as the former made the app announce
NO TRANSLATOR YET on every launch. The same for `ModelStatus.Loading`: mapping a
1.3 GiB file takes about a second, during which the model is very much present.
Anything that asks «is there a translator» has to exclude both.

**Do not hand-list llama.cpp sources.** Upstream split every architecture into its
own translation unit (168 files under `src/`). The Android build delegates to
llama.cpp's own CMake for this reason; a hand-maintained list rotted on the first
submodule update.

## Workflows

**Producing a model** — base + adapter to the GGUF files the app downloads:

```shell
pyenv shell ilo-toki   # `hf` lives only in this env; the shim fails without it
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
- **Nothing downloads on its own.** `ensureLoaded()` only loads a model already on
  disk; the slab and the translators screen are the only things that start a
  transfer. This is the point of the redesign — a gigabyte of someone's data plan is
  their decision — so do not "helpfully" put the fetch back into startup.
- **The dark background is true black, and so is the dark shadow.** Black costs an
  OLED panel nothing, which is the point. The consequence is that the hard shadow has
  to go with it: nothing is darker than the surface it falls on, so the mock's
  `#0A0906` would render as a faint halo around every plate rather than a shadow. In
  dark it is the 2 dp `line` that separates a plate from the screen. Light keeps its
  shadow; do not "restore" the dark one.
- **One accent colour, one meaning.** `jelo` marks the target of the translation or
  the active choice, and nothing else. A second accent-filled surface on a screen
  makes both unreadable at a glance.
- **The script switch is not a setting.** Latin versus sitelen pona belongs to the
  text on screen, so it lives on the plate showing it. It was a `Switch` in the
  content flow before; it is not going back there.
- **The launcher icon is the same geometry as `IloTokiIcons.Mark`.** Android draws
  it from three vector drawables (`ic_launcher_background`/`_foreground`/
  `_monochrome`) — the monochrome layer is what themed icons use, so it is not
  optional. iOS builds it from `AppIcon.icon` with a flat jelo fill and
  `Assets/mark.png`, regenerated by the same 100-grid arithmetic. Change the mark and
  all four have to move together; there are no launcher PNGs left to forget, minSdk
  is 26 so `mipmap-anydpi-v26` always wins.
- **The launcher mark is sized by its diagonal, not its bounding box.** Its farthest
  corner is 52.1 units from the centre of the 100 grid, so at scale `s` it sits
  `52.1*s` dp from the centre of the 108 dp canvas. A round mask shows a 72 dp circle
  (radius 36) and the safe circle is 66 (radius 33). Scale 0.7 put the corner at 36.4
  and Pixel sliced the box corners off; 0.58 puts it at 30.2. `translateX/Y =
  54 - 50*scale` keeps it centred. Preview a change under a circular mask before
  believing it — a square-looking icon that fits the safe *box* still gets cut by the
  circle.
- **The script pill lives at the foot of its plate, on the left.** The mock puts it
  top-right, but that is exactly where the swap knob overhangs the target plate, so
  it vanished under the knob whenever toki pona was the target. Its glyphs are
  `FontWeight.Bold` — sitelen pona pona ships a single weight, so that is a synthetic
  embolden, which is what is wanted: the same strokes thickened to sit among the
  700-weight stamps around it.
- **The language picker never sits on the toki pona plate.** One side of the pair is
  always toki pona and has nothing to choose, so only the other side's pair stamp
  carries the caret and reacts to a tap — and it moves to the other plate when the
  direction flips. `pickerOnSource` in `TranslatorScreen` is the single place that
  decides which stamp gets it. Where the popover *hangs* is measured, not derived —
  the plate area and the target plate report themselves through
  `onGloballyPositioned` — because the target plate's top moves with the keyboard and
  with whatever state the plate is in. A constant offset is what put the list down at
  the bottom of the screen in the first place.

## Current state

*Last updated: 2026-07-28.*

- Model: [ilo-toki-MiLMMT-46-1b-merged](https://huggingface.co/NetherQuartz/ilo-toki-MiLMMT-46-1b-merged),
  one repository holding the merged weights and four quantizations.
- **The «sitelen» redesign is on `main`**, merged as
  [#3](https://github.com/NetherQuartz/ilo-toki-app/pull/3). Four screens (translator, settings,
  translators, history) plus an about overlay, own primitives and tokens instead of
  Material 3, drawn icons, Space Grotesk added beside the sitelen pona font,
  file-backed settings and history. `swap_horiz.xml` is gone — the knob is vertical.
  The design source is the handoff bundle in the Claude Design project
  `57bb2870-714f-455c-b967-404449312680` (`ilo toki redesign.dc.html`, turn 3 option
  `3a`, plus its README).
- Verified on a Pixel 6 and the iOS simulator: download, resume, model switching,
  deletion, translation in all three languages both ways. The redesign itself was
  walked through on an iPhone 17 Pro simulator and a Pixel 9 emulator: first run,
  download start/pause/resume, translation, script flip, history, both themes.
- **There is no release signing config.** `assembleRelease` produces
  `composeApp-release-unsigned.apk` and nothing installs it. Test builds so far were
  signed by hand with the *debug* keystore
  (`apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android
  --ks-key-alias androiddebugkey`), which is fine for a phone you own and wrong for
  anything handed out: the key is public, and a real release later cannot update over
  it — the signature will not match and the app has to be uninstalled first.
- `gh` is installed and authenticated as NetherQuartz. The Hugging Face token is
  stored (`~/.cache/huggingface/token`), but `hf` is only installed in the pyenv env
  `ilo-toki`; from the default 3.12.2 the shim fails with «command not found».

Open:

- **A real release keystore, before anything is distributed.** `keytool -genkeypair`
  into a file outside the repository, then `signingConfigs` in
  [build.gradle.kts](composeApp/build.gradle.kts) reading its passwords from
  `local.properties` or the environment — never from a committed file. `versionCode`
  is still 1 and has to start moving once updates are a thing.

- **The model needs retraining.** `jan` comes back as "Player" — almost certainly the
  Minecraft translation corpus, and `jan` is in half of all sentences. Longer inputs
  lose clauses: `soweli lili li lape lon tomo` gives "Rabbit sleeps". Translation
  *into* Toki Pona looks clean; the damage is one-directional.
- The translators screen could offer the other quantizations; the machinery is there,
  it needs entries in the catalog.
- Swipe-to-delete on a history card is in the spec; the explicit `✕` is what is
  implemented, as in the prototype.
- Desktop targets are maybe a day for macOS: four expect/actuals, and the JNI bridge
  works unchanged on a desktop JVM. Cross-compiling the natives is the real cost.
