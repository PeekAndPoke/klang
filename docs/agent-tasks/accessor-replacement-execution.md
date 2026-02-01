# Accessor Replacement - Execution Plan

## Current State

- **Total usages**: ~113 instances of `.begin`, `.end`, `.dur` on events
- **Files affected**: ~20 files
- **Current test failures**: 98

## Prioritized File List

### Critical Path (Core Operations - Fix First)

#### Tier 1: Core Helper Methods & Clipping (Most Critical)

1. **StrudelPattern.kt** (9 usages) - Core helper methods used everywhere
2. **PickSqueezePattern.kt** (7 usages) - Clipping operation
3. **PickRestartPattern.kt** (6 usages) - Clipping operation
4. **StructurePattern.kt** (3 usages) - Clipping operation
5. **PickResetPattern.kt** (3 usages) - Clipping operation

#### Tier 2: Time Transformations & Control Flow

6. **ReversePattern.kt** (6 usages) - Time transformation (flip times)
7. **SwingPattern.kt** (4 usages) - Time transformation (rhythm adjust)
8. **TakePattern.kt** (5 usages) - Windowing operation
9. **DropPattern.kt** (4 usages) - Windowing operation
10. **SometimesPattern.kt** (6 usages) - Conditional execution

#### Tier 3: Pattern Creation

11. **EuclideanPattern.kt** (3 usages) - Event creation
12. **EuclideanMorphPattern.kt** (6 usages) - Event creation
13. **RandrunPattern.kt** (3 usages) - Event creation
14. **RandLPattern.kt** (3 usages) - Event creation

#### Tier 4: DSL & Playback

15. **lang_structural.kt** (7 usages) - DSL functions
16. **lang_sample.kt** (5 usages) - DSL functions
17. **lang_conditional.kt** (3 usages) - DSL functions
18. **StrudelPlayback.kt** (4 usages) - Playback scheduling

#### Tier 5: Supporting

19. **StrudelVoiceData.kt** (2 usages) - Data merging
20. **StrudelPatternEvent.kt** (2 usages) - Event utilities

## Replacement Patterns by Operation Type

### Pattern A: Intersection/Bounds Checking → `part.begin/end`

```kotlin
// BEFORE
val intersectStart = maxOf(from, event.begin)
val intersectEnd = minOf(to, event.end)

// AFTER
val intersectStart = maxOf(from, event.part.begin)
val intersectEnd = minOf(to, event.part.end)
```

### Pattern B: Query Sub-patterns → `part.begin/end`

```kotlin
// BEFORE
pattern.queryArcContextual(event.begin, event.end, ctx)

// AFTER
pattern.queryArcContextual(event.part.begin, event.part.end, ctx)
```

### Pattern C: Duration Calculation → `part.duration`

```kotlin
// BEFORE
val duration = event.end - event.begin
// or
val duration = event.dur

// AFTER
val duration = event.part.duration
```

### Pattern D: Filter/Sort → `part.begin/end`

```kotlin
// BEFORE
events.filter { it.begin >= from && it.end <= to }
events.sortedBy { it.begin }

// AFTER
events.filter { it.part.begin >= from && it.part.end <= to }
events.sortedBy { it.part.begin }
```

### Pattern E: Cycle Calculation → `part.begin`

```kotlin
// BEFORE
val cycle = event.begin.floor()
val cyclePos = event.begin - cycle

// AFTER
val cycle = event.part.begin.floor()
val cyclePos = event.part.begin - cycle
```

### Pattern F: Time Reversal → Both `part` and `whole`

```kotlin
// BEFORE (ReversePattern specific)
event.copy(begin = pivot - event.end, end = pivot - event.begin)

// AFTER
event.copy(
    part = TimeSpan(pivot - event.part.end, pivot - event.part.begin),
    whole = event.whole?.let { TimeSpan(pivot - it.end, pivot - it.begin) }
)
```

## Execution Strategy

### Phase 1: Start with Most Critical (Days 1-2)

Fix Tier 1 files first - these are used by everything else:

1. ✅ StrudelPattern.kt
2. ✅ PickSqueezePattern.kt
3. ✅ PickRestartPattern.kt
4. ✅ StructurePattern.kt
5. ✅ PickResetPattern.kt

**After each file:**

- Compile: `./gradlew :strudel:compileKotlinJvm`
- Verify patterns are correct
- Commit if desired

### Phase 2: Time Transformations (Days 3-4)

6. ✅ ReversePattern.kt (special case - flips times)
7. ✅ SwingPattern.kt
8. ✅ TakePattern.kt
9. ✅ DropPattern.kt
10. ✅ SometimesPattern.kt

### Phase 3: Pattern Creation (Day 5)

11. ✅ EuclideanPattern.kt
12. ✅ EuclideanMorphPattern.kt
13. ✅ RandrunPattern.kt
14. ✅ RandLPattern.kt

### Phase 4: DSL & Playback (Day 6-7)

15. ✅ lang_structural.kt
16. ✅ lang_sample.kt
17. ✅ lang_conditional.kt
18. ✅ StrudelPlayback.kt

### Phase 5: Supporting & Verification (Day 8)

19. ✅ StrudelVoiceData.kt
20. ✅ StrudelPatternEvent.kt

### Phase 6: Comment Out Accessors (Day 9)

- Comment out the three convenience accessors
- Compile to find any missed usages
- Fix any remaining compilation errors
- Verify tests still pass

### Phase 7: Final Verification (Day 10)

- Run full test suite
- Check for any regressions
- Update documentation

## Decision Matrix for Ambiguous Cases

When replacing, ask:

| Scenario                                  | Use                             | Reasoning                 |
|-------------------------------------------|---------------------------------|---------------------------|
| Checking if event intersects query range  | `part.begin/end`                | Part is visible time      |
| Querying sub-pattern for event's duration | `part.begin/end`                | Query for visible portion |
| Calculating event duration                | `part.duration`                 | Visible duration          |
| Sorting events by time                    | `part.begin`                    | Sort by visible time      |
| Checking if event starts in cycle         | `part.begin`                    | Check visible start       |
| Reversing time                            | Both `part` and `whole`         | Must reverse both         |
| Scaling time                              | Both `part` and `whole`         | Must scale both           |
| Shifting time                             | Both `part` and `whole`         | Must shift both           |
| Creating new event                        | Set `part` and `whole`          | Both must be set          |
| Clipping event                            | Modify `part`, preserve `whole` | Critical!                 |

## Quality Checks for Each File

After fixing each file:

### 1. Compilation Check

```bash
./gradlew :strudel:compileKotlinJvm
```

### 2. Pattern Verification

- [ ] All intersections use `part.begin/end`?
- [ ] All sub-queries use `part.begin/end`?
- [ ] All durations use `part.duration`?
- [ ] Time transformations handle both `part` and `whole`?
- [ ] Clipping operations preserve `whole`?

### 3. Logic Check

- [ ] Does the operation create events? → Set both `part` and `whole`
- [ ] Does it clip events? → Modify `part`, preserve `whole`
- [ ] Does it scale/shift? → Transform both `part` and `whole`
- [ ] Is it pass-through? → Should not modify at all

## Special Cases to Watch For

### ReversePattern

Must reverse BOTH part and whole:

```kotlin
event.copy(
    part = TimeSpan(pivot - event.part.end, pivot - event.part.begin),
    whole = event.whole?.let { w -> TimeSpan(pivot - w.end, pivot - w.begin) }
)
```

### SwingPattern

Calculates offset from part.begin but must shift both:

```kotlin
val offset = calculateSwingOffset(event.part.begin, ...)
event.copy(
    part = event.part.shift(offset),
    whole = event.whole?.shift(offset)
)
```

### Continuous Pattern Handling

When whole is null (continuous patterns):

```kotlin
// If we need a whole for operations, use part
val effectiveWhole = event.whole ?: event.part
```

## Progress Tracking

Create: `docs/agent-tasks/accessor-replacement-progress.txt`

```
✅ = Fixed and verified
⏳ = In progress
⬜ = Not started

Tier 1 (Critical):
⬜ StrudelPattern.kt (9 usages)
⬜ PickSqueezePattern.kt (7 usages)
⬜ PickRestartPattern.kt (6 usages)
⬜ StructurePattern.kt (3 usages)
⬜ PickResetPattern.kt (3 usages)

Tier 2 (Time Transforms):
⬜ ReversePattern.kt (6 usages)
⬜ SwingPattern.kt (4 usages)
⬜ TakePattern.kt (5 usages)
⬜ DropPattern.kt (4 usages)
⬜ SometimesPattern.kt (6 usages)

Tier 3 (Creation):
⬜ EuclideanPattern.kt (3 usages)
⬜ EuclideanMorphPattern.kt (6 usages)
⬜ RandrunPattern.kt (3 usages)
⬜ RandLPattern.kt (3 usages)

Tier 4 (DSL/Playback):
⬜ lang_structural.kt (7 usages)
⬜ lang_sample.kt (5 usages)
⬜ lang_conditional.kt (3 usages)
⬜ StrudelPlayback.kt (4 usages)

Tier 5 (Supporting):
⬜ StrudelVoiceData.kt (2 usages)
⬜ StrudelPatternEvent.kt (2 usages)

Final:
⬜ Comment out accessors
⬜ Fix any remaining compilation errors
⬜ Run full test suite
⬜ Verify 98 → 0 test failures (goal)
```

## Estimated Timeline

- **Phase 1-2 (Tier 1-2)**: 2-3 days
- **Phase 3-4 (Tier 3-4)**: 2-3 days
- **Phase 5-7 (Tier 5 + Final)**: 2-3 days
- **Total**: 6-9 days of focused work

## Success Metrics

- [ ] All 113 usages replaced with explicit `part.`/`whole.` access
- [ ] Compilation succeeds after commenting out accessors
- [ ] All tests pass (98 → 0 failures)
- [ ] No performance regression
- [ ] Code is more explicit and maintainable

## Next Step

Start with **StrudelPattern.kt** (9 usages, most critical file) →
Fix all usages → Compile → Verify → Move to next file
