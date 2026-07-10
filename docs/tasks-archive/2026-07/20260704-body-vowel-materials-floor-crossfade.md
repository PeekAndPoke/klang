# Body / vowel resonators: materials, floor, grouping, UI tool, declick

Status: **DONE 2026-07-04** (uncommitted at time of writing). Follow-on to
`20260703-body-vowel-orbit-katalyst.md` (which moved body/vowel to orbit-level Katalyst effects).

## What changed

- **Material library** — added `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`, `violin`,
  `steel`, `bell` (+ existing `wood`/`tube`/`glass`/`membrane`/`brass`) and a `none` reset (for both
  `body()` and `vowel()`). Tables extracted from the old private `SprudelVoiceData.resolveBodyModes`
  into the **public `SprudelBodyMaterials`** catalogue (`names` / `descriptions` / `modesFor(name)` →
  `List<FilterDef.Body.Mode>(freq, db, q)`) so the UI + docs stay in sync. Each material is a
  *caricature* — a few load-bearing "tells" anchored from acoustics (credited), the rest sparse fill,
  tuned by ear. Vowel `none` handled in `SprudelVoiceData.resolveVowelBands`.

- **User-settable dry floor** — `FilterDef.Body`/`FilterDef.Formant` gained a nullable `floor`
  (null → engine default `BODY_FLOOR`/`VOWEL_FLOOR`), threaded through `createBody`/`createFormant` and
  both Katalyst effects. New DSL `bodyFloor()` / `vowelFloor()`. `BODY_FLOOR` lowered 0.6 → 0.4 (body
  was too quiet). `bodyMix` stays uncapped > 1 (raw — secured by tests).

- **Voice-data grouping** — the body/vowel flat fields folded into `SvdBody` / `SvdVowel` sub-objects
  (`SvdGroups.kt`) with `mergeSvd*` helpers, matching the `SvdAdsr` leaf-clone-perf pattern; flat
  `body`/`bodyMix`/`bodyFloor`/`vowel`/... are accessors over the groups.

- **`body()` UI tool** — `SprudelBodyEditorTool` (sprudel jsMain): material picker + live
  modal-response "fingerprint" curve; bound via `@param-tool`.

- **Live-change declick** — `KatalystFilterSwap` crossfades the orbit resonator bank (~12 ms) on any
  material/mix/floor rebuild, so live changes don't click. Shared by both Katalyst twins.

- **Credits** — "Acoustics & Physical Modeling" section in `CREDITS.MD` + the UI credits page (Fletcher
  & Rossing; Benade) for the landmark resonance frequencies.

## Code-review catches (fixed same session)

- `vowelFloor()` was a no-op: `KatalystFormantEffect` (the twin of `KatalystBodyEffect`) hadn't been
  updated to pass `floor` — the twins must stay in sync. Added the missing `KatalystFormantEffectSpec`.
- `VoiceFactory.toFilter` Body/Formant arms are unreachable (body/vowel are pulled out at line ~107) →
  made a hard `error()`.

## Key files

`SprudelBodyMaterials.kt`, `SvdGroups.kt`, `SprudelVoiceData.kt`, `lang_body.kt` / `lang_vowel.kt`,
`SprudelBodyEditorTool.kt` (sprudel); `FilterDef.kt` (audio_bridge); `LowPassHighPassFilters.kt`,
`KatalystBodyEffect.kt` / `KatalystFormantEffect.kt` / `KatalystFilterSwap.kt`, `VoiceFactory.kt`
(audio_be); `CREDITS.MD`, `CreditsPage.kt`.

## Follow-ons (not done)

- Second, more characterful material batch (marimba/glockenspiel/tabla/ocarina/plastic).
- A floor slider on the `body()` UI tool.
- Deriving materials from impulse responses — see `docs/tasks/future/ir-to-modal-table-extraction.md`.
