# Strudel — Event Model

## Event Structure

```kotlin
data class StrudelPatternEvent(
    val part: TimeSpan,   // visible portion (may be clipped)
    val whole: TimeSpan,  // complete original event — always non-nullable
    val data: StrudelVoiceData
) {
    val isOnset: Boolean = whole.begin == part.begin
}
```

`queryArc()` returns all events including non-onsets. Playback filters by `isOnset`.

## Pattern Operation Categories

| Type           | Examples                              | Part rule       | Whole rule               |
|----------------|---------------------------------------|-----------------|--------------------------|
| Event Creation | AtomicPattern, EuclideanPattern       | set to bounds   | same as part             |
| Clipping       | BindPattern, StructurePattern         | clip to bounds  | **preserve**             |
| Scaling        | TempoModifierPattern, HurryPattern    | scale by factor | scale by **same** factor |
| Shifting       | RepeatCyclesPattern, TimeShiftPattern | shift by offset | shift by **same** offset |
| Data Transform | ControlPattern, Gain, Note            | don't touch     | don't touch              |
| Pass-Through   | MapPattern, ChoicePattern             | don't modify    | don't modify             |

## Common Operations

```kotlin
// Create event
StrudelPatternEvent(part = ts, whole = ts, data = data)

// Clip — whole is preserved, part is clipped
val clipped = event.part.clipTo(bounds)
if (clipped != null) event.copy(part = clipped)

// Scale — both part and whole by same factor
event.copy(part = event.part.scale(factor), whole = event.whole.scale(factor))

// Shift — both part and whole by same offset
event.copy(part = event.part.shift(offset), whole = event.whole.shift(offset))
```
