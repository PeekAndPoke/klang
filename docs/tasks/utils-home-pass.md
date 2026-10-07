# One home for generic helpers in every module

Status: **queued (maintainer, 2026-10-07).** The `audio_*` modules are part of
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
