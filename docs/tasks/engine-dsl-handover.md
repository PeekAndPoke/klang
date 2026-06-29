# EngineDsl — handover: what's already wired FE→BE

> For the agent picking up the EngineDsl enhancements + the `EngineDsl` → `PipelineDsl` rename.
> Written end of Q2 2026, after the per-playback-engine + FE/BE state-placement work.
> Companion plan: `docs/tasks/engine-dsl.md`.

## TL;DR

The **whole FE→BE transport for a custom engine is built, per-playback, and fork-ready** — sending a
`Cmd.RegisterEngine(playbackId, name, dsl)` and a voice with `VoiceData.engine = name` resolves that custom
engine end-to-end **today**. The **only** missing piece is the **frontend application surface**: there is no
`.engine(EngineDsl)` that attaches an *inline* DSL to voices, so nothing calls the ready hook yet. Mirror the
inline-**oscillator** path — it's the exact analog.

## Already prepared (don't rebuild)

- **Wire** — `KlangCommLink.Cmd.RegisterEngine(playbackId, name, dsl: EngineDsl)` exists, `@WireFormat`,
  codec round-trips it (`WireCodecRoundTripSpec`, `IgnitorDslWireCodecSpec`). `VoiceData.engine: String?`
  (`VoiceData.kt:150`) is the per-voice reference (a **name**, resolved on the backend).
- **Backend — fork-ready, per-playback.** `audio_be/.../engines/EngineRegistry.kt` now has `parent`/`fork()`
  (mirrors `IgnitorRegistry`). Each `PlaybackEngine`'s `VoiceScheduler` forks it (`engineFork`); built-ins
  (`modern`/`pedal` from `AudioEngine`) live on the root, custom engines on the per-playback fork. The
  dispatcher routes `Cmd.RegisterEngine` → `engineFor(pid).scheduler.registerEngine(name, dsl)` → the fork.
  `VoiceFactory.get(data.engine)` resolves through the fork (custom locally + built-ins via parent). So a
  custom engine is **playbackId-scoped and freed with the playback** — no leak, no cross-playback clash.
- **Frontend registry — per-playback.** `klang/.../EngineRegistry.kt` lives on each
  `KlangPlaybackController` (stamped with the controller's `playbackId`). `registerOrLookup(dsl): String`
  returns the stable `EngineDsl.uniqueId()` name and fires `Cmd.RegisterEngine` once per unique DSL.
- **The ready hook** — `KlangPlaybackController.registerEngine(dsl: EngineDsl): String` is wired and waiting.
  **It currently has NO caller** — that's the dormant bit.

## What's missing (the application path — your job)

1. **Pattern surface for an inline DSL.** `lang_engine.kt:53` `fun SprudelPattern.engine(name: PatternLike)`
   only takes a **name** today (`.engine("pedal")`). Add an overload that takes an **`EngineDsl`** and stores
   the inline DSL on the event — exactly how `sound(IgnitorDsl)` stores `SoundValue.Osc(dsl)` on the event
   (`lang_tonal.kt:359`, test `LangSoundSpec.kt:132`). (You may want an `EngineValue.Dsl` carrier, or stash
   the dsl on `VoiceData` directly — your call.)
2. **Denormalize in `queryEvents`.** In `KlangPlaybackController.queryEvents` (`~:369-379`), the osc path
   pre-registers every inline osc then resolves it to its `uniqueId()` name in `toVoiceData`. Do the same for
   engines: detect inline-engine events → `registerEngine(dsl)` (pre-register) → set
   `VoiceData.engine = dsl.uniqueId()`. After that, the existing backend plumbing above just works.

**Template to copy = the inline-oscillator path:** `sound(IgnitorDsl)` → `SoundValue.Osc` →
`queryEvents` `registerIgnitor(it.osc)` → `toVoiceData` resolves `data.sound = osc.uniqueId()`. The engine
case is the 1:1 mirror (`registerEngine` + `data.engine = dsl.uniqueId()`).

## Rename heads-up (`EngineDsl` → `PipelineDsl`)

- Well-motivated: the word **"engine" is now claimed** by the per-playback `PlaybackEngine` /
  `PlaybackEngineDispatcher` layer (the DSP-isolation host). `EngineDsl` is really a **per-voice pipeline**.
  See memory `engine_dsl_misnamed`.
- There are **two** `EngineRegistry` classes — FE `klang/.../EngineRegistry.kt` and BE
  `audio_be/.../engines/EngineRegistry.kt` — plus `EngineDsl` in `audio_bridge`, `Cmd.RegisterEngine`,
  `VoiceData.engine`, `AudioEngine` (the built-ins enum), and the `.engine(...)` DSL. Rename coherently so
  "engine" disambiguates from `PlaybackEngine`. (Don't touch `PlaybackEngine*` — that layer keeps the name.)
- The per-playback `EngineRegistry.fork()` + per-controller FE registry are **new** (from the FE/BE
  state-placement work) — carry them through the rename; they're the reason custom engines are per-playback.
