# REQUEST:

Hi! We have a structural problem in the project. The subproject klangui needs access to streams of KlangPlaybackSignal.

The current work-around is the introduction of `PlaybackVoiceEvent`

Currently the class KlangPlaybackSignal lives in the "klang" sub-project.

But "klang" is supposed to be a "bom" for the entire project.

So how can we restructure all of this, so that we do not need the work-around with `PlaybackVoiceEvent` ?

# PROPOSAL:

To solve this structural issue and remove the `PlaybackVoiceEvent` workaround while respecting the rule that the `klang`
subproject acts as a BOM (Bill of Materials), you should extract the audio domain models into a dedicated module that
both `klang` and `klangui` can depend on.

Here is the step-by-step architectural restructuring plan:

### 1. Relocate Core Audio Classes to `audio_fe`

Since `klang` is meant to be a BOM, it shouldn't contain core implementations. The files currently in
`klang/src/commonMain/kotlin/` should be moved to an appropriate engine module. `audio_fe` (Audio Front End) is the
perfect candidate since it handles the client-facing audio communication:
Move the following files from `klang` to `audio_fe`:

* `KlangPlaybackSignal.kt`
* `KlangPlayback.kt`
* `KlangPlayer.kt`
* `SamplePreloader.kt`
* `KlangPlaybackContext.kt`

*(Make sure to update their package declarations if needed, though keeping them in `io.peekandpoke.klang.audio_engine`
is fine as long as they physically reside in the `audio_fe` module).*

### 2. Update `klangui` Dependencies

Now that `KlangPlaybackSignal` lives in a lower-level module, you can safely expose it to the UI layer without creating
a circular dependency or violating the BOM structure.
In `klangui/build.gradle.kts`, add a dependency on the audio front-end:

```kotlin
// klangui/build.gradle.kts
sourceSets {
    jsMain {
        dependencies {
            // ... existing dependencies ...
            api(project(":audio_fe")) // Add this dependency
        }
    }
}
```

### 3. Eliminate the `PlaybackVoiceEvent` Workaround

Now that `klangui` can access `KlangPlaybackSignal` natively, you can clean up the codebase by removing the workaround
completely.

* **Delete** `PlaybackVoiceEvent.kt`.
* **Delete** the mapping function `asPlaybackVoiceEvents()` (which mapped `KlangPlaybackSignal` to the UI workaround).

### 4. Refactor UI Consumers (e.g., `CodeSongPage.kt`)

Update your UI components to directly consume `KlangPlaybackSignal.VoicesScheduled.VoiceEvent`.

For instance, anywhere you previously used `createVoiceStream` with `PlaybackVoiceEvent`, you can pass the raw stream
directly:

```kotlin
// In CodeSongPage.kt, replace the `createVoiceStream` with the direct generic stream mapping:
private fun createVoiceStream(playback: StrudelPlayback): Stream<List<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>> {
    return playback.signals
        .filterIsInstance<KlangPlaybackSignal.VoicesScheduled>()
        .map { it.voices }
}
```

Update all consumers that relied on `PlaybackVoiceEvent` to simply use the properties from
`KlangPlaybackSignal.VoicesScheduled.VoiceEvent` (which already carries `startTime`, `endTime`, and `sourceLocations`).

### Summary of Benefits

* **Preserves the BOM:** `klang` correctly remains a high-level aggregator project.
* **Cleaner Dependency Graph:** `klangui` -> `audio_fe` -> `audio_bridge`.
* **Less Boilerplate:** Eliminating mapping adapters (`asPlaybackVoiceEvents`) and redundant data classes means less
  memory overhead during high-frequency audio stream events.
