# Soundfont Looping Investigation

Looped soundfont instruments (e.g., gm_accordion, gm_violin, gm_organ) do not loop correctly.
Need to understand the soundfont data structure and how to translate it to the Klang engine.

## Status: ✅ FIXED 2026-09-02 — by-ear check owed (`docs/tasks/by-ear/README.md` §6)

Applied as proposed: `playhead0 = if (data.begin != null) startSample else 0.0`. Guard:
`SamplePlayheadStartSpec` (4 rows on a ramp PCM through the real `VoiceFactory`: attack plays first,
the loop still engages at `loopStart`, `anchor` no longer moves the start, `begin()` still wins).
Mutation-checked: reverting the fix turns three rows red; disabling the loop wrap turns the loop row
red. `meta.anchor` is now read nowhere on the playback path — it can be dropped from
`SampleMetadata` in a later tidy, or kept as informational.

**Round 2, 2026-09-03 — the accordion still did not loop, and it was a second defect.** Variant 0
for the accordion is JCLive: 0.13–0.39 s samples ending in 1–5 ms *single-cycle* loops, which
`getSampleMetadata`'s 50 ms heuristic rejected as "fake". Corpus census: that heuristic discarded
**2 698 of 5 959 loops (45 %)**. Removed — a loop is a loop whenever `loopEnd > loopStart`. And the
synthesized ADSR went with it: `ahdsr` is a boolean on all 6 476 zones and never a curve, and the
invented shapes fought the sample (the percussive one cut a 4 s guitar at 0.5 s). Every zone now gets
a transparent VCA (attack 0, sustain 1, release 50 ms) that the user's `.adsr()` merges over.

**The accordion's high notes being sharp is the DATA.** JCLive's declared roots are 0.4–1.4 st
below the recorded pitch (measured); FluidR3 is accurate. Maintainer's rule: no font-name handling
in code — the index order must be correct. Filed as `docs/tasks/future/soundfont-variant-curation.md`.

**Round 3, 2026-09-03 — still no loop in the browser, and THIS was the one hiding the other two.**
`JsAudioBackend.kt:288` converts every `Sample.Complete` into chunks before the worklet boundary,
regardless of size. Each chunk carries `meta` (`toChunks` puts it on all of them). The worklet's
`SampleStore` reassembled every chunked sample as `MonoSamplePcm(sampleRate, pcm)` and let `meta`
default to `{loop = null, adsr = null, anchor = 0}`. **In the browser, no soundfont had ever looped.**
Rounds 1 and 2 were computed correctly on the frontend and discarded at the boundary. The JVM backend
passes `Complete` in-process and never lost it — the offline renderer always looped, which is why the
code kept agreeing with the reasoning while the ear did not. The violin never looped either; its 1 s
samples simply outlast a normal note, and the accordion's 0.13–0.39 s ones do not.

Fix: one argument, `meta = msg.meta`, on the reassembled `MonoSamplePcm`. Guard: three new rows in
`SampleChunkRoundTripSpec` — the one spec that crosses the wire, and which until now checked that the
PCM bytes survived and never asked about `meta`. Reverting the argument turns all three red.

**Zero of the three earlier specs could see this**, and that is the finding worth carrying: the FE
spec proves the metadata is *computed*, the BE spec hands the PCM to `VoiceFactory` *directly*, the
round-trip spec checked *bytes*. Both ends covered, the join empty — F18's shape, third time.

Still open from below: zone selection by `keyRange` (secondary, not the bug) — filed as
`docs/tasks/future/soundfont-zone-selection.md`.

**Two defects, both in the same six lines of `VoiceFactory` (`:333-340`), and both the same shape:
the playhead does not start at the start.** Everything else on the wire checked out.

### Defect 1 — a looped instrument starts INSIDE its loop and never plays its attack

```kotlin
val playhead0 = if (data.begin != null) startSample
    else if (useMetaLoop && sampleMetaLoop != null) sampleMetaLoop.startSec * sample.sampleRate  // ← here
    else sample.meta.anchor * sample.sampleRate
```

A looped zone is played from `loopStart`, so the region `[0, loopStart)` — the attack — is skipped
entirely. Measured on the FluidR3 set (loop points in seconds, decoded with ffprobe):

| instrument | attack `[0, loopStart)` | loop `[loopStart, loopEnd)` | what you hear |
|---|---|---|---|
| violin z0 | 0 → **1.27 s** | 1.27 → 1.45 (a 180 ms slice) | no bow onset; a static 180 ms drone |
| flute z0 | 0 → **0.66 s** | 0.66 → 0.91 | no breath attack |
| accordion z0 | 0 → **1.66 s** | 1.66 → 8.26 | starts mid-bellows |

That is the reported symptom: it *does* loop, but a violin that skips its bow and loops steady state
does not read as a violin. Both SoundFont 2 and WebAudioFont start playback at the sample's start,
play *through* the attack, and loop `[loopStart, loopEnd)` only once the playhead gets there.

### Defect 2 — `anchor` is the PEAK position, not a start offset

The non-loop branch starts percussive samples at `anchor`. The task asked what `anchor` means;
measured against the decoded audio, **it is `argmax |x|` in seconds** — the position of the loudest
sample — exact in 7 of 10 zones (violin 0.75/0.75, flute 0.80/0.81, guitar 0.03/0.02 …), and the
other three are sustained tones where many samples tie for the maximum. It is a normalisation
artefact of the converter, and nothing to do with where playback should begin. For the nylon guitar
(`anchor` 0.03–0.06 s) it skips the pluck transient. `meta.anchor` is read in exactly one place:
this line.

### Cleared — verified correct, so nobody re-derives them

- **Sample-rate round trip.** Zones declare 22050/44100 Hz, the browser decodes at the context
  rate; the loop travels as **seconds** (`startSec = loopStart / zoneRate`) and the backend multiplies
  by the *decoded* `sample.sampleRate` (`BrowserAudioDecoder:55`). Correct by construction.
- **Loop geometry.** `loopEnd` ≈ OGG length − 70 ms on every zone checked; never past the PCM.
- **`SampleIgnitor`'s wrap** is `ph >= loopEnd → loopStart + (ph − loopStart) % len`, i.e. the loop is
  `[start, end)` with an exclusive end — exactly SF2's definition.
- **The sustain envelope** (`getSampleMetadata`: sustain 1.0, release 0.2 s for loops ≥ 50 ms) is
  the WebAudioFont convention: loop while held, amplitude release over the still-looping sample. SF2
  mode 3's "play past `loopEnd` on release" is not expressible in this data format, so it is not a
  target.

### Secondary — not the bug, but wrong by the spec

`SampleIndexLoader:118` picks the first zone whose **root pitch** is at or above the requested note
and ignores `keyRangeLow`/`keyRangeHigh` entirely. It approximates the right zone (always pitching
down from the nearest root above) but a zone's key range is the spec's selection rule. Lower priority.

### Proposed fix (two lines, NOT applied)

```kotlin
val playhead0 = if (data.begin != null) startSample else 0.0
```

Play from the start unless the user set `begin`; the loop engages when the playhead reaches
`loopStart`, as the format intends. Drops `anchor` from the playback path (keep the field as
informational, or remove it). **No shipped song uses a `gm_` soundfont, so no shipped sound moves** —
but every soundfont instrument gets its attack back, which is a by-ear change the maintainer should
hear on violin/flute/guitar before it lands. Guard to write with it: a looped-sample voice's first
frames must equal the PCM's first frames, not `pcm[loopStart]`.

---

## Original brief

### Status (original): TODO

## Problem

Looped soundfont instruments don't sustain/loop as expected. The data flow from soundfont JSON
through to SampleIgnitor needs to be traced and verified end-to-end.

## Investigation Steps

### 1. Understand the soundfont zone data structure

Examine actual soundfont JSON files for loopable instruments at:
`/opt/dev/peekandpoke/peekandpoke.github.io/klang/felixroos/gm/`

Look at:

- `gm_accordion/` — sustained instrument, should loop indefinitely while key is held
- `gm_violin/` — bowed instrument, continuous sustain
- `gm_organ/` — organ, indefinite sustain

For each, understand:

- How `loopStart` / `loopEnd` frame values relate to the actual PCM data
- What `anchor` means in practice (sample start offset? attack point?)
- How `originalPitch`, `keyRangeLow`, `keyRangeHigh` define zone coverage
- What `ahdsr` flag means vs the heuristic loop detection (50ms threshold)
- How multiple zones interact (layering? splitting by key range?)

### 2. Trace the data flow

```
SoundFont JSON zone
  → SoundFont.Zone.getSampleMetadata() [loop detection, ADSR, anchor]
  → SampleMetadata.LoopRange(startSec, endSec)
  → VoiceFactory [converts seconds → frames, creates SampleIgnitor]
  → SampleIgnitor [playhead, loop wrap, interpolation]
```

Verify at each step:

- Are loop points correct after seconds conversion?
- Is the playhead starting at the right position?
- Is `isLooping` being set to true?
- Does SampleIgnitor actually wrap at loop boundaries?
- Is the ADSR envelope allowing sustain (sustain=1.0)?

### 3. Compare with a known working SoundFont player

Compare Klang's zone selection and loop behavior with:

- WebAudioFont (the original source of these soundfont conversions)
- A standard SF2 player (e.g., FluidSynth)

Key questions:

- Does WebAudioFont use `anchor` as a playback start offset?
- How does WebAudioFont handle the `loopStart`/`loopEnd` values?
- Does it apply an ADSR or use raw sample looping?

### 4. Fix issues found

Likely areas:

- Loop point calculation (frames vs seconds, sample rate conversion)
- Anchor interpretation (is it seconds from start? normalized position?)
- ADSR interaction with looping (does the envelope cut the sound before the loop sustains?)
- Zone selection for multi-zone instruments
- `useMetaLoop` conditions in VoiceFactory (when does meta loop activate?)

## Key Files

| File                                        | Role                                                              |
|---------------------------------------------|-------------------------------------------------------------------|
| `audio_fe/.../samples/SoundFont.kt`         | Zone data model, getSampleMetadata(), loop detection              |
| `audio_fe/.../samples/SampleIndexLoader.kt` | Zone selection, Base64 decode, sample construction                |
| `audio_bridge/.../SampleMetadata.kt`        | LoopRange(startSec, endSec), anchor, ADSR                         |
| `audio_be/.../voices/VoiceFactory.kt`       | Seconds→frames, playhead start, isLooping, SampleIgnitor creation |
| `audio_be/.../ignitor/SampleIgnitor.kt`     | Playback engine: loop wrap, interpolation, stopFrame              |
| `audio_bridge/.../VoiceData.kt`             | Voice params: begin, end, loop, speed                             |

## Sample Data Location

`/opt/dev/peekandpoke/peekandpoke.github.io/klang/felixroos/gm/`
