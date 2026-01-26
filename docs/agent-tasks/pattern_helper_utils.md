# Task: Pattern Helpers Extraction (GC‑Free Prep)

## Goal

Prepare the Strudel `pattern` implementation for a future **GC‑free** audio engine by **centralizing repeated allocation
hotspots** into helper utilities. This should reduce duplication and make it easier to introduce object pools later.

This is **not** a performance rewrite yet. It’s a structural refactor to make future pooling changes localized.

---

## Scope

Work in:

- `strudel/src/commonMain/kotlin/pattern/`
- Optionally add a helper file, e.g. `PatternQueryUtils.kt` (name up to you).

Do **not** change public DSL APIs.

---

## Priorities

Focus on repetitive logic that creates new objects in hot paths:

1. **Event list creation**
    - Many patterns do:
        - `val result = mutableListOf<StrudelPatternEvent>()`
        - Add mapped events
    - Centralize creation so it can later use pooled lists or buffers.

2. **Overlap / clipping logic**
    - Repeated “overlap begin/end” calculations:
        - `maxOf(begin...)`, `minOf(end...)`
        - `if (overlapEnd <= overlapBegin) continue`
    - Centralize in helper methods.

3. **Time mapping / scaling**
    - Common patterns:
        - `mappedBegin = ev.begin / scale`
        - `mappedEnd = ev.end / scale`
        - `mappedDur = ev.dur / scale`
    - Extract into helper.

4. **Static‑like fast paths**
    - Patterns that check “single event covering [from, to]”
    - A helper to detect this consistently.

---

## Do NOT

- Change behavior.
- Introduce pooling yet.
- Over‑abstract complex behavior (e.g., Euclidean rhythm generation).
- Create new allocations in the helper APIs.

---

## Suggested Helper API (example)

You can create something like:

(Exact design is up to you.)

---

## Patterns to Touch First

Prioritize these because they have the most repeated mapping logic:

- `TempoModifierPattern`
- `HurryPattern`
- `FastGapPattern`
- `TimeShiftPattern`
- `CompressPattern`
- `FocusPattern`
- `ZoomPattern`
- `BitePattern`
- `PlyPattern`
- `ReversePattern`
- `SegmentPattern`

Leave complex logic (Euclidean) for later unless simple helpers can be applied safely.

---

## Tests

- Ensure all existing tests still pass.
- Add **no new tests** unless helper changes behavior in a subtle way.

---

## Notes

The long‑term goal is to replace event and list creation with **object pools** to prevent GC glitches during audio
playback. This refactor should **minimize the number of places that allocate**.

If any helper design decision could limit future pooling, flag it.

---

## Deliverables

- New helper file (if needed).
- Refactored patterns using helper methods.
- No API changes.
- Preserve behavior.
