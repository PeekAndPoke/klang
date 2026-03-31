# Project-Wide Code Quality Review — Findings

## Context

Three review agents (audio DSP engineer, software quality engineer, software engineer) audited
all 18 modules (~894 .kt files) against the 16 code-style rules, plus logic bugs, duplication,
scattered helpers, and performance issues.

---

## Implementation Plan

### Decisions Made

- **Platform-specific optimizations get "why" comments** — every Int-instead-of-Long, IntArray-instead-of-String
  optimization must explain the Kotlin/JS boxing reason. Future us will thank us.
- **Bit tricks get "how it works" comments** — e.g., `isPowerOfTwo` bit manipulation.
- **Common math/formatting utils go to `common` module** — `isPowerOfTwo`, `roundTo`, `toFixed`,
  `formatAsIntOrDecimal` (the number-to-integer-string pattern duplicated 5x).
- **DSP utils stay in `audio_be/DspUtil.kt`** — `TWO_PI`, `ONE_OVER_TWELVE`, `wrapPhase()`, `polyBlep()`,
  `ResolvedShape` + `resolveDistortionShape()`.
- **Generic infra moves from `audio_bridge` to `common/.../infra/`**:
    - `KlangMinHeap` (+ fix `removeWhen` to in-place compact + heapify, zero alloc)
    - `KlangRingBuffer`
    - `KlangMessageSender`
    - `KlangMessageReceiver`
    - `KlangCommLink` stays in `audio_bridge` (depends on audio-specific types)
- **Audio renderer must NEVER die** — bad data is silently ignored. Add `KLANG_AUDIO_DEBUG` compile-time
  flag in `audio_bridge` for debug messages. Replace `error("unreachable")` with silent returns.
  Future: debug mode sends messages through comm-link.
- **M12 badge DOM leak** — remove badge container from DOM on cleanup. Future TODO: Kraft component
  lifecycle "attachment" mechanism where state lives in a self-cleanup object that reacts to
  onMount/onUnMount, keeping state out of the host component.
- **M13 undo stack** — cap at 1,000.
- **L1 file naming** — NOT a violation. Update code-style skill: PascalCase for files containing
  classes, lowercase with `_` is acceptable for utility/helper files.
- **L4 sprudel DSL dedup** — only unify internals (dispatch functions). User-facing DSL functions
  and registration must not change.
- **Test coverage first** — before changing any module, check existing test coverage. If insufficient,
  add tests before refactoring.

### Execution Order (small steps, test after each)

1. **Quick wins** — H5 (isPowerOfTwo as common util), H6 (division guards), M8 (roundToLong),
   M14 (double call), M11 (Rational.ONE), L5 (uncomment else branches), M13 (undo cap 1000),
   M12 (badge DOM cleanup + future TODO)
2. **Code-style skill update** — L1 naming rule clarification
3. **Infra moves** — Move KlangMinHeap/RingBuffer/MessageSender/MessageReceiver to common,
   fix KlangMinHeap.removeWhen (in-place + heapify)
4. **H1: Long-to-Int cascade** — Start at `KlangAudioWorklet.cursorFrame`, propagate through
   voice system. Biggest/riskiest change. Add `KLANG_AUDIO_DEBUG` flag.
5. **H2: Char boxing in lexers** — KlangScriptParser + MiniNotationParser → IntArray approach
6. **M1-M3: Audio thread cleanup** — Allocations, denormal flushing, error() → silent return
7. **M4-M7: Deduplication** — ResolvedShape, number formatting, Errors.kt format()
8. **H4 + L7: isInsideString** — Fix single-quote bug + dedup
9. **L4: Sprudel DSL internal dedup** — Unify dispatch functions, preserve API surface
10. **Remaining low priority** — Scattered helpers, cold-path boxed types

---

## HIGH PRIORITY

### H1. `Long` propagation through entire audio callback chain

- **Root cause:** `KlangAudioWorklet.kt:63` — `var cursorFrame = 0L` is the source of all `Long` usage in the audio
  stack
- **Propagates to:** `KlangAudioRenderer.kt:30`, `Voice.kt:20-22,112`, `BlockContext.kt:43-78`,
  `VoiceScheduler.kt:81-389`, `VoiceFactory.kt:43-352`, `EnvelopeCalc.kt:16-18`, all `*Renderer.kt` files,
  `KlangTime.kt:38-39`
- **Impact:** Per-block Long boxing on the audio thread (per-sample loops already convert to Int at boundaries)
- **Fix:** Convert `cursorFrame` to `Int` (overflows after ~12.4h at 48kHz — acceptable) or `Double`, then cascade
  through the voice system

### H2. Pervasive `Char` boxing in lexers/parsers

- **klangscript parser** (`KlangScriptParser.kt:176-762`): ~100+ `source[i]` accesses + char comparisons, all boxing on
  JS. Runs on every keystroke in live-coding.
- **sprudel MiniNotationParser** (`MiniNotationParser.kt:119,370,382-385`): Same pattern, called on every pattern parse.
- **Fix:** Convert source string to `IntArray` of code points before lexing, compare against `Int` constants (e.g.,
  `val LPAREN = '('.code`)

### H3. `AstToKBlocks.convertStmt()` silently drops loop statements

- **File:** `klangblocks/.../AstToKBlocks.kt:57` — `else -> null`
- **Impact:** `WhileStatement`, `DoWhileStatement`, `ForStatement`, `BreakStatement`, `ContinueStatement` are silently
  lost on round-trip through the block editor
- **Fix:** Add block representations for these statements, or at minimum surface a warning

### H4. `isInsideString()` ignores single-quote strings

- **Files:** `DslCompletionSource.kt:260-275`, `DslEditorExtension.kt:106-121`
- **Impact:** Autocompletion and hover fire incorrectly inside `'...'` strings
- **Fix:** Count both `"` and `'` (with escape handling)

### H5. Logic bug: `TimeSignature.isPowerOfTwo` uses float equality on `log2`

- **File:** `tones/.../TimeSignature.kt:126`
- **Code:** `(log2(x.toDouble()) % 1.0) == 0.0` — can fail for certain integers due to float precision
- **Fix:** `x > 0 && (x and (x - 1)) == 0`

### H6. Division-by-zero risks in sprudel pattern evaluation

- `SequencePattern.kt:39` — `w / totalWeight` when all weights are 0
- `lang_structural.kt:3900` — `acc / divisor` with user-provided `"5:0"` input
- **Fix:** Guard against zero divisors

---

## MEDIUM PRIORITY

### M1. Allocations on the audio thread

- `VoiceScheduler.kt:87` — `sources.toList()` every block
- `VoiceScheduler.kt:279` — `mutableSetOf<String>()` every block
- `KlangMinHeap.kt:33-35` — `removeWhen` allocates via `data.filter { ... }` + re-push (O(n log n))
- **Fix:** Pre-allocate, iterate without copying, in-place heapify

### M2. Missing denormal flushing in Karplus-Strong

- `Exciters.kt:~1293` — `lpState`, `apPrevIn`, `apPrevOut` not flushed
- `Exciters.kt:~1491` — Super Karplus-Strong: same issue × `v` voices
- **Fix:** Add `flushDenormal()` calls after state updates

### M3. `error("unreachable")` in audio hot path code

- `ExciterFm.kt:58-59`, `ExciterPitchMod.kt:47,106,190`
- **Impact:** Throws exception + allocates string on audio thread if triggered
- **Fix:** Replace with `!!` (null assertion) or silent early return

### M4. Duplicated `ResolvedShape` / shape resolution

- `ExciterEffects.kt:62-78` and `DistortionRenderer.kt:63-79` — character-for-character duplicate
- **Fix:** Extract to shared file (e.g., `DspUtil.kt` or a new `ShapeUtil.kt`)

### M5. Duplicated number-to-integer-string formatting (5 locations)

- `RuntimeValue.kt:116`, `AstToKBlocks.kt:293`, `KBCodeGen.kt:167`, `KlangBlocksVariableStmtComp.kt:65`,
  `KlangBlocksBlockComp.kt:308`
- Pattern: `val l = value.toLong(); if (value == l.toDouble()) l.toString() else value.toString()`
- **Fix:** Shared utility using `toInt()` instead of `toLong()`

### M6. `KBCodeGen.kt:52` — Long cache key defeats its own purpose on JS

- Comment says "avoid boxing" but Long IS boxed on JS
- **Fix:** Use `Int` bit-packing or `"$line:$col"` String key

### M7. Duplicated `Errors.kt` format() methods (6 copies)

- Lines 83-96, 111-124, 155-168, 184-197, 213-226 — all identical pattern
- **Fix:** Base class implementation with `formatHeader()` hook

### M8. `roundToLong()` in sprudel JS code

- `sprudel/src/jsMain/kotlin/ui/_utils.kt:105` — creates boxed Long
- **Fix:** Use `kotlin.math.round()` which returns Double

### M9. Sprudel `Pair` allocations in pattern query hot paths

- `PatternQueryUtils.kt:114,130,156,170` — inline functions return `Pair` (not elided by inlining)
- **Fix:** Use a reusable mutable result holder or destructure differently

### M10. Sprudel per-query sorting

- `StackPattern.kt:29` — `sortedBy` on every query (O(n log n) + allocation)
- `EuclideanPattern.kt:378` — same issue
- **Fix:** Remove or make debug-only

### M11. `1.0.toRational()` in hot paths

- `RepeatCyclesPattern.kt:38,97` — allocates new Rational each time
- **Fix:** Use `Rational.ONE`

### M12. DslEditorExtension badge container DOM leak

- `DslEditorExtension.kt:266,311` — div appended to body, never removed on editor destroy
- **Fix:** Add cleanup on editor unmount

### M13. Unbounded undo stack

- `KBProgramEditingCtx.kt` — `undoStack` grows forever
- **Fix:** Cap at 1,000

### M14. `VoiceScheduler.prefetchSampleSound()` double call

- `VoiceScheduler.kt:376,382` — `voice.data.asSampleRequest()` called twice instead of reusing `req`
- **Fix:** Use `req` on line 382

---

## LOW PRIORITY

### L1. ~~Non-PascalCase file names~~ — NOT A VIOLATION

- These are utility/helper files, not class files. **Update code-style rule:** PascalCase for files
  containing classes; lowercase with `_` is acceptable for utility/helper files.
- `common`: `bjorklund.kt`, `math.kt`, `levenshtein.kt`, `distributions.kt`
- `sprudel`: `_utils.kt` (x2), `pattern_test_utils.kt`
- `klangblocks`: `_chain_rendering.kt`, `_slot_rendering.kt`
- `klangui`: `_utils.kt`
- `klangjs`: `codemirror.kt`

### L2. Scattered helper functions

- `audio_be/util.kt` — `TWO_PI`, `ONE_OVER_TWELVE` should be in `DspUtil.kt`
- `Exciters.kt` — `wrapPhase()`, `polyBlep()` locked as private, should be shared DSP utils
- `sprudel/ui/_utils.kt` — `Note.staffPosition()` belongs in `tones`, numeric utils in `common`
- `klangscript/index_common.kt:46` — `KClass<*>.getUniqueClassName()` belongs in common

### L3. Boxed types in non-hot-path code (low impact but violate project rule)

- `audio_bridge/KlangPlaybackSignal.kt:33,80,83` — `Long` in UI-facing signal data
- `klangscript/RuntimeValue.kt:44,47` — `toLongOrNull()`, `toLongOr()` helpers
- `klangscript/NativeInterop.kt:70-71,93-95` — `Short`, `Byte`, `LongArray`, `ByteArray`, `ShortArray` class matching
- `klang/SamplePreloader.kt:88` — `toLong()` for duration
- `klangui/comp/_utils.kt:16` — `lowercaseChar()` returns Char
- `common/BerlinNoise.kt:17` — Long multiplication for hash

### L4. Sprudel DSL boilerplate duplication

- `lang_pattern_picking.kt` (2594 lines) — 5 near-identical `dispatch*` functions, ~16 functions per variant
- `lang_euclid.kt` — arg-parsing `when` block duplicated 10+ times
- `SprudelVoiceValue.kt:292-349` — fraction parsing duplicated between decoder branches
- These are large refactors with risk; flag for future sprint

### L5. Interpreter `when` blocks with commented-out `else`

- `Interpreter.kt:944-952,972-979` — could silently return invalid values if new operators added
- **Fix:** Uncomment `else -> error("...")` or use exhaustive `when` on sealed type

### L6. `Char` boxing in tones module (non-hot-path)

- `Note.kt:284`, `AbcNotation.kt:37,49`, `RhythmPattern.kt:36`, `Tones.kt:36`, `Chord.kt:161`
- Low frequency, negligible impact

### L7. Duplicate `isInsideString()` implementations

- `DslCompletionSource.kt:260-275` and `DslEditorExtension.kt:106-121` — near-identical
- **Fix:** Extract to shared util (and fix H4 at the same time)

---

## VERIFICATION

After fixes, verify by:

1. `./gradlew :klangscript:jvmTest` — language tests pass
2. `./gradlew :klangscript:jsTest` — JS-specific tests pass
3. `./gradlew :klangblocks:jvmTest` — block editor round-trip tests pass
4. `./gradlew :sprudel:jvmTest` — pattern evaluation tests pass
5. `./gradlew :audio_be:jvmTest` — audio processing tests pass
6. `./gradlew :tones:jvmTest` — music theory tests pass
7. `./gradlew :common:jvmTest` — utility tests pass
8. Full JS build: `./gradlew jsBrowserDevelopmentWebpack` — verify no runtime errors
9. Manual: play audio in browser, verify no dropouts or glitches
10. Manual: use block editor with loops, verify round-trip
