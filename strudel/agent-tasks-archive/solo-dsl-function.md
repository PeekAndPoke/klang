Here is the implementation plan for the `solo()` function in Strudel DSL.

### Implementation Plan

The goal is to implement a `solo()` function that tags specific patterns. When the playback engine detects any active
voice with the `solo` tag in the current time window, it will mute all other voices that do not have this tag.

This requires changes in three areas:

1. **Data Structure**: Add the `solo` property to `StrudelVoiceData`.
2. **Playback Logic**: Filter events in `StrudelPlayback` based on the `solo` flag.
3. **DSL**: Expose the `solo()` function to the Strudel language.

---

### Step 1: Modify `StrudelVoiceData.kt`

**File:** `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`

We need to add a `solo` field to the `StrudelVoiceData` class. This field acts as a flag.

1. **Add the property**:
   Add `val solo: Boolean?` to the data class properties.

2. **Update `empty` object**:
   Initialize `solo = null` in the `empty` companion object.

3. **Update `merge` function**:
   Ensure `solo` is merged correctly: `solo = other.solo ?: solo`.

4. **Update `toVoiceData`**:
    * **Note**: The audio engine (`VoiceData`) does *not* need to know about `solo`, because the filtering happens at
      the playback scheduling level (in `StrudelPlayback`). You do not need to map this field in `toVoiceData()`.

#### Code Snippet

```kotlin
// ... existing code ...
// Dynamics / Compression
/** Dynamic range compression settings (threshold:ratio:knee:attack:release) */
val compressor: String?,

// Playback control
val solo: Boolean?,

// Custom value
val value: StrudelVoiceValue? = null,
) {
    companion object {
    val empty = StrudelVoiceData(
// ... existing code ...
        vowel = null,
        compressor = null,
        solo = null,
        value = null,
    )
}

    fun merge(other: StrudelVoiceData): StrudelVoiceData {
        return StrudelVoiceData(
            note = other.note ?: note,
// ... existing code ...
            vowel = other.vowel ?: vowel,
            compressor = other.compressor ?: compressor,
            solo = other.solo ?: solo,
            value = other.value ?: value
        )
    }

    fun isTruthy(): Boolean {
// ... existing code ...
```

---

### Step 2: Modify `StrudelPlayback.kt`

**File:** `strudel/src/commonMain/kotlin/StrudelPlayback.kt`

We need to implement the filtering logic in the `queryEvents` method. If any event in the queried batch has
`solo == true`, we drop all events where `solo` is not true.

1. **Locate `queryEvents`**:
   Find the `queryEvents` method.

2. **Apply Filtering**:
   After fetching `events` from `pattern.queryArcContextual(...)`, check if `solo` is active and filter accordingly.

#### Code Snippet

```kotlin
// ... existing code ...
val events: List<StrudelPatternEvent> =
    pattern.queryArcContextual(from = fromRational, to = toRational, ctx = ctx)
        .filter { it.part.begin >= fromRational && it.part.begin < toRational }
        .filter { it.isOnset }  // Allow continuous OR onset events
        .sortedBy { it.part.begin }
        // Implement SOLO logic:
        // If any event in this batch is "soloed", drop all events that are NOT soloed.
        .let { rawEvents ->
            val isSoloActive = rawEvents.any { it.data.solo == true }
            if (isSoloActive) {
                rawEvents.filter { it.data.solo == true }
            } else {
                rawEvents
            }
        }

// Transform to ScheduledVoice using absolute time from KlangTime epoch
val secPerCycle = 1.0 / cyclesPerSecond
// ... existing code ...
```

---

### Step 3: Implement DSL Function in `lang_structural.kt`

**File:** `strudel/src/commonMain/kotlin/lang/lang_structural.kt`

We need to expose `solo()` to the DSL. We will use the same pattern as `loop` or `gain`, utilizing the `voiceModifier`
helper to set the data property.

1. **Define Mutation**: Create a `soloMutation` that sets the `solo` property.
2. **Define Application Logic**: Create `applySolo` to lift the data into the pattern.
3. **Register Extensions**: Add extensions for `StrudelPattern`, `String`, and the global function.

*Note: We place this in `addons/lang_structural_addons.kt` as it affects the structural output of the song (muting
others).*

#### Code Snippet

```kotlin
// ... existing code ...
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import kotlin.math.abs
// ... existing code ...

// -- hush() -----------------------------------------------------------------------------------------------------------

/** Stops all playing patterns by returning silence, ignoring all arguments. */
// ... existing code ...
@StrudelDsl
val String.hush by dslStringExtension { _, _, _ -> silence }

// -- solo() -----------------------------------------------------------------------------------------------------------

private val soloMutation = voiceModifier { copy(solo = it?.asVoiceValue()?.asBoolean) }

fun applySolo(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Default to true (1.0) if called without arguments: .solo()
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(soloMutation)
    return source._liftData(control)
}

/**
 * Solos the pattern. If any pattern is soloed, all non-soloed patterns are muted.
 * Can be disabled by passing 0 or false: .solo(0)
 */
@StrudelDsl
val solo by dslFunction { args, /* callInfo */ _ -> args.toPattern(soloMutation) }

/**
 * Solos the pattern. If any pattern is soloed, all non-soloed patterns are muted.
 * Can be disabled by passing 0 or false: .solo(0)
 */
@StrudelDsl
val StrudelPattern.solo by dslPatternExtension { p, args, /* callInfo */ _ -> applySolo(p, args) }

/**
 * Solos the pattern. If any pattern is soloed, all non-soloed patterns are muted.
 * Can be disabled by passing 0 or false: .solo(0)
 */
@StrudelDsl
val String.solo by dslStringExtension { p, args, callInfo -> p.solo(args, callInfo) }

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates silence with a specific duration in steps (metrical steps). */
fun applyGap(args: List<StrudelDslArg<Any?>>): StrudelPattern {
// ... existing code ...
```
