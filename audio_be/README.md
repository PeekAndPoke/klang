# Audio Backend (`audio_be`)

The Klangmotor's audio engine: voices, orbits (cylinders), the master bus, and the scheduler that plays
what the frontend sends over the wire.

Every voice is an Ignitor tree (phase 3 step 9, 2026-09-27): a built-in sound is `source.pregain().classic()`, a
sample voice runs the same shape over its PCM, and an authored instrument ends in `.classic()`. The pattern's voice
doors reach the tree as slots. The orbit's effects are its Katalyst chain.

The reference lives with the rest of the audio docs, so it is written once:

| Topic                                               | File                                |
|-----------------------------------------------------|-------------------------------------|
| Modules, the wire, playback engines                 | `audio/ref/architecture.md`         |
| `VoiceData`, the slot maps, sample metadata         | `audio/ref/data-model.md`           |
| The voice, `classic()`, pitch, lifetime and culling | `audio/ref/voice-synthesis.md`      |
| Orbit and master effects, mixing                    | `audio/ref/effects-mixing.md`       |
| Samples: loading, playback                          | `audio/ref/sample-management.md`    |
| Denormals, NaN guards, numerical rules              | `audio/ref/numerical-safety.md`     |
| Performance notes                                   | `audio/ref/performance.md`          |
| Where every file lives                              | `docs/audio-backend-file-map.md`    |

Start with `audio/CLAUDE.md`, or load the `/klangaudio-knowhow` skill.
