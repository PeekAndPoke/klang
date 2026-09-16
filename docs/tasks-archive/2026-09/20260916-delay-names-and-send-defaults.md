# Delay names, and one set of defaults for delay and reverb on every surface

> Archived 2026-09-16. Status: **COMPLETE 2026-09-16.** Follow-up of
> [`20260916-reverb-naming-unification.md`](20260916-reverb-naming-unification.md).
> Decisions by the maintainer on 2026-09-16 (below). Priority: **SHOULD** (DSL hygiene, parameter parity).

## Why

An audit of the delay after the reverb unification found the doors already consistent (`wet`, `time`, `feedback`,
`cap` on sprudel and on the master builder, same scales, same on-threshold), and four smaller problems:

1. **Names drift below the doors.** Wire `MasterStageDsl.Delay.timeSeconds`, engine
   `KatalystDelayEffect.configure(timeSeconds, ...)`, `DelayLine.delayTimeSeconds` (repeats the class name) and
   `DelayLine.feedbackCap`, where every other layer says `time` and `cap`.
2. **Invalid numbers on an orbit keep the previous voice's settings.** A NaN feedback is dropped by the
   `DelayLine` setter, so the orbit keeps the previous owner's feedback; an infinite time is refused a ring and the
   orbit keeps its previous time. The master falls back to its defaults. The reverb and phaser already refuse this
   carry-over; the delay never got it.
3. **Defaults differ per surface.** The master stages default to a usable sound (delay wet 0.25, time 0.25, feedback
   0.3; reverb wet 0.25, size 5). On an orbit an unset slot is 0, so `delay(0.3)` and `reverb(0.3)` are silent and
   `delay(0.3, 0.25)` has no repeats.
4. **Editor labels do not match the door.** The delay editor says "Send" for `wet` and has no `cap` field; the
   reverb editor says "Wet (send)"; the reverb size editor says "Reverb Size".

## Decisions (maintainer, 2026-09-16)

| #  | Decision |
|----|----------|
| D1 | One word end to end: `time` and `cap` on the wire (`MasterStageDsl.Delay.time`) and in the engine (`KatalystDelayEffect.configure(time, feedback, cap)`, `DelayLine.time`, `DelayLine.cap`). |
| D2 | No carry-over: a non-finite value never leaves the previous voice's setting in place. |
| D3 | **The default is the same on every surface, and it is musical**: unset means a usable sound. Delay wet 0.25, time 0.25 s, feedback 0.3, cap 1.0; reverb wet 0.25, size 5, lowpass unset. One set of constants in `audio_bridge/constants`. |
| D4 | Applies to the reverb too. |
| D5 | Editor labels match the door's slot names. |
| D6 | (later the same day) **A sprudel `delay(...)` / `reverb(...)` call sets every slot**: the given ones, and every slot still unset takes the default, filled at WRITE time. A slot an earlier call set keeps its value; an event the call writes nothing to (a rest in a control pattern, a mapper on a never-set slot) is not filled; `merge` carries filled defaults like any set value; a zero send fills too. Readers return what was set, so `reverb.size` reads 5 after `reverb(0.3)` and nothing before any call. `VoiceFactory`'s fill stays as the wire contract for every non-sprudel producer. |

## Design

- **Constants.** `audio_bridge/src/commonMain/kotlin/constants/SendEffectDefaults.kt`: `DELAY_WET`, `DELAY_TIME_SECONDS`,
  `DELAY_FEEDBACK`, `DELAY_CAP`, `REVERB_WET`, `REVERB_SIZE`. Wire defaults, so they live in `audio_bridge` by that
  directory's rule. Consumers: the `MasterStageDsl.Delay`/`Reverb` field defaults, `MasterChain`'s fallbacks,
  `VoiceFactory`, the engine `Reverb` unit default size, and the sprudel editor tools.
- **Orbit meaning of "unset".** A voice that does not touch an effect sends nothing and configures nothing, as today
  (otherwise every voice would feed every orbit's delay and reverb). A voice that touches the effect (any of its
  slots set) gets the constants for every slot it left unset. The fill happens in ONE place, `VoiceFactory`, so every
  frontend that speaks the wire inherits it.
- **Invalid values (D2).** `VoiceFactory` and `MasterChain` treat a non-finite slot as unset, so it takes the default
  on both buses. `KatalystDelayEffect.configure` additionally refuses a non-finite time as off and a non-finite
  feedback or cap as the default, so a direct caller cannot carry the previous owner's value either (the reverb door
  already reads non-finite size as off).
- **Songs keep their sound.** A throwaway scan compared, for every event of every builtin song (600 cycles), tutorial
  block, benchmark case and playable doc example, the old and new effective delay and reverb settings. Only two
  shipped texts depend on today's zeros, and both get an explicit value that keeps what plays today:
  - `Sakura`: `delay(wet = 0.3, time = pure(1/8).div(cps))` gains `feedback = 0.0`.
  - `DerSchmetterling` count-in: `reverb("0.1")` on orbit 7 had no size and was silent; it gains `size = 0`.
  (The frozen copy `FrozenPieces` gets the same pin.) Four doc examples now repeat with the default feedback; their
  comments stay true. One tutorial code block changes shape, not sound: the `tut_SpaceAndDirt` finale's two delay
  calls merge into one, so deleting it in the bottom-up exercise is still audible.
- **Docs that teach the silent gate go.** `reverb` KDoc ("A bare `reverb(0.4)` is silent"), the music-writing
  reference, the `tut_SpaceAndDirt` lesson (its §2 builds on the silent bare send) and its KDoc notes, `tut_Layers`
  notes, `tutorial-curriculum.md` notes.

## Verification

- Door parity spec: the master stage and an orbit voice that touches the effect reach the DSP with the same values
  for every unset slot, through the real paths (`MasterChain.build`, `VoiceFactory` + `Cylinder`). Mutation-checked.
- No carry-over spec for the delay door (NaN feedback, NaN cap, infinite time). Mutation-checked.
- Golden voice-data corpus unchanged (the fill is engine-side; sprudel voice data stays raw).
- Full suites, JS compile, review loop.

## Result (2026-09-16)

- Review: round 1 (coding + audio) 1 MAJOR (the rewritten tutorial §2 claimed "every effect" works this way; false
  for `phaser`/`tremolo`, whose depth defaults to 0) and 9 MINOR; round 2 clean with 4 MINOR, applied as a batch
  (tutorial finale merged into one `delay(...)` call so the bottom-up exercise stays audible, `Reverb`'s own default
  goes through `normalizeSize`, the carry-over test asserts against the constants, editor `seed` KDoc narrowed).
  Rejected: pinning the default values to literals (review-loop rule 2026-08-28, no value-echo tests).
- Mutation-checked: orbit default fill (feedback, wet, size), non-finite reads as unset, every part of both touched
  gates, the delay door's non-finite feedback and time guards, the master feedback fallback.

## Resolved by D6

- The zero-send question: `delay(0)` writes a slot, so it fills; no exemption.
- The readers: they read what the call set, defaults included.
- Before D6 a static check found no song, tutorial or benchmark that reads, maps or merges these slots, so filling
  at write time changes nothing audible against the engine-side fill. Specs: `LangReverbSpec`, `LangDelaySpec`
  ("the call sets every slot"), mutation-checked (the combiner fill, the size fill, the rest guard, the feedback
  fill, keep-earlier-value).

