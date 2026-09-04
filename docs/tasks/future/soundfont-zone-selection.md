# Soundfont zone selection: by key range, not nearest root

Status: **future, low priority.** Approximately right today, wrong by the spec. Found during the
soundfont looping investigation (`../../tasks-archive/2026-09/20260903-soundfont-looping-investigation.md`), 2026-09-02.

## What happens now

`SampleIndexLoader.provide` sorts an instrument's zones by `effectivePitchCents()` and picks the
**first zone whose root pitch is at or above the requested note**, falling back to the highest zone:

```kotlin
val selectedIndex = zones.indexOfFirst { it.effectivePitchHz() >= requestedPitch }
    .let { if (it < 0) zones.lastIndex else it }
```

`keyRangeLow` / `keyRangeHigh` are parsed into the zone model and never read.

## Why it is wrong by the spec

A SoundFont zone's key range IS its selection rule: the font author decided which sample covers
which keys, and a zone's root is only where that sample plays at its recorded pitch. "Nearest root at
or above" happens to agree with the ranges most of the time because authors usually put the root near
the top of a range, but not always — JCLive's accordion zone 10 covers key 89 only with root 94, and
zone 11 covers 90–92 with the same root, so the two tie on root and the sort breaks the tie by file
order rather than by range. Any note gets the *right pitch* (the ratio is computed off whichever root
was chosen) but possibly the *wrong sample*, i.e. the wrong timbre for that register.

Also: `request.note` null or unparsable makes `requestedPitch` NaN, every comparison false, and the
**highest** zone is chosen for every note — pitched down as far as needed.

## The fix, when someone wants it

Select the zone whose `[keyRangeLow, keyRangeHigh]` contains the requested MIDI key; fall back to
nearest root only when no range matches (fonts do leave gaps). Guard it with a spec that constructs
zones whose ranges disagree with their roots and asserts the range wins.
