Base directory for this skill: /opt/dev/peekandpoke/klang/.claude/skills/klangaudio-knowhow

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
2. **`audio/MEMORY.md`** — current status, recent changes, lessons learned

**Then read only the ref file(s) relevant to your task:**

| Task                                                                         | Read                             |
|------------------------------------------------------------------------------|----------------------------------|
| Understanding overall architecture, data flow, comm-link, platform backends  | `audio/ref/architecture.md`      |
| Working with VoiceData fields, FilterDefs, ADSR, scheduled voices            | `audio/ref/data-model.md`        |
| Working on voice synthesis, oscillators, SynthVoice, SampleVoice, modulation | `audio/ref/voice-synthesis.md`   |
| Working on effects, Orbits mixing, KlangAudioRenderer pipeline               | `audio/ref/effects-mixing.md`    |
| Working on sample loading, decoding, caching (audio_fe)                      | `audio/ref/sample-management.md` |

## Testing

```bash
./gradlew :audio_bridge:jvmTest    # bridge types + comm-link tests
./gradlew :audio_be:jvmTest        # DSP engine + voice tests
./gradlew :audio_fe:jvmTest        # sample management tests
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `audio/MEMORY.md` after completing significant work.
- `audio_bridge` is the dependency root — all other audio modules depend on it.
- Real-time audio: no heap allocations in hot paths; block-based processing (128–256 frames/block).
