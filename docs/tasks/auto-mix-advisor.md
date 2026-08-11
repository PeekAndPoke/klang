# Auto-mix advisor — attribution, suggestions, and a deliberately weak auto-master

> **Status: 🔴 proposed 2026-08-11 (user idea, same-day sketch), not started. Not slotted in
> [`_priorities.md`](_priorities.md).** Priority proposal: **NICE** — but with an unusual upside, see
> the framing below.
>
> **Depends on:** [`realtime-analytics-meters.md`](realtime-analytics-meters.md) §3 (capture ring +
> worker, windowed balance distribution — the measurement substrate) · stem export (§4 here, new) ·
> `MasterFx.eq()` ([`master-dsl-followups.md`](master-dsl-followups.md), new item) for the
> closed-loop phase only.
>
> **The framing that makes this interesting:** every commercial auto-master tool (LANDR, Ozone…)
> receives a stereo sum and can only EQ symptoms. **Klang is the mixer.** The engine owns every
> orbit buffer, and the pattern source declares each voice's register and role. So the valuable
> version of this feature is not auto-master — it is **auto-mix-advice**: "band X is over target
> and voice Y owns it; here is the knob." Fixing the mix beats bending the sum, and uniquely, klang
> can.
>
> **Grounded in:** the 2026-08-10/11 Der Schmetterling sessions (klang-ai repo,
> `sessions/20260810-gemini-review/`). Every rule proposed in §2 is a mechanization of a diagnosis
> that was performed by hand there, including one quantitatively verified transfer (§2, rule 2).

## 1. Attribution — which pattern owns which frequency band

The prerequisite for any advice. Three routes, complementary:

1. **Per-orbit band tap (realtime, approximate).** The cylinder mix buffers already exist per orbit; band-energy
   summaries per orbit per block give a live `orbit × band` attribution matrix. This is the meters doc's orbit ladder
   (§2.3 there) upgraded from one RMS bar to ~7 region bars per orbit.
2. **Stem export (offline, exact).** During one offline render, write each cylinder's buffer to its own WAV alongside
   the master (§4). Per-stem band tables then give exact attribution — including how each stem's contribution moves
   across 16-cycle windows.
3. **Source-declared roles (static).** The pattern already says `orbit(3) … scale("e1:minor")` — who *should* own 40–80
   Hz is machine-readable. Divergence between declared role and measured attribution is itself a finding ("rhythm guitar
   contributes 61 % of the bass band").

## 2. The suggestion engine — rules validated by hand first

Rule shape: `measured deviation` + `dominant contributor` → `concrete knob change with an estimated
effect in dB`. Suggestions only — **never auto-apply** (author taste is the spec; see §6.3).

Starting rule set, each one a mechanized session diagnosis:

1. **Band over target, contributor known** → the contributor's low-shaping knobs. Example from the sessions: "160–200 Hz
   is +3.4 dB over target; guitar 2 owns most of it → raise its tracking highpass (`hptrack` 1.0 → 1.4) or trim
   `postgain` 1.5 dB." The knob map per voice type lives in a registry (§6.2).
2. **Low-band excess + deep GR events coinciding with low peaks** → the gain-staging transfer:
   "trim the low contributors X dB, raise `MasterFx.gain` ~X dB." **Measured 2026-08-11:** bass −2.1 dB + kick −1.9 dB +
   master +1.8 dB ⇒ **+1.3 dB louder, beat-rate pumping −5…−12 %, zero clipping.** The mix was paying a
   loudness-and-pumping tax to carry the low end; the rule refunds it.
3. **Pumping metric over threshold** → master gain down / limiter drive down (the v7→v8 finding:
   gain 1.70 → 1.50 measurably reduced beat-rate modulation).
4. **Crest healthy + GR shallow** → headroom is available; gain may come up.
5. **Air region below target with cymbal orbit below its usual share** → level suggestion on the air suppliers, not EQ
   (sessions: the top octave has exactly one supplier; brightness via supersaw knobs was tried repeatedly and always
   read as harshness, not air).

**Targets are editable per-style preset curves** — region *ranges* against the pink-anchored scale (e.g. this project's
current healthy state: sub +0…+3, bass 0…+2, lowmid ≤ +2, presence −4…−8, air −10…−15), not absolute truths.

**Section-awareness is mandatory.** The author *wants* dark/bright arcs (the Der Schmetterling break inverts the
spectrum by 40 dB in the sub — deliberately). Rules therefore run on the **windowed distribution's normal-section
median** with structural outlier windows excluded (machinery:
`specdist` reference script / meters doc §3), never on the whole-file average — or the advisor will
"fix" the drama.

## 3. Closed-loop auto-master — deliberately weak, last

Only after §1–§2 exist, and only on the master stage: solve for **`MasterFx.gain` + a gentle tilt/shelf pair**
(`MasterFx.eq()`, 2–3 bands) against the target curve, offline: measure → set → re-render → verify convergence. Kept
intentionally weak because §2 is where real correction belongs; the master EQ handles only what no single contributor
owns. Adaptive/realtime versions are out of scope until the offline loop has proven itself.

EQ placement in the chain (decided by the mechanism of rule 2): **before gain and both limiters** — reverb → eq → gain →
authored limiter → house limiter — so the limiter's detector sees the corrected spectrum and the freed GR budget
converts to loudness.

## 4. Stem export (new build item)

One offline render → per-orbit WAVs + the master. Belongs in the offline render path (`KlangAudioRenderer` /
`console/record.sh --stems`), where the cylinder buffers are already summed separately before the master stage.
Independently useful beyond this feature (debugging, remixing, external mastering). Constraint: stems must be pre-master
(post-orbit-effects), and the manifest should record the orbit → pattern-voice mapping so attribution reports can name
voices, not numbers.

## 5. Suggested phasing

- **P0 — attribution report, offline:** stem export + per-stem band tables + `orbit × band × window`
  matrix, printed as a report. No UI. Immediately answers "who owns this hump" without hand analysis.
- **P1 — rule engine + targets:** preset curve format, the §2 rules over the P0 matrix, suggestions rendered as text
  with estimated dB effects. Still offline/CLI.
- **P2 — UI integration:** advisor panel consuming the meters doc's Tier-B analyses (capture ring + worker); live
  attribution via the per-orbit band tap.
- **P3 — closed-loop master** (needs `MasterFx.eq()`): offline convergence loop, verify-by-measure.

## 6. Open decisions

1. Target-curve preset format and where presets live (project file? per-song in the pattern?).
2. **The knob registry:** mapping voice types → their spectral-shaping knobs (supersawHp: `hptrack`,
   `postgain`, `lpf`; sample drums: `gain`, `lpf`; bass: `postgain`, octave-layer gain …). Where is it maintained, and
   can Osc definitions self-describe their knobs (Osc.param descriptions already exist — possibly derivable)?
3. Suggestion delivery: report text only, or one-click "apply to source" edits? (Proposal: text with exact
   `.knob(value)` snippets, author pastes — keeps the author in the loop and survives pattern refactors.)
4. Outlier-window detection rule for section-awareness (fixed threshold vs. deviation from median).
5. Whether per- *voice* attribution (beyond per-orbit) is ever needed — superimpose copies share an orbit; the
   mute-others offline render trick covers it manually when it matters.

## 7. Explicitly out of scope

- Arrangement/musical advice (lead phrasing, section contrast, fills) — that dimension came from differently-framed
  listening review in the sessions, and it is judgment, not measurement.
- Auto-apply of any suggestion without an explicit author action.
- Realtime adaptive master EQ (a mastering-grade dynamic EQ is its own project; the offline convergence loop is the
  honest first version).
