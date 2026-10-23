# Fable experiment, Katalyst 5a-3 (MC-00026), 2026-10-23

Does a round-3 review on Fable find what Opus 5.5 at max effort misses? Klang's sample: round 3 of Katalyst step
5a-3 (2026-09-18), where Fable's code reviewer found the duck door filling its companions on any knob (a MAJOR).

## The setup

- **The tree:** `c8e6990d` with the 5a-2/5a-3 change set of `1d4e01ef` and the rule text of `c14b14ed` as uncommitted
  changes. The duck door's section and its spec row were restored exactly as Fable read them (recovered from Fable's
  own transcript). The render rows, the ledger rows and the review-loop lessons that round 3 itself produced were
  left out. The review-time diffs no longer existed, so this is 1d4e01ef's state (rounds 3 and 4's other fixes
  included), not the exact round-3 tree.
- **The briefs:** Fable's two round-3 briefs (code and audio), word for word, pointed at the review copy, plus one
  isolation note (work only in the copy, no history beyond HEAD).
- **The reviewers:** Opus 5.5 (`general-purpose`, `model: opus`, effort max), blind, phase 1 only.
- **An incident:** the first pair saw part of the answer. The copy was built with `git apply --index`, so its index
  held the fixed duck door, and a plain `git diff` showed it (`docs/tasks/_incidents.md`). Both reviewers disclosed
  it. The index was corrected and the code role ran again on the clean tree. That rerun started before the
  maintainer's "no repeat runs" (2026-10-23) and was let finish. The audio role was not rerun, so its duck severity
  does not count as independent.

## Score against Fable's own phase-1 output

Fable's round-3 findings still present in the review copy (the others were fixed before `1d4e01ef` and are not
counted):

| Fable's finding (role, severity) | Opus 5.5 max |
|---|---|
| The duck door fills on a tail-only call, against the closed name-knob list (code MAJOR; audio MINOR) | found by the clean code run, MAJOR, with its own audible case (a chain-authored `duck.attack`); the audio run found it as MINOR before its peek |
| The duck KDoc's "no numeric setter can receive a null" (code MINOR) | found (clean code run) |
| The family-1 criterion in `KatalystDefaultsSyncSpec` does not separate family 1 from family 3 (code MINOR) | found (clean code run) |
| The raw `==` in `KatalystDefaultsSyncSpec` family 2 where rule 23 wants `shouldNotBe` (code NIT) | found (clean code run, as a wrong rule-23 exception) |
| The phaser fill has no render evidence; no frozen song calls it (audio, a recommendation) | found (audio run, MINOR) |
| A NaN body or vowel mix makes `KatalystBodyEffect.configure` rebuild every block (audio, pre-existing, outside the change set; fixed later in 95b0049e) | **missed** |

**Found 5 of 6.** The miss is the out-of-scope engine defect, which Fable's audio reviewer found beyond its brief.

**Opus's extras** (the clean code run: 9 MINORs, 4 NITs): stale 5a-2 comments in the declared-body render spec, a
false test name in `LangPhaserSpec`, a false "audible as soon as reached" sentence in `BusEffectDefaults`, the
formant twin's "both get the fix", the null-clearing inventory copied to seven sites and disagreeing. No false alarm
found among them on reading; none was checked by a test. Two are still open on `main`: the `LangPhaserSpec` row
name "does not touch the head field" (the row asserts the filled head) and the family-1 wording.

## Cost

The transcripts' output counts are not comparable (Opus 5.5's do not include its thinking), so these are the
comparable figures, phase 1 only:

| reviewer | tool calls | cache read | wall time |
|---|---|---|---|
| Fable, code | 97 | 21.5M | 20 min |
| Fable, audio | 77 | 14.6M | 15 min |
| Opus 5.5 max, code (clean) | 108 | 32.4M | 27 min |
| Opus 5.5 max, audio | 113 | 27.0M | 22 min |

The harness counted 512k tokens for the clean Opus code run and 443k for the Opus audio run.

## Verdict for this sample

On klang's one Fable code find, Opus 5.5 max found the same MAJOR blind and rated it the same. Fable's audio
reviewer found one real engine defect outside its brief that Opus did not. Opus cost about 1.5 to 1.9 times
Fable's reading at a longer wall time. n = 1: one sample toward the tally (the maintainer, 2026-10-23: one Opus and
one Fable on every top-rung round until the next meeting).
