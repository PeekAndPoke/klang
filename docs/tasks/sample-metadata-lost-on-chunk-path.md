# SampleMetadata is dropped on the chunk path, so the browser and offline renders disagree

Status: **found 2026-08-31, NOT fixed. Needs the maintainer's ear, because fixing it changes what
the browser sounds like.** Found by a reviewer while reviewing the realtime-latency change; it is
pre-existing and unrelated to that change, which is why it was not folded into it.

## The bug

`SampleStore.addSample` builds the partial buffer without the metadata the chunk carries:

```kotlin
// audio_be/src/commonMain/kotlin/SampleStore.kt:94
val entry = (existing as? SampleEntry.Partial) ?: SampleEntry.Partial(
    req = req,
    note = msg.note,
    pitchHz = msg.pitchHz,
    sample = MonoSamplePcm(sampleRate = msg.sampleRate, pcm = DoubleArray(msg.totalSize)),
    //                                                  ^ no `meta =`, so SampleMetadata.default
)
```

`Cmd.Sample.Complete.toChunks` faithfully puts `meta = sample.meta` on **every** chunk
(`KlangCommLink.kt`), and the reassembler throws it away. `SampleMetadata.default` is
`anchor = 0.0, loop = null, adsr = null`.

## Why it is a host divergence and not just a dropped field

The **browser always chunks**: `JsAudioBackend.drainControl` explodes every `Cmd.Sample.Complete`
into `sampleUploadBuffer`, and the `Complete` command itself never reaches the worklet. So the
worklet's `SampleStore` only ever executes the `Chunk` branch.

The **JVM and the offline renderer do not**: `PlaybackEngineDispatcher.handle` routes
`Cmd.Sample.Complete` straight to `addSample`, which takes the `Complete` branch and keeps
`msg.sample` with its metadata intact.

`VoiceFactory` reads exactly the three fields that are lost:

| Field | Read at | What is lost in the browser |
|---|---|---|
| `meta.adsr` | `VoiceFactory.kt:297` (`mergeWith`) | the sample's embedded envelope |
| `meta.loop` | `VoiceFactory.kt:314` | the loop range, so a sustained instrument does not sustain |
| `meta.anchor` | `VoiceFactory.kt:339` | the start offset, so playback begins at the wrong place |

Metadata reaches the frontend for real: `SampleIndexLoader` → `getSampleMetadata()` →
`Samples.withMetadata`. SoundFont-backed sounds are the ones that carry it.

**Consequence to know about meanwhile:** an offline WAV render is currently **not** a valid
reference for browser playback of a sample that carries metadata. If someone reports "the browser
sounds different from the render" on a SoundFont instrument, this is why. Do not chase it as a
renderer bug.

## The fix, and why it is not a one-liner in practice

The code change is one argument (`meta = msg.meta`). What makes it a decision is that it is
**audible**: browser playback of every metadata-carrying sample changes the day it lands, in the
direction of matching the offline render. That wants a by-ear pass, not just a green suite.

Carry into the fix:

- `SampleChunkRoundTripSpec` (`audio_be/src/commonTest`) guards the splitter/reassembler pair but
  builds `MonoSamplePcm` with the **default** `meta`, so it cannot see this bug. The fix needs a
  metadata assertion added there, or it lands unguarded.
- Chunks carry `meta` on every chunk, not just the first, so the reassembler can take it from
  whichever chunk creates the `Partial`. Worth a thought about whether `meta` belongs on every
  chunk at all, or once on the stream.
