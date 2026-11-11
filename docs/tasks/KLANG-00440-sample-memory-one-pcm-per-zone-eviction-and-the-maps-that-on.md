# KLANG-00440: Sample memory: one PCM per zone, eviction, and the maps that only grow

> **Status (2026-11-11): DESIGNED.** From KLANG-00431 (D2, D12), maintainer 2026-11-11.
>
> **Part of:** KLANG-00355 (Performance: fast on every device)

## Why this exists

The engine audit (KLANG-00420) found that sample memory only grows (KLANG-00431, findings L9-05, L9-06, its Decide
items D2 and D12). The maintainer (2026-11-11): "yes to own ticket", and the debt pointer of the root `CLAUDE.md`
rule ("nothing allocates without a way to clean it up") names this ticket.

What grows per new sample request and never shrinks:

- The worklet's `SampleStore.samples`: one full PCM per requested NOTE, as doubles (a soundfont over 40 notes in 10
  zones holds 40 copies), and the `NotFound` entries of samples that do not exist.
- The frontend's `SamplePreloader.sent`, `Samples`' two caches, `InMemoryUrlCache`.
- The `uniqueId()` name maps (`globalIgnitorNames` and its Katalyst sibling): a few bytes per edit, accepted
  (KLANG-00429, L5-10); listed here because the old debt list (KLANG-00210 section 11, archived) held them.

## Decide before implementing

- The wire shape of "this note's sample is that zone's PCM": an alias command, or a zone id in the request
  (KLANG-00431, D2 options (a) and (b)).
- The eviction rule: how long a sample no live engine has used stays, and how the frontend's `sent` set learns that
  it was dropped.

## The work

1. One PCM per zone in the worklet: the frontend sends a zone's PCM once and points the other notes at it.
2. Eviction: a sample no live engine has used for a while leaves the store, and is asked for again when needed.
3. The frontend's caches get the same bound.

## Acceptance

- A soundfont instrument over 40 notes in 10 zones holds 10 PCMs in the worklet.
- After a long session of edits the sample memory is bounded by a stated budget.

## History

- 2026-11-11: created.
