# Sprudel Sound-Function Surface — Redesign (the `snd*` family: compound-string vs named/per-param)

> Status: **NOT STARTED (design).** This is the tracked home for the `snd*`-family cleanup — "single compound
> colon-string vs. named / per-param control patterns." Captured 2026-06-05; refreshed **2026-06-30** with
> what changed since (see the update block below).
>
> ### 2026-06-30 update — what's changed
> - **Named arguments now work in KlangScript** (`name = value`). The old "positional-only, use `/* name */`
    > comments" convention below is **superseded** — the redesign can lean on real named args. See
    > `[[KlangScript supports named arguments]]` (memory) for the KSP safe-literal-default caveat.
> - The **noise-generator knobs** work (2026-06-30) deliberately extended the `snd*` family in the
    > **compound colon-string** form as the *interim*: `sndCrackle("1.7")` (chaos), `sndNoise("-0.5")` (color),
    > `sndBrown("0.5")` (depth), `sndDust("0.2:4")` (density:tail). This was an explicit "do the compound string
    > now, defer the real fix" decision — so the family is now larger and the per-param blocker is more pressing.
> - **The blocker is unchanged and is the whole point of this task:** a compound `"a:b:c"` string is one value,
    > so you cannot modulate one sub-param with its own control pattern. Per-param control-pattern support
    > (the project rule "all params accept control patterns") needs the positional/named surface below.
> - The Osc-side already models the target shape: `Osc.crackle(chaos = 1.7)`, `Osc.dust(density, tail, bipolar)`
    > take per-param named args today — the `snd*` sprudel surface is what lags.

## Why

The sprudel sound-selection functions (`sndSaw()`, `sndZamp()`, `sndPluck()`, `sndSupersaw()`, …) have two
problems with their current surface:

1. **`snd`-prefixed camelCase is not idiomatic for sprudel.** Sprudel DSL functions are lowercase
   (`note`, `gain`, `lpf`, `room`…). `sndSaw` / `sndSupersaw` read as Kotlin, not sprudel.
2. **They take a single composite colon-string param** (`"p1:p2:p3"`). This is the blocker: a user cannot
   modulate **one** of the sub-parameters with its own control pattern. The whole `"a:b:c"` string is one
   value, so you can't e.g. pattern just `brightness` while holding `decay` fixed.

## Current state (grounding)

- Defined in `sprudel/src/commonMain/kotlin/lang/addons/lang_snd_addons.kt`.
- Each function: a `voiceModifier` splits the single string on `":"`, `toDoubleOrNull()`s each field, and
  maps them onto oscParams via `withOscParams("decay" to parts[0], "brightness" to parts[1], …)`. Empty
  args → bare `copy(sound = Named(...))`; non-empty → `_applyControlFromParams(...)` so the **composite
  string** can be a control pattern (but only as a whole).
- **Tool-window integration is annotation-driven** (this is the part that complicates the redesign):
    - `@param-tool params SprudelPluckSequenceEditor` — binds the single `params` arg to a dedicated editor.
    - `@param-sub params decay Feedback amount (…)` — decomposes the colon-string into labelled sub-fields
      for the editor UI.
    - See `docs/tasks/sprudel-ui-tools.md` for the tool catalogue (e.g. `SprudelUnisonEditorTool`,
      `SprudelWaveformEditor`).

## Proposed direction (NOT decided — user's quick suggestions)

1. **Lowercase, multi-positional + named params** — `.saw(p1, p2, …)`, `.supersaw(voices, spread, …)`,
   `.pluck(decay, brightness, pickPosition, stiffness)`. Each param is independently patternable (its own
   control pattern), which fixes problem 2. **Named args now work** (`name = value`) — so the surface can be
   `.supersaw(voices = 8, spread = 0.3)` (override only what you want), not just positional. (Note: the super-osc
   spread param is `spread`, not `freqSpread`, post-rename; and `analog` is a chained character knob, not a
   constructor positional.)
2. **Keep a single-string composite shorthand** — when a function is called with exactly one **string**
   argument, treat it as the legacy `"p1:p2:p3"` composite (sugar / back-compat). So
   `.supersaw("8:0.3:0.2")` and `.supersaw(/* voices */ 8, /* spread */ 0.3, /* analog */ 0.2)` both work.

## Open questions to resolve before building

- **Naming / migration**: drop the `snd` prefix entirely (`saw` vs `sndSaw`)? Does a bare `saw` collide
  with anything in the sprudel namespace (note names, existing functions)? Keep `sndX` as deprecated
  aliases during transition?
- **Tool windows** (the hard part): the current `@param-tool` + `@param-sub` model assumes ONE composite
  param. With N positional params:
    - Does each param get its own `@param-tool` (e.g. a slider per param) and the old sequence-editor
      disappears, or do we keep a "combined" editor that writes back into N positional args?
    - How does the editor round-trip code that mixes literals and control patterns across the N params?
    - The single-string shorthand still needs the old composite editor — two code shapes to support.
- **Patternability**: confirm each positional param flows through `_applyControlFromParams` independently
  so per-param control patterns actually reach the oscParam (the plumbing today only patterns the whole
  string).
- **Scope**: which sounds get the new surface first? (saw/supersaw family vs pluck vs the full set.)
- **Consistency with oscParams**: positional params should map 1:1 to the ignitor oscParam names
  (`voices`, `freqSpread`, `analog`, `duty`, `decay`, …) — keep the mapping in one place.

## Related

- `docs/tasks/sprudel-ui-tools.md` — the editor-tool catalogue that the `@param-tool` annotations drive.
- `docs/tasks/sprudel-dsl-named-args.md` — sprudel arg-handling conventions.
- Oscillator oscParams + per-variant constants — `audio_be/.../ignitor/OscillatorTuning.kt`,
  `Ignitors.kt` (see `docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md`).
