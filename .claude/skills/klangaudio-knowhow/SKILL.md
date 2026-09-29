---
name: klangaudio-knowhow
description: Use when working on the audio engine (audio_bridge, audio_be, audio_fe, audio_jsworklet), voice synthesis, Ignitor instruments, Katalyst effects, orbits and the master, sample loading, or needing audio architecture knowledge.
---

## What This Skill Does

Loads context for working on the Klang **audio subsystem** — a real-time multiplatform DSP engine
for live-coding music. The audio subsystem spans four Kotlin Multiplatform modules:

| Module            | Purpose                                                               |
|-------------------|-----------------------------------------------------------------------|
| `audio_bridge`    | Shared data contracts, message-passing infra, platform-agnostic types |
| `audio_be`        | DSP backend — synthesis, effects, mixing, voice lifecycle             |
| `audio_fe`        | Frontend — sample loading, decoding, caching                          |
| `audio_jsworklet` | Web Audio worklet thread entry point (JS only)                        |

Documentation lives in `audio/` (top-level docs dir, not a Kotlin module) and `audio/ref/`.

## Context to Read

**Always read first:**

1. **`audio/CLAUDE.md`** — dispatcher: module overview, key files, which ref to read next
2. **`audio/MEMORY.md`**: what is true now (state, rules in force, traps, open threads), short by design; the full dated record is `audio/ref/memory-history.md`, read only when you need the history of a decision

**Then read only the ref file(s) relevant to your task:**

| Task                                                                                                | Read                             |
|-----------------------------------------------------------------------------------------------------|----------------------------------|
| Understanding overall architecture, data flow, comm-link, platform backends                         | `audio/ref/architecture.md`      |
| Working with VoiceData fields, the `oscParams` / `katalystParams` slots, scheduled voices           | `audio/ref/data-model.md`        |
| A stage's gate: which knob value turns it off (the one home of the off values)                       | `audio/ref/off-values.md`        |
| Working on voice synthesis, `classic()`, oscillators, samples, modulation, voice lifetime           | `audio/ref/voice-synthesis.md`   |
| Working on effects, Orbits mixing, KlangAudioRenderer pipeline                                      | `audio/ref/effects-mixing.md`    |
| Working on sample loading, decoding, caching (audio_fe)                                             | `audio/ref/sample-management.md` |
| Numerical safety (NaN/Inf/subnormals), `SAFE_MIN`/`SAFE_MAX` choice, framework precedents (SC/JUCE) | `audio/ref/numerical-safety.md`  |
| Performance rules — no SAM Ignitors, no per-block alloc, Kotlin/JS hot-path patterns                | `audio/ref/performance.md`       |
| The Katalyst chain: its two hosts, where a knob comes from, how each stage switches and glides, swaps, drains | `audio/ref/katalyst.md`          |
| Proving identity (corpus renders, measurement floors) and test lessons                              | `audio/ref/verification.md`      |
| The history of a decision (the full dated record, not read by default)                              | `audio/ref/memory-history.md`    |

## Testing

```bash
./gradlew :audio_bridge:jvmTest    # bridge types + comm-link tests
./gradlew :audio_be:jvmTest        # DSP engine + voice tests
./gradlew :audio_fe:jvmTest        # sample management tests
```

## Critical Rules

- **NEVER use boxed types in audio modules.** The following types are banned in audio_be, audio_fe, audio_bridge,
  and audio_jsworklet: `Long`, `ULong` (emulated wrapper objects), `Byte`, `Short`, `UByte`, `UShort`
  (range-check overhead on every operation), `Char` (heap-allocated wrapper). Use `Int` for frame counts, buffer
  indices, loop counters, MIDI notes (max ~2.1B frames = ~12.4 hours at 48kHz). Use `Double` for time values.
  Convert banned types from external APIs to `Int`/`Double` at the boundary immediately.
- **Stateful Ignitors must be classes, not SAM lambdas.** `interface Ignitor` is intentionally NOT a `fun interface`.
  Anything with mutable cross-block state lives in a `private class XxxIgnitor : Ignitor` with explicit fields.
  See `audio/ref/performance.md` for the full rationale (closure-captured `var` becomes Kotlin/JS ObjectRef).
- Real-time audio: no heap allocations in hot paths; block-based processing, 128 frames per block, pinned (a tone parameter; never raised to speed up a render).
- `audio_bridge` is the dependency root — all other audio modules depend on it.

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- After completing significant work, keep `audio/MEMORY.md` short (restructured 2026-09-29, it had grown into a log): update the current-state section your change touched IN PLACE, add ONE line to its History list (date, a few words, the link to the task record), and put the narrative in the task record, which gets archived. Never append a dated essay to MEMORY.md.
