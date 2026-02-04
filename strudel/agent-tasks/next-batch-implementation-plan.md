# Next Batch DSL Functions - Implementation Plan

## Summary

Based on analysis of TODOS.MD and JavaScript original implementations in `originaljs/pattern.mjs`, here's a detailed
plan for implementing the next batch of high-priority DSL functions.

**Total Functions**: 13 functions (10 primary + 3 sample manipulation)
**Estimated Complexity**: Low to Medium (most use existing primitives)
**Expected Implementation Time**: 2-3 sessions

---

## Priority 1: Simple Composition Functions (EASY - 4 functions)

These functions are simple compositions of existing primitives. Can be implemented quickly.

### 1. `linger(t)` - Pattern Time Window Selection

**JavaScript Implementation**:

```javascript
export const linger = register('linger', function (t, pat) {
    if (t == 0) {
        return silence;
    } else if (t < 0) {
        return pat._zoom(t.add(1), 1)._slow(t);
    }
    return pat._zoom(0, t)._slow(t);
}, true, true);
```

**What it does**: Selects and repeats a fraction of the pattern.

- `t = 0`: Returns silence
- `t < 0`: Zooms into the end portion `[1+t, 1]` and slows by `|t|`
- `t > 0`: Zooms into the start portion `[0, t]` and slows by `t`

**Kotlin Implementation Strategy**:

- Use existing `zoom()` and `slow()` functions
- Support control patterns for `t`
- File: `lang_temporal.kt` or create new `lang_linger.kt`

**Example**:

```kotlin
s("lt ht mt cp, [hh oh]*2").linger("<1 .5 .25 .125>")
```

### 2. `press()` - Syncopation (Half-beat Shift)

**JavaScript Implementation**:

```javascript
export const press = register('press', function (pat) {
    return pat._pressBy(0.5);
});
```

**What it does**: Syncopates rhythm by shifting each event halfway into its timespan.

**Kotlin Implementation Strategy**:

- Simply calls `pressBy(0.5)`
- One-liner implementation
- File: `lang_temporal.kt`

**Example**:

```kotlin
s("bd mt sd ht").every(4, { it.press() })
```

### 3. `pressBy(r)` - Adjustable Syncopation

**JavaScript Implementation**:

```javascript
export const pressBy = register('pressBy', function (r, pat) {
    return pat.fmap((x) => pure(x).compress(r, 1)).squeezeJoin();
});
```

**What it does**: Compresses each event to start at position `r` within its timespan.

- `r = 0`: No compression (normal timing)
- `r = 0.5`: Events start halfway through (syncopated)
- `r = 1`: Events compressed to end

**Kotlin Implementation Strategy**:

- Use `fmap()` to map over values
- Use `compress()` on pure pattern
- Use `squeezeJoin()` to flatten
- Support control patterns for `r`
- File: `lang_temporal.kt`

**Example**:

```kotlin
s("bd mt sd ht").pressBy("<0 0.5 0.25>")
```

### 4. `ribbon(offset, cycles)` / `rib()` - Loop Time Window

**JavaScript Implementation**:

```javascript
export const {ribbon, rib} = register(['ribbon', 'rib'], (offset, cycles, pat) =>
    pat.early(offset).restart(pure(1).slow(cycles))
);
```

**What it does**: Loops a specific time window from the pattern timeline.

- Think of time as a ribbon
- Cut a piece at `offset` for `cycles` duration
- Loop that piece

**Kotlin Implementation Strategy**:

- Use `early(offset)` to shift pattern
- Use `restart()` with slow trigger pattern
- Support control patterns for both parameters
- File: `lang_temporal.kt`

**Example**:

```kotlin
note("<c d e f>").ribbon(1, 2)  // Loop cycles 1-3
n(irand(8).segment(4)).scale("c:pentatonic").ribbon(1337, 2)  // Loop random seed
```

---

## Priority 2: Chunk Family Functions (MEDIUM - 6 functions)

These build on the existing `chunk()` implementation. The helper `_chunk()` is likely already implemented.

### 5. `chunkBack(n, func)` / `chunkback()` - Reverse Chunk Order

**JavaScript Implementation**:

```javascript
export const {chunkBack, chunkback} = register(['chunkBack', 'chunkback'],
    function (n, func, pat) {
        return _chunk(n, func, pat, true);
    }, true, true
);
```

**What it does**: Like `chunk()` but cycles through parts in reverse order.

**Kotlin Implementation Strategy**:

- Call `_chunk(n, func, pat, back = true)`
- Verify `_chunk()` helper exists in `lang_chunk.kt`
- File: `lang_chunk.kt`

**Example**:

```kotlin
"0 1 2 3".chunkBack(4) { it.add(7) }.scale("A:minor").note()
```

### 6. `fastChunk(n, func)` / `fastchunk()` - Non-Repeating Chunk

**JavaScript Implementation**:

```javascript
export const {fastchunk, fastChunk} = register(['fastchunk', 'fastChunk'],
    function (n, func, pat) {
        return _chunk(n, func, pat, false, true);
    }, true, true
);
```

**What it does**: Like `chunk()` but source pattern cycles aren't repeated.

**Kotlin Implementation Strategy**:

- Call `_chunk(n, func, pat, back = false, fast = true)`
- File: `lang_chunk.kt`

**Example**:

```kotlin
"<0 8> 1 2 3 4 5 6 7".scale("C2:major").note()
    .fastChunk(4) { it.color("red") }.slow(2)
```

### 7. `chunkInto(n, func)` / `chunkinto()` - Looped Subcycle Chunk

**JavaScript Implementation**:

```javascript
export const {chunkinto, chunkInto} = register(['chunkinto', 'chunkInto'],
    function (n, func, pat) {
        return pat.into(
            fastcat(true, ...Array(n - 1).fill(false))._iterback(n),
            func
        );
    }
);
```

**What it does**: Like `chunk()` but applied to a looped subcycle of source pattern.

**Kotlin Implementation Strategy**:

- Use `into()` with boolean trigger pattern
- Create pattern: `fastcat(true, false, false, ...).iterBack(n)`
- Verify `into()` exists (may need to check if implemented)
- File: `lang_chunk.kt`

**Example**:

```kotlin
sound("bd sd ht lt bd - cp lt").chunkInto(4) { it.hurry(2) }.bank("tr909")
```

### 8. `chunkBackInto(n, func)` / `chunkbackinto()` - Reverse Looped Subcycle

**JavaScript Implementation**:

```javascript
export const {chunkbackinto, chunkBackInto} = register(['chunkbackinto', 'chunkBackInto'],
    function (n, func, pat) {
        return pat.into(
            fastcat(true, ...Array(n - 1).fill(false))._iter(n)._early(1),
            func
        );
    }
);
```

**What it does**: Like `chunkInto()` but moves backwards through chunks.

**Kotlin Implementation Strategy**:

- Use `into()` with boolean trigger pattern
- Create pattern: `fastcat(true, false, false, ...).iter(n).early(1)`
- File: `lang_chunk.kt`

**Example**:

```kotlin
sound("bd sd ht lt bd - cp lt").chunkBackInto(4) { it.hurry(2) }.bank("tr909")
```

### 9-10. Verify `_chunk()` Helper and `into()` Function

**Need to check**:

1. Does `_chunk()` helper exist in `lang_chunk.kt`?
2. Does `into()` function exist? (Used by chunkInto variants)

**`_chunk()` JavaScript Implementation** (for reference):

```javascript
const _chunk = function (n, func, pat, back = false, fast = false) {
    const binary = Array(n - 1).fill(false);
    binary.unshift(true);
    const binary_pat = _iter(n, sequence(...binary), !back);
    if (!fast) {
        pat = pat.repeatCycles(n);
    }
    return pat.when(binary_pat, func);
};
```

**What it does**:

- Creates binary trigger pattern `[true, false, false, ...]`
- Uses `_iter()` to shift pattern (forward or backward)
- Optionally repeats pattern cycles
- Applies function when trigger is true

**If `_chunk()` doesn't exist**:

- Implement in `lang_chunk.kt` as private helper
- Use existing `iter()`, `sequence()`, `repeatCycles()`, `when()`

---

## Priority 3: Pattern Alternation (MEDIUM - 1 function)

### 11. `stepalt(...groups)` / `s_alt()` - Alternate Between Pattern Groups

**JavaScript Implementation**:

```javascript
export function stepalt(...groups) {
    groups = groups.map((a) => (Array.isArray(a) ? a.map(reify) : [reify(a)]));

    const cycles = lcm(...groups.map((x) => Fraction(x.length)));

    let result = [];
    for (let cycle = 0; cycle < cycles; ++cycle) {
        result.push(...groups.map((x) => (x.length == 0 ? silence : x[cycle % x.length])));
    }
    result = result.filter((x) => x.hasSteps && x._steps > 0);
    const steps = result.reduce((a, b) => a.add(b._steps), Fraction(0));
    result = stepcat(...result);
    result._steps = steps;
    return result;
}
```

**What it does**: Alternates between groups of patterns using LCM for alignment.

- Each group can be a single pattern or array of patterns
- Cycles through groups, picking one pattern per group per cycle
- Uses LCM to determine total cycles needed
- Concatenates results using `stepcat()`

**Kotlin Implementation Strategy**:

- Convert arguments to pattern groups
- Calculate LCM of group lengths
- Build result by cycling through groups
- Use `stepcat()` to concatenate
- Set `_steps` property on result
- File: Create new `lang_stepalt.kt`

**Dependencies**:

- Need LCM (Least Common Multiple) function for Rational numbers
- Need `reify()` equivalent (convert values to patterns)
- Need `stepcat()` (already implemented)

**Example**:

```kotlin
stepalt(listOf("bd", "cp"), "bd").sound()
// Same as "bd cp bd mt bd".sound()
```

---

## Priority 4: Sample Manipulation (COMPLEX - 3 functions)

These are more complex and require understanding of sample begin/end parameters.

### 12. `chop(n)` - Chop Sample into Slices

**JavaScript Implementation**:

```javascript
export const chop = register('chop', function (n, pat) {
    const slices = Array.from({length: n}, (x, i) => i);
    const slice_objects = slices.map((i) => ({begin: i / n, end: (i + 1) / n}));
    const merge = function (a, b) {
        if ('begin' in a && 'end' in a && a.begin !== undefined && a.end !== undefined) {
            const d = a.end - a.begin;
            b = {begin: a.begin + b.begin * d, end: a.begin + b.end * d};
        }
        return Object.assign({}, a, b);
    };
    const func = function (o) {
        return sequence(slice_objects.map((slice_o) => merge(o, slice_o)));
    };
    return pat.squeezeBind(func).setSteps(__steps ? Fraction(n).mulmaybe(pat._steps) : undefined);
});
```

**What it does**: Chops samples into n equal slices, plays them in sequence.

- Creates slice definitions `{begin: 0/n, end: 1/n}`, `{begin: 1/n, end: 2/n}`, etc.
- Merges with existing begin/end values
- Uses `squeezeBind()` to apply slicing
- Updates step count

**Kotlin Implementation Strategy**:

- Create slice objects for each portion
- Implement merge function for begin/end values
- Use `squeezeBind()` (verify it exists)
- Support control patterns for `n`
- File: `lang_sample_manipulation.kt`

**Example**:

```kotlin
s("breaks165").chop(4).rev().loopAt(2)
```

### 13. `striate(n)` - Progressive Sample Portions

**JavaScript Implementation**:

```javascript
export const striate = register('striate', function (n, pat) {
    const slices = Array.from({length: n}, (x, i) => i);
    const slice_objects = slices.map((i) => ({begin: i / n, end: (i + 1) / n}));
    const slicePat = slowcat(...slice_objects);
    return pat.set(slicePat)._fast(n).setSteps(__steps ? Fraction(n).mulmaybe(pat._steps) : undefined);
});
```

**What it does**: Cuts sample into n parts, triggers progressive portions each loop.

- Creates slice pattern using `slowcat()`
- Uses `set()` to apply slices (verify `set()` exists)
- Speeds up by factor n
- Updates step count

**Kotlin Implementation Strategy**:

- Create slice objects
- Use `slowcat()` to sequence them
- Use `set()` to apply (need to verify/implement)
- Support control patterns for `n`
- File: `lang_sample_manipulation.kt`

**Example**:

```kotlin
s("numbers:0 numbers:1 numbers:2").striate(6).slow(3)
```

### 14. `fit()` - Match Sample to Event Duration

**JavaScript Implementation**:

```javascript
export const fit = register('fit', (pat) =>
    pat.withHaps((haps, state) =>
        haps.map((hap) =>
            hap.withValue((v) => {
                const slicedur = ('end' in v ? v.end : 1) - ('begin' in v ? v.begin : 0);
                return {
                    ...v,
                    speed: ((state.controls._cps || 1) / hap.whole.duration) * slicedur,
                    unit: 'c',
                };
            }),
        ),
    ),
);
```

**What it does**: Adjusts sample speed to match event duration.

- Uses `withHaps()` to access events and state
- Calculates slice duration from begin/end
- Sets speed based on CPS and event whole duration
- Sets unit to 'c' (cycles)

**Kotlin Implementation Strategy**:

- Need `withHaps()` or equivalent way to access state
- Need access to CPS (cycles per second) from state
- Calculate speed adjustment
- Set speed and unit on voice data
- File: `lang_sample_manipulation.kt`

**Dependencies**:

- `withHaps()` or similar state-accessing function
- Access to playback state/CPS

**Example**:

```kotlin
s("rhodes/2").fit()  // Adjusts speed to match event duration
```

---

## Implementation Order Recommendation

### Batch 1: Quick Wins (Session 1 - ~30 minutes)

1. ✅ `press()` - One-liner
2. ✅ `linger(t)` - Simple composition
3. ✅ `ribbon(offset, cycles)` / `rib()` - Simple composition
4. ✅ `pressBy(r)` - Uses existing functions

**Why first**: Easiest, high user value, builds confidence

### Batch 2: Chunk Variants (Session 1 - ~45 minutes)

5. ✅ Verify/implement `_chunk()` helper (if needed)
6. ✅ `chunkBack(n, func)` / `chunkback()`
7. ✅ `fastChunk(n, func)` / `fastchunk()`
8. ⚠️ Verify `into()` function exists
9. ⚠️ `chunkInto(n, func)` / `chunkinto()` - May need `into()` implementation
10. ⚠️ `chunkBackInto(n, func)` / `chunkbackinto()` - May need `into()` implementation

**Why second**: Logical grouping, builds on existing chunk() infrastructure

### Batch 3: Pattern Alternation (Session 2 - ~30 minutes)

11. ✅ Implement LCM helper for Rational numbers
12. ✅ `stepalt(...groups)` / `s_alt()` - More complex logic

**Why third**: Requires helper function, moderate complexity

### Batch 4: Sample Manipulation (Session 2-3 - ~90 minutes)

13. ⚠️ `chop(n)` - Needs `squeezeBind()`
14. ⚠️ `striate(n)` - Needs `set()`
15. ⚠️ `fit()` - Needs `withHaps()` and state access

**Why last**: Most complex, may require new infrastructure

---

## Pre-Implementation Checks

Before starting, verify these functions exist:

### Required Functions (Already Implemented)

- ✅ `zoom(start, end)` - Used by `linger()`
- ✅ `slow(factor)` - Used by `linger()`, `ribbon()`
- ✅ `compress(start, end)` - Used by `pressBy()`
- ✅ `early(offset)` - Used by `ribbon()`, `chunkBackInto()`
- ✅ `restart()` - Used by `ribbon()`
- ✅ `pure(value)` - Used by `ribbon()`, `pressBy()`
- ✅ `fmap()` - Used by `pressBy()`
- ✅ `squeezeJoin()` - Used by `pressBy()`
- ✅ `when(condition, func)` - Used by `_chunk()`
- ✅ `repeatCycles(n)` - Used by `_chunk()`
- ✅ `iter()` - Used by `chunkBackInto()`
- ✅ `iterBack()` - Used by `chunkInto()`
- ✅ `fastcat()` - Used by chunk variants
- ✅ `sequence()` - Used by `_chunk()`, `chop()`
- ✅ `stepcat()` - Used by `stepalt()`
- ✅ `fast(factor)` - Used by `striate()`

### Need to Check/Implement

- ⚠️ `into(pattern, func)` - Used by `chunkInto()` and `chunkBackInto()`
- ⚠️ `squeezeBind(func)` - Used by `chop()`
- ⚠️ `set(pattern)` - Used by `striate()`
- ⚠️ `withHaps(func)` - Used by `fit()`
- ⚠️ `setSteps(steps)` - Used by sample manipulation functions
- ⚠️ LCM function for Rational - Used by `stepalt()`

### Search Commands

```bash
# Check if these exist
grep -r "fun into\|fun squeezeBind\|fun set\|fun withHaps\|fun setSteps" strudel/src/
grep -r "fun lcm" strudel/src/
```

---

## Testing Strategy

### Unit Tests

Each function should have tests in corresponding `*Spec.kt` file:

1. **LangLingerSpec.kt**
    - Test `linger(1)` (full pattern)
    - Test `linger(0.5)` (first half)
    - Test `linger(-0.5)` (last half)
    - Test `linger(0)` (silence)
    - Test with control patterns

2. **LangPressSpec.kt**
    - Test `press()` (default 0.5 syncopation)
    - Test `pressBy(0.25)`, `pressBy(0.75)`
    - Test with control patterns
    - Verify event timing shifts

3. **LangRibbonSpec.kt**
    - Test `ribbon(0, 1)` (loop first cycle)
    - Test `ribbon(5, 2)` (loop cycles 5-7)
    - Test with control patterns
    - Test negative offsets

4. **LangChunkSpec.kt** (update existing)
    - Test `chunkBack()` vs `chunk()` order
    - Test `fastChunk()` non-repeating behavior
    - Test `chunkInto()` and `chunkBackInto()`

5. **LangStepaltSpec.kt**
    - Test alternating between groups
    - Test LCM calculation
    - Test with arrays and single patterns

6. **LangSampleManipulationSpec.kt**
    - Test `chop(4)` creates 4 slices
    - Test `striate(6)` progressive triggering
    - Test `fit()` speed adjustment (if state access available)

### JS Compatibility Tests

Add examples to `JsCompatTestData.kt`:

```kotlin
Example("Linger half", """s("bd cp").linger(0.5)"""),
Example("Press", """s("bd mt sd ht").press()"""),
Example("Ribbon", """note("c d e f").ribbon(1, 2)"""),
Example("Chop", """s("breaks165").chop(4)"""),
// etc.
```

---

## File Organization

### New Files to Create

1. **lang_linger.kt** - `linger()` function
2. **lang_press.kt** - `press()`, `pressBy()` functions
3. **lang_ribbon.kt** - `ribbon()` / `rib()` functions
4. **lang_stepalt.kt** - `stepalt()` / `s_alt()` and LCM helper
5. **lang_sample_slicing.kt** - `chop()`, `striate()`, `fit()` (or add to existing sample file)

### Files to Update

1. **lang_chunk.kt** - Add chunk variants, verify `_chunk()` helper
2. **JsCompatTestData.kt** - Add examples for all new functions

### Test Files to Create

1. **LangLingerSpec.kt**
2. **LangPressSpec.kt**
3. **LangRibbonSpec.kt**
4. **LangStepaltSpec.kt**
5. **LangSampleSlicingSpec.kt**

### Test Files to Update

1. **LangChunkSpec.kt** - Add tests for new chunk variants

---

## Implementation Checklist

### Before Starting

- [ ] Read this plan thoroughly
- [ ] Verify all prerequisite functions exist
- [ ] Check if `into()`, `squeezeBind()`, `set()`, `withHaps()` exist
- [ ] Check if LCM function exists for Rational numbers
- [ ] Review existing `chunk()` implementation for patterns

### Batch 1: Quick Wins

- [ ] Implement `press()`
- [ ] Implement `pressBy(r)` with control pattern support
- [ ] Implement `linger(t)` with control pattern support
- [ ] Implement `ribbon(offset, cycles)` / `rib()` with control pattern support
- [ ] Create test files with comprehensive coverage
- [ ] Add JsCompatTestData examples
- [ ] Run tests: `./gradlew :strudel:jvmTest`
- [ ] Verify all tests pass

### Batch 2: Chunk Variants

- [ ] Verify `_chunk()` helper exists in lang_chunk.kt
- [ ] If missing, implement `_chunk()` helper
- [ ] Implement `chunkBack()` / `chunkback()`
- [ ] Implement `fastChunk()` / `fastchunk()`
- [ ] Check if `into()` exists
- [ ] If `into()` missing, implement or find alternative
- [ ] Implement `chunkInto()` / `chunkinto()`
- [ ] Implement `chunkBackInto()` / `chunkbackinto()`
- [ ] Update LangChunkSpec.kt with new tests
- [ ] Add JsCompatTestData examples
- [ ] Run tests
- [ ] Verify all tests pass

### Batch 3: Pattern Alternation

- [ ] Implement LCM function for Rational numbers (if missing)
- [ ] Implement `stepalt(...groups)` / `s_alt()`
- [ ] Create LangStepaltSpec.kt with tests
- [ ] Add JsCompatTestData examples
- [ ] Run tests
- [ ] Verify all tests pass

### Batch 4: Sample Manipulation

- [ ] Check if `squeezeBind()` exists
- [ ] Check if `set()` exists
- [ ] Check if `withHaps()` exists
- [ ] Check if `setSteps()` exists
- [ ] Implement `chop(n)` (may need squeezeBind)
- [ ] Implement `striate(n)` (may need set)
- [ ] Implement `fit()` (may need withHaps + state access)
- [ ] Create LangSampleSlicingSpec.kt with tests
- [ ] Add JsCompatTestData examples
- [ ] Run tests
- [ ] Document any blockers (missing infrastructure)

### After All Batches

- [ ] Update TODOS.MD to mark functions as complete
- [ ] Update feature count in TODOS.MD summary
- [ ] Run full test suite: `./gradlew :strudel:test`
- [ ] Update CLAUDE.md with any architectural notes
- [ ] Commit changes with clear message

---

## Expected Outcomes

After completing all batches:

1. **13 new DSL functions** implemented (or 10-11 if sample manipulation blocked)
2. **Feature completion**: ~273/303 (90%) - up from ~263/303 (87%)
3. **Test coverage**: Maintain 100% pass rate
4. **User value**: Significant improvement in pattern manipulation capabilities

## Known Risks

1. **Missing infrastructure**: `into()`, `squeezeBind()`, `set()`, `withHaps()` may not exist
    - **Mitigation**: Check first, implement alternatives or document as blocked

2. **Sample manipulation complexity**: May require audio engine understanding
    - **Mitigation**: Start with simpler functions, document blockers for complex ones

3. **State access for `fit()`**: Needs CPS from playback state
    - **Mitigation**: May need to defer `fit()` or find workaround

4. **Step count tracking**: `setSteps()` may not be implemented
    - **Mitigation**: Functions will work without step tracking, add later if needed

---

## Success Criteria

✅ **Minimum Success**: Batches 1-2 complete (10 functions)
✅ **Target Success**: Batches 1-3 complete (11 functions)
✅ **Stretch Success**: All batches complete (13 functions)

All implementations should:

- Match JavaScript behavior
- Support control patterns where applicable
- Have comprehensive unit tests
- Pass JS compatibility tests
- Maintain 100% test pass rate
