# KLANG-00482: sprudel every question mark of the mini-notation has its own random value

> **Status (2026-11-24): DONE.** Every `?` of one notation string has its own dice; its number is the probability of being played.

## Why this exists

The maintainer (2026-11-24): "a pattern like [hh?!8] should produce different hihat strikes every pattern, but only
produces either all or none, since they use the same random value". Measured: `[hh?!8]` and `hh? hh? hh? ...` dropped
all steps of a cycle or none, with or without a `seed`; only `hh?*8` dropped each hit on its own. Every mark read the
one signal `rand` at its step's own time, which for every step of a sequence is the cycle's start. No change of
November caused it (the playback's cycle store, KLANG-00450, does not touch the dice): it was so since the mark
existed.

## Decided (the maintainer, 2026-11-24)

- "every ? should get its own random value".
- "it would be better if the number after the ? is the probability of being played defaulting to 0.5": `a?0.3` plays
  about three times in ten. It was the share dropped. `degradeBy(0.3)`, the function, still drops that share.
- Marks of two notation strings count anew, so the first marks of `s("hh?*8")` and `s("bd?*8")` in a stack still go
  together: "Keep it as is for now".

## The work

- `QuestionMarks` in `MnPatternToSprudelPattern.kt`: one counter per conversion, each mark a dice of its own, told by
  the seed, the time and the mark's place in the string (the copies of a `!` each count). The same notation under the
  same seed gives the same events again.
- The parser takes a mark's number only where it touches the mark: in `"0 2? 4"` the 4 is a step again.
- The song writers' reference and `sprudel/MEMORY.md` say what the mark does.

## Acceptance

- `MiniNotationQuestionMarkSpec`, 7 cases, on the JVM and in the browser; `:sprudel:jvmTest` and
  `:sprudel:jsBrowserTest`, 6561 tests, 0 failed. No song and no tutorial uses a mark with a number.
- Mutants killed: one dice for all marks; the mark multiplied with the time (the first fix's defect); the number as
  the share dropped; the time spread by 31 (red in the browser only); a number after a space taken.
- Review, strict tier: round 1 (level 1, reviewer-fable-medium) NOT CLEAN, 1 MAJOR: in cycle 0 every mark still
  read the same dice (a product with the time's hash, which is 0 there), fixed. Round 2 (level 2,
  reviewer-fable-high, two phases) CLEAN: 1 MINOR (seeds 31 apart gave the same dice a cycle apart in the browser)
  and the parser's number after a space, both fixed.

## History

- 2026-11-24: created, built, reviewed, done.
- 2026-11-24: DONE.
