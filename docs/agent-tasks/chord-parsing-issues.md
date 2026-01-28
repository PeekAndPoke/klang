# Chord Parsing Issues and Solutions

**Date:** 2026-01-28
**Component:** `tones` module - Chord parsing system
**Status:** Analysis complete, awaiting implementation

## Executive Summary

Testing revealed that **22 out of 90 chord notations** from the Strudel test suite fail to parse correctly or return
wrong notes. Issues fall into 4 categories:

1. **Tokenization Bug** (1 chord) - Critical parsing issue
2. **Missing Aliases** (13 chords) - Chord types exist but lack alternative notation support
3. **Missing Chord Types** (3 chords) - Completely undefined chord types
4. **Wrong Voicings** (2 chords) - Parse successfully but return incorrect notes
5. **Suspicious/Need Verification** (3 chords) - Potentially incorrect but need music theory verification

---

## 1. Critical Tokenization Bug 🔴

### Issue: Csus

**Problem:**

```kotlin
Chord.tokenize("Csus") // Returns: tonic='Cs', type='us', bass=''
// Expected: tonic='C', type='sus', bass=''
```

The tokenizer incorrectly interprets the 's' in 'sus' as part of the note name (likely treating it as a sharp
indicator), resulting in parsing 'Csus' as C-sharp + 'us' instead of C + 'sus'.

**Impact:** Any chord name ending with 'sus' where the tonic is C could be affected. The dictionary has the 'sus' alias
correctly defined.

**Location:** `/opt/dev/peekandpoke/klang/tones/src/commonMain/kotlin/chord/Chord.kt:68-80`

### Solution Plan A: Fix Note Tokenization

The `Chord.tokenize()` function calls `Note.tokenize()` which seems to be too greedy in accepting characters as part of
the note name.

**Investigation needed:**

1. Check `Note.tokenize()` implementation to understand how it determines note boundaries
2. Determine if this affects other chord names (e.g., Dsus, Esus, etc.)
3. Fix the tokenizer to properly handle 'sus' as a chord type suffix

**Steps:**

```kotlin
// In Chord.tokenize()
fun tokenize(name: String): Triple<String, String, String> = tokenizeCache.getOrPut(name) {
    val tokens = Note.tokenize(name)
    val letter = tokens[0]
    val acc = tokens[1]
    val oct = tokens[2]
    val rest = tokens[3]

    // TODO: Add special handling for 'sus' before calling tokenizeBass
    // Check if rest starts with 'sus' and letter+acc is a valid note
    // If so, don't let Note.tokenize consume the 's' from 'sus'

    when {
        letter == "" -> tokenizeBass("", name)
        letter == "A" && rest == "ug" -> tokenizeBass("", "aug")
        else -> tokenizeBass(letter + acc, oct + rest)
    }
}
```

### Solution Plan B: Pre-process Known Patterns

Add special case handling before tokenization for known problematic patterns:

```kotlin
fun tokenize(name: String): Triple<String, String, String> = tokenizeCache.getOrPut(name) {
    // Special handling for 'sus' chords
    val adjustedName = if (name.matches(Regex("^[A-G][#b]*sus.*"))) {
        // Ensure proper boundary between note and 'sus'
        // ...
    } else name

    // Continue with normal tokenization
    val tokens = Note.tokenize(adjustedName)
    // ...
}
```

**Recommended:** Solution Plan A - fix at the root cause level

---

## 2. Missing Chord Type Aliases ⚠️

These chords tokenize correctly but lack the necessary aliases in the ChordTypeDictionary.

### 2.1 Capital M Notation for Major Chords

| Chord      | Tokenizes To | Current Aliases             | Missing |
|------------|--------------|-----------------------------|---------|
| **CM9**    | type='M9'    | maj9, Δ9, ^9                | M9      |
| **CM13**   | type='M13'   | maj13, Maj13, ^13           | M13     |
| **CM7#5**  | type='M7#5'  | maj7#5, ^7#5, maj7+5, +maj7 | M7#5    |
| **CM9#11** | type='M9#11' | maj9#11, Δ9#11, ^9#11       | M9#11   |

**Location:** `/opt/dev/peekandpoke/klang/tones/src/commonMain/kotlin/chord/ChordType.kt`

**Solution:**

```kotlin
// Line 157 - Update major ninth
listOf("1P 3M 5P 7M 9M", "major ninth", "maj9 Δ9 ^9 M9"),

// Line 158 - Update major thirteenth
listOf("1P 3M 5P 7M 9M 13M", "major thirteenth", "maj13 Maj13 ^13 M13"),

// Line 203 - Update augmented seventh
listOf("1P 3M 5A 7M", "augmented seventh", "maj7#5 maj7+5 +maj7 ^7#5 M7#5"),

// Line 204 - Update major sharp eleventh
listOf("1P 3M 5P 7M 9M 11A", "major sharp eleventh (lydian)", "maj9#11 Δ9#11 ^9#11 M9#11"),
```

### 2.2 Caret Notation for Minor/Major Chords

| Chord    | Tokenizes To | Current Aliases            | Missing |
|----------|--------------|----------------------------|---------|
| **Cm^7** | type='m^7'   | m/ma7, mM7, -^7, mΔ, -maj7 | m^7     |
| **Cm^9** | type='m^9'   | mM9, mMaj9, -^9            | m^9     |

**Solution:**

```kotlin
// Line 168 - Update minor/major seventh
listOf("1P 3m 5P 7M", "minor/major seventh", "m/ma7 m/maj7 mM7 mMaj7 m/M7 -Δ7 mΔ -^7 m^7 -maj7"),

// Line 171 - Update minor/major ninth
listOf("1P 3m 5P 7M 9M", "minor/major ninth", "mM9 mMaj9 -^9 m^9"),
```

### 2.3 Dash Notation for Minor Chords

| Chord      | Tokenizes To | Current Aliases       | Missing |
|------------|--------------|-----------------------|---------|
| **C-M7**   | type='-M7'   | m/M7, mM7, -^7, -maj7 | -M7     |
| **C-M9**   | type='-M9'   | mM9, mMaj9, -^9       | -M9     |
| **C-add9** | type='-add9' | madd9                 | -add9   |

**Solution:**

```kotlin
// Line 168 - Update minor/major seventh (also add -M7)
listOf("1P 3m 5P 7M", "minor/major seventh", "m/ma7 m/maj7 mM7 mMaj7 m/M7 -M7 -Δ7 mΔ -^7 m^7 -maj7"),

// Line 171 - Update minor/major ninth (also add -M9)
listOf("1P 3m 5P 7M 9M", "minor/major ninth", "mM9 mMaj9 -M9 -^9 m^9"),

// Line 258 - Update minor add ninth
listOf("1P 3m 5P 9M", "", "madd9 -add9"),
```

### 2.4 Minor Flat Sixth Chords

| Chord    | Tokenizes To | Current Similar | Issue             |
|----------|--------------|-----------------|-------------------|
| **C-b6** | type='-b6'   | mb6M7, mb6b9    | No standalone -b6 |
| **Cmb6** | type='mb6'   | mb6M7, mb6b9    | No standalone mb6 |

**Current situation:** Only compound chords exist (mb6M7 at line 261, mb6b9 at line 265)

**Solution:** Add new chord type for standalone minor flat sixth:

```kotlin
// Add after line 169 (in minor section)
listOf("1P 3m 6m", "minor flat sixth", "mb6 -b6"),
```

**Note:** This creates intervals `[1P, 3m, 6m]` which gives us `[C, Eb, Ab]`. Verify this is musically correct for a "
minor flat sixth" chord (essentially a minor triad with b6 instead of 5th).

---

## 3. Missing Chord Types Entirely ❌

These chord types don't exist in the dictionary at all.

### 3.1 Ch9 (Half-Diminished Ninth)

**Current state:**

- `h` (half-diminished triad) exists: `1P 3m 5d` → "dim ° o"
- `h7` (half-diminished seventh) exists: `1P 3m 5d 7m` → "m7b5 ø -7b5 h7 h"
- `h9` (half-diminished ninth) **missing**

**Solution:**

```kotlin
// Add after line 178 (in diminished section)
listOf("1P 3m 5d 7m 9M", "half-diminished ninth", "h9"),
```

This gives: C, Eb, Gb, Bb, D

### 3.2 C7susadd3 (7sus with Added 3rd)

This is a suspended seventh chord with the major third added back in, creating a 4-note cluster.

**Solution:**

```kotlin
// Add after line 195 (in suspended section)
listOf("1P 3M 4P 5P 7m", "suspended fourth seventh with third", "7susadd3"),
```

This gives: C, E, F, G, Bb

**Alternative interpretation:** Maybe it should be part of the extended chords section with both 3M and 4P.

### 3.3 C7b13sus (Dominant Flat 13 Suspended)

Suspended dominant with flat 13th.

**Solution Option A - With ninth:**

```kotlin
// Add after line 273 (in suspended section)
listOf("1P 4P 5P 7m 9M 13m", "suspended fourth seventh flat thirteenth", "7b13sus 7susb13"),
```

This gives: C, F, G, Bb, D, Ab

**Solution Option B - Without ninth:**

```kotlin
listOf("1P 4P 5P 7m 13m", "suspended fourth seventh flat thirteenth", "7b13sus 7susb13"),
```

This gives: C, F, G, Bb, Ab

**Recommendation:** Need to check Strudel's JavaScript implementation to see which interpretation they use.

---

## 4. Wrong Chord Voicings 🐛

These chords parse successfully but return incorrect notes due to being grouped with the wrong chord type in the
dictionary.

### 4.1 C7#9b5 (Dominant Sharp Ninth Flat Fifth)

**Current behavior:**

```
C7#9b5 → [C, E, G, Bb, D#, F#]
Intervals: [1P, 3M, 5P, 7m, 9A, 11A]
```

**Problem:** Has natural 5th (G) and augmented 11th (F#) instead of flat 5th (Gb)

**Root cause:** Line 227 in ChordType.kt:

```kotlin
listOf("1P 3M 5P 7m 9A 11A", "", "7#9#11 7b5#9 7#9b5"),
```

The alias "7#9b5" is incorrectly grouped with "7#9#11" which has intervals `5P` and `11A`.

**Expected behavior:**

```
C7#9b5 → [C, E, Gb, Bb, D#]
Intervals: [1P, 3M, 5d, 7m, 9A]
```

**Solution:**

```kotlin
// Remove "7b5#9 7#9b5" from line 227
listOf("1P 3M 5P 7m 9A 11A", "", "7#9#11"),

// Add new entry for the correct voicing
listOf("1P 3M 5d 7m 9A", "dominant sharp ninth flat fifth", "7#9b5 7b5#9"),
```

### 4.2 C7b9b5 (Dominant Flat Ninth Flat Fifth)

**Current behavior:**

```
C7b9b5 → [C, E, G, Bb, Db, F#]
Intervals: [1P, 3M, 5P, 7m, 9m, 11A]
```

**Problem:** Same issue - has natural 5th and augmented 11th

**Root cause:** Line 235:

```kotlin
listOf("1P 3M 5P 7m 9m 11A", "", "7b9#11 7b5b9 7b9b5"),
```

**Expected behavior:**

```
C7b9b5 → [C, E, Gb, Bb, Db]
Intervals: [1P, 3M, 5d, 7m, 9m]
```

**Solution:**

```kotlin
// Remove "7b5b9 7b9b5" from line 235
listOf("1P 3M 5P 7m 9m 11A", "", "7b9#11"),

// Add new entry for the correct voicing
listOf("1P 3M 5d 7m 9m", "dominant flat ninth flat fifth", "7b9b5 7b5b9"),
```

---

## 5. Suspicious Chords - Need Verification 🤔

### 5.1 C7alt (Altered Dominant)

**Current behavior:**

```
C7alt → [C, E, G#, Bb, D#]
Intervals: [1P, 3M, 5A, 7m, 9A]
```

**Dictionary entry (line 210):**

```kotlin
listOf("1P 3M 5A 7m 9A", "", "7#5#9 7#9#5 7alt"),
```

**Question:** Is this the correct voicing for an "altered" chord?

Traditional jazz theory defines "altered" (or "super-locrian") as having:

- Root, 3M, 7m
- Both altered fifths (b5 and #5) OR omit the fifth
- Both altered ninths (b9 and #9) OR one of them

Common voicings:

1. `1P 3M 7m 9m` (no fifth, flat ninth) - **Currently this is "alt7" in line 190**
2. `1P 3M 5d 7m 9m` (flat fifth, flat ninth)
3. `1P 3M 5d 7m 9A` (flat fifth, sharp ninth)
4. Or larger voicings with multiple alterations

**Current "7alt" is actually:** `1P 3M 5A 7m 9A` (sharp fifth, sharp ninth)

**Recommendation:** Check Strudel.js implementation to see what they expect for "7alt"

### 5.2 C2 (Treated as Add9)

**Current behavior:**

```
C2 → [C, E, G, D]
Symbol: C2
```

**Tokenization:** type='2' maps to "Madd9 2 add9 add2" (line 241)

**Question:** Is "C2" supposed to be:

1. A suspended second chord (1P 2M 5P) → [C, D, G]
2. Or an add9 chord (1P 3M 5P 9M) → [C, E, G, D]

Currently it's being treated as option 2. However, "Csus2" exists and gives the suspended voicing.

**Observation:** The alias "2" in line 241 creates ambiguity. Users might expect "C2" to mean suspended second.

**Recommendation:** Verify Strudel's behavior. If they use "C2" for sus2, then remove "2" from the aliases in line 241.

### 5.3 Co7 (Diminished Seventh with Double Flat)

**Current behavior:**

```
Co7 → [C, Eb, Gb, Bbb]
```

**Note:** This returns Bbb (B double-flat) which is enharmonically equivalent to A natural.

**Question:** Is this the correct spelling for a diminished seventh, or should it be:

- [C, Eb, Gb, A] (simpler enharmonic spelling)

**Music theory:** Technically Bbb is correct because it maintains the interval pattern of stacked minor thirds (
C-Eb-Gb-Bbb = m3 + m3 + m3). However, in practical use, many musicians would expect "A" instead.

**Recommendation:** This is probably correct as-is, but document the behavior.

---

## Implementation Plan

### Phase 1: Critical Fix (Must Do)

1. **Fix Csus tokenization bug** - Investigate and fix Note.tokenize() or add special handling

### Phase 2: Add Missing Aliases (Easy Wins)

2. **Add M-prefix aliases** for CM9, CM13, CM7#5, CM9#11
3. **Add m^ aliases** for Cm^7, Cm^9
4. **Add dash aliases** for C-M7, C-M9, C-add9

### Phase 3: New Chord Types

5. **Add mb6/-b6** standalone chord type
6. **Add h9** (half-diminished ninth)
7. **Add 7susadd3** (research correct intervals first)
8. **Add 7b13sus** (research correct intervals first)

### Phase 4: Fix Wrong Voicings

9. **Split C7#9b5** into its own entry with correct intervals
10. **Split C7b9b5** into its own entry with correct intervals

### Phase 5: Verification & Testing

11. **Verify C7alt** voicing against Strudel.js
12. **Verify C2** behavior against Strudel.js
13. **Run comprehensive test suite** to ensure all 90 chord notations work correctly

---

## Testing Strategy

The comprehensive test file has been created at:
`/opt/dev/peekandpoke/klang/tones/src/commonTest/kotlin/chord/ChordNotationComprehensiveTest.kt`

After fixes are applied:

1. Run the comprehensive test: `./gradlew :tones:jvmKotest`
2. Verify all 90+ chord notations parse correctly
3. Compare voicings against Strudel.js reference implementation
4. Document any intentional differences from Strudel

---

## References

- **Strudel chord test data:** `/opt/dev/peekandpoke/klang/strudel/src/commonTest/kotlin/lang/LangChordParsingSpec.kt`
- **Chord dictionary:** `/opt/dev/peekandpoke/klang/tones/src/commonMain/kotlin/chord/ChordType.kt` (lines 153-278)
- **Chord parsing:** `/opt/dev/peekandpoke/klang/tones/src/commonMain/kotlin/chord/Chord.kt`
- **Debug test:** `/opt/dev/peekandpoke/klang/tones/src/commonTest/kotlin/chord/ChordNotationDebugTest.kt`

---

## Notes

- All testing was done with Java 23 (GraalVM)
- The chord dictionary is based on tonal.js/Strudel.js conventions
- Some chord names have multiple valid interpretations in music theory - we follow Strudel's conventions
