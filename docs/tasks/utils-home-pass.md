# One home for generic helpers in every module

Status: **non-audio modules done, uncommitted, awaiting review (2026-10-07).** The verdict per module is below. The `audio_*` modules are part of
[`engine-tidy-up.md`](engine-tidy-up.md) ("Extract reusable helpers"); this file covers the rest.

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
[`engine-tidy-up.md`](engine-tidy-up.md)). A private helper with one caller inside one feature stays where it is.

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
