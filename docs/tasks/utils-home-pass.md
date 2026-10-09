# One home for generic helpers in every module

Status: **the moves are done, reviewed and committed on `engine-pass-1` (v0.6.0): the non-audio modules 2026-10-07
(`0c16de3c` sprudel, `67fa50fe` klangscript), the audio modules in engine tidy-up step 6, 2026-10-08 (`d2e09a2c`).**
The verdict per module is below. The `audio_*` modules were part of the engine tidy-up
([`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md), "Extract reusable helpers",
step 6); their verdict is the last section here.

**What is left:** the four helper merges under "Left for a decision" (each changes behaviour or is an extraction, not
a move): Q15 in [`_maintainer-questions.md`](_maintainer-questions.md). The coordinator would do them as small
reviewed steps unless the maintainer says otherwise.

## The rule

`/code-style` §3, "One home for generic helpers": a domain-free helper lives in the module's `utils/` directory,
package `io.peekandpoke.klang.<module>.utils` (already used by `audio_fe`, `tones` and the root module). A
feature's own helpers stay next to the feature as `_<feature>_helpers.kt`. Each `utils` helper has a spec.

## Inventory (2026-10-07, main sources)

| File | Today | Verdict to check |
|---|---|---|
| `audio_be/.../DspUtil.kt` | module root, package `audio_be` | generic DSP laws: move to `audio_be/.../utils/` (engine tidy-up) |
| `audio_be/.../voices/_retain_in_order.kt` | created in voice lifecycle step 5 | moved to `audio_be/.../utils/` in that step |
| `audio_be/src/jsMain/.../js_helpers.kt` | module root | generic JS interop: `utils/` |
| `audio_fe/.../utils/utils.kt` | already `utils/` | rename the file to say what is inside (`/code-style` §3: no generic file names) |
| `tones/.../utils/TonesUtils.kt` | already `utils/` | fine |
| `klangscript-libs/.../stdlib/KlangScriptObjectUtility.kt` | stdlib | a stdlib object, probably not a helper: check |
| `sprudel/.../lang/lang_helpers.kt` | `lang/` | split: generic parts to `sprudel/.../utils/`, DSL plumbing stays |
| `sprudel/.../pattern/PatternQueryUtils.kt` | `pattern/` | pattern-specific: stays, or `utils/` if generic |
| `sprudel/.../lang/editor/_mn_node_helpers.kt`, `sprudel/src/jsMain/.../ui/_mn_selection_helpers.kt`, `KlangToolInfoHelpers.kt` | next to their feature | feature helpers: stay |
| `klangui/.../comp/_staff_pos_helpers.kt` | next to its component | feature helper: stays |
| `src/jsMain/.../comp/editor_helpers.kt`, `pages/docs/tutorials/TutorialHelpers.kt` | next to their feature | feature helpers: stay |

Also look for generic helpers hiding inside larger files (private functions that are domain-free and duplicated
across modules), and for modules with no `utils/` yet that grow one.

## How

Behaviour-neutral moves, one commit per module, imports updated, suites green; a spec for each helper that moves
into `utils/` and has none.

## Verdict and what was done (2026-10-07, non-audio modules)

A helper earns a `utils/` home by being domain-free AND reused or worth pinning (the rule of
[`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md)). A private helper with one caller inside one feature stays where it is.

**sprudel**

| Helper | Verdict | Where now |
|---|---|---|
| `roundTo`, `decimalPlaces`, the trimming `toFixed` (was in `ui/_mn_selection_helpers.kt`) | generic number formatting: `utils/` | `sprudel/src/jsMain/kotlin/utils/number_formatting.kt`; the trimming `toFixed` is renamed `roundToString`, because ultra's `toFixed` (padding, imported by most tools) has the same name and the opposite trimming |
| `private fun Double.fmt()` (17 identical copies in the `Sprudel*EditorTool` files) | the generic law to `utils/` as `toFixedTrimmed(decimals)`; the tools' 3-decimal choice is a feature helper | `utils/number_formatting.kt` + `ui/_editor_tool_arg_helpers.kt` (`Double.formatArg()`); one deliberate change: from 1e21 up an exponent form is no longer trimmed (`"1.5e+30"` stays, the old copies printed `"1.5e+3"`) |
| `parseNum`, `parseNumOrNull` (12 copies each), `parseStr` (2 copies) | editor-tool arg parsing, a feature helper | `ui/_editor_tool_arg_helpers.kt` |
| the same parse law written inline in ten more tools (Gain, Pulze, Pan, Numeric, Dust numeric; Euclid, Waveform, Sample, Body, DistortShape string) and `toFixed(1).removeSuffix(".0")` in Gain and Numeric (review round 1) | the same helpers | `parseNum` / `parseStr(...).orEmpty()`, and `toFixedTrimmed(1)` (equal: `toFixed(1)` prints exactly one decimal) |
| `waveshape` + its `tanh` (2 identical copies in the two distortion tools) | the distortion tools' curve preview, a feature helper | `ui/_distort_curve_helpers.kt` (`tanh` renamed `previewTanh`, file-private) |
| `pattern/PatternQueryUtils.kt` | pattern-specific (`CycleTime`, events): stays, renamed for `/code-style` §3 | `pattern/_pattern_query_helpers.kt` |
| `lang/lang_helpers.kt` | DSL plumbing throughout (`SprudelDslArg`, voice modifiers, mappers, arg-to-pattern); `asDoubleOrNull` / `asIntOrNull` know `SprudelVoiceValue`. Nothing generic to take out | unchanged |
| `ui/_mn_selection_helpers.kt` (`quoteForCommit`), `lang/editor/_mn_node_helpers.kt`, `ui/KlangToolInfoHelpers.kt` | feature helpers | unchanged |

**klangscript**

| Helper | Verdict | Where now |
|---|---|---|
| `buildLineOffsets`, `lineColToOffset` (were at the end of `ast/AstIndex.kt`, used by `ast` and `intel`) | generic text positions: `utils/` | `klangscript/src/commonMain/kotlin/utils/line_offsets.kt`, spec `LineOffsetsSpec`; call sites name `line` and `column` (`/code-style` §24) |
| `suggestNames`, `formatAvailableNames` (`runtime/NameSuggestions.kt`) | generic "did you mean" ranking: `utils/` | `klangscript/src/commonMain/kotlin/utils/name_suggestions.kt`, spec `NameSuggestionsSpec` moved along |
| the lexer's `isAsciiDigit` family, `scanDecimalEnd` | lexer-private hot path | unchanged |

**klangscript-libs**: `stdlib/KlangScriptObjectUtility.kt` is the script door `Object`, not a helper: stays. `Configure.kt`,
`describeArgument`, `catalogueIndex` are DSL plumbing. No `utils/`.

**klangscript-ui, klangui, klang**: nothing domain-free beyond single-use private functions. `klangui/.../_staff_pos_helpers.kt`
stays. No `utils/`.

**tones**: `utils/` already holds `TonesUtils`, `TonesArray`, `TonesRange`, with specs. `TimeSignature`'s private
`isPowerOfTwo` duplicates `common`'s on purpose (documented at the site: `tones` stands alone). Unchanged.

**root (`src/`)**: `utils/` holds `FullscreenController` and `VersionController`; `comp/editor_helpers.kt` and
`TutorialHelpers.kt` are feature helpers. Unchanged.

### Left for a decision (not behaviour-neutral, or a design step)

- `Environment.loadLibrary` ranks library names with its own Levenshtein copy of `suggestNames` (threshold 3,
  double quotes). Folding it into `suggestNames` changes the message.
- `MnRenderer.renderNumber` is `formatAsIntOrDouble` (`common/math`) with one difference: above `Int.MAX_VALUE` it
  prints a clamped integer, where `formatAsIntOrDouble` prints the decimal form. Swapping fixes that edge and is a change.
- Text positions are computed in four places: `klangscript/utils/line_offsets.kt`, `sprudel`'s private
  `String.nthLineOffset` (`ui/MnEditorBase.kt`), the root's private `offsetToSourceLocation` (`comp/KlangCodeEditorComp.kt`)
  and `intel/AnalyzerDiagnosticOffsets.kt`'s clamping `offsetOf`. Their semantics differ (clamping, allocation); a shared
  home would be `common` next to `SourceLocation`, which is outside this pass.
- `MnEditorBase` and `NoteStaffEditor` both carry `highlightTimeouts` + `scheduleTracked` + `cancelPendingHighlights`
  (a tracked set of window timeouts). A small class in `sprudel`'s `utils/` would hold it once; that is an extraction,
  not a move.

## Verdict and what was done (2026-10-08, audio modules, engine tidy-up step 6)

| Helper | Verdict | Where now |
|---|---|---|
| `audio_be/.../DspUtil.kt` | generic DSP laws, split by content | `audio_be/src/commonMain/kotlin/utils/`: `math_constants.kt`, `fast_math.kt`, `numerical_safety.kt`, `phase_wrap.kt`; package `io.peekandpoke.klang.audio_be.utils`, every importer updated, `inline` kept |
| `applySemitoneDetuneToFrequency` (was in `DspUtil.kt`) | a duplicate of `common.math.semitones()` | deleted; its callers and three inline copies call `semitones()` |
| `waveTrapezoid` (was in `DspUtil.kt`) | the oscillators' waveform, a feature helper | next to its state class, `audio_be/.../ignitor/WaveVoiceState.kt` |
| `audio_be/src/jsMain/.../js_helpers.kt` | generic JS interop | `audio_be/src/jsMain/kotlin/utils/js_objects.kt`, spec `JsObjectsSpec` (`jsTest`) |
| `audio_fe/.../utils/utils.kt` | renamed after what is inside | `audio_fe/.../utils/url_checks.kt`, spec `UrlChecksSpec`; `safeEnumOf` / `safeEnumOrNull` had no caller in any module and are deleted |
| `audio_be/.../utils/retain_in_order.kt` | already there (voice lifecycle step 5) | unchanged |
| new in step 6 | the B2 twins and the per-block copy | `utils/`: `finiteOrZero` (in `numerical_safety.kt`), `fade_to_zero.kt`, `time_constant.kt`, `wrapPhaseFastOrSafe` (in `phase_wrap.kt`), `ramp_step.kt`, `buffer_copy.kt`; one spec each |
| the stereo add (new in step 6) | an operation of the module's bus type | a member, `StereoBuffer.addFrom`, next to `clear()` and `fill()`; spec `StereoBufferAddFromSpec` |
| `audio_be/.../_pcm16_edge.kt` (`pcm16`, `writePcm16`) | the output edge, a feature: `writePcm16` takes a `StereoBuffer`, and `pcm16`'s rule is the partner of the output clip | stays at the module root |

`audio_be`'s `utils/` imports nothing from the rest of the module (`AudioSample` is written `Double` there).
`audio_bridge` and `audio_jsworklet`: no domain-free helper with a second caller; no `utils/`. Specs of the moved
`audio_be` helpers moved to `audio_be/src/commonTest/kotlin/utils/` (`DspUtilSpec` is `NumericalSafetySpec`); their
integration rows (the oscillator, the envelope shape, the two pitch envelopes) sit next to those features, so the
`utils` specs import nothing from the rest of the module either.
