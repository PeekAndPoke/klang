# Sample Library Refactor

Caching bugs, architecture cleanup, pitching/playback correctness.

---

## A. Bugs

### A1. CRITICAL — Soundfont loop points wrong on JS (sample rate mismatch)

`SoundFont.Zone` stores `loopStart`/`loopEnd` in frames at the zone's `sampleRate` (e.g., 22050 Hz).
`BrowserAudioDecoder` resamples all audio to 48000 Hz via `OfflineAudioContext`. The decoded PCM has
~2x more frames, but `loopStart`/`loopEnd` still reference the original frame indices — loops wrap at
the wrong position (roughly midpoint instead of end).

`anchor` is stored in **seconds** and converted via `anchor * decodedSampleRate` in VoiceFactory, so
anchor is NOT affected. Only loop frame indices are broken.

**Fix:** Store loop points as time (seconds) in `SampleMetadata`, convert to frames using decoded
sample rate. Or scale loop indices by `decodedSampleRate / zoneSampleRate` after decoding.

**Files:** `SoundFont.kt:54-91`, `SampleMetadata.kt`, `VoiceFactory.kt` (loop frame calculation)

### A2. HIGH — Pitched samples decoded/stored multiple times (cache key bug)

`loadCache` in `Samples.kt` is keyed by `SampleRequest` (includes `note`). Playing C4 and D4 on
piano both resolve to `C4v8.mp3` but generate different cache keys → same file decoded twice, PCM
stored twice in memory.

**Fix:** Key `loadCache` by resolved sample identity (URL for `FromUrl`, zone fingerprint for
`FromBytes`), not by `SampleRequest`.

**Files:** `Samples.kt:156-186`

### A3. HIGH — Soundfont provider re-parses JSON + re-decodes Base64 every provide() call

`SoundFontLoader.Provider.provide()` calls `loadVariantData()` every time — re-parses full JSON.
Base64.decode runs on every call even for zones already decoded. No zone-level caching.

**Fix:** Cache parsed `SoundData` per variant. Cache decoded `Sample.FromBytes` per zone.

**Files:** `SampleIndexLoader.kt:78-134`

### A4. MEDIUM — `coarseTune` and `fineTune` parsed but never applied

Soundfont zones have `coarseTune` (semitones) and `fineTune` (cents) but these are never factored
into the pitch calculation. Effective pitch should be:
`originalPitch + coarseTune * 100 + fineTune` (all in cents).

**Files:** `SampleIndexLoader.kt:102,109`, `SoundFont.kt:42-43`

### A5. MEDIUM — `keyRangeLow`/`keyRangeHigh` parsed but not used for zone selection

Zone selection picks by `originalPitch` only. In well-authored soundfonts, key ranges define which
notes each zone serves and can overlap or have gaps. Current algorithm works for simple soundfonts
but can select wrong zones for complex ones.

**Files:** `SampleIndexLoader.kt:100-103`

### A6. LOW — `VoiceData.loopBegin`/`loopEnd` set by sprudel but never consumed

Sprudel sets `loopBegin` and `loopEnd` on voice data, but `VoiceFactory` only reads `data.begin`
and `data.end`. These are separate fields. Dead data — planned feature not yet wired.

**Files:** `VoiceData.kt:121-122`, `VoiceFactory.kt:221-265`

---

## B. Architecture Issues

### B1. HIGH — Samples.kt is a god-file (7 nested concepts)

Defines `SampleType`, `SoundProvider`, `ResolvedSample`, `LoadedSample`, `Index`, `Bank`, `Sample`
all nested inside `Samples`. Forces `Samples.SoundProvider`, `Samples.Sample` prefix everywhere.

**Fix:** Extract to top-level types in `samples` package:

- `SampleType.kt`, `SoundProvider.kt`, `SampleIndex.kt` (Index + Bank), `Sample.kt`
- Rename `Samples` → `SampleRegistry`

### B2. HIGH — Sample sealed interface mixes data with loading behavior

`Sample.FromUrl.getPcm()` does I/O (download + decode). `Sample.FromBytes` has mutable `_pcm` field.
Data type should not know how to load itself.

**Fix:** Make `Sample` a pure data holder. Move decoding into `SampleRegistry.get()` or a
`SampleDecoder` helper.

### B3. MEDIUM — SoundProvider implementations buried as inner classes

`GenericBundleLoader.Provider` and `SoundFontLoader.Provider` are inner classes nested 3 levels deep.
Can't unit test or reuse them independently.

**Fix:** Extract as top-level package-private classes.

### B4. MEDIUM — GenericBundleLoader tangles parsing with domain construction

`parseSoundsFile()` simultaneously parses JSON, splits keys, constructs URLs, creates Samples, and
groups into Banks. Should be two steps: parse → map to domain.

### B5. MEDIUM — Samples caches not thread-safe

`resolveCache` and `loadCache` are bare `mutableMapOf`. KDoc says "safe to call from scheduler thread"
but HashMap is not thread-safe. Concurrent `getOrPut` can corrupt state.

**Fix:** Add `Mutex`, or document single-caller constraint via `SamplePreloader`.

### B6. LOW — DiskUrlCache NPE when loader returns null

`DiskUrlCache.getOrPut()` calls `Files.write(tmp, bytes)` where `bytes` can be null.

### B7. LOW — Stale KDoc references `getIfLoaded` method that doesn't exist

### B8. LOW — Debug println in production code

`println("Loading sample $url")` fires for every sample load.

---

## C. Implementation Order

### Phase 1 — Fix critical bugs

1. **A1**: Soundfont loop point sample rate mismatch (CRITICAL)
2. **A2 + A3**: Cache-by-identity + soundfont zone caching (HIGH)
3. **A4**: Apply coarseTune/fineTune to zone pitch (MEDIUM)

### Phase 2 — Architecture cleanup

4. **B1 + B2**: Extract types from Samples.kt, make Sample a pure data type, rename to SampleRegistry
5. **B3 + B4**: Extract providers, separate parsing from domain construction
6. **B5**: Thread-safety for caches

### Phase 3 — Minor cleanup

7. **A5**: Use keyRangeLow/keyRangeHigh for zone selection (optional — current algo works for simple fonts)
8. **A6**: Wire or remove VoiceData.loopBegin/loopEnd
9. **B6 + B7 + B8**: Null handling, stale docs, debug prints

## Verification

```bash
./gradlew :audio_fe:jvmTest
./gradlew :audio_bridge:jvmTest
./gradlew :audio_be:jvmTest
```

Manual tests:

- Play piano sample with multiple notes → verify each audio file decoded only once
- Play looped soundfont instrument (e.g., GM violin) in browser → verify loop sounds correct
- Play soundfont instruments with non-zero coarseTune/fineTune → verify pitch correctness
