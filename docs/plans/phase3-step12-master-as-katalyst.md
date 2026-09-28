# Phase 3 step 12: plan and inventory for "`.master()` accepts a Katalyst, the Master DSL retires"

Planner's inventory, 2026-09-27, branch `engine-redesign` at `f848c86b`; the maintainer's decisions of the same day are section 0. Read-only: nothing in the repo was
edited, no Gradle run. Every code claim carries `file:line` and was read; anything not read is marked
UNVERIFIED. Paths are repo-relative to `/opt/dev/peekandpoke/klang`.

Two things called "master" exist and must not be confused:

- **the house stage** `MasterStage` (`audio_be/src/commonMain/kotlin/MasterStage.kt`): DC blockers, the always-on
  safety limiter with its 5 ms lookahead, clip and interleave. ONE instance, on the SUM of every playback
  (`PlaybackEngineDispatcher.kt:33`, `KlangAudioRenderer.kt:27`). Not authorable, not a DSL. **Out of scope; it
  stays.**
- **the authored master** `MasterBus` + `MasterChain` + `MasterDsl`: one per `PlaybackEngine`, on that playback's
  summed orbits (`PlaybackEngine.kt:78-101`). **This is what step 12 replaces with a Katalyst at the output
  position.**

---

## 0. The decisions (maintainer, 2026-09-27; the options are in section 8)

**Unification changes are accepted (maintainer, 2026-09-27, after decision (h)):** a behaviour change that
follows only from a master-only special case disappearing, so the master now does what the Katalyst already
does, is accepted for step 12 without a separate decision (less surface, less logic). It is named in the commit
and in this plan. Only a change a listener meets in normal use still goes to the maintainer first.

| # | Decision | Decided |
|---|---|---|
| (a) | The authored limiter's lookahead | **A user can build a lookahead limiter, and it runs anywhere.** `lookahead` is a knob of the Katalyst `compressor` stage (the `Compressor` DSP already has it); at the output the playback is late by it, on an orbit THAT orbit is late by it (the user's choice and ear; no compensation). The guardrail "the master limiter lookahead is master-only" narrows to the house limiter when this lands (C2). Not a3 (retire) and not a1 (output only), both proposed below. |
| (b) | What fills a chain's `Param` slots at the output | **Nothing (b1).** A `Param` is its default at the output; the flexible graph can add a channel later. |
| (c) | Stages that exist on one side only | **duck is inert at the output** (built, never run; recorded in its KDoc and pinned by a spec); **body, vowel, phaser, compressor, eq are allowed** there. |
| (d) | How `limiter` is spelled | **A builder door over the compressor (d3)**: `limiter(...)` appends a `compressor` stage with the limiter defaults, `lookahead` included as the compressor's knob. One DSP, one wire word; "limiter" is a preset, not a second concept. |
| (e) | The shape of `SwapState` | Coordinator: three states (Idle, Fading, Draining) plus the host's parked key (latest wins), per the 5c-11 `KatalystFilterSwap` precedent; `effect-state-machines.md` section 3 is updated to say so when C1 lands. |
| (f) | Does the master adopt the orbit's swap law | **Yes, one law (f1).** The old master chain drains instead of being cut; a named sound change on live master edits only, with a listening pair (C4). |
| (g) | A request arriving while the old chain drains | Kept as is for step 12 (identity); recorded as an open question for the maintainer's ear later. |
| (h) | A master reverb or delay whose unit is REFUSED (an allocation that throws: out of memory, or a ring past the array limit) | **Recovery, the orbit's rule (maintainer, during C3).** The stage stays in the chain Off (dry), re-asks the shelf every block without allocating and engages when a unit comes back, from an empty unit (nothing stale replayed; the wet enters unramped, as on an orbit, so a delay's first echo can land as a step); a reset re-arms one allocating retry. The old master dropped the stage for the chain's life. |
| (i) | A self-sustaining chain (a delay at feedback >= 1) swapped away never finishes draining, so every later request waits forever (found in C4's review; orbits had it already) | **Cap the drain (maintainer, 2026-09-28), option A.** A leaving chain rings out naturally for up to 20 s (`ChainSwap.MAX_DRAIN_SECONDS`, the engine's post-stop hold); if it is still ringing then, it gets a smooth EXPONENTIAL release (about -60 dB over about 3 s) instead of a cut, so a long echo dies away as if on its own; then any parked request lands (worst-case wait about 23 s). Normal rooms finish long before the cap. A request during a drain still waits (decision (g)); at most two chains run at once (the one in service and one draining). One rule in `ChainSwap` for both positions. Also: C4's listening pair ACCEPTED (maintainer, 2026-09-28: the drain stays). |
| (j) | After a playback STOPS, the engine renders tails for up to 20 s of quiet, then is dropped instantly: a hard cut when a master swap is still draining, or when the chain in service holds a self-sustaining delay (found in the capped drain's audio review) | **No hard cut after stop, ever (maintainer, 2026-09-28).** The engine waits for a draining master swap to settle (bounded, about 24.5 s after the swap), and a tail still ringing when the post-stop hold ends gets the same exponential release as the capped drain (60 dB per 3 s, to -90 dB), the law in one place. The engine's life after stop grows by at most about 4.5 s. **Refined the same day (maintainer, after the audio review):** only ENDLESS tails get that release (a self-sustaining delay at feedback >= 1); every tail that can end on its own rings out fully after stop, however long, as before. |

---|---|---|
| (a) | The authored limiter's lookahead | **a3: retire the authored lookahead** (no song, no tutorial uses it; the house limiter keeps its 5 ms). Fallback a1 (a build-time knob honoured only at the output). |
| (b) | What fills a chain's `Param` slots at the output | **b1: nothing.** `applyParams(null)` every block: a `Param` is its default. Record b2 (the carrier event's own `katalystParams`) as the cheap path if master automation is wanted before the graph. |
| (c) | Stages that exist on one side only | **duck: inert at the output** (built, never run; documented and pinned). **body, vowel, phaser, compressor, eq: allowed** (no orbit-specific code in them; eq closes `master-dsl-followups.md` section 5 for free). |
| (d) | How the master's `limiter` is spelled on a Katalyst | **d3: a `limiter(...)` builder door** on `KatalystBuilder` (script and Kotlin in one function) that appends a `KatalystStageDsl.Compressor` with the limiter constants. One DSP, one wire word. |
| (e) | The shape of `SwapState` | Three states (Idle, Fading, Draining); "Pending" is the HOST's parked key (latest wins), not a state, following the 5c-11 `KatalystFilterSwap` precedent. Probably coordinator-level; listed because the plan's table names four states. |
| (f) | Does the master adopt the orbit's swap law (input ramp, drain) | **f1: yes**, as its own commit with a named sound change on live master edits only (no corpus row moves). Closes the open "if audible, extend the old chain's life" item in `MasterBus.kt:30-33`. |
| (g) | A request arriving while the old chain DRAINS | Today it waits for the whole ring-out on the orbit (`Cylinder.kt:498-500`), up to many seconds for a big room; at the master that becomes a live-coding latency. Keep (identity) for step 12, record as an open question. |

---

## 1. Both DSLs side by side

### 1.1 The wire types (audio_bridge)

`MasterDsl(val stages: List<MasterStageDsl>)` (`audio_bridge/src/commonMain/kotlin/MasterDsl.kt:33-50`), default
EMPTY (`:45`, "This MUST stay empty"), `MasterDsl.of(...)` (`:48`). Knobs are plain `Double`s.

`KatalystDsl(val stages: List<KatalystStageDsl>)` (`audio_bridge/src/commonMain/kotlin/KatalystDsl.kt:56-210`),
default for an orbit is `KatalystDsl.classic` (`:147-205`, every knob a `Param` slot), `KatalystDsl.of(...)`
(`:208`). Knobs are `IgnitorDsl` restricted to `Constant` / `Param` (`:222-226`); anything else is coerced at chain
build (`KatalystSlots.kt:63-68, 99-126`).

Constants (values read): `SendEffectDefaults.kt:22-40` (`DELAY_WET` 0.25, `DELAY_TIME_SECONDS` 0.25,
`DELAY_FEEDBACK` 0.3, `DELAY_CAP` 1.0, `REVERB_WET` 0.25, `REVERB_SIZE` 5.0); `BusEffectDefaults.kt:55-128`
(`SLOT_UNSET` NaN, phaser 0 / 0 / 1000 / 1000 / 1.0, compressor -20 / 4 / 6 / 0.003 / 0.1, duck depth 0, attack
0.1, body 0.5 / 0.4, vowel 0.5 / 0.2); `MasterLimiterDefaults.kt:17-51` (`LIMITER_THRESHOLD_DB` -1,
`LIMITER_RATIO` 20, `LIMITER_KNEE_DB` 2, `LIMITER_RELEASE_SECONDS` 0.1, `AUTHORED_LIMITER_LOOKAHEAD_SECONDS` 0.0,
`AUTHORED_LIMITER_ATTACK_SECONDS` 0.001).

| Stage (wire name) | Master (`MasterStageDsl`) | Katalyst (`KatalystStageDsl`) | Verdict |
|---|---|---|---|
| `gain` | `Gain(gain = 1.0)` literal, `MasterDsl.kt:73-76` | `Gain(gain = Constant(1.0))`, `KatalystDsl.kt:469-472` | **Same name, scale, default.** Different lifetime: master resolves at chain build, a new value is a new chain and a 60 ms bus crossfade (`KatalystGainEffect.kt:29-31`); Katalyst is a slot that GLIDES over `KNOB_GLIDE_SECONDS` (0.05 s). Master DROPS a unity gain at build (`MasterChain.kt:274-276`); Katalyst keeps the stage and skips the multiply (`KatalystGainEffect.kt:129-136`). |
| `reverb` | `Reverb(wet = REVERB_WET, size = REVERB_SIZE, lowpass: Double? = null)`, `MasterDsl.kt:149-154` | `Reverb(wet = Constant(REVERB_WET), size = Constant(REVERB_SIZE), lowpass: IgnitorDsl? = null)`, `KatalystDsl.kt:331-336` | **Same names, scales (size authored 0..10), defaults.** Asymmetries: master drops the stage at `wet <= 0.0001` (`MasterChain.kt:171, 322`), Katalyst runs any authored `wet > 0` (`KatalystChainBuilder.kt:351-356`); a non-finite size is the constant on the master (`MasterChain.kt:320`) and OFF on the Katalyst (`Reverb.normalizeSize(NaN) = 0.0`, `Reverb.kt:472-480`, below `MIN_ACTIVE_SIZE`). Master rents its unit at chain build, Katalyst at first configure. |
| `delay` | `Delay(wet, time, feedback, cap)` = `DELAY_*`, `MasterDsl.kt:167-173` | `Delay(...)` = `Constant(DELAY_*)`, `KatalystDsl.kt:308-314` | **Same names, scales, defaults.** Same two asymmetries as the reverb (wet floor; a non-finite time is the constant on the master, `MasterChain.kt:374`, off on the orbit; the recorded "one asymmetry" row `SendEffectDefaultsParitySpec.kt:196`). |
| `limiter` | `Limiter(threshold, ratio, knee, attackSeconds, releaseSeconds, lookaheadSeconds)`, `MasterDsl.kt:103-128`; defaults from `MasterLimiterDefaults.kt` | none | **Master-only.** The DSP is the same `Compressor` as the Katalyst `compressor`. Differences: the name, the DEFAULTS (-1/20/2/0.001/0.1 vs -20/4/6/0.003/0.1), the field spelling `attackSeconds`/`releaseSeconds` vs `attack`/`release`, and `lookaheadSeconds` (a constructor val that sizes arrays, `Compressor.kt:73, 139-157`). |
| `compressor` | none | `Compressor(threshold, ratio, knee, attack, release)`, `KatalystDsl.kt:376-383` | Katalyst-only as a name; the master's `limiter` is its twin. |
| `body` | none | `Body(material, wet, floor)`, `KatalystDsl.kt:267-272` | Katalyst-only. No orbit-specific code in the effect (section 5). |
| `vowel` | none | `Vowel(vowel, wet, floor)`, `KatalystDsl.kt:290-295` | Katalyst-only. Same. |
| `phaser` | none | `Phaser(rate, wet, center, sweep, floor)`, `KatalystDsl.kt:350-357` | Katalyst-only. Same. |
| `eq` | none (a planned master eq is `master-dsl-followups.md` section 5) | `Eq(sections)`, `KatalystDsl.kt:432-435` | Katalyst-only. **Stale KDoc:** `KatalystDsl.kt:417-419` says "The master puts its EQ after the reverb and before the dynamics"; the master has no eq stage. |
| `duck` | none | `Duck(orbit, depth, attack)`, `KatalystDsl.kt:404-409`; run OUTSIDE the list in the cross-orbit pass (`Cylinders.kt:112-116`), last declaration wins (`KatalystChainBuilder.kt:223-226, 260-278`) | Katalyst-only and POSITION-BOUND (needs another orbit's mix as its sidechain). |

The defaults of the two DSLs' shared stages are already one set of constants (`SendEffectDefaults.kt`), pinned by
`SendEffectDefaultsParitySpec` and `KatalystDefaultsSyncSpec`.

### 1.2 The script doors (klangscript-libs)

| | Master | Katalyst |
|---|---|---|
| Object | `@KlangScript.Object("Master")`, `stdlib/KlangScriptMaster.kt:34-78`: `build(configure)` `:49-51`, `default()` = `MasterDsl.default` `:66-67`, `invoke(configure)` `:76-77` | `@KlangScript.Object("Katalyst")`, `stdlib/KlangScriptKatalyst.kt:41-128`: `build(configure)` `:58-60` (no lambda = the EMPTY chain), `classic()` `:88-89`, `param(name, default, description)` `:116-118`, `invoke` `:127-128` |
| Builder | `MasterBuilder(node: MasterDsl)`, `stdlib/MasterBuilders.kt:37-39` | `KatalystBuilder(node: KatalystDsl)`, `stdlib/KatalystBuilders.kt:52` |
| gain | `gain(gain: Double = 1.0)` `:45-46` | `gain(gain: IgnitorDslLike = 1.0)` `:389-391` |
| reverb | `reverb(wet?, size?, lowpass?)` `:106-120`, flat | `reverb(wet?, size?, lowpass?)` `:234-250`, flat; same order and defaults |
| delay | `delay(wet?, time?, feedback?, configure)`, `cap` on `MasterDelayBuilder` `:135-160` | `delay(wet?, time?, feedback?, configure)`, `cap` on `KatalystDelayBuilder` `:202-220, 422-427`; same shape |
| limiter / compressor | `limiter(threshold?, ratio?, knee?, attack?, lookahead?, release?)` `:73-92`, flat | `compressor(threshold?, ratio?, knee?, attack?, release?)` `:305-323`, flat |
| Katalyst-only | | `classic()` `:76-77`, `body` `:135-151`, `vowel` `:172-188`, `phaser` `:264-284`, `duck` `:342-359`, `eq` `:367-375` |

Door parity specs: `KlangScriptMasterBuilderSpec.kt` (65 master mentions), `KlangScriptKatalystDoorParitySpec.kt`.

`Master()` (no lambda) = `Master.default()` = unity. **`Katalyst()` (no lambda) is already the empty chain**
(`KlangScriptKatalyst.kt:58-60`), so `master(Katalyst())` is the drop-in for `master(Master.default())`.

### 1.3 The sprudel doors

- `.master(...)`: `sprudel/src/commonMain/kotlin/lang/lang_master.kt`, four forms, all taking `MasterDsl`:
  top-level carrier `master(dsl)` `:58-60` (a control event, `control = true`), `SprudelPattern.master` `:82-83`,
  `String.master` `:96-97`, `PatternMapperFn.master` `:111-112`; the stamp `applyMaster` `:27-28` writes
  `MasterValue.Dsl`. `@scope master` `:53, :77`.
- `.katalyst(...)`: `lang/lang_katalyst.kt`, the same four forms `:81, :119, :133, :148`, writing one
  preallocated `KatalystValue.Dsl` per door (`:40-41`). REPLACES like `master()` (`:95`).
- `katp(key, value)`: `lang_katalyst.kt:158-298`, writes one `katalystParams` slot.
- The bus doors that write `katalystParams` slots (via `katalystParamsOrNew` / `putKatalystParam`,
  `SprudelVoiceData.kt:1133, 1159-1162`): `lang_effects_reverb.kt`, `lang_effects_delay.kt`,
  `lang_dynamics_compressor.kt` (e.g. `:48-136`), `lang_dynamics_orbit.kt` (duck, `:209-241`),
  `lang_effects_modulation.kt` (phaser), `lang_effects_body.kt`, `lang_effects_vowel.kt`. They write the ORBIT's
  slots; nothing writes a master slot.
- The event model: `SprudelVoiceData.master: MasterValue?` `:144-148`, merged `:773, :823`, denormalized in
  `toVoiceData` `:897-901` (`MasterValue.Dsl -> m.master.uniqueId()`, recomputed per event; the Katalyst side
  memoizes in `KatalystValue.Dsl.name`, `KatalystValue.kt:40`), written `:952`; `SprudelPatternEvent.master`
  `SprudelPatternEvent.kt:39`.

### 1.4 The Kotlin doors

`MasterDsl.of(vararg MasterStageDsl)` (`MasterDsl.kt:48`), `KlangScriptMaster.build { it.gain(2.5).limiter() }`
(the builder extension functions are plain Kotlin), sprudel `master(dsl)` / `.master(dsl)`. Katalyst:
`KatalystDsl.of(...)`, `KlangScriptKatalyst.build { ... }`, `.katalyst(dsl)`. Both are two-door by construction
(one `@KlangScript.Function` per builder function).

---

## 2. The DSP classes per stage

No stage duplicates DSP. Both hosts run the same classes in `audio_be/effects/`; what differs is the HOST code
around them. (There is no `CompressorCore`; the shared compressor DSP is `effects/Compressor.kt`.)

| Stage | Master host (`master/MasterChain.kt`) | Katalyst host (`cylinders/katalyst/`) | Shared core |
|---|---|---|---|
| gain | a `MasterFx` lambda, `left[i] *= gain` (`MasterChain.kt:271-287`) | `KatalystGainEffect` over `KnobGlide.advanceScaled` (`KatalystGainEffect.kt:73-144`, `KnobGlide.kt:137-163`) | the settled path is `target[i] = source[i] * end` (`KnobGlide.kt:145-150`): the same multiply |
| reverb | `buildReverb`: `fillSend` (bus * wet) then `TailCeiling.observe` then `Reverb.process(send, bus)` (`MasterChain.kt:315-352, 420-430`) | `KatalystReverbEffect` Off/Active/Draining (`KatalystReverbEffect.kt`), Active: `wetGlide.advanceScaled(feed, mix)`, `observe`, `unit.process(feed, mix)` | `Reverb`, `TailCeiling`; **raw-bits parity already proven** for 30 blocks: `SendEffectDefaultsParitySpec.kt:288-298` via `renderParity` `:123-153` |
| delay | `buildDelay`, same send trick (`MasterChain.kt:367-417`) | `KatalystDelayEffect` Off/Active/Draining, `configure` `:489-526`, Active `:322-351` | `DelayLine`, `TailCeiling`; raw-bits parity `SendEffectDefaultsParitySpec.kt:220-238` |
| limiter / compressor | `Compressor(sampleRate, thresholdDb, ratio, kneeDb, attack, release, lookahead)` built once (`MasterChain.kt:289-302`), `process` per block (`:208`) | `KatalystCompressorEffect`: ONE `Compressor(sampleRate)` (`:107`) written through setters, Off/Engaged/Fading, knob glides; settled blocks call `c.process` (`:376-388`) | `Compressor`. The KDoc claims setters give "the same doubles as a freshly built instance on the classic path", pinned by spec rows against a fresh bare `Compressor` (`KatalystCompressorEffect.kt:97-106`). The lookahead path exists only in the constructor (`Compressor.kt:139-157`) and `processGliding` is classic-path only (`Compressor.kt:364-365`). |
| body / vowel | none | `KatalystBodyEffect`, `KatalystFormantEffect` over `KatalystFilterSwap` + `ResonatorBank` | `ResonatorBank`, `SvfBPF` |
| phaser | none | `KatalystPhaserEffect` | `Phaser` / `PhaserCore` |
| eq | none | `KatalystEqEffect` | `EqCore` |
| duck | none | `KatalystDuckEffect` | `Ducking` |
| the swap | `MasterBus` + `Crossfade.blend` (`MasterBus.kt:324-354`) | `Cylinder` + `Crossfade.rampDown` / `rampUpAndAdd` / `blendHeld` (`Cylinder.kt:581-636, 693-757`) | `Crossfade` (`audio_be/.../Crossfade.kt`, `XFADE_SECONDS` 0.06), `KnobGlide` |

---

## 3. How parameters reach each

**Katalyst (orbit).** Every knob is a `KatalystKnob` (`KatalystSlots.kt:250-287`): a `Param` knob reads its slot
from a `Map<String, Double>?`, a `Constant` keeps its number, `null` map = the authored default (`:278-286`). The
map is the orbit OWNER voice's `katalystParams` (wire field `VoiceData.katalystParams`, `VoiceData.kt:66`), taken
through the lease in `Cylinder.updateFromVoice` (`Cylinder.kt:375-435`: `resolveParams` `:387`, `applyParams`
`:424`, the outgoing chain too while it FADES, not while it drains, `:431-433`). `KatalystChain.applyParams`
(`KatalystChain.kt:191-197`) re-resolves only when the map INSTANCE changes (`:215-227`) and re-writes every block.
A chain can be configured with no owner: `applyParams(null)` (`KatalystChain.kt:10-20`), used by `beginFade`
(`Cylinder.kt:1074-1087` region, `next.applyParams(ownerParams)`).

**Master.** No parameter channel at all. A master knob is a `Double` in the DSL, read ONCE at chain build
(`MasterChain.build`, `MasterChain.kt:187-241`) and frozen into the stage closures. The only runtime event is a
chain SWAP by name: `VoiceScheduler.kt:587` (`head.data.master?.let { masterBus.requestSwap(it) }`), applied even
for a late event (`:582-586`). A different number is a different `MasterDsl`, hence a different `uniqueId()`
(`MasterDslIdentity.kt:33-35`), a `RegisterMaster`, and a 60 ms crossfade.

**What would fill a chain's `Param` slots at the output.** Today nothing could: no map exists on the master path.
The three candidates are decision (b), section 8. With b1, `chain.applyParams(null)` per block: the resolve is
gated (`null === null` after the first call, `KatalystChain.kt:216`), so the steady cost is one `apply()` per
stage per block (a virtual call writing numbers already in hand; body/vowel short-circuit an unchanged config,
the compressor compares `settings !== applied`, `KatalystCompressorEffect` `configure`).

---

## 4. Every place that knows "master" as a type

"Change" = what happens if `.master()` takes a `KatalystDsl` and the Master DSL retires (the target of sections
8 and 9).

### audio_bridge

| Place | What it is | Change |
|---|---|---|
| `MasterDsl.kt:1-174` | `MasterDsl`, `MasterStageDsl` (4 variants) | **retires** |
| `MasterValue.kt:22-29` | authoring reference Named / Dsl | **retires**; `KatalystValue` (`KatalystValue.kt:22-47`) serves both positions and brings its memoized name |
| `MasterDslIdentity.kt:21-35` | `MasterDsl.uniqueId()`, names `master-N` | **retires**; `KatalystDsl.uniqueId()` (`KatalystDslIdentity.kt:21-35`), names `katalyst-N`: one namespace for both positions |
| `constants/MasterLimiterDefaults.kt:17-51` | `LIMITER_*` (house and authored), `AUTHORED_LIMITER_*` | `LIMITER_*` stay (the house limiter reads them, `MasterStage.kt:120-128`); `AUTHORED_LIMITER_ATTACK_SECONDS` stays if the `limiter` door exists (d3); `AUTHORED_LIMITER_LOOKAHEAD_SECONDS` goes under a3 |
| `VoiceData.kt:115-127` | `master: String?` | **stays** (a name, now in the Katalyst namespace); KDoc `:116-121` rewritten |
| `VoiceData.kt:147` | `control` KDoc names master | KDoc only |
| `KlangPatternEvent.kt:38-45` | `val master: MasterValue?` | becomes `KatalystValue?` |
| `infra/KlangCommLink.kt:107-118` | `Cmd.RegisterMaster` (`register-master`) | **retires**; a master chain is announced with `RegisterKatalyst` (`:120-130`) into the one Katalyst registry. KDoc `:124` |
| `KatalystDsl.kt:40-42, 109, 219, 299, 318, 363, 417-419, 467`; `KatalystValue.kt:18`; `KatalystDslIdentity.kt:12` | KDoc cross-references | rewritten |
| KSP codec (generated, not in git): `audio_bridge/build/generated/ksp/js/jsMain/kotlin/io/peekandpoke/klang/audio_bridge/wire/WireCodecGenerated.kt:1829, 1845, 1919-2000` | `encode_/decode_MasterDsl`, `MasterStageDsl_*`, the `register-master` arm | vanish on regeneration, no hand edit |

### audio_be

| Place | What it is | Change |
|---|---|---|
| `master/MasterChain.kt:41-432` | `MasterFx`, `MasterChain`, `build` | **retires**; `KatalystChainBuilder.build` + `KatalystChain` serve (`MAX_LOOKAHEAD_SECONDS` `:168` goes with a3) |
| `master/MasterRegistry.kt:18-43` | name to `MasterDsl`, per-playback fork | **retires**; the bus looks names up in the engine's `KatalystRegistry` fork (`PlaybackEngine.kt:175`, `katalystRegistry`), keyed lookups via `findByKey` (`KatalystRegistry.kt:265-269`) |
| `master/MasterBus.kt:50-401` | the output host: cache, swap, tail | **rewritten** as the host of a `KatalystChain` at the output (section 6); `register()` `:165-178` goes (registration is registry-only, the chain is built on request like the cylinder's `chainFor`, `Cylinder.kt:1136-1158`) |
| `AudioBackendContext.kt:12-13, 35-36, 106` | `masterRegistry` | **retires** |
| `KlangAudioRenderer.kt:10, 36` | `masterRegistry` accessor | **retires** (`:27` the house `MasterStage` stays) |
| `PlaybackEngine.kt:55` | `registerMaster(name, MasterDsl)` | **retires** (`registerKatalyst` `:64` serves both) |
| `PlaybackEngine.kt:78-101` | fast path when `!masterBus.isActive`, else the bus path | stays; `isActive` must mean "the output chain has a stage that can change a sample" (section 7, risk R2) |
| `PlaybackEngine.kt:143, 155, 184-190` | `releaseAll`, `isRinging`, construction with `context.masterRegistry.fork()` | the bus gets the engine's `katalystRegistry` fork instead |
| `PlaybackEngineDispatcher.kt:90-91` | the `RegisterMaster` arm | **retires** |
| `voices/VoiceScheduler.kt:14, 43, 47, 587` | `masterBus.requestSwap(name)` | unchanged (a name) |
| `jsMain/JsAudioBackend.kt:298` | forwards `RegisterMaster` | **retires** |
| `Crossfade.kt:14-17, 29-39` | KDoc: the master blends outputs, the cylinder ramps inputs | KDoc rewritten when the master adopts the orbit law (decision f) |
| `cylinders/Cylinder.kt:67, 438, 1039, 1130, 1176`; `KatalystChain.kt:47`; `KatalystChainBuilder.kt:253`; `KatalystDelayEffect.kt:25`; `KatalystReverbEffect.kt:21`; `KatalystRegistry.kt:15`; `KatalystGainEffect.kt:20-21, 29-31` | KDoc cross-references to `MasterBus` / `MasterChain` / `MasterStageDsl` | rewritten |
| `MasterStage.kt:30, 70-81` | KDoc: "shares its character with `MasterStageDsl.Limiter`" | rewritten (the house stage itself stays) |

### klang (the frontend runtime)

| Place | Change |
|---|---|
| `InlineDslRegistrar.kt:10, 15-16, 42, 60-65, 92, 103-106, 133-136` | the `masters` announce-once registry and `RegisterMaster` go; the sweep collects `KatalystValue.Dsl` from `it.master` AND `it.katalyst` into the one `katalysts` registry (keyed on the memoized name, `:68-75`) |
| `KlangOfflineRenderer.kt:72, 105` | the `masters = masterRegistry` argument goes |
| `KlangPlaybackContext.kt:57` | unchanged (`overWire`) |
| `AnnounceOnceRegistry.kt:16-20`, `KlangPatternScheduler.kt:399` | KDoc / comment |

### sprudel

`lang/lang_master.kt:11-12, 27-28, 58-60, 82-83, 96-97, 111-112` (the four doors take `KatalystDsl`, stamp a
`KatalystValue.Dsl` preallocated per door like `applyKatalyst`); KDoc examples `:42, :71, :89` become
`Katalyst(k => ...)`. `SprudelVoiceData.kt:12-13, 144-148, 773, 823, 897-901, 952, 999` (`master:
KatalystValue?`, denormalized to `k.name`). `SprudelPatternEvent.kt:10, 39`. `lang_katalyst.kt:29, 48, 95` and
`lang_effects_delay.kt:28, 81`, `lang_effects_reverb.kt:28, 106` (KDoc mentions only).

### klangscript-libs / klangscript / UI

| Place | Change |
|---|---|
| `stdlib/KlangScriptMaster.kt` (whole) | **retires**: the global `Master` leaves the stdlib (a loud break for user code: release note) |
| `stdlib/MasterBuilders.kt` (whole) | **retires**; `limiter(...)` moves to `KatalystBuilders.kt` (d3) |
| `index_libs.kt:10`, `klangscript-libs/CLAUDE.md:22`, `stdlib/KatalystBuilders.kt:37`, `KlangScriptKatalyst.kt:17, 33` | KDoc |
| `klangscript/.../intel/CompletionProvider.kt:85`, `ExpressionTypeInferrer.kt:103`, `NamedArgumentChecker.kt:227`, `runtime/Interpreter.kt:652, 665, 706`, `runtime/NativeInterop.kt:574`, `types/KlangDecl.kt:53` | comments that use `Master(...)` as THE example of a callable object: switch the example to `Katalyst(...)` |
| `klangscript-annotations/.../KlangScope.kt:41` (`MASTER`), `src/jsMain/kotlin/comp/KlangScopeLabel.kt:35, 47` | **stay**: "master" as a SCOPE (where it runs) is the position name and remains true |
| `src/jsMain/kotlin/pages/docs/KlangScriptLibraryDocsPage.kt:135` | comment |
| `klangui`, `klangscript-ui`, `audio_fe`, `audio_jsworklet`, `klangjs`, `klang-notebook` | no reference to the master DSL types (searched) |

### Songs, tutorials

Builtin songs: 6 (section 7). Frozen texts: 2 (section 7), syntax-only migration permitted by the frozen-song
exception (`src/jvmMain/kotlin/FrozenSongs.kt:31-37`). **No tutorial calls `master(...)`** (searched
`src/commonMain/kotlin/pages/docs/tutorials/`).

### Tests that name master types (file, count of "master" mentions)

audio_be: `master/MasterBusTest.kt` (117, 18 rows), `master/MasterChainSpec.kt` (40, 16 rows),
`master/LimiterLookaheadSpec.kt` (15, 11 rows), `master/MasterRingShelfSpec.kt` (29, 8 rows),
`master/MasterBusAdoptionSpec.kt` (15, 2 rows), `master/MasterDefaultsSyncSpec.kt` (20),
`master/MasterOrbitReverbParitySpec.kt` (28), `master/SendEffectDefaultsParitySpec.kt` (35),
`warehouse/EngineDisposalReturnSpec.kt` (10), `warehouse/LazyReverbSpec.kt` (14), `warehouse/CylinderShelfSpec.kt`
(3), `cylinders/CylinderFaderThroughZeroSpec.kt` (12), `cylinders/CylinderChainCrossfadeSpec.kt` (5),
`cylinders/katalyst/ClosedFormTailSpec.kt` (8), `cylinders/katalyst/KatalystGainEffectSpec.kt` (2),
`cylinders/KatalystChainRequestSpec.kt` (1). audio_bridge: `jsTest/WireCodecRoundTripSpec.kt:16, 22, 138-157, 243`
(the `VoiceData.master` rows `:269-276` stay), `KatalystDefaultsSyncSpec.kt:202, 224` (comments). klang:
`KlangOfflineRendererMasterTest.kt` (18), `KlangOfflineRendererKatalystTest.kt` (10), `jvmTest/InlineDslRegistrarTest.kt`
(11). sprudel: `LangMasterSpec.kt` (50), `LangKatalystSpec.kt` (2, comments), `LangFeedbackCapSpec.kt:23-24` (KDoc).
klangscript-libs: `KlangScriptMasterBuilderSpec.kt` (65, retires), `jvmTest/intel/AnalyzedAstTest.kt:1149-1150`.
klangscript: `jvmTest/intel/InvokeAnalysisTest.kt` (26), `commonTest/runtime/NativeObjectInvokeTest.kt:19` (KDoc).
klangscript-ksp: `DefaultValueExtractorTest.kt:173-174` is a source-TEXT fixture, unaffected.

### Docs and skills (the docs commit)

Root `CLAUDE.md` (retired list; guardrail row `:86` per decision a), `.claude/skills/review-loop/audio-constraints.md:20`,
`.claude/skills/dsl-design/SKILL.md:63, 103, 227`, `.claude/skills/klang-music-writing/ref/sprudel-reference.md:167-168, 194`,
`klangscript/MEMORY.md:69-71`, `klangscript/language-features/04-functions.md:184-193`,
`klangscript/ref/feature-catalog.md:49`, `audio/MEMORY.md:1570, 2651`, `docs/tasks/tutorial-curriculum.md:13-19, 195`,
`docs/tasks/master-dsl-followups.md` (sections 1, 5, 7), `docs/tasks/katalyst-master-configure-doors.md`
(`.master(m => ...)` becomes `.master(k => ...)`), `docs/plans/effect-state-machines.md` (status line and the
section 3 row), `docs/plans/future/signal-graph-engine.md` section 3 seed list, `docs/tasks/builtin-instruments.md`
row 12.

---

## 5. Position-bound features

- **The limiter lookahead.** Guardrail, root `CLAUDE.md:86`: "Deliberate engine exceptions a reviewer must not
  "fix": ... the master limiter lookahead is master-only." Same line in
  `.claude/skills/review-loop/audio-constraints.md:20`. Its record:
  `docs/tasks-archive/2026-09/20260927-master-limiter-lookahead.md` decision 1 (`:30-31`, "Lookahead is exposed on
  the master limiter ONLY") and section 3 (`:297-316`, a per-orbit lookahead shifts that orbit late) and the
  two-limiter table (`:366-371`: the authored one has lookahead "for parity", default off). Code: the house one,
  `MasterStage.kt:94, 104, 120-128` (5 ms, not authorable); the authored one, `MasterDsl.kt:110-127`,
  `MasterLimiterDefaults.kt:41-45` (default 0 "on purpose": per-playback latency desyncs this playback against
  the others), `MasterChain.kt:168, 300-301` (bounded at 50 ms because it allocates), `MasterBuilders.kt:69-91`.
  The mechanism is a constructor val (`Compressor.kt:73, 139-157`): it cannot be a slot or glide, and
  `KatalystCompressorEffect` builds its one instance without it (`:107`). **No song, frozen text or tutorial sets
  `lookahead`** (all three `limiter()` calls are bare or threshold-only: `ATruthWorthLyingFor.kt:103`,
  `Greensleeves.kt:73`, and `LangMasterSpec.kt:91` in a test).
- **duck.** Needs a sidechain buffer, another ORBIT's mix, resolved by `Cylinders.processAndMix`
  (`Cylinders.kt:112-116`) and handed through `KatalystContext.sidechainBuffer` (`KatalystContext.kt:29-33`).
  At the output, `MasterBus` never calls `processDuck`, so a declared duck is naturally inert: `KatalystChain`
  keeps the duck out of `serial` (`KatalystChain.kt:49-51, 91`) and only `processDuck` (`:246-248`) runs it. A
  meaning exists ("the whole playback pumps under orbit N", a real mastering move) but needs the output host to
  read an orbit's post-chain mix after `processAndMix`, plus a rule for which playback's orbit. Decision (c).
- **body / vowel / phaser / eq / compressor at the output.** Searched every Katalyst effect for orbit-specific code
  (`cylinder`, `orbit`, `lease`, `sidechain` outside comments): the only hits are the package name
  `cylinders.katalyst` and a `deniedRents` comment. They read and write `ctx.mixBuffer` only. They work at the
  output as they are.
- **Master-only host behaviour** (must survive in the output host, not in the chain):
  - first adoption at full weight before the engine's first block (`MasterBus.kt:108-127, 259-278`, ledger M1:
    without it every mastered song's downbeat fades in, up to 8.3 dB down);
  - the fast path when the chain is inert (`PlaybackEngine.kt:78-82`, `MasterBus.isActive` `:145`);
  - the empty default (`MasterDsl.kt:36-45`); a Katalyst at the output defaults to the EMPTY chain, never
    `classic` (the orbit default);
  - NO deactivation or reset on silence (the cylinder resets its chain when silent, `Cylinder.kt:862-918`; the
    master never did, and resetting a limiter envelope after a gap would change the next bars). Therefore the
    output host is NOT a `Cylinder` without voices;
  - the tail keep-alive `isRinging` (`MasterBus.kt:148-153, 364-383`) and the engine hold
    `MAX_MASTER_TAIL_HOLD_SECONDS` (`PlaybackEngine.kt:167`).
- **Orbit-only host behaviour** (stays on `Cylinder`): the owner lease and `ownerParams` (`Cylinder.kt:351, 358`),
  the classic shortcut (`chainFor` `:1136-1158`, `dsl.stages == KatalystDsl.classic.stages`), deactivation, the
  duck handover (`handOverDuck` `:1102-1124`, `processDuck` `:693-757`).
- **The package name.** The chain lives in `audio_be/.../cylinders/katalyst/`; at the output that name lies a
  little. A package move is audio-inert and can wait for the graph plan.

---

## 6. The chain swap

### 6.1 `Cylinder`: the five fields, every read and write (`audio_be/src/commonMain/kotlin/cylinders/Cylinder.kt`)

| Field | Declared | Written | Read |
|---|---|---|---|
| `outgoing: KatalystChain?` | `:173` | `:809` null (startNewLife), `:678` null (retireOutgoing), `:1067` = leaving (beginFade) | `:208, :210` (isFading / isDraining seams), `:266` (deniedRents), `:432` (owner configures it while fading), `:498` (requestChain: slot taken), `:596, :607` (processEffects), `:808` (retire it), `:882` (tryDeactivate refuses), `:952` (installPending: slot taken), `:1172` (evictIfNeeded never evicts it) |
| `draining: Boolean` | `:180` | `:664` true (beginDrain), `:679` false (retireOutgoing), `:810` false (startNewLife), `:1068` false (beginFade) | `:208, :210`, `:431` (no owner writes while draining), `:598` (fade-to-drain edge), `:615` (drain branch) |
| `duckingOut: KatalystDuckEffect?` | `:189` | `:411` null (late-duck correction), `:660` null (beginDrain), `:684` null (retireOutgoing), `:751` null (processDuck at fade end), `:811` null (startNewLife), `:1123` = leavingDuck (handOverDuck) | `:249` (`duck` getter: Cylinders resolves the sidechain from it), `:401` (late duck), `:698` (processDuck) |
| `duckFadingIn: Boolean` | `:197` | `:661` false, `:680` false, `:716` false (fade end), `:812` false, `:1112` = arrivingDucks | `:700` (processDuck ramp-in branch) |
| `pendingKey: String?` | `:141` | `:473, :485` null (same chain requested), `:493` = key (unknown name), `:500` = key (slot taken), `:507` null (last intent wins), `:962` null (installPending consumed), `:1190` null (selectClassicChain) | `:536` (pollPendingChain), `:950` (installPending) |

Also on the swap: `fade: Crossfade` `:201` (`restart` `:1069`; `rampDown` `:632`; `rampUpAndAdd` `:635`;
`blendHeld` `:708, :737`; `isComplete` `:598, :715, :744`; `isAtStart` `:409`), `fadeBuffer` / `fadeContext`
(`:288, :307-310`), `duckFadeBuffer` (`:295`).

**Derived states.** Idle = `outgoing == null`; Fading = `outgoing != null && !draining`; Draining = `outgoing != null
&& draining`. Invariants that hold today (read, not tested as such): `draining` implies `outgoing != null`;
`duckingOut != null` or `duckFadingIn` implies Fading (both cleared on every exit from Fading: `:660-661` into
Draining, `:680, :684` into Idle, `:716, :751` at the ramp's end, `:811-812` on a hard cut). `pendingKey` is
ORTHOGONAL: it can be set in Idle (unknown name, `:493`) and in Fading or Draining (slot taken, `:500`).

**Transitions.**

| From | Event | To | Where |
|---|---|---|---|
| Idle | request, known, orbit inactive | Idle (instant `install`, lease reset) | `:510-515, :998-1028` |
| Idle | request, known, orbit sounding | Fading (`next.reset()`, `fade.restart()`, resolve, `handOverDuck`, `applyParams(ownerParams)`) | `:509, :1050-1088` |
| Idle | request, unknown name | Idle + parked key | `:490-495` |
| Fading, Draining | request (not the chain in service) | unchanged + parked key, last wins | `:498-503` |
| any | request for the chain in service | unchanged, parked key dropped | `:471-487` |
| Fading | first block after the ramp ran out | Draining if `leaving.hasTail()`, else retire, Idle | `:596-600, :656-670` (at the START of the next block, so the previous block's duck pass saw the ramp's last weights) |
| Draining | a block where `!leaving.hasTail()` | Idle (retire, units back) | `:615-625, :673-685` |
| Idle + parked key | per-block poll, resolvable | Fading (sounding) or instant install (idle) | `:535-541, :949-973` |
| Idle + parked key | `tryDeactivate` on silence | instant install instead of the reset | `:908-912` |
| any | `retire` / `adopt` (startNewLife) | Idle, hard cut, leaving retired | `:767-829` |
| Fading | owner claims in the swap's first block and the arriving chain ducks | takes the envelope over (duckingOut cleared) | `:401-412` |

### 6.2 The master's swap (`audio_be/src/commonMain/kotlin/master/MasterBus.kt`)

Fields: `fade` `:86`, `chains` (cache, units kept while cached) `:89`, `unity` `:91-97`, `current` `:100`, `previous`
(the outgoing chain) `:103`, `currentName` `:106`, `hasRendered` `:118`, `pendingName` `:130`, `scratch` `:133`,
`ringing` `:136`, `silentBlocks` `:139`.

Transitions: request for the current name drops the queue (`:241-246`); for the queued name, no-op (`:248-250`);
an UNKNOWN name is dropped, not latched (`:252`, the carrier re-emits every cycle); mid-fade, queued (`:254-257`);
before the first rendered block, adopted at full weight (`:270-278`); otherwise `beginFade` (`:280, :308-321`: resets
the incoming chain unless it is the chain that was just audible, so A to B to A carries A's tail). `process`
(`:324-354`): both chains get the SAME full input, `fade.blend` mixes the OUTPUTS, and at `fade.isComplete` the
outgoing chain is CUT (`:344-346`) and a queued swap starts in the same block (`:348-352`).

### 6.3 How they differ

| | Cylinder | MasterBus |
|---|---|---|
| Crossfade law | input ramp on the leaving chain + incoming output ramped up (`Crossfade.rampDown` / `rampUpAndAdd`) | output blend of two chains fed the same input (`Crossfade.blend`) |
| End of fade | Draining: the leaving chain rings out on silent input at full weight until `hasTail()` is false | cut (`MasterBus.kt:30-33`: "Still open for this host") |
| Duck | handover in three directions (`handOverDuck`, `processDuck`) | none |
| Unknown name | parked, polled per block | dropped (relies on re-emission) |
| Request while busy | parked, lands when the slot frees (after the DRAIN too) | parked, lands when the fade completes |
| Instant install | orbit inactive (`isActive == false`) | engine has never rendered (`hasRendered == false`) |
| Incoming reset | always (`next.reset()`, `Cylinder.kt:1063`) | unless it is the chain just audible (A to B to A) |
| Cache | 8, a cached chain is RETIRED (units back to the shelf) when it leaves service | 8, a cached chain KEEPS its units until evicted |
| Owner params | configures both chains while fading, not while draining | none |
| Fast path by name | raw-spelling compare, no allocation (`Cylinder.kt:471-475`) | `name.lowercase()` per request (`MasterBus.kt:239`), once per cycle per carrier |

### 6.4 One `SwapState` for both (sketch, per `effect-state-machines.md` sections 1 and 2)

A plain class next to `Crossfade` in `audio_be` (no framework, section 5 of the plan; it is a shared component
like `Crossfade`, not a shared state object), owned once by each host:

```kotlin
internal class ChainSwap(sampleRate: Int, blockFrames: Int) {
    private val fade = Crossfade(sampleRate)            // a resource: outlives the states
    private val leavingMix = StereoBuffer(blockFrames)  // today's fadeBuffer
    val leavingContext = KatalystContext(blockFrames, leavingMix)

    private sealed class State {
        abstract fun process(chain: KatalystChain, ctx: KatalystContext)
        abstract fun hardCut()                          // retire/adopt: drops the references it holds
        abstract val leaving: KatalystChain?            // for deniedRents, eviction, the owner write
    }
    private inner class Idle : State() { fun enter() { state = this } ... }        // owns nothing
    private inner class Fading : State() {
        var leaving: KatalystChain? = null              // a REFERENCE: dropped by the fade-end edge and hardCut
        fun enter(leaving: KatalystChain) { this.leaving = leaving; fade.restart(); state = this }
        // process: if fade.isComplete -> (host hook: duck fields cleared) then
        //   leaving.hasTail() ? draining.enter(leaving) : { retire(leaving); idle.enter() }, and run the new state;
        // else rampDown, leaving.process(leavingContext), chain.process(ctx), rampUpAndAdd
    }
    private inner class Draining : State() {
        var leaving: KatalystChain? = null
        fun enter(leaving: KatalystChain) { this.leaving = leaving; state = this }
        // process: leavingMix.clear(); leaving.process(leavingContext); chain.process(ctx); add;
        //   if (!leaving.hasTail()) { retire(leaving); idle.enter() }
    }
    val settled: Boolean get() = state === idle        // the precondition a caller reads (5c-11)
    fun begin(leaving: KatalystChain): Boolean          // REFUSED unless settled; the host parks the key
}
```

The four questions of plan section 2, answered for the swap:

1. **What outlives its states:** the `Crossfade` instance, the leaving mix buffer and context, the HOST's current
   chain, cache and parked key. No `enter` touches them except `Fading.enter` restarting the ramp.
2. **The record of a finished life:** the leaving reference (dropped on Draining to Idle, after `retire`), the
   duck handover data (dropped at the ramp's end), `retiredDeniedRents` carried BEFORE the retire
   (`Cylinder.kt:675-677`).
3. **The Idle precondition:** the leaving chain reports no tail, or the host guarantees silence (`startNewLife`,
   the hard cut).
4. **References and who drops them:** the leaving chain (Fading, Draining) and, on the cylinder, the leaving duck
   (Fading). The fade-end edge, the drain-end edge and `hardCut` drop them, so `hardCut` DISPATCHES (the delay's
   "enter Off without dispatching" shape would strand a chain; the plan's warning about the filter swap's `clear()`).

**Pending.** Recommended as the host's parked key (latest wins), re-offered by the host's per-block poll, with
the swap REFUSING a `begin` while not settled: the 5c-11 precedent (`KatalystBodyEffect.kt:41-48, 82, 142-147,
202`; plan section 3, "a state machine may REFUSE an event, and the refusal belongs in the table"). A `Pending`
STATE would have to exist beside Fading and Draining (a key parked behind a fade), which is a product of states,
not a state. The plan's table can then read "Idle, Fading, Draining; Pending is the host's parked key". Decision (e).

**The duck.** `duckingOut` and `duckFadingIn` die with Fading (6.1 invariants), so by rule 2 they belong ON Fading.
But the duck is cylinder-only and runs in a later pass (`processDuck`). Two clean options: Fading carries a nullable
`duckingOut` and a `duckFadingIn` flag that the master never sets; or they stay on `Cylinder` and are cleared by a
hook the swap calls at the ramp's end. The first answers rule 2 literally; the second keeps the master's swap free
of a concept it cannot have. Either is identity; the implementer picks with every host file open.

**Timing that identity depends on.** The Fading to Draining edge fires at the START of the block after the ramp's
last block (`Cylinder.kt:596-600`), because the duck pass of the ramp's last block still blends with that block's
weights (`Crossfade.blendHeld`, `Crossfade.kt:117-119`). `Fading.process` must therefore test `isComplete` FIRST and
then run the entered state's `process` in the same call.

**What the master gains on this swap (decision f):** input ramp and drain (the old master's room rings out instead
of being cut), parking of unknown names, eager retire of cached chains (the "reset unless just audible" rule and
the unit-holding cache go), the raw-name fast path. It keeps its own instant-adoption predicate (never rendered)
as the host's input to "install or fade".

### 6.5 What acceptance (plan section 4) would need

- (a) a one-off host-level harness: a `Cylinder` driven through every row of the 6.1 table (and the five duck
  directions of `CylinderChainCrossfadeSpec.kt:514-844`), deterministic input, RAW BITS per block, HEAD in a
  throwaway worktree against the tree, with one deliberate mutation shown to fail (candidate: the drain edge one
  block early). For the master commit, the same harness on `MasterBus` (A to B, A to B mid-fade C, A to B to A,
  unknown then late registration).
- (b) render rows: **no corpus song swaps a chain** (no song calls `katalyst(`, searched; every `master(` is one
  top-level carrier adopted before the first block, section 7). So (b) needs written minimal rows: a sounding
  orbit with reverb and delay swapped mid-song by `katalyst(...)` (reaches the drain), a duck swap row, and for the
  master a mid-song `master(...)` change with a wet room and a limiter. Plus the 8 master corpus rows for the
  output chain itself.
- (c) a counter in each `enter`, added and removed, to measure which edges the rows reach.
- (d) permanent: the identity spec (three preallocated states, a `currentState` seam, an identity collection),
  the refusal row (a `begin` while Fading or Draining is refused, the host parks, latest wins), the re-entry row
  (a request during the drain lands after it, continuously), the "finished life leaves no record" row (no leaving
  reference and no duck reference after Idle), each mutation-checked.
- (e) three timed renders each side; `audio_benchmark` has no orbit-stage case (plan section 4).
- (f) `CylinderChainCrossfadeSpec` (30 rows), `KatalystChainRequestSpec` (4), `CylinderShelfSpec`,
  `CylinderFaderThroughZeroSpec` unchanged in their assertions for the cylinder commit. For the master commit (f)
  cannot hold: `MasterBusTest` rows `:259` (ramp), `:289` (queue), `:539` (swap back) encode the blend and the cut,
  and change with the law.

---

## 7. Render identity

### 7.1 The rows that call `.master(...)`

| Row | Call | Stages |
|---|---|---|
| `src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt:103` | `master(Master(m => m.reverb(0.01, 3).gain(1.5).limiter()))` | reverb, gain, limiter (defaults) |
| `builtinsongs/DerSchmetterling.kt:449-451` | `master(Master(m => m.reverb(0.2, 7, 3500).gain(3.0)))` | reverb with lowpass, gain |
| `builtinsongs/Greensleeves.kt:73` | `master(Master(m => m.reverb(0.14, 6).gain(1.6).limiter()))` | reverb, gain, limiter (never engages, `master-dsl-followups.md` section 7) |
| `builtinsongs/IrishLamentTechno.kt:253` | `master(Master(m => m.reverb(0.05, 7).gain(1.1)))` | reverb, gain |
| `builtinsongs/StrangerThings.kt:79-82` | `master(Master(m => m.reverb(0.05, 9).gain(2.5)))` | reverb, gain |
| `builtinsongs/Tetris.kt:108` | `master(Master(m => m.gain(1.5)))` | gain |
| `src/jvmMain/kotlin/FrozenSongs.kt:500-502` (`derSchmetterling_2026_09_25`) | `m.reverb(0.2, 7, 3500).gain(3.3)` | reverb, gain |
| `src/jvmMain/kotlin/FrozenPieces.kt:463-465` (`derSchmetterling_2026_09_16`) | `m.reverb(0.2, 7, 3500).gain(3.5)` | reverb, gain |

Not using master: the other 8 builtin songs, `FrozenSongs.strangerThings_2026_07_03`. No row uses a master
`delay`, a `lookahead`, or changes its master mid-song. Every master is a top-level carrier, so the first request
lands in the engine's first block, before `markRendered` (`PlaybackEngine.kt:70-101`), and is adopted at full
weight (`MasterBus.kt:270-278`): **no master crossfade runs anywhere in the corpus.** The offline path registers
masters on the parent registry and builds lazily at the request (`KlangOfflineRenderer.kt:103-107`,
`MasterBus.kt:289-297`).

### 7.2 Why identity is predicted, stage by stage

- **gain:** settled `KnobGlide.advanceScaled` is `source[i] * end` (`KnobGlide.kt:145-150`), the master's
  `left[i] *= gain` (`MasterChain.kt:282-285`); the first configure snaps (`KnobGlide.kt:79, 90-95`). Identical.
- **reverb:** the Katalyst reverb stage and the master reverb stage are already proven RAW-BITS identical over 30
  blocks by `SendEffectDefaultsParitySpec.kt:288-298` (orbit via the classic chain's slots, master via
  `MasterChain.build`). A constant-knob chain takes the same path (`KatalystKnob.resolve` returns early for a
  non-`Param`, `KatalystSlots.kt:278-280`; `sendStageRuns` is true for an authored wet above 0,
  `KatalystChainBuilder.kt:351-356`; the size goes through the same `Reverb.normalizeSize`,
  `KatalystSlotWriters` reverb `gate()`).
- **limiter as a Katalyst compressor with the limiter numbers:** the settled path is `c.process`
  (`KatalystCompressorEffect.kt:384-388`), first life is instant (`fresh`, `:403-405`, `Off.switchOn`). Equality of
  "constructor with values" and "default instance plus setters" is CLAIMED by the KDoc and its spec rows
  (`KatalystCompressorEffect.kt:97-106`); not independently verified here beyond reading the setters' names.
  The corpus render proves it for ATruthWorthLyingFor (the one row where the limiter engages; Greensleeves' never
  does).
- **order:** the builder keeps list order (`KatalystChainBuilder.kt:93-258`); the songs' order reverb, gain,
  limiter is preserved.

### 7.3 Risks to identity (UNVERIFIED until rendered)

- **R1 rent order.** The master's reverb unit was rented at request time (in `scheduler.process`, before any orbit
  processed); a Katalyst reverb rents at its first `configure`, in the first `MasterBus.process`, after the
  orbits. Which pool unit goes where changes. Identical only if every rentable unit is in the same state (fresh or
  fully zeroed). UNVERIFIED; the render decides.
- **R2 the fast path.** `MasterChain` drops a unity gain and inaudible sends at build, so `Master(m => m.gain(1.0))`
  keeps the fast path; a `KatalystChain` keeps the stage. The bus path is identical for a single playback
  offline (the target starts at zero); live, with several playbacks, the summation order changes in the last bit.
  No corpus row is affected. An `isEmpty`-style test on the output chain (no serial stage) keeps the common case.
- **R3 the tail flag.** `isRinging` is throttled today (`MasterBus.kt:364-383`); a chain's `hasTail()` is a
  compare and can be read every block. Changes live engine disposal timing (earlier by up to 10 blocks), never
  the offline render (one engine, never disposed as idle: `KlangAudioRenderer.kt:15-25`, `isIdle` is read only by
  `PlaybackEngineDispatcher.kt:227`).

### 7.4 Is a sound change unavoidable?

**Not for the corpus**: identity is predicted for all 8 rows (R1 to be confirmed). Sound changes outside the corpus,
each nameable and none forced by the merge itself except the first two if chosen:

1. live master EDITS if the master adopts the orbit swap law (decision f): the old master's room and echoes ring
   out; a limiter or compressor in the leaving master is fed a ramped input (the bulge the `Crossfade` KDoc
   records, `Crossfade.kt:41-45`); the ring-out bypasses the new master's inserts (`Crossfade.kt:50-54`);
2. an authored lookahead stops existing (decision a3): no row uses it;
3. a master reverb or delay with wet in (0, 0.0001] now runs (inaudible);
4. a NaN reverb size or delay time on the master is OFF instead of the constant (unreachable from finite songs);
   as landed in C5 (2026-09-28, a named unification): also a non-finite reverb or delay WET switches the stage off
   (the shim substituted `REVERB_WET`/`DELAY_WET`), and a non-finite limiter knob falls back to the COMPRESSOR
   constants, not the limiter's (-20 dBFS, 4:1, 6 dB knee, 0.003 s attack), as it already did on an orbit. A song
   reaches a non-finite knob only by computing one in script (`Math.sqrt(-1)`, `Math.log(0)`; `1/0` throws);
5. an unknown master name is parked and lands at the next poll, not at the next re-emission (a race only).

---

## 8. The decisions for the maintainer

### (a) The authored limiter's lookahead

Background: the house limiter's 5 ms lookahead (`MasterStage.kt:94`) is untouched by every option. The authored
`limiter` exposes `lookahead` "for parity" with default 0 (per-playback latency desyncs this playback against the
others, `MasterLimiterDefaults.kt:41-45`). A Katalyst compressor builds its one `Compressor` without lookahead
(`KatalystCompressorEffect.kt:107`) and its glide path is classic-only (`Compressor.kt:364-365`).

- **a1, allowed only at the output.** A build-time knob (like `passes`) on the limiter door; the chain builder
  gets a position argument and honours it only at the output (0 on an orbit). Cost: a position parameter on
  `KatalystChainBuilder.build`, a second `Compressor` construction path in `KatalystCompressorEffect`, a "same knob,
  different meaning by position" recorded under the parity rule, the 50 ms allocation bound carried over. Keeps an
  opt-in feature nobody uses.
- **a2, a separate final stage outside the chain.** The output host owns an optional lookahead limiter after the
  chain. Needs a place to configure it: a field beside the chain on the wire, i.e. a new `MasterSettings`-like type,
  which is the Master DSL coming back through the side door. Matches the graph plan's "a property of the node before
  the output" (`signal-graph-engine.md` section 3), but that plan is future.
- **a3, retire the authored lookahead** (recommended). The only lookahead is the house one, on the sum, where it
  costs nothing musically (the archive's own argument, `20260927-master-limiter-lookahead.md:335-338`). No row uses
  it; the guardrail's substance (never a lookahead on a per-orbit or per-playback compressor) becomes simpler and
  stronger. Cost: reverses decision 1's "for parity" half; `LimiterLookaheadSpec` rows on the AUTHORED stage go,
  the house rows stay; the guardrail row and `audio-constraints.md:20` get one sentence ("the only lookahead is the
  house limiter's"). Won't-implement is a first-class outcome (Guideline "taste is also what you do not do").

### (b) What fills a chain's `Param` slots at the output

- **b1, nothing** (recommended). `applyParams(null)`: a `Param` is its default, a constant is a constant. Zero
  wire, zero doors. `Katalyst.classic()` at the output is transparent (every classic slot's default is off or
  unity, `KatalystDsl.kt:147-205`), `Katalyst.param("x", 0.5)` is 0.5 for ever. Prepares the graph at no cost: the
  graph's "named nodes that events configure" can add the channel later.
- **b2, the carrier's own `katalystParams`.** `VoiceScheduler.kt:587` hands `head.data.katalystParams` to the bus
  with the name. No new wire field; `master(k).katp("gain.gain", "<1 0.5>")` automates. Risk: a top-level
  `stack(...).reverb(0.3)` stamps slots onto the carrier too, so a master chain written with `Param`s named like
  orbit slots would silently move with the orbits; the map's lifetime (until the next carrier) needs a rule.
- **b3, a dedicated channel** (`VoiceData.masterParams` and a `mastp(...)` door on both surfaces with a parity spec
  and a regenerated golden). Clean but the most surface, and the graph would likely replace it.

### (c) Stages that exist on one side only

- **duck at the output:** c1 inert (built, never run; recorded in the `Duck` KDoc and pinned by a spec; zero code;
  recommended), c2 refused at the door (a throw breaks "coerce, never require"; a silent drop equals c1), c3 a
  meaning ("the playback pumps under orbit N"; needs the bus to read an orbit's mix after `processAndMix` and a rule
  for which playback; defer to the graph plan, where ducking is an edge, `signal-graph-engine.md` section 3).
- **body / vowel at the output:** allowed (recommended; nothing to build) or refused (needs a position check for no
  engine reason).
- **phaser / compressor / eq at the output:** allowed (recommended). The eq delivers `master-dsl-followups.md`
  section 5; the compressor is the dynamics the master never had besides the limiter.
- **limiter on an orbit** (the reverse direction, with d3): allowed, without lookahead; it is a compressor with
  limiter numbers.

### (d) How the master's `limiter` is spelled (further decision, found in this inventory)

- d1 a new wire variant `KatalystStageDsl.Limiter` (own defaults, room for a lookahead knob under a1): two wire
  words for one DSP.
- d2 no limiter word: songs write `k.compressor(-1, 20, 2, 0.001, 0.1)`; five numbers where `limiter()` stood.
- **d3 a builder door** `KatalystBuilder.limiter(threshold, ratio, knee, attack, release)` (recommended) appending a
  `KatalystStageDsl.Compressor` with `LIMITER_*` and `AUTHORED_LIMITER_ATTACK_SECONDS`. One DSP and one wire word;
  the musical word stays on the door; both doors in one `@KlangScript.Function`. The maintainer should confirm that
  "limiter" as a door over the "compressor" stage fits the one-word rule (a preset, not a second concept).

### (e) The `SwapState` shape

Three states plus the host's parked key (recommended, section 6.4) versus a literal four-state machine. Not a sound
question; the coordinator can settle it unless the maintainer wants the plan's wording kept.

### (f) Does the master adopt the orbit's swap law

- **f1 yes** (recommended): one mechanism, the drain closes `MasterBus.kt:30-33`'s open item, the cache stops holding
  units for chains nobody plays (the allocation-cleanup rule). A named sound change on live master edits only; one
  listening pair (a master edit over a wet room, and one over a limiter).
- f2 keep the master's output blend and cut as a second shape inside the shared swap: identity everywhere, but two
  laws in one class, which is not "converted once".

### (g) A request while the old chain drains

Today on the orbit it waits for the whole ring-out (`Cylinder.kt:498-500, 952-956`), up to seconds (a size-10
room is about 12.5 s). At the master under f1 a second master edit inside a long ring-out would wait as long.
Options: keep (identity, recommended for step 12, record the question), cut the drain when a new request arrives
(a click unless faded), or allow a second leaving chain (a list of draining chains: more state). Maintainer's call
by ear, later.

---

## 9. Proposed commit sequence

Each commit reviewed to a clean round; new tests mutation-checked (mandatory tier: engine and wire).

1. **C1, the cylinder's `SwapState` (identity).** `ChainSwap` beside `Crossfade`, three preallocated states, the
   leaving chain a reference on Fading and Draining, the duck handover data per 6.4, `pendingKey` stays the host's
   parked key, `begin` refused while not settled. Acceptance 6.5 (a) to (f) for the cylinder. Corpus identical
   (trivially: no song swaps). `effect-state-machines.md` row updated for the cylinder half.
2. **C2, the `limiter` door on `KatalystBuilder` and the compressor's `lookahead` knob (identity)** under d3 and
   decision (a): `lookahead` becomes a build-time knob of the Katalyst `compressor` stage, honoured at any position
   (an orbit then runs late by it), bounded by the existing lookahead ceiling so a chain install allocates within
   the known bound; its door-parity row on both surfaces and the constants test. The root `CLAUDE.md` guardrail
   ("the master limiter lookahead is master-only") narrows to the house limiter in this commit, dated. No song touched.
3. **C3, the output runs a `KatalystChain` (identity, behind a shim).** `MasterBus` builds a `KatalystChain` from a
   one-file `MasterDsl` to `KatalystDsl` translation in `audio_be` (Gain, Reverb, Delay to their twins; Limiter to a
   `Compressor` stage with its five numbers and its `lookahead` knob, per decision a), calls `applyParams(null)`
   and `process` on a `KatalystContext` over the engine's bus; its OWN swap untouched. `MasterChain` goes.
   Prediction: the 8 master rows identical (R1 decides), `SendEffectDefaultsParitySpec` and `MasterBusTest`
   unchanged in assertions except rows that inspect `MasterChain` internals. The shim is scaffolding and dies in C5.
4. **C4, the master on the shared swap (named sound change, live edits only).** `MasterBus` uses `ChainSwap` with its
   own "never rendered" predicate; drain, parking, eager retire, the raw-name fast path. Predicted: no corpus row
   moves; `MasterBusTest` swap rows rewritten to the new law; the master half of acceptance 6.5; a listening pair.
   `Crossfade` KDoc rewritten (one law now). The plan's section 3 row becomes CONVERTED.
5. **C5, the doors flip and the Master DSL retires (identity).** `.master()` takes `KatalystDsl` on all four sprudel
   forms; `KatalystValue` serves both positions; one Katalyst registry (`RegisterMaster`, `MasterRegistry`,
   `InlineDslRegistrar.masters`, `masterRegistry` on context and renderer go); `MasterDsl`, `MasterStageDsl`,
   `MasterValue`, `MasterDslIdentity`, `KlangScriptMaster`, `MasterBuilders`, the C3 shim and their specs go
   (a replaced surface is removed, not deprecated); the 6 songs and the 2 frozen texts migrate syntax-only
   (`Master(m => m.` to `Katalyst(k => k.`); a `limiter` call is mapped BY NAME, because the two doors' positional
   orders differ (Master: `..., attack, lookahead, release`; Katalyst since C2: `..., attack, release, lookahead`; no
   song passes five or more positional arguments today, C2 round 1); the codec regenerates; `WireCodecRoundTripSpec` updated. Prediction:
   corpus identical (the chain content is the same numbers, now as `Constant`s). If too large to review, split the
   backend registry move (5a) from the doors and songs (5b), each identity.
6. **C6, the docs sweep.** Section 4's doc list, the retired list in root `CLAUDE.md`, the guardrail row per decision
   a, `effect-state-machines.md` status line, `builtin-instruments.md` row 12, `signal-graph-engine.md` section 3
   (Katalyst and Master are one chain type now: tick that seed item), `master-dsl-followups.md` sections 1, 5, 7.

Order note: C1 and C2 are independent of each other and of C3. C4 needs C1 and C3. C5 needs C3 (and C2 for the
limiter songs).

### Risks

- R0 (found in test consolidation commit 5, 2026-09-28): the house `MasterStage` clip (both clamp branches, the
  `-1.0 -> -32767` boundary) is exercised by no spec through `MasterStage.process`; `KlangAudioRendererSpec`'s clip
  table tests a copy. Give it a real row in this step (a production seam for the clip, or a `MasterStage` row that
  drives the clip past the limiter).

- R1 rent order (7.3): the one identity risk for the corpus; the C3 render answers it.
- R2 the fast path (7.3): live multi-playback last-bit summation only.
- R3 the tail flag (7.3): live disposal timing only.
- R4 the M1 first adoption: if the shared swap drops the master's "never rendered" predicate, every mastered song
  fades in over 60 ms (up to 8.3 dB down on the downbeat, `MasterBus.kt:259-269`). Pin it with
  `MasterBusAdoptionSpec` through C4.
- R5 no silence reset at the output: a `Cylinder`-shaped host would reset the chain on silence and change the
  limiter envelope after gaps. The output host must not deactivate.
- R6 the drain wait (decision g) becomes a master behaviour under f1.
- R7 `KatalystSlots.coerce` builds an exciter on the audio thread at chain install (`KatalystSlots.kt:99-126`):
  already true on orbits, now also at the output; only for a non-leaf knob.
- R8 script break: removing `Master` from the stdlib breaks user code loudly ("unknown identifier"); release note.
  No saved song stores a `MasterDsl` value (songs are text; UNVERIFIED for any external notebook content).
- R9 the one Katalyst namespace: a chain used both on an orbit and at the output shares one registration and one
  name (intended; it is what the graph wants), and `Katalyst.classic()` at the output is transparent but is NOT
  short-circuited to a born-with instance there (the classic shortcut is the cylinder's).
