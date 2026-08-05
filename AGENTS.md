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

**Merging this LoRA the obvious way produces a broken model.** gemma3 ties
`lm_head` to `embed_tokens`, and the adapter trains that tensor, so a plain
`merge_and_unload()` yields a model that repeats one token forever. See
[scripts/merge_lora.py](scripts/merge_lora.py) for the fix and the reasoning. Always
generate from a merged model before shipping it — the failure is total but silent
until you look.

The trap survives a change in how the adapter reaches the embeddings, so do not
read «no `embed_tokens` in `target_modules`» as «fixed». 1.0 put a full LoRA on
`embed_tokens`; 1.1 trains 15 764 individual rows through PEFT's
`trainable_token_indices` with `ensure_weight_tying: false` — which means the tied
output head still read the *base* embeddings all through training, so the untie is
still exactly what reproduces it. To check a merge rather than assume it: every
index the adapter lists must have moved, no other row may have, `lm_head` must
equal the *base* embeddings, and `tie_word_embeddings` must be `false`.

**Prompt format is per-model and fails silently.** A fine-tune answers only to the
format it was trained on. Send the wrong one and the model still produces fluent
text, it just ignores the requested target language. `PromptStyle` in
[Translation.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/Translation.kt)
carries it per catalog entry; tests pin both strings. Never assume two fine-tunes
share a format, even from the same author.

**Gradle needs JDK 21.** With a newer JDK the whole error message is the version
number. Android Studio's bundled JBR is fine; from a shell:
`export JAVA_HOME=/Users/vlarkin/Library/Java/JavaVirtualMachines/corretto-21.0.3/Contents/Home`

**The iOS build hits that same trap through Xcode.** `iosApp`'s «Compile Kotlin
Framework» phase shells out to Gradle, so a build started from a tool that does not
carry the environment fails with `What went wrong: 26.0.1` and nothing else. Export
`JAVA_HOME` and drive `xcodebuild` yourself:

```shell
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "id=<simulator-udid>" -derivedDataPath <dir> build
```

then `xcrun simctl install`/`launch`. `--console-pty` on the launch is the only way
to read a `println` out of Kotlin/Native on the simulator.

**Hugging Face transfers wedge on Xet.** Multi-gigabyte downloads stall with an idle
socket and no error. `export HF_HUB_DISABLE_XET=1` and retry in a loop — plain HTTP
is slower but finishes, and it drops connections often enough that the retry matters.

**The models directory also holds the app's data.** `settings` and `history` are
written next to the `.gguf` files. `ModelRepository.removeUnknownFiles()` used to
delete everything in there the catalog did not claim, which silently ate both on the
next launch; it now only removes files ending in `.gguf`/`.gguf.part`. Anything new
stored beside the models is safe, but a sweep that defaults to deleting is not —
leave that predicate alone.

**Backgrounding the app used to stop the download dead.** Android freezes a cached
process, so a gigabyte transfer running on the app's own coroutine simply stopped —
no error, no notice, and on coming back the figure had not moved. The cure is a
foreground service (`ModelDownloadService`, type `dataSync`) whose entire job is to
exist: it downloads nothing, and being started is what keeps the process out of the
freezer while `ModelRepository` carries on. The `DownloadPresence` calls bracket
`prepare()` in a `finally`, not one attempt of the transfer — the loop retries a
dropped connection several times and the hold has to span all of them and be
released exactly once, including on the pause path, which cancels the job.
POST_NOTIFICATIONS is asked for when a download starts rather than at launch, and
refusing it costs the progress bar, not the transfer. On Android 15 and up
`dataSync` has a daily budget of a few hours; a model is minutes, so it is not in
reach — but a longer-running transfer would have to care.

**The hold has to outlast the load, not the last byte.** Releasing it when the
transfer ended dropped the process straight back to a cached one while it still
had a 1.3 GiB file to map, and a device short of memory killed it right there:
the model was on disk, nothing was loaded, and the notification saying so never
came. Caught on the emulator — `lowmemorykiller ... oom_score_adj 700 ... died:
prev LAST`, the 700 being the giveaway that it was no longer protected — but the
same window exists on a real phone under pressure. `downloadBegan`/`downloadEnded`
therefore bracket the whole of `prepare()` rather than `download()`, and what the
user is waiting for is a working translator, which is what the service outlasts.
With that fixed the same emulator still killed it, now at `adj 200` and
`died: prcp FGS` — protected and killed anyway, because its swap was gone and it
was thrashing at 302%. That one is the emulator, not the app.

**«Ready» is announced after the load and only when something was fetched.** The
progress notification takes itself away with the transfer, so a download waited out
in another app used to finish to nothing at all; `translatorReady` posts a separate,
tappable, auto-cancelling one on its own channel. It fires after `loadLlmEngine`
rather than after the last byte, because ready should mean it can answer, and it is
skipped when an activity is resumed — someone watching the plate turn into a
translator does not need to be told. An ordinary launch of an already-downloaded
model says nothing, which is what the `fetched` flag in `prepare()` is for.

To exercise all of this without waiting on a gigabyte, copy a complete `.gguf` to
`.gguf.part` a few megabytes short and delete the original: the resume finishes in
seconds and the load and the notification run exactly as they would otherwise.

**«Translates with» is the loaded model, not the selected one.** Picking a
translator that has to be fetched leaves the previous one loaded and answering for
the length of the download, so the about card naming `selectedSpec()` told people
they were using a model that was still arriving. `ModelRepository.loaded` is the
one the engine actually holds; anything claiming what does the translating has to
read that, including the prompt format. The card also changes tense rather than
name — «will translate with» — because between choosing a model and its arriving
there are moments when nothing is loaded at all.

**A silent notification has no status bar icon.** From Android 12 a notification
whose channel sits below `IMPORTANCE_DEFAULT` is kept out of the status bar
entirely — it appears in the shade under «Silent» and nowhere else. `IMPORTANCE_LOW`
looks like the considerate choice for a progress bar and costs exactly the thing
the bar is for: the icon is what says «this is still going» while the app is off
screen. The channel is `IMPORTANCE_DEFAULT` with `setSound(null, null)` and
vibration off instead, which is quiet without being invisible; only
`IMPORTANCE_HIGH` peeks. A channel's importance cannot be raised from code once it
exists, and recreating one under the same id restores what it had, so moving up
meant a new id and deleting the old one — see `LEGACY_CHANNEL_ID`.

**The status bar icon is not the launcher's monochrome layer.** That one is laid
out for a 108 dp adaptive canvas with the mark scaled to 0.58 to clear the round
mask, so at the 24 dp a status bar gives you it draws a speck in a field of
nothing. `ic_notification.xml` fits the launcher's 100-grid paths — the 7-wide
ones — to 24 dp directly: the mark spans 90 units tall, so 0.2222 puts it at 20 dp
with 2 dp either side.

**A model can be loaded that is not the selected one.** When the chosen translator
is not on the device, whatever *is* there loads and answers in the meantime —
otherwise the app is dead for the length of a gigabyte download with a working
model sitting in its files directory, which is what it did both on a launch whose
selection named a model that was never fetched and for the whole of a switch.
Three things follow, and all three were bugs first:

- **The prompt format must come from the loaded model**, never from the selected
  one. Sending the wrong one does not fail loudly — see the prompt-format trap
  above. 1.0 and 1.1 happen to share a format, so this would not have shown up
  until a fine-tune that does not.
- **`select()` may not return early on «already selected and an engine exists»**.
  A stand-in fills the engine slot, so that reading made tapping the chosen
  model's own download button do nothing at all. The condition is «the chosen one
  is also the one running».
- **The download only takes the target plate when nothing is loaded.** With a
  stand-in answering, the plate goes back to translating and the slab carries the
  progress. The gear's dot and the translators row light through `standingIn`,
  and that row keeps naming the *chosen* model with a `NOT ON DEVICE` stamp — the
  same word the translators screen uses — because the row is about the choice.

**Adding a newer catalog entry strands everyone who never chose a model.** There is
no `selected-model` file until someone picks one on the translators screen, so most
people are implicitly on `ModelCatalog.default` — and the release that puts a newer
entry at the top of the list moves that name onto a file they do not have. Marking
the old entry `deprecated` keeps the *file* (`removeUnknownFiles()` spares anything
the catalog still claims), but on its own it does not keep the *use* of it: the app
came up saying NO TRANSLATOR YET and offering a 1.29 GiB download on a phone with a
working 1.29 GiB model already on disk. `readSelection()` therefore falls back to
the newest entry actually present, and only to the default when the device holds
nothing at all. The update dot still lights, which is the right amount of pressure.
Found by installing over a real phone's existing install — a fresh emulator cannot
show it, because there the default genuinely is missing.

**Switching the theme used to animate every colour on screen at once.** Each of
them is animated for a good reason — a cell filling, the slab going from offer to
progress — and each reads its target out of the palette, so changing light to dark
changed all their targets together and one tap became a dozen tweens crossing at
their own durations. The accent visibly crawled from element to element, which
looks nothing like anything else here. `animateStateColour` in
[Motion.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/Motion.kt) keys
the animation on the palette, so a new theme rebuilds it at its target: state still
eases, the theme snaps. Measured with `screenrecord` at 60 fps — the whole screen
goes from mean brightness 240 to 27 in a single frame. Use it, not
`animateColorAsState`, for anything whose target comes out of `IloTokiTheme.colors`.

**Haptics go through the platform's UI-feedback path, never the vibrator.**
`View.performHapticFeedback` on Android and the `UIFeedbackGenerator` family on
iOS: both are tuned per device and both already obey the phone's own haptics
switch, which a raw `Vibrator` call would have to be taught. The app's switch
(`AppSettings.haptics`, «haptic feedback» in settings) sits on top of that.

The two taps are different in kind, not in volume. A press is a decision and gets
`KEYBOARD_TAP` / a light impact; a word landing is a texture that repeats inside
one action and gets `CLOCK_TICK` / a selection tick, the faintest thing either
platform offers. On a Pixel 6 that comes out as `Primitive=CLICK` at 21–37 ms
against 15–16 ms for the tick — `adb shell dumpsys vibrator_manager` prints what
was actually played, which is the only way to check this without a hand on the
phone. Words are also floored at 60 ms apart: it never engages at the two to eight
tokens a second a phone decodes at, and it is there so a faster model cannot turn
an answer into a buzz.

**An icon's stroke is not its weight on screen.** The set is drawn on a 100-unit
grid that is scaled to whatever size a call site asks for, so what reaches the eye
is `stroke ÷ 100 × size` — and the same grid stroke across icons drawn at
different sizes comes out visibly uneven. The clock at 7/22 dp and the back
chevron at 9/18 dp sat at 1.54 and 1.62 dp against the gear's 2.30 and read as
thin beside it. Each stroke is now chosen for the size its icon is drawn at,
aiming at about 2.2 dp, which is also `BorderWidth` — the line every surface here
is outlined with, so the icons sit at the weight of everything around them. The
table is in
[IloTokiIcons.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/IloTokiIcons.kt);
changing a size at a call site means moving the stroke with it.

**A typed glyph will never match a drawn icon.** The two dismiss buttons were the
character `✕` at 12 and 13 sp, sitting beside squares full of drawn strokes, and
no font size makes a letterform agree with a 2.2 dp stroke — `IloTokiIcons.Close`
is the same X on the 100 grid instead. `olin` cannot be drawn that way, since it
is a sitelen pona ligature and the whole point is that the font renders it, so it
gets `FontWeight.Bold` — a synthetic embolden, the same strokes thickened, which
is what the script pill already does for the same reason.

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
means adding its collapsed form in the same change. The strip only exists while the
keyboard is up, which is why the tap that puts the keyboard away lives on the strip
itself rather than on each state.

**Do not animate the target plate's height on a tween, and do not key its collapse
on `imeVisible`.** That boolean only goes false once the keyboard has *finished*
leaving, so the plate grew back after it rather than with it, and no tween can fix
being told late. The height is a function of the room there is —
`(open - SOURCE_MIN).coerceIn(76.dp, open / 2)`, worked out in a `layout {}` from the
constraints it is handed — and that area is already being squeezed frame by frame by
the ime inset, so reading it is exact synchronisation for free, on both platforms.
Read it in the *measure* block and nowhere else: in the composable body it rebuilds
the whole screen sixty times a second, and while this lived in a `BoxWithConstraints`
every one of those re-measures was a subcomposition of everything inside it — 22 ms
a frame on a Pixel 6 against 10 ms now, which is the difference between the resize
looking like an animation and looking like a slideshow. Which form the plate wears
is decided in that same block and written back to state, so it changes once each way
instead of being asked every frame. `SOURCE_MIN` has to sit
between two bounds, and the comment on it says which; outside them the plate either
jumps at the start or never reaches the strip. The contents swap over at 120 dp
rather than at the keyboard's say-so, which is what keeps the stacked column out of
a box too short for it.

The sample chips under the slab are not in that area and cannot read its height, so
they fold on the keyboard's own fraction — and they read it in their own `layout {}`
and `graphicsLayer {}`, for the same reason. That fraction is measured from the
**squeeze**, `ime.getBottom() - navigationBars.getBottom()`, over the deepest squeeze
seen; not from the raw inset. The screen's bottom padding is
`navigationBars ∪ ime`, so the last stretch of the keyboard's travel — the part
still inside the gesture bar's own height — moves nothing at all. Measured from the
raw inset the chips were still unfolding through that stretch and taking their space
back off the plates *after* the plates had finished growing, which is the top block
growing and then shrinking again at the end of every close. On a tween
they were a *second* movement: it can only start when «is the keyboard up» flips,
and on the way down that is after the keyboard has already gone. The very first
keyboard of a session has no remembered height yet and falls back to the boolean.

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

**An entrance must be the last modifier in its chain, and must not read its
animation inside the layer block.** Both bite the same way — the tween runs to
completion (logged, full duration, on both platforms) while the element stays drawn
at its first frame, so it turns up in one step at the end and looks like no
animation at all. Two separate causes:

- Reading inside `Modifier.graphicsLayer { }` defers the read to draw, which is the
  cheap pattern, but the block is only re-run when the node is re-placed — and a
  node nothing else moves never is. The entrances read at composition and use the
  *parameter* overload instead; only the two idle loops defer, and they sit in the
  normal layout flow where it works.
- A layer placed *above* a deferred `Modifier.offset { }` drew nowhere at all on
  iOS. The language popover had `zIndex → align → popIn → offset {}`; moving
  `popIn` to the end of the chain fixed it. Put the transform on the element, not
  on a node whose placement is still pending.

Frame-count a new animation before believing it — `adb shell screenrecord` or
`xcrun simctl io … recordVideo`, `ffmpeg -vf fps=60`, then diff the region frame by
frame. With `cubic-bezier(.2,.8,.2,1)` a broken animation and a working one both
look like «it appeared»: the working one is three quarters done in two frames.
`println` from a `LaunchedEffect` is what separates «the tween never ran» from «the
tween ran and nothing drew it» — on iOS read it with
`xcrun simctl launch --console-pty`.

**An entrance hidden behind an `alpha(0f)` guard is an entrance thrown away.** Same
reason: by the time a one-frame measurement guard lifts, the curve is at 75%. Pass
the guard into the animation (`popIn(enabled = …)`) so it waits instead.

**A fading element loses its hard shadow, and the cure is per platform.** The
shadow is painted outside the element's bounds and an offscreen buffer is the size
of the element, so on Android every entrance dropped its shadow for the whole
animation and snapped it back on at the end — «тени появляются после всех
анимаций». `CompositingStrategy.ModulateAlpha` fixes it by applying the alpha per
draw command instead. On Skia that same strategy stops some filled surfaces being
drawn at all — the history screen's ALL chip vanished outright — and iOS only loses
the shadow for about two frames anyway. Hence `entranceCompositing`, expect/actual,
with the reasoning in its own doc comment. Check a change to it on **both**.

**Per-command alpha also means nothing may hide under anything.** `Modifier.surface`
used to paint the whole shadow rectangle and then cover most of it with the fill,
which is invisible until the fill is translucent — and then a near-black rectangle
comes up through the paper and the plate reads as filling with ink. It now clips
its own rounded rect out of the shadow (`clipPath(…, ClipOp.Difference)`) and paints
only the L-shaped band that was ever visible. Anything else drawn behind a fill has
the same problem waiting.

**A back gesture cannot afford to compose a screen.** It shows two screens at once,
and building the second one takes longer than a frame: measured on a Pixel 6, a
third of the gesture's frames janked, the 90th percentile sat at 85 ms and
`gfxinfo` blamed the UI thread — against 13 ms with a coloured box in its place.
A quick flick is over in six frames, so there is nowhere in it to hide that. The
destination is therefore built 300 ms *after* the screen it sits under settles,
kept composed and simply not drawn (`drawWithContent { if (inFlight) drawContent() }`),
and the gesture only changes transforms. Everything the gesture animates is read
through a lambda inside a layer or draw block, never at composition — a screen is
an expensive thing to recompose sixty times a second. `adb shell dumpsys gfxinfo
<pkg> reset` before the gesture and reading it after is how any of this was known.

**Do not hand-list llama.cpp sources.** Upstream split every architecture into its
own translation unit (168 files under `src/`). The Android build delegates to
llama.cpp's own CMake for this reason; a hand-maintained list rotted on the first
submodule update.

## Workflows

**A second model on the same machine used to come out as the first.** The download
cache in `merge-and-quantize.sh` was keyed on the *role* — `$WORK/adapter` — so the
directory left over from the previous run was reused and the new repo was never
fetched. The build succeeded, the quantizations were the right size and the name on
the files was the new one; the only signs were that the merge probe printed the
same sentence as last time and `mean |delta|` on the trained rows matched to the
last digit. It is keyed on the repo id now, but the general lesson stands: check a
merge with numbers, not by whether the pipeline exited zero. `scripts/merge_lora.py`
prints the probe for exactly this reason, and the row/`lm_head` check in the merge
trap above is what confirms it.

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

**Comparing two models is a host job, not an emulator one.** Same quantization on
both sides or the comparison means nothing. The tool is `llama-completion`, not
`llama-cli`: upstream moved raw completion there, and `llama-cli` is now a chat
CLI that is only built with `-DLLAMA_BUILD_SERVER=ON` — configure with
`-DLLAMA_BUILD_TOOLS=ON` and build the `llama-completion` target. Feed the prompt
with `-f` rather than `-p` (it has newlines in it), and add `--no-display-prompt`,
`--no-warmup`, `-c 2048 -n 512` to match `LlmParams`.

For greedy use `--temp 0 --top-k 1`. **Not `--samplers greedy`** — there is no
sampler by that name, llama.cpp only warns («unable to match sampler by name») and
carries on with *no* sampler at all, and what comes back is uniform noise that
reads exactly like a broken merge. That warning goes to stderr, so it is invisible
if stderr is being discarded. An hour went into blaming Metal for this; Metal is
fine, and CPU and Metal agree on this model to within a word.

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
- **A stack deals itself out once, when the screen arrives.** The history cards'
  stagger is gated on a 600 ms window from the screen's first frame; after it, a
  card that scrolls into view gets no entrance at all. Keyed on the index alone it
  made a fast scroll show blank cards — an item is composed as it scrolls in, and
  it would then wait out a delay meant for cards that were never on screen.
- **The motion is the design's, and it lives in one file.**
  [Motion.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/ui/Motion.kt) holds
  the three curves and the named entrances (`screenIn`, `plateIn`, `stampIn`,
  `popIn`, `cardIn`, `cardDrop`, `fadeIn`) plus the two idle loops (`pulse`,
  `nudge`), transcribed from the handoff's keyframes rather than invented. Nothing
  eases *in*; `Motion.Overshoot` is only for the swap knob and the olin mark. Press
  feedback is geometric because this look has no ripple: a pill squeezes
  (`tap(pressScale = PressSqueeze)`), a shadowed surface falls into its shadow —
  which is why `Plate` takes an `onClick` of its own, since the shadow is drawn
  inside it and a `Modifier.tap` in the caller cannot reach it.
- **The back gesture shows where it is going, and the destination must be opaque.**
  `rememberBackGesture` ([BackGesture.kt](composeApp/src/commonMain/kotlin/one/larkin/ilotoki/BackGesture.kt))
  reports the Android drag; while one is in flight `App` composes the destination
  *behind* the current screen and peels the current one off it. Two things that
  look like bugs and are not: the peeled screen carries its own background,
  because both screens are otherwise transparent over the same `bg` and read as one
  double-exposed screen; and `LocalEntranceSuppressed` is set for the one
  composition in which the destination becomes the screen, because it has been on
  display under the thumb for half a second already and playing `screenIn` at that
  point is a flash. The peel also draws the 2 dp `line` every lifted surface here
  carries — in dark both screens are the same near-black and the edge is otherwise
  invisible, which is the same reason plates keep their outline when the shadow is
  dropped. iOS has no system back; its `actual` returns a gesture that never starts.
- **The peel always leaves to the right, whichever edge the finger came from.**
  `BackEventCompat.swipeEdge` is deliberately not read. Mirroring the animation onto
  the swiping edge looks like the obliging thing to do and is what this had at
  first; it is wrong, because back means one direction and users read the direction,
  not the finger. The platform agrees — a right-edge swipe in the settings app moves
  its page right as well, and only the system's own arrow changes sides.
- **The back gesture is Android's motion, not this design's.** Two of its curves
  come from the platform rather than from the handoff, and both were arrived at by
  recording the settings app's own back and diffing it frame by frame:
  `Motion.PredictiveBack` for the drag, which front-loads so hard that the screen
  stops answering the thumb after about a fifth of the pull — that damping is what
  makes it read as a switch rather than as dragging the screen around — and
  `Motion.Emphasized` for the commit, the one thing here that eases *in*, because a
  departure that starts at full speed reads as a cut. The commit is also two halves:
  the leaving screen fades and finishes its travel while the arriving one grows out
  of `revealedBack`'s 5% inset, and forgetting the second half is what made the
  first attempt feel like no animation at all. Measured against the reference: same
  rise, same peak six frames in, ~320 ms against its ~420. The drag itself is taken
  from the event as given — **do not smooth it**. A spring on it was tried, on the
  theory that a stuttering flick was the system reporting progress in jumps; it is
  not, the stutter was jank (see the note above about composing a screen), and all
  a spring adds is a screen that keeps moving after the thumb has stopped. Even a
  stiff one lags a held drag visibly; a soft one lags it by half a second. With the
  raw value the peel stops when the finger stops, which is what the settings app
  does when you drag and hold — measured with the same synthetic drag on both.
- **Back is not always a change of screen, and then it should not animate like one.**
  The about card gets `previewed = false`, which keeps the gesture and drops the
  peel: there is nothing behind the card to preview — it is itself what back is
  dismissing — so sliding it under the thumb only invites the question of where it
  is going. It also skips the commit animation and closes the moment the gesture is
  let go, rather than after something has finished moving.
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
  `Assets/mark.png`, regenerated by the same 100-grid arithmetic. Change the mark
  and all four have to move together; there are no launcher PNGs left to forget,
  minSdk is 26 so `mipmap-anydpi-v26` always wins. The one thing that does *not*
  follow is `IloTokiIcons.MarkHeavy`, the header tile's 9.5-wide variant — that
  exists so the mark matches the gear and the clock beside it, and an app icon has
  nothing beside it to match.
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
  decides which stamp gets it. It also drops *above* its stamp instead of below when
  the list would not fit below — which is every time toki pona is the source and the
  keyboard is up, since the picker then sits on a 76 dp strip at the foot of the
  plate area and the list landed under the keyboard. That test needs the popover's
  own height, so it is measured too, and the popover is drawn at alpha 0 for the one
  frame before that measurement exists. Where the popover *hangs* is measured,
  not derived — the plate area and the target plate report themselves through
  `onGloballyPositioned` — because the target plate's top moves with the keyboard and
  with whatever state the plate is in. A constant offset is what put the list down at
  the bottom of the screen in the first place.

## Current state

*Last updated: 2026-08-05.*

- Model: [ilo-toki-1.1-MiLMMT-46-1b-merged](https://huggingface.co/NetherQuartz/ilo-toki-1.1-MiLMMT-46-1b-merged),
  one repository holding the merged weights and four quantizations. 1.0
  ([ilo-toki-MiLMMT-46-1b-merged](https://huggingface.co/NetherQuartz/ilo-toki-MiLMMT-46-1b-merged))
  is still in the catalog, marked `deprecated`, and should be dropped a release
  later. Same base and same prompt format, so `PromptStyle.SourceTarget` covers both.
- **1.1 is better on balance, not on every axis**, measured over 95 prompts in both
  directions across the three languages, Q8_0 against Q8_0. Fixed: `jan` no longer
  comes back as Minecraft interface text (1.0: `jan li tawa ma` → «Player moves»,
  `jan li pali e tomo` → «Building a Structure»; there is no Minecraft corpus in
  1.1), clauses are no longer dropped from longer inputs, and terminal punctuation
  barely moves the answer any more — it was dropped with p = 0.25 during training,
  and 1.0 could flip meaning on it. Regressed: `la` is often read as a conditional
  «if», `sona e toki pona` can answer about the wrong language («I know Russian»),
  and short inputs pick up invented specifics. Separately, and more systematic than
  it first looked: **1.1 resolves everything the source leaves unmarked to a fixed
  default instead of reading it off the context.** Bare `mi` comes back as «we» in
  six of seven sentences where 1.0 said «I», unmarked verbs tend to come back past,
  and `la` tends to come back conditional. None of these is an *error* — `mi` covers
  «we» and `mi mute` is optional, so filtering such pairs out of the training data
  would be throwing away good ones — but consistently picking the less expected of
  two valid readings is still a behaviour change worth knowing about. On a
  head-count of the general set the two are near enough level; 1.1 wins because
  `jan` is in a large share of all sentences and short bare sentences are the
  common case, while its own failures need rarer constructions.
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
- The animations are in and frame-counted on a Pixel 6, a Pixel 9 emulator and an
  iPhone 17 Pro simulator: screen and card entrances, the popover, the segmented
  slide (240 ms), the toggle knob, the press sink (~85 ms), the slab's colour
  crossing (200 ms), the swap rotation, and both idle loops at their 2.6 s and
  4.2 s periods. On the simulator also the history stagger, the target plate's
  `plateIn` and the olin pop, measured at 1.148× and back. The per-word `tokenIn`
  needs a real device to be visible at all — on a warm simulator a three-word answer
  lands inside 70 ms and each word's fade is cut short by the next; on the Pixel 6
  at 1.5 tok/s the last word darkens over nine frames, ≈150 ms of its 170.
- Predictive back is in on Android, with the destination previewed under the drag.
  Verified on the Pixel 9 emulator for a committed drag, a cancelled one (the peel
  runs home in 180 ms) and the about card, which peels while its scrim lifts.
  A held gesture for a screenshot is `adb shell input motionevent DOWN/MOVE/…`;
  `input swipe` only ever reaches about a third of the progress before committing.
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

- **What the next training round should address.** Three things, all 1.1's, all
  visible in the comparison log. `la` is read as a conditional — `mi wile lape la mi
  tawa tomo` gives "If I want to sleep then I go home", and the second clause is
  sometimes mangled along with it; 1.0 handled `la` better, so something in its mix
  covered this and got diluted, and diffing the `la` subsets of the two mixes is the
  fast way to find out what. `sona e toki pona` names the wrong language ("I know
  Russian"), which is a bad place for a bug in this app; other constructions around
  `toki pona` are fine, so the trigger is narrow and counterfactual examples
  (language names as sentence content, templated across the language list) should
  close it. Short inputs acquire invented specifics — `jan li moku e kili` into
  Vietnamese produces a US state — which looks like mined pairs whose target side
  carries more than the Toki Pona side ever said; a length-ratio filter and a
  round-trip check over the mined set target that class directly.

  The likely common cause is the `x`↔`y` pairs added in 1.1 to keep natural-language
  generation fluent. They worked — 1.0's Russian broke outright ("Яблоко ест", "Не
  удалось отправить жалобу"), 1.1's does not — but in an `x`↔`y` pair every feature
  the model has to *infer* in `tok`→`x` is already marked on the source side. Number,
  tense and clause relation are all given; nothing there teaches reading them off
  the context, only priors over them. `tok`→`x` is the sole place that skill is
  practised, so the more the mix is diluted, the flatter the defaults get.

  Two things that were checked and did **not** hold, so nobody has to re-run them:
  the damage does not track how rare a construction is — `pi`, `o`, `anu`, `en`,
  `nanpa` and `kin` all survive 1.1 intact, and `kin` and `anu` come out better than
  in 1.0 — and `la` is not broken across the board, only collapsed onto the
  conditional reading. Phrase-`la` (`tenpo pimeja la mi lape`) is right in both
  models, and clause-`la` is right in 1.1 whenever the relation genuinely *is*
  conditional. So «Toki Pona's share of the mix fell from 3:1 to 1:1:1» does not on
  its own explain what regressed; what the evidence points at is the share of pairs
  in which nothing has to be inferred.
- **iOS still stops its download when the app leaves the screen.** The Android cure
  does not port: there the same coroutine is kept alive, whereas iOS wants the
  transfer handed to the system through a background `NSURLSession`, which runs out
  of process and survives the app being killed. That is a different download rather
  than the same one held open, so it replaces the ktor path on that platform and
  has to report progress back through a session delegate — it cannot hide behind
  the three `DownloadPresence` calls, which is why the iOS actual is an honest
  no-op with the reasoning in its doc comment. `beginBackgroundTask` was considered
  and rejected: thirty seconds of grace covers switching apps and coming straight
  back, which would make the bug look fixed while a gigabyte over a slow connection
  fails exactly as before. **The simulator cannot verify any of this** — it does not
  suspend apps the way a device does, so both the bug and its fix are invisible
  there. Whoever picks this up needs a real iPhone, or the code goes in unproven.
- The translators screen could offer the other quantizations; the machinery is there,
  it needs entries in the catalog.
- Swipe-to-delete on a history card is in the spec; the explicit `✕` is what is
  implemented, as in the prototype.
- Desktop targets are maybe a day for macOS: four expect/actuals, and the JNI bridge
  works unchanged on a desktop JVM. Cross-compiling the natives is the real cost.
