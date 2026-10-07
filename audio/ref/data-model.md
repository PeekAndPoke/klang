# Audio — Data Model

All types live in `audio_bridge/src/commonMain/kotlin/` and are `@Serializable`.

## ScheduledVoice

The scheduling message sent via `Cmd.ScheduleVoice`. Enqueued in the voice min-heap keyed by `startTime`.

```kotlin
data class ScheduledVoice(
    val playbackId: String,       // unique ID for deduplication / replace
    val data: VoiceData,          // all synthesis parameters
    val startTime: Double,        // engine-relative ms — when to activate
    val gateEndTime: Double,      // engine-relative ms — when to release (ADSR gate off)
    val playbackStartTime: Double // engine-relative ms — origin of the playback cycle
)
```

## VoiceData — All Parameters

`VoiceData.kt`: nullable fields; omitting one means the engine default. Since phase 3 step 9
(2026-09-27) every voice is an Ignitor tree, and the voice doors (the envelope, the four filters,
crush, coarse, distort, tremolo, the sample's begin/end/speed/loop) are not typed fields: they
travel as slot keys in `ignitorParams`, and the orbit stages as slot keys in `katalystParams`.

### Pitch & Tuning

| Field        | Type      | Meaning                                   |
|--------------|-----------|-------------------------------------------|
| `note`       | `String?` | Note name                                 |
| `freqHz`     | `Double?` | Direct frequency in Hz                    |
| `accelerate` | `Double?` | Pitch glide in SEMITONES over the event (12 = one octave; was octaves pre-2026-08-24) |

### Gain & Dynamics

| Field        | Type       | Meaning                                |
|--------------|------------|----------------------------------------|
| `gain`       | `Double?`  | The channel fader: the one level word on the wire (1.0 = unity, null = unset). A frontend's articulation shorthand (sprudel's `velocity`, a MIDI key velocity) is multiplied into it BEFORE it crosses, so `velocity` and the retired second multiplier are not wire fields (signal-flow plan section 6, 2026-09-19). |
| `legato`     | `Double?`  | Legato                                 |
| `solo`       | `Double?`  | 1.0 = full solo (mute others), 0.0 = no solo |

### Sound Selection

| Field        | Type      | Meaning                                                                                                                                              |
|--------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `bank`       | `String?` | Sample bank name (e.g. `"mdk"`)                                                                                                                      |
| `sound`      | `String?` | Sound name within the bank, or a registered ignitor name                                                                                             |
| `soundIndex` | `Int?`    | Variant index — for samples picks the bank entry; for ignitors dispatches `IgnitorDsl.Variants` (`children[index.mod(N)]`). Defaults to 0 when null. |

### The instrument's slots: `ignitorParams`

`ignitorParams: Map<String, Double>?` is the voice's slot bag. The instrument's tree resolves every
`IgnitorDsl.Param` against it, falling back to the slot's authored default. Sprudel's
`toVoiceData()` writes the voice doors here (`classicSlotParams` in
`sprudel/src/commonMain/kotlin/_classic_slot_params.kt`, the one place sprudel's words meet the
engine's slot names):

- `classic()`'s slots, keyed `<door>.<param>` (`lpf.freq`, `lpf.q`, `adsr.attack`, `adsrCurves.release`,
  `distort.oversample`, `tremolo.depth`, ...), plus the flat `onepole` (Hz, its first stage), declared in
  `IgnitorDsl.Slots` (`audio_bridge/.../IgnitorDsl.kt`, the stage slot groups in `IgnitorDslClassic.kt`); a tree without `classic()` reads none of them;
- the sample instrument's playback slots, flat: `begin`, `end`, `speed`, `loop` (`Slots.sample`);
- the oscillator's own generic slots (`density` on dust, `voices` and `spread` on the super
  oscillators) and any raw `ignp(name, value)` write. sprudel also writes `panSpread` (`unison(pan = ...)`),
  which no engine stage reads yet.

Every built-in sound is `source.pregain().classic()` and every sample voice runs the same shape over its
PCM (`IgnitorRegistry.builtInVoice`); an authored instrument reaches the doors by ending in `.classic()`.
A tree without it plays as it is, and the door slots in the bag go unread.

### Pitch Modulation

| Field        | Type      | Meaning                          |
|--------------|-----------|----------------------------------|
| `vibrato`    | `Double?` | Vibrato LFO rate in Hz           |
| `vibratoMod` | `Double?` | Vibrato depth in SEMITONES (sprudel `vibrato(depth)`) |
| `pAttack`    | `Double?` | Pitch envelope attack (s)        |
| `pDecay`     | `Double?` | Pitch envelope decay (s)         |
| `pRelease`   | `Double?` | Pitch envelope release (s)       |
| `pEnv`       | `Double?` | Pitch envelope depth (semitones) |
| `pSustain`   | `Double?` | Pitch envelope sustain (a share of the depth) |
| `pAttackCurve` / `pDecayCurve` / `pReleaseCurve` | `AdsrCurve?` | Pitch envelope stage curves (unset = `MOD_ENV_CURVE`) |

### FM Synthesis

| Field       | Type      | Meaning                                      |
|-------------|-----------|----------------------------------------------|
| `fmh`       | `Double?` | FM modulator ratio, a multiplier of the base frequency (sprudel `fm(h)`) |
| `fmAttack`  | `Double?` | FM envelope attack (s)                       |
| `fmDecay`   | `Double?` | FM envelope decay (s)                        |
| `fmSustain` | `Double?` | FM envelope sustain level                    |
| `fmEnv`     | `Double?` | FM modulation depth                          |

### Routing and lifetime

| Field      | Type      | Meaning                                      |
|------------|-----------|----------------------------------------------|
| `cylinder` | `Int?`    | Cylinder (orbit bus) ID for this voice       |
| `pan`      | `Double?` | Stereo pan, 0 = full left, 1 = full right, unset = 0.5 (center) |
| `cut`      | `Int?`    | Choke group: a new voice in the group ends the ones still sounding (a 4 ms fade, `Voice.cutOff`) |
| `cull`     | `Double?` | Silence-culling window in seconds; null = `VOICE_CULL_SECONDS`, negative = never |

### Orbit bus slots: `katalystParams`

Every orbit stage (delay, reverb, compressor, duck, phaser, body, vowel) reads its knobs from
`katalystParams: Map<String, Double>?`, keyed `<stage>.<knob>` exactly as `KatalystDsl.classic` names
them (`delay.wet`, `reverb.size`, `compressor.threshold`, `duck.orbit`, `phaser.wet`, `body.material`,
`vowel.vowel`, ...), and the orbit's OWNER voice applies them. The delay, reverb, compressor and duck
fields left the wire in Katalyst step 5b-3 (2026-09-19); the phaser fields and the `filters` list in
phase 3 step 9. The one rule of which chain reads which slot lives in the `katalystParam` door's KDoc
(`sprudel/src/commonMain/kotlin/lang/lang_katalyst.kt`).

### Control and metadata

`master` and `katalyst` (chain names in the one Katalyst namespace since phase 3 step 12, last writer wins), `control` (a control-only event, never
synthesized), `tags` (UI only), `sourceId`.

## AdsrDef

```kotlin
sealed interface AdsrDef {
    @WireName("std")
    data class Std(
        val attack: Double? = null, val decay: Double? = null, val sustain: Double? = null, val release: Double? = null,
        val attackCurve: AdsrCurve? = null, val decayCurve: AdsrCurve? = null, val releaseCurve: AdsrCurve? = null,
    ) : AdsrDef
}
```

An envelope carried as data, with one wire host: a sample's own envelope in `SampleMetadata.adsr`. The
sample instrument fills the `adsr.*` slots the pattern left unset from it (`withSampleEnvelopeDefaults` in
audio_be). A voice's own envelope travels as `classic()`'s `adsr.*` slots; their defaults are the
`VOICE_ADSR_*` constants (`audio_bridge/.../constants/EnvelopeDefaults.kt`).

## FilterDef

```kotlin
sealed class FilterDef {
    // Resonators: parallel modal BPF banks blended over the dry via ParallelMixFilter(mix, floor).
    // Applied at the ORBIT level (KatalystFormantEffect / KatalystBodyEffect), never per voice.
    data class Formant(val bands: List<Band>, val mix: Double, val floor: Double? = null)  // vowel()
    data class Body(val bands: List<Mode>,   val mix: Double, val floor: Double? = null)   // body()
    // Band / Mode are both (freq, db, q). floor null → engine default (VOWEL_FLOOR / BODY_FLOOR).
}
```

- Not a wire type any more: the orbit builds these from its slots. The bands resolve from the
  `body.material` / `vowel.vowel` INDEX slot through `BodyMaterials.modesAt` / `VowelBands.bandsAt`
  (`KatalystSlotWriters`, `KatalystSlots`); DSP = one `ResonatorBank` (band gain rules in `bodyBand` /
  `vowelBand`) + `createBody`/`createFormant`; blend + declick-crossfade in `ParallelMixFilter` /
  `KatalystFilterSwap`. See `ref/architecture.md` "Per-Playback Engine".
- The per-voice filters are `classic()`'s `lpf`/`hpf`/`bpf`/`notch` stages, each an `Ignitor.svf` node
  with its cutoff envelope, filled from the `<door>.<param>` slots.

## MonoSamplePcm

```kotlin
data class MonoSamplePcm(
    val sampleRate: Int,           // e.g. 44100
    val pcm: FloatArray,           // interleaved mono samples
    val meta: SampleMetadata,
)

data class SampleMetadata(
    val anchor: Int?,              // playback anchor frame
    val loop: LoopRange?,          // LoopRange(start, end) for looping
    val adsr: AdsrDef?,            // sample-embedded ADSR
)
```

## SampleRequest

```kotlin
data class SampleRequest(
    val bank: String,
    val sound: String,
    val index: Int,
    val note: Double?,
)
```
