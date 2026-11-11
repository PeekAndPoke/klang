# Audio — Sample Management (audio_fe)

All types in `audio_fe/src/`.

## Overview

```
SampleRequest (bank + sound + index + note, each nullable)
    ↓
Samples.get(request): Index.resolve(request) → ResolvedSample (a Sample: a URL or bytes)
    ↓
AssetLoader.download(url) → ByteArray?  (CachingAssetLoader over a UrlCache)
    ↓
AudioDecoder.decodeMonoFloatPcm(audioBytes) → MonoSamplePcm?
    ↓
Cmd.Sample.Complete(req, note, pitchHz, pcm) → KlangCommLink → audio_be  (the JS host transfers its PCM buffer)
```

The frontend's `SamplePreloader` (module `klang`) runs the pipeline above for the samples a playback's voices use
before they are sent, and again for each `Feedback.RequestSample` the backend sends, then sends
`Cmd.Sample.Complete` (or `Cmd.Sample.NotFound`).

## Samples Registry

`audio_fe/src/commonMain/kotlin/samples/Samples.kt`

### Samples.Index

Describes the catalogue structure: an immutable data class, built from the sample catalogues by
`SampleIndexLoader` (`samples/SampleIndexLoader.kt`).

```kotlin
data class Samples.Index(
    val banks: List<Bank>,                       // Bank(key, sounds: List<SoundProvider>)
    val aliases: Map<String, String> = emptyMap(), // bank name → bank name
) {
    // The bank (aliases followed; the default sounds are the bank "") and its sound; null if not found
    suspend fun resolve(request: SampleRequest): ResolvedSample?
}
```

### Samples.SoundProvider (an interface)

Each provider has a `key` (the sound's name), a `variantCount`, a `sampleType` (`SINGLE`, `PITCHED`: picked by
`note`, `VARIANTS`: picked by `index`) and `provide(request)`. The two implementations live in `SampleIndexLoader`:
one for a soundfont bundle and one for a generic bundle of sample files.

## AudioDecoder

`audio_fe/src/commonMain/kotlin/samples/AudioDecoder.kt`

```kotlin
interface AudioDecoder {
    suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm?
}
```

### JvmAudioDecoder (jvmMain, `decoders/`)

- Detects format from magic bytes (WAV: `RIFF`, MP3: `ID3`/`0xFF 0xFB`)
- WAV: parsed via `javax.sound.sampled.AudioSystem`
- MP3: decoded via JLayer (`javazoom.jl.decoder`)
- Converts to mono PCM (every channel averaged) and keeps the file's own sample rate: no resampling at decode

### BrowserAudioDecoder (jsMain, `BrowserAudioDecoder.kt`)

- Delegates to `decodeAudioData(buffer)` (Web Audio API) on an `OfflineAudioContext(1, 1, sampleRate)` at the ENGINE's
  rate (its constructor parameter, the player's resolved rate: the app's `Player` passes it to `Samples.create`), so
  the browser's own resampler converts a file to that rate once, before the engine sees it (KLANG-00430, option 3;
  until 2026-11-11 it decoded at a fixed 48 kHz, and an engine at another rate converted every file twice). The
  fallback `AudioContext()` decodes at the device's rate, which the playhead then converts
- Averages every channel to mono, as the JVM decoders do (since KLANG-00430; it used to keep the left channel only)
- Supports all browser-native formats (MP3, WAV, OGG, AAC)

### The two hosts differ in the rate conversion (KLANG-00430, audit finding L9-07)

The channel rule is the same on both hosts (the mean of all channels). The rate conversion is not:

- **Browser**: the file reaches the engine at the engine's own rate, converted once by the browser's resampler (its
  quality is the browser's own, not specified by Web Audio). An untransposed sample then steps by exactly 1, at
  every rate.
- **JVM** (the offline render, the corpus, `RenderWavCommand`): the file keeps its rate, and the playhead converts it
  with the engine's four-point cubic Lagrange read (`SampleIgnitor`). A 44.1 kHz file's images of a 10 kHz tone read
  -26 to -31 dB under the tone there (the linear read before KLANG-00430: -20.4); a band-limiting converter would
  remove them, the four-point read only lowers them.

So an offline render of a sample song is not the browser's sound in the top octaves of a file that is not at the
engine's rate. A band-limiting converter at decode on the JVM, so the offline render matches the browser's in kind,
is KLANG-00438.

## UrlCache

`audio_fe/src/commonMain/kotlin/cache/UrlCache.kt`

```kotlin
interface UrlCache {
    suspend fun has(uri: String): Boolean
    suspend fun get(uri: String): ByteArray?
    suspend fun getOrPut(uri: String, loader: suspend () -> ByteArray?): ByteArray?
}
```

| Implementation     | Platform | Storage                                |
|--------------------|----------|----------------------------------------|
| `InMemoryUrlCache` | JS/JVM   | `MutableMap<String, ByteArray>` in RAM |
| `DiskUrlCache`     | JVM      | Files in a cache directory on disk     |

`DiskUrlCache` uses a hash of the URL as the filename for collision-free storage.

## AssetLoader

`audio_fe/src/commonMain/kotlin/utils/AssetLoader.kt`

Fetches a URL as `ByteArray`.

```kotlin
interface AssetLoader {
    suspend fun download(uri: String): ByteArray?
}
```

- `KtorAssetLoader` (the default, `AssetLoader.default`): an HTTP GET through a Ktor `HttpClient`, on both hosts;
  a failed status throws.
- `CachingAssetLoader(cache, delegate)`: asks the `UrlCache` first (`getOrPut`), and a URL already in flight is
  loaded once.

## Sample Loading Flow in Detail

1. The frontend's `SamplePreloader` loads the samples of the voices it is about to send; the backend's
   `VoiceScheduler` also asks, when it schedules a sample voice whose sample it does not hold
   (`Feedback.RequestSample`, once per sample, `SampleStore.requestIfMissing`)
2. Frontend receives it on the KlangCommLink feedback channel
3. `Samples.get(request)`: `Index.resolve(request)` (null → `Cmd.Sample.NotFound`)
4. `AssetLoader.download(url)` → bytes (cache hit or HTTP fetch + cache put)
5. `AudioDecoder.decodeMonoFloatPcm(bytes)` → `MonoSamplePcm`
6. Frontend sends `Cmd.Sample.Complete` on the command channel. The JVM hands it over in process; the browser posts
   it to the worklet as one message whose PCM buffer is transferred, not cloned (`WorkletContract.sendSample`,
   KLANG-00431 D5): the main thread copies the PCM (the frontend's cache keeps its own) and transfers the copy, so
   the audio thread allocates and copies nothing (until 2026-11-11: 1 MB chunks, each cloned and copied there). A copy
   that cannot be allocated is sent as not found, acknowledged to the preloader and counted
   (`sampleAllocationFailures`)
7. The backend's `SampleStore` stores the PCM. There is no pending sample voice: a voice whose sample is not
   complete at its start is dropped whole and counted soundless

## MonoSamplePcm (reminder)

```kotlin
class MonoSamplePcm(
    val sampleRate: Int,
    val pcm: DoubleArray,       // always mono; the decoders widen their floats once, here
    val meta: SampleMetadata = SampleMetadata.default,
)
```

Stereo samples are downmixed to mono during decoding (every channel averaged, on both hosts).
Rate conversion to the engine sample rate is part of the playhead rate `VoiceFactory` computes at voice
build (the PCM's rate over the engine's, times pitch and speed); `SampleIgnitor` steps the playhead by it.
