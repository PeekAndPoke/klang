# Project-Wide Code Quality Review — Open Items

Remaining items from the quality review (2026-03-31). Completed items archived below.

---

## OPEN — HIGH PRIORITY

### H3. `AstToKBlocks.convertStmt()` silently drops loop statements

- **File:** `klangblocks/.../AstToKBlocks.kt:57` — `else -> null`
- **Impact:** `WhileStatement`, `DoWhileStatement`, `ForStatement`, `BreakStatement`, `ContinueStatement` are silently
  lost on round-trip through the block editor
- **Fix:** Add block representations for these statements, or at minimum surface a warning
- **Note:** This is a feature gap, not a cleanup. Requires designing block representations for loops.

---

## OPEN — MEDIUM PRIORITY

### M9. Sprudel `Pair` allocations in pattern query hot paths

- `PatternQueryUtils.kt:114,130,156,170` — inline functions return `Pair` (not elided by inlining)
- **Fix:** Use a reusable mutable result holder or destructure differently

### M10. Sprudel per-query sorting

- `StackPattern.kt:29` — `sortedBy` on every query (O(n log n) + allocation)
- `EuclideanPattern.kt:378` — same issue
- **Fix:** Remove or make debug-only

---

## OPEN — LOW PRIORITY

### L2. Scattered helper functions

- `audio_be/util.kt` — `TWO_PI`, `ONE_OVER_TWELVE` should be in `DspUtil.kt`
- `Exciters.kt` — `wrapPhase()`, `polyBlep()` locked as private, should be shared DSP utils
- `sprudel/ui/_utils.kt` — `Note.staffPosition()` belongs in `tones`, numeric utils in `common`
- `klangscript/index_common.kt:46` — `KClass<*>.getUniqueClassName()` belongs in common

### L3. Boxed types in non-hot-path code (low impact but violate project rule)

- `klangscript/RuntimeValue.kt:44,47` — `toLongOrNull()`, `toLongOr()` helpers
- `klangscript/NativeInterop.kt:70-71,93-95` — `Short`, `Byte`, `LongArray`, `ByteArray`, `ShortArray` class matching
- `klangui/comp/_utils.kt:16` — `lowercaseChar()` returns Char
- `common/BerlinNoise.kt:17` — Long multiplication for hash

### L4. Sprudel DSL boilerplate duplication

- `lang_pattern_picking.kt` (2594 lines) — 5 near-identical `dispatch*` functions, ~16 functions per variant
- `lang_euclid.kt` — arg-parsing `when` block duplicated 10+ times
- `SprudelVoiceValue.kt:292-349` — fraction parsing duplicated between decoder branches
- **Constraint:** only unify internals; user-facing DSL functions and registration must not change
- Large refactor with risk; flag for future sprint

### L6. `Char` boxing in tones module (non-hot-path)

- `Note.kt:284`, `AbcNotation.kt:37,49`, `RhythmPattern.kt:36`, `Tones.kt:36`, `Chord.kt:161`
- Low frequency, negligible impact

---

## COMPLETED (archived 2026-03-31)

<details>
<summary>Click to expand completed items</summary>

### Decisions Made

- Platform-specific optimizations get "why" comments
- Bit tricks get "how it works" comments
- Common math/formatting utils in `common` module
- DSP utils stay in `audio_be/DspUtil.kt`
- Generic infra (KlangMinHeap, KlangRingBuffer, KlangMessageSender/Receiver) moved to `common/infra/`
- Audio renderer must NEVER die — `KLANG_AUDIO_DEBUG` compile-time flag added
- File naming: PascalCase for class files, lowercase with `_` OK for utility files

### Completed Items

| ID  | Description                                                                                   | Step |
|-----|-----------------------------------------------------------------------------------------------|------|
| H1  | Long-to-Int cascade through entire audio chain (22 files, ~12.4h overflow documented)         | 3    |
| H2  | Char boxing eliminated in KlangScriptParser + MiniNotationParser (IntArray + named constants) | 4    |
| H4  | `isInsideString()` fixed to handle both `"` and `'` quotes + deduplicated                     | 7    |
| H5  | `isPowerOfTwo` — bitwise fix in common + TimeSignature, with tests                            | 1    |
| H6  | Division-by-zero guards in SequencePattern + ratioMutation, with tests                        | 1    |
| M1  | Audio thread allocations fixed (pre-allocated set, deferred mutation pattern)                 | 5    |
| M2  | Denormal flushing added in Karplus-Strong + Super Karplus-Strong                              | 5    |
| M3  | `error("unreachable")` → `return@Exciter` in ExciterFm + ExciterPitchMod                      | 5    |
| M4  | ResolvedShape / resolveDistortionShape extracted to shared DistortionShape.kt                 | 6    |
| M5  | Number-to-integer-string formatting → `Double.formatAsIntOrDouble()` in common (5 sites)      | 6    |
| M6  | KBCodeGen Long cache key → String key `"$line:$col"`                                          | 6    |
| M7  | Errors.kt format() deduplicated via `formatPrefix()` / `formatHeader()` base methods          | 6    |
| M8  | `roundToLong()` → `kotlin.math.round()` (avoids Long boxing)                                  | 1    |
| M11 | `1.0.toRational()` → `Rational.ONE` in RepeatCyclesPattern                                    | 1    |
| M12 | Badge container DOM leak fixed (attached to editor DOM, not document.body)                    | 1    |
| M13 | Undo stack capped at 1,000                                                                    | 1    |
| M14 | Double `asSampleRequest()` call → reuse `req` variable                                        | 1    |
| L1  | File naming rule clarified in code-style skill (not a violation)                              | 1    |
| L5  | Interpreter `else -> error()` branches uncommented                                            | 1    |
| L7  | `isInsideString()` deduplicated (shared with H4 fix)                                          | 7    |

### Infra Moves

- `KlangMinHeap` → `common/infra/` + `removeWhen` rewritten (in-place compact + Floyd's heapify)
- `KlangRingBuffer` → `common/infra/`
- `KlangMessageSender` → `common/infra/`
- `KlangMessageReceiver` → `common/infra/`

### New Shared Utilities

- `common/math/math.kt`: `Int.isPowerOfTwo()`, `Double.formatAsIntOrDouble()`
- `audio_be/DistortionShape.kt`: `ResolvedShape`, `resolveDistortionShape()`
- `audio_bridge/KlangAudioDebug.kt`: `KLANG_AUDIO_DEBUG` compile-time flag

### New Tests

- `IsPowerOfTwoSpec.kt` — powers, non-powers, zero, negatives, boundaries
- `FormatAsIntOrDoubleSpec.kt` — integers, fractions, -0, NaN, Infinity, Int boundaries
- `KlangMinHeapSpec.kt` — 11 test cases for removeWhen (empty, single, remove all/none/middle/first/last/root)
- `KBProgramEditingCtxSpec.kt` — undo cap at 1000, undo/redo correctness
- `SequencePatternSpec.kt` — zero-weight fallback, mixed zero/non-zero weights
- `LangRatioSpec.kt` — zero divisor produces null value

</details>
