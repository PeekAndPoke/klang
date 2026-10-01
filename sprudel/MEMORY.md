# Sprudel: Memory

What is true in the sprudel module now: the state, the decisions and rules in force, the traps and the
open threads, each said once with a pointer to its home. Short by design. To keep it that way: when a
change moves something here, update the section it touched in place, add ONE line to History (date, a
few words, the link to the task record), and put the narrative in the task record, which gets archived.
The full dated record up to 2026-09-29 is `ref/memory-history.md`; read it only when you need the
history of a decision. Which functions exist is answered by the `lang/` files and the generated docs
(`generatedSprudelDocs`), not by a list here.

## Voice data and the wire

- `SprudelVoiceData` is mutable and single-owner (see Lessons); its fields are grouped into the `Svd*`
  classes of `SvdGroups.kt`, and `oscParams` / `katalystParams` are `ParamBag`s (`ParamBag.kt`): a door
  writes one name in place, the bag is allocated on the first write (`oscParamsOrNew()` /
  `katalystParamsOrNew()`), copied once per event by `clone()`, `merge` builds a fresh one, `mergeFrom`
  folds into the receiver's own. The class KDoc of `ParamBag` has the contract.
- `toVoiceData()` is the one boundary. The voice doors stay TYPED on this side (the query hot loop keeps its
  one allocation, `docs/plans/signal-flow-redesign.md` §4) and are written there as `classic()` slot keys
  into `oscParams` by `_classic_slot_params.kt`: the filters, `adsr`, distort / crush / coarse, tremolo and
  the sample's flat `begin` / `end` / `speed` / `loop`. The rules are in that file's KDoc; the key names are
  read from `IgnitorDsl.Slots`, never retyped. Guard: `ClassicSlotParamsSpec`.
- Still typed wire fields beside the two bags: `note`, `freqHz`, `accelerate`, `vibrato`, `vibratoMod`, `sourceId`, the `penv` and
  `fm` fields, `gain`, `pan`, `legato`, `bank`, `sound`, `soundIndex` (from `n`), `cut`, `cylinder`, `solo`,
  the `master` and `katalyst` names, `control`, `tags`, `cull`. The orbit stages travel only as
  `katalystParams` slots.
- An authored instrument that does not end in `classic()` plays as its bare tree: a voice door reaches it
  only when its tree reads that slot (the editor hint waits in
  `docs/tasks/future/editor-voice-door-diagnostics.md`).
- `crush.oversample` and `coarse.oversample` travel but nothing reads them
  (`docs/tasks/oversampling-regions.md`); `distort.oversample` is a `classic()` slot, read at voice build.
- `velocity` stops at the wire: `foldedGain()` multiplies it into `gain`, `VoiceData` has no `velocity`.
  Both unset gives `null`; a non-finite operand reads as unset before the product. Guard: `WireGainFoldSpec`.
- `tag(name)` tags cross the wire as `VoiceData.tags`, an unordered set. The argument is a literal
  (`reinterpretVoice`), never lifted as mini-notation.
- Both bags reach the wire as `toMap()` copies: the backend holds `katalystParams` for the voice's life and
  gates its re-resolve on the map's identity.

## Door shapes in force

The rules for writing a door live in `ref/dsl-conventions.md` (the recipe, field accessors, compound doors,
setter semantics) and `/dsl-design`. What they produce today:

- Every numeric single-field setter is an accessor object named like its script name (`object gain`) on
  the `FieldAccessor` base; its `@KlangScript.Invoke` member is the Kotlin door. Aliases are constants
  (`val vel: velocity = velocity`); `comp`, `uni`, `vib`, `pamt` stay.
- The compound doors are objects with one reader child per numeric slot: `adsr`, `lpf` / `hpf`
  `(freq, q, passes, env, attack, decay, sustain, release)`, `bpf` / `notch` (the same without `passes`),
  `reverb(wet, size, lowpass)`, `delay(wet, time, feedback, cap)`, `phaser(wet, rate, center, sweep, floor)`,
  `body(wet, material, floor)`, `vowel(wet, vowel, floor)`, `tremolo`, `distort`, `crush`, `coarse`,
  `compressor`, `unison`, `duck`, `vibrato`, `penv`, `fm`. No bare read (`pan(reverb)` is nothing), no single
  doors per slot. The curve objects (`adsrCurves`, `lpfCurves`, `hpfCurves`, `bpfCurves`, `notchCurves`,
  `penvCurves`) are setters only. The long names `lowpass` / `highpass` / `bandpass` are constants.
- The name slots `body.material` and `vowel.vowel` carry the INDEX of the name (`BodyMaterials.indexOf`,
  `VowelBands.indexOf`, 0 is `none`); they have no readers (`docs/tasks/future/string-slot-readers.md`).
- The five wet doors take `wet` first; a bare call reinterprets the pattern's values as the wet.
- `.katalyst(dsl)`, `.master(dsl)` and `sound()` REPLACE; each stamps one value onto every event.

## Levels: `gain`, `velocity`, `pregain`

- `gain` is the one level word (the channel fader, applied once with pan). `velocity` / `vel` is a sprudel
  word that folds into it at the wire (above).
- `pregain(amount)` is exactly `oscp("pregain", amount)` (`lang_dynamics_level.kt`): it writes a slot, and
  the slot does what the instrument's tree wires it to. Every built-in synth and sample places it at the
  source, in front of `classic()`. What it does to each distortion shape is in the door's KDoc; the render
  guards are audio_be's (`PregainSlotRenderSpec`, `VoicePregainWireSpec`).

## The bus doors and the orbit's Katalyst

- The bus doors (`reverb`, `delay`, `compressor`, `duck`, `phaser`, `body`, `vowel`) write the orbit's
  `<stage>.<knob>` slots in `katalystParams`; `.katp(name, value)` writes one directly. A bus door and its
  `katp` slot are the same knob; the rule's home is the `katp` door's KDoc.
- A door fills its stage's companions per param with `ParamBag.setOrDefault(name, null, CONST)`, constants
  from `audio_bridge/constants/`. The rule itself has one home, `/dsl-design` §4; do not restate it here.
- `delay`, `reverb`, `compressor` and `duck` write the slots only, and their accessors read the slots
  (`reverb.wet` is `katalystParams["reverb.wet"]`). `phaser`, `body` and `vowel` also still write their
  `Svd*` fields, which no longer cross the wire, and their accessors read those fields.
- Guards: `LangKatalystParamSpec`, `ParamBagSpec`, `KatalystDoorFillRenderSpec`, the per-door lang specs.
  The door-forms matrix is `LangDoorFormsSpec` (`ref/testing.md`).

## Lessons

- **Immutable at construction, mutable at runtime, both deliberate.** Every combinator returns a new pattern
  (`/dsl-design` §1); the query and render path uses mutable single-owner `SprudelVoiceData` on purpose (leaf
  clone about 17x faster, `docs/tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md`). Do not
  "fix" either side toward the other. Guards: `VoiceDataAliasingSpec` (no two events of a query share their
  data, an `Svd*` group or a `ParamBag`; a new group joins its `mutableParts()` and `writeEveryPart()`),
  `SprudelVoiceDataSpec`, `ParamBagSpec`.
- **`.scale()` applies exactly once per chain.** `resolveNote()` (`lang_tonal_note.kt`) consumes the note
  index on first resolution, so a later `.scale()` is inert for pitch. Hence exported Klangbuch parts carry
  NO scale, not even a default; an importer's own `.scale()` works because it is first.
- **Klangbuch parts are arrangement-free.** Exported patterns, shapes and parts carry no arrangement timing
  (`filterWhen`, `early`, `late`, anything time-gating); that lives only at the song's `stack(...)`. An
  importer can add gating but cannot remove it. `slow` / `fast` are intrinsic to the voice and stay.
- **Structural cycle selection is exact.** `<...>` (`AlternationPattern`) and `arrange` pick the active item
  by integer cycle index and place it with integer-tick shifts, never by `slow(N)` scaling, which rounds
  whenever N does not divide `CycleTime.T`. Guards: `StructuralCycleSelectionSpec`, `LangLateAlternationSpec`.
- **Control patterns need a join**, or they silently break while static values work: `ref/control-patterns.md`.
- **Step-based, not cycle-based**: `take(2)` takes two steps. `repeatCycles(n)` repeats each cycle n times
  (source cycle `floor(c / n)`). `extend(n)` is `fast(n)`. `iter(n)` is a slowcat of `early(i / n)` copies,
  not a pattern class of its own. `whole` is never null (`ref/event-model.md`).
- **Test across 12 cycles and filter by `isOnset`**: `ref/testing.md`.
- **A stdlib string method must not share a name with a sprudel function**: sprudel registers every pattern
  function on strings too, and the later import wins. Guard: `LangStdlibStringMethodCollisionSpec`.

## Open threads

- `scale()` KDoc should say that later `.scale()` calls do not re-pitch (not written yet).
- `docs/tasks/sprudel-sound-doors-compound.md` and `docs/tasks/sprudel-sound-function-surface.md`: the
  `snd*` family, the last doors that are not compound objects.
- `docs/tasks/sprudel-function-testing.md`, `docs/tasks/sprudel-test-coverage-and-review.md`,
  `docs/tasks/sprudel-ui-tools.md`.
- `docs/tasks/future/string-slot-readers.md`, `docs/tasks/future/editor-voice-door-diagnostics.md`,
  `docs/tasks/future/optimize-constant-control-fast-path.md`, `docs/tasks/oversampling-regions.md`.

## History

One line per step; the narrative is in the linked record or in `ref/memory-history.md`.

- 2026-02: full KDoc pass, fenced KlangScript examples, `press` / `pressBy`, docs page search
  (`ref/memory-history.md#recent-work-2026-02`).
- 2026-08-20: `tag(name)`, `Set<T>` in the wire codec, `ref/dsl-conventions.md` rewritten
  (`ref/memory-history.md#recent-work-2026-08-20`).
- 2026-09-06: field accessor pilot on `freq`, mapper arguments apply to their own field
  (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`).
- 2026-09-07: field accessor batches one to four, accessor objects carry the script name, one Kotlin door
  per accessor, the `adsr` compound pilot (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`).
- 2026-09-07: compound effects (batch E), filters (batch F) and the last compounds (batch G) become objects
  with named slots; per-knob doors retired (`ref/memory-history.md#recent-work-2026-09-07`).
- 2026-09-07: the addons split retired, every DSL file is `lang/lang_<group>_<subgroup>.kt`
  (`docs/tasks-archive/2026-09/20260907-sprudel-lang-file-reorganisation.md`).
- 2026-09-08: arithmetic reads a continuous control per onset (`_appLeft`), `segment` fixed
  (`docs/tasks-archive/2026-09/20260908-sprudel-arithmetic-continuous-controls.md`).
- 2026-09-16: the reverb is `reverb`, not `room` (`docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`).
- 2026-09-16: a rest in a setter's control pattern leaves the field untouched (`ref/memory-history.md#recent-work-2026-09-07`).
- 2026-09-18: Katalyst step 5a: bus doors write the orbit's slots, material and vowel as indices,
  `.katalyst()` replaces, `ParamBag`, the per-param fill (`docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` §9).
- 2026-09-19: Katalyst step 5b: the slot is what reaches the orbit; delay, reverb, compressor and duck
  write slots only (`docs/tasks-archive/2026-09/20260928-katalyst-dsl.md` §9).
- 2026-09-19: `postgain` retired, `velocity` folds at the wire
  (`ref/memory-history.md#gain-is-the-one-level-word-velocity-folds-at-the-wire-2026-09-19`).
- 2026-09-19: the `pregain` door (`ref/memory-history.md#the-pregain-door-the-other-level-word-2026-09-19`).
- 2026-09-24: `phaser`, `body`, `vowel` wet-first, phase 3 step 3d(iii)
  (`ref/memory-history.md#phaser-body-and-vowel-are-wet-first-2026-09-24-phase-3-step-3diii`).
- 2026-09-27: phase 3 step 8, the `classic()` slot keys are written in `toVoiceData`
  (`ref/memory-history.md#the-classic-slot-keys-are-written-in-tovoicedata-phase-3-step-8-2026-09-27`).
- 2026-09-27: phase 3 step 9, the typed voice fields left the wire, `loopBegin` / `loopEnd` removed
  (`ref/memory-history.md#the-typed-voice-fields-left-the-wire-phase-3-step-9-2026-09-27`).
- 2026-09-28: `VoiceDataAliasingSpec` replaces the wire golden `MutableVoiceDataGoldenSpec`
  (`ref/memory-history.md#lessons-learned`).
- 2026-09-29: this file restructured; the old status line, feature list and dated entries are in
  `ref/memory-history.md` (`#current-status`, `#completed-features`).
- 2026-10-01: `beats(n, base = 4)`, a tempo-following duration in seconds
  (`docs/tasks-archive/2026-10/20261001-sprudel-beats-helper.md`).
- 2026-10-01: `beatRate(n, base = 4)`, the rate twin of `beats`, one cycle every n beats in Hz
  (`docs/tasks-archive/2026-10/20261001-sprudel-beat-rate-helper.md`).
