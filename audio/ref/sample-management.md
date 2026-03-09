# Audio — Sample Management (audio_fe)

All types in `audio_fe/src/`.

## Overview

```
SampleRequest (bank + sound + index + note)
    ↓
Samples.resolve(request) → URL
    ↓
AssetLoader.load(url) → ByteArray  (with UrlCache)
    ↓
AudioDecoder.decode(bytes) → MonoSamplePcm
    ↓
Cmd.Sample.Complete(request, pcm) → KlangCommLink → audio_be
```

The frontend receives `Feedback.RequestSample` from the backend, runs the pipeline above,
then sends `Cmd.Sample.Complete` (or `Cmd.Sample.NotFound`) back.

## Samples Registry

`audio_fe/src/commonMain/kotlin/samples/Samples.kt`

### Samples.Index

Describes the catalogue structure. Built by the application at startup.

```kotlin
class Samples.Index {
    // Register a bank with its sound providers
    fun addBank(bank: String, vararg providers: SoundProvider)

    // Resolve a SampleRequest to a URL (null if not found)
    fun resolve(request: SampleRequest): String?
}
```

### Samples.SoundProvider (sealed)

| Subtype    | When to use                                |
|------------|--------------------------------------------|
| `Pitched`  | One sample per semitone; selects by `note` |
| `Single`   | Single sample URL for the whole sound      |
| `Variants` | Multiple files indexed by `soundIndex`     |
| `Aliased`  | Redirects one sound name to another sound  |

Example registration:

```kotlin
index.addBank("mdk",
    SoundProvider.Single("bd", "https://cdn/.../bd.wav"),
    SoundProvider.Variants("hh", listOf("hh0.wav", "hh1.wav", "hh2.wav")),
    SoundProvider.Pitched("piano", noteToUrlMap),
)
```

## AudioDecoder

`audio_fe/src/commonMain/kotlin/decoders/AudioDecoder.kt`

```kotlin
interface AudioDecoder {
    suspend fun decode(bytes: ByteArray, mimeType: String?): MonoSamplePcm
}
```

### JvmAudioDecoder (jvmMain)

- Detects format from magic bytes (WAV: `RIFF`, MP3: `ID3`/`0xFF 0xFB`)
- WAV: parsed via `javax.sound.sampled.AudioSystem`
- MP3: decoded via JLayer (`javazoom.jl.decoder`)
- Converts to mono float PCM, resamples if needed

### BrowserAudioDecoder (jsMain)

- Delegates to `AudioContext.decodeAudioData(buffer)` (Web Audio API)
- Returns first channel as mono PCM
- Supports all browser-native formats (MP3, WAV, OGG, AAC)

## UrlCache

`audio_fe/src/commonMain/kotlin/cache/UrlCache.kt`

```kotlin
interface UrlCache {
    suspend fun get(url: String): ByteArray?
    suspend fun put(url: String, data: ByteArray)
}
```

| Implementation     | Platform | Storage                                |
|--------------------|----------|----------------------------------------|
| `InMemoryUrlCache` | JS/JVM   | `MutableMap<String, ByteArray>` in RAM |
| `DiskUrlCache`     | JVM      | Files in a cache directory on disk     |

`DiskUrlCache` uses a hash of the URL as the filename for collision-free storage.

## AssetLoader

`audio_fe/src/commonMain/kotlin/utils/AssetLoader.kt`

Fetches a URL as `ByteArray`. Checks cache first; on miss, performs HTTP GET and stores result.

```kotlin
class AssetLoader(private val cache: UrlCache) {
    suspend fun load(url: String): ByteArray
}
```

Platform HTTP clients:

- **JVM**: `java.net.URL.openStream()`
- **JS**: `fetch()` API (Kotlin/JS extern)

## Sample Loading Flow in Detail

1. `VoiceScheduler` needs a sample, sends `Feedback.RequestSample(SampleRequest)`
2. Frontend receives it on the KlangCommLink feedback channel
3. `Samples.Index.resolve(request)` → URL string (or null → `Cmd.Sample.NotFound`)
4. `AssetLoader.load(url)` → bytes (cache hit or HTTP fetch + cache put)
5. `AudioDecoder.decode(bytes)` → `MonoSamplePcm`
6. Frontend sends `Cmd.Sample.Complete(request, pcm)` on the command channel
7. `VoiceScheduler` receives it, stores PCM, activates the pending `SampleVoice`

## MonoSamplePcm (reminder)

```kotlin
data class MonoSamplePcm(
    val sampleRate: Int,
    val pcm: FloatArray,        // always mono
    val meta: SampleMetadata,
)
```

Stereo samples are downmixed to mono during decoding (L+R averaged).
Rate conversion to engine sample rate is done in `SampleVoice.generateSignal()`.
