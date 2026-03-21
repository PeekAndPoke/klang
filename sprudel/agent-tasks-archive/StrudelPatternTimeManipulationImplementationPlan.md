# Strudel Pattern Operations - Step & Time Manipulation

This document outlines the tasks required to implement the missing Step-Based Pattern Operations.

## 🎯 Goal

Implement pure pattern manipulation functions that alter the timing, duration, or selection of events.

**Features:**

- `pace(n)` (alias: `steps`) - Sets the speed so the pattern completes in `n` steps.
- `take(n)` - Keeps only the first `n` cycles (or events, dependent on implementation).
- `drop(n)` - Skips the first `n` cycles.
- `repeatCycles(n)` - Repeats the pattern `n` times.
- `extend(factor)` - Slows down / stretches the pattern.

---

## 🛠️ Implementation Steps

**File:** `strudel/src/commonMain/kotlin/lang/lang_structural.kt`
*(Or `lang_time.kt` if appropriate)*

### 1. `pace(n)` / `steps(n)`

- **Concept:** Resets the cycle duration relative to a number of steps.
- **Implementation:** Usually involves `fast` or `slow` calculated against the pattern's natural duration.

### 2. `take(n)` & `drop(n)`

- **Concept:** Standard list-like operations but applied to the infinite stream of cycles.
- **Implementation:** Modifies the `queryArc` to filter out time ranges before/after the cut points.

### 3. `repeatCycles(n)`

- **Concept:** Loops a specific section.

### 4. `iter(n)` & `iterBack(n)`

- **Concept:** Divides the cycle into `n` slices and shifts the view for each cycle.
- **Logic:** `time + (cycle_index * (1/n))` modulo 1.

---
