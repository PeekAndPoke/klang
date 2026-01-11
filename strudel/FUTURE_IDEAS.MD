# TODO: Smooth Dynamic Tempo & Time Warping

## Context

The current implementation of `fast(pattern)` / `slow(pattern)` uses a "phasor modulation" approach:

- `inner_time = outer_time * factor(outer_time)` (for `fast`)
  This causes "time reversal" or "stalling" when the factor decreases rapidly, because the effective playback speed is
  `factor(t) + t * factor'(t)`. If the second term is large and negative, speed becomes negative.

## Goal

Achieve smooth, monotonic tempo changes where the speed strictly follows the control pattern (e.g., `sine`), without
artifacts.

## Proposed Approach: Integral-Based Mapping

Instead of direct multiplication, we should relate time via integration:

- `inner_time(t) = integral(0 to t) speed_factor(x) dx`

Since Strudel patterns are functional and stateless, computing arbitrary integrals from `t=0` is difficult. The proposed
workaround is to expose an explicit **Time Warping** interface where the user (or helper functions) provides the
integrated time map.

### 1. New Pattern Operation: `.integral()`

Add an `.integral()` method to `ContinuousPattern` (and potentially others).

- **Analytical Integration**: For basic oscillators (`sine`, `saw`, `square`, `tri`), implement exact mathematical
  antiderivatives.
    - `sine.integral()` -> `-cos` (appropriately scaled/shifted).
    - `const(k).integral()` -> `k * t`.
- **Numerical Integration**: For complex patterns, implementing a stateless numerical integrator is hard. We might limit
  this feature to patterns where we know the integral, or implement a "sampling integrator" that approximates the area
  under the curve (expensive).

### 2. New Modifier: `.warp(timeMap)`

A general-purpose time warping modifier.

- **Syntax**: `note("...").warp(timeMapPattern)`
- **Logic**:
    - `inner_time = timeMapPattern.valueAt(outer_time)`
    - To map events from `inner` to `outer` (Strudel's query direction), we need to **invert** this function.
    - **Inversion**: Use the iterative solver (Newton's method or similar, already explored in `TempoModifierPattern`)
      to find `t` such that `timeMapPattern.valueAt(t) == inner_event_time`.
    - Since `timeMapPattern` (being an integral of a positive speed) is strictly monotonic, the inversion is stable and
      unique.

### 3. High-Level Helper: `.speed(pattern)` (or improved `.fast(pattern)`)

Re-implement dynamic `fast` as syntactic sugar:

- `fast(p)` -> `warp(p.integral())`
- This requires `p` to support `.integral()`.

## Feasibility & Challenges

- **Inversion Cost**: Solving `map(t) = target` numerically for every event start/end is computationally heavier than
  linear scaling, but feasible for typical note densities.
- **Global Synchronization**: Integrals usually need an integration constant (`+ C`). `integral(sine)` oscillates around
  0? No, speed must be positive, so we integrate `sine.range(1, 2)`. This grows indefinitely (monotonic).
- **Statelessness**: The main challenge is ensuring `integral()` returns a consistent value for `t` regardless of
  when/how it's called. Analytical solutions solve this perfectly.

## Future Implementation Steps

1. Extend `ContinuousPattern` with an optional `integralFn: (Double) -> Double`.
2. Implement `integralFn` for standard oscillators in `lang.kt`.
3. Create `WarpPattern` (or update `TempoModifierPattern`) to accept a `timeMap` pattern.
    - Implement robust `solveOuterTime` using the `timeMap`.
4. Add `.integral()` extension to the DSL.
