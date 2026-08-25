# Filter and effect unification — one thing, one way, everywhere

**Status: PLAN, not started.** Chunks will be reviewed one at a time before each starts.

## Why this exists

The filter surface "happened" by copying strudel, which itself stayed compatible with Tidal. The
result is 48 sprudel method names for roughly 13 concepts, a default `q` that differs per door
for the same filter (sprudel `.lpf(800)` builds `q = 1.0`, the ignitor's `.lowpass(800)` builds
`0.707` — an audible difference nobody decided), three different wet/dry curves in the engine,
and an envelope-depth formula that is linear in Hz (which no synth uses and which has a dead
zone at `depth = -1`).

We are pre-alpha with no compatibility obligation. This is the moment to build the correct
surface rather than inherit the accidental one.

**The overarching principle (maintainer, 2026-08-23):** the same thing must have the same way,
no matter in which context it is used. We owe this to the user; the project already carries
enough complexity.

## Decisions (all settled in discussion, 2026-08-23)

### Vocabulary — identical on sprudel, ignitor, and `eq()`

| canonical | short | parts (sprudel) | notes |
|---|---|---|---|
| `lowpass(freq, q, passes)` | `lpf` | `lpq` `lpx` `lpe` `lpadsr` | |
| `highpass(freq, q, passes)` | `hpf` | `hpq` `hpx` `hpe` `hpadsr` | |
| `bandpass(freq, q)` | `bpf` | `bpq` `bpe` `bpadsr` | no passes: cascading only narrows, q does that |
| `notch(freq, q)` | `ntf` | `ntq` | no passes, **no envelope** (see below) |
| `band(freq, q, db)` | — | — | NEW on sprudel; serial bell, dB gain. **COLLISION:** sprudel already has `band(mask)` = bitwise AND (`lang_arithmetic.kt`, pinned by `LangBandSpec`); an overload on the same receiver is unresolvable in the docs registry. One must be renamed; decide in the C6 review (the bitwise family `band/bor/bxor` is the likelier candidate, it is rarely used in music) |
| `tap(freq, q, gain)` | — | — | NEW on sprudel; parallel boost, linear gain |

Parameter ORDER is `freq, q, passes` (and `freq, q` where passes does not apply), positional or
all-named — there is no colon form any more (see "The function-shape contract" below). The
envelope is NOT a filter parameter (it was the third slot of the old `lpf` string); it moves to
its own two calls so that one filter's state is no longer spread across two partial bundles.

**Deleted:** `cutoff`, `ctf`, `lp`, `hp`, `bp`, `res`, `resonance`, `hres`, `hresonance`,
`hcutoff`, `bandf`, `bandq`, and the ten-per-filter envelope aliases (`lpattack/lpa/lpdecay/lpd/
lpsustain/lps/lprelease/lpr/lpenv/lpe` x3). Mixed casing (`hresonance` next to `bandq`) goes with
them. Alias count goes from 48 to roughly 20, and every survivor has a parallel on the other
doors.

**Correction from plan review: sprudel ALREADY ships a notch with an envelope** (`notchf`/`nf`,
`nresonance`, `nfattack/nfdecay/nfsustain/nfrelease/nfenv`, `nfadsr`). So "add `ntf`/`ntq`" is
a RENAME, and dropping the notch envelope is a FEATURE DELETION that needs a usage check across
songs and belongs in the Deleted list with its reasoning, not a silent omission.

**Why no `nte`:** an envelope on a lowpass/highpass sweeps a BOUNDARY (the sound opens or
closes), on a bandpass it sweeps a CENTRE (a wah), but on a notch it sweeps a HOLE, which is
barely audible as a gesture — that is why phasers use several notches. A knob that does nearly
nothing is worse than an absent one: the user turns it, hears nothing, and concludes the engine
is broken. Symmetry is not a reason to ship it.

**Why no `bpx`/`ntx`:** `passes` cascades identical stages, which for lowpass/highpass steepens
the slope (12 vs 24 dB/oct: a real, named thing). For bandpass/notch it only narrows, which is
what `q` already does. A second knob that does what `q` does is exactly the duplication being
removed.

### The envelope split

A filter is `freq / q / passes`. Its envelope is a DEPTH plus a SHAPE, and each is one call:

```
.lpf(800, 0.707, 2)              // freq, q, passes
.lpe(12)                         // envelope depth, in SEMITONES
.lpadsr(0.01, 0.3, 0.2, 0.1)     // envelope shape: attack, decay, sustain, release
```

`lpadsr` takes the SAME parameters in the SAME order with the SAME units as `adsr()` (seconds,
seconds, 0..1 level, seconds). Identical units, not normalised variants, or the sameness is
cosmetic. Any of the four can be a pattern on its own, which the old `"a:d:s:r"` string could
never express.

### Envelope depth: semitones, not linear Hz

Today: `cutoff = base * (1 + depth * env)`. Linear in Hz. Measured consequences: `+1` sweeps one
octave up but `-1` sweeps to 0 Hz (garbage), `-2` to negative Hz; depth 1 can NEVER exceed 2x
base so big sweeps need absurd numbers; and the ear hears octaves, not Hz (`+2` is 1.6 octaves,
`+4` is 2.3).

New: `cutoff = base * 2^(depth / 12 * env)`, depth in semitones. Symmetric (`+12` = one octave
up, `-12` = one octave down), no dead zone, unbounded, round numbers for the common cases
(`12`, `24`, `36`), and it shares a unit with `transpose`. This is the DAW convention (Ableton
semitones, Serum/Vital octaves, Eurorack V/oct); linear-Hz is the one convention nobody uses.

**Negative depth becomes first-class**, not an edge case: `lpe(-24)` is a closing-filter pluck.

**Three consequences to put in the docs, not discover by ear:**
- *Saturation at the ceiling.* `bilinearK` clamps `fc` to `[5, sr/2 - 1]`, so no bad coefficient
  is possible, but the round numbers the docs will teach reach the clamp fast: `36` is 8x, so any
  base above 3 kHz clips at 48 k, and the same patch clips DIFFERENTLY at 44.1 k. At the clamp a
  LOWPASS degenerates to passthrough (harmless) but HIGHPASS and BANDPASS degenerate to SILENCE,
  so `hpe(+36)` on a mid-high base can mute the voice at the peak of its own envelope. C3's
  guards include a clamp-saturation row; the docs name the ceiling.
- *Two smoothing policies for one parameter.* `SvfIgnitor` lerps coefficients across every sample;
  the class-form path ramps for 32 samples of a 128-frame block then snaps. The geometric law
  concentrates the fastest Hz movement at the TOP of the sweep, into the door that snaps. Not a
  blocker; listen to the deep-sweep basses specifically, and note the inconsistency (it is the
  ramp-API gap D9 is blocked on, seen from the other side).
- *`lpe` is the one change here that keeps its name while changing meaning.* `lpe(2)` stays legal
  and silently means a tenth of what it meant. Every other breaking change in this plan gets a new
  spelling. Decision for the chunk review: either rename the depth call too, or teach intellisense
  to flag small values (`lpe(2)` = 2 semitones, almost certainly a stale linear value).

**Migration: there is NO exact conversion, and the deep sweeps are the ones that move most.**
`new = 12 * log2(1 + old)` reproduces the old cutoff only at `env = 0` and `env = 1`. In between,
the old law interpolates cutoff ARITHMETICALLY in Hz and the new one GEOMETRICALLY, so the
trajectory of every sweep changes shape. Measured worst-case deviation mid-envelope:

| old depth | migrated | worst deviation | where |
|---|---|---|---|
| 0.25 | 3.9 | 11 cents | negligible |
| 2 | 19 | 257 cents | audible |
| 15 (`TetrisRemix`) | 48 | 1512 cents | 12 semitones off |
| 25 (`Tetris` `lpf(200).lpe(25)`) | 56 | 2022 cents | 1.7 OCTAVES off |

An earlier draft of this plan had the risk backwards: it flagged patterned depths as the risky
case and called constants exact. The patterned `lpe(perlin.range(2.5, 4.0))` deviates by ~27
cents at endpoint conversion; the deep constants are the ones whose entire identity is the
trajectory. **Every constant with `old > ~1` needs the maintainer's ear**, starting with the
bass sweeps. The endpoint formula is the starting point, not the answer.

**The DEFAULT depth is a migration site too:** `FilterEnvDef.default` and `resolve()` carry
`depth = 0.5`, which in semitones is a 3% cutoff move — indistinguishable from off. Any voice
that sets a filter ADSR shape without an explicit depth would silently lose its envelope. That
constant migrates to `12 * log2(1.5) = 7` in the same commit, and its KDoc table (which teaches
the linear formula with a worked example) goes with it. Parity item for the same file: the
`audio_be` side of `FilterEnvDef` defaults depth to `0.0` while the bridge defaults `0.5` — two
defaults for one concept on the two doors this plan is unifying; pick one.

**Blast radius, measured:** 27 envelope usages across built-in songs, 12 in tutorials, plus the
music-writing skill reference. Heaviest: `IrishLamentTechno`. Engine change and migration land
in ONE commit, never apart: a tutorial teaching `lpe(2)` against a semitone engine would silently
sweep a different amount.

### One default `q` everywhere: 0.707

For all four filters, both doors. Today lowpass/highpass default 0.707 on the ignitor and 1.0 on
sprudel; bandpass/notch default 1.0 everywhere. A single constant that can be learned once beats
a per-filter exception. Bandpass/notch get slightly wider at 0.707 than at 1.0; that is accepted.

This changes the sound of every sprudel `.lpf()` without an `lpq` (slightly less resonant). The
sprudel-vs-ignitor LP/HP split (1.0 vs 0.707) genuinely was never decided. The bandpass-vs-bell
split (1.0 vs 0.707) WAS decided, in D5, with a reasoned KDoc ("there 1.0 is a resonance
setting, here it is a bell width"); this plan overrides that decision for uniformity, and that
KDoc is rewritten to say so rather than pretending the earlier choice never happened.

Build sites: the four `SprudelVoiceData` lines AND the two engine-side `q ?: 1.0` in
`createBPF`/`createNotch`. And `q == null` on LowPass/HighPass selects the ONE-POLE filters, a
different topology, not an SVF at some q: the wire field stays nullable; "default 0.707" must
never become "make q non-nullable", which would delete the one-pole path. `tap`'s own `q = 1.0`
default joins the list (after normalisation its q is a pure width, so 1.0 next to `band`'s
0.707 would be exactly the split being removed).

### The q/level coupling: normalise the BANDPASS FAMILY, not just the tap

Today the engine's raw bandpass tap (`v1`) peaks at Q, not 1. So a `tap` lifts by `1 + gain*q`
(measured: gain 1.0, q 0.5 -> +3.5 dB, q 4.0 -> +14.0 dB), and raising q makes it LOUDER as
well as narrower. One knob, two aspects.

**The first draft normalised only the tap. That breaks the plan's own principle:** after it,
`EqCore.BANDPASS`, `SvfBPF` and `FormantFilter`'s bands would ALL still couple q to level
(`bpf(1000, 4)` +12 dB louder than `bpf(1000, 1)`) while `tap(1000, 4, g)` alone is level-flat.
Three of four couplings would survive. So the decision is: **every bandpass-family output is
unity-peak at fc** — the tap, the BANDPASS section, `SvfBPF`, and the formant bands — so that
`q` means width and ONLY width on every door and every filter.

Implementation: scale by the section's stored `k` (`= 1/safeQ` AFTER the `[0.1, 200]` clamp in
`computeSvfCoeffs`), NOT by the user's `q`. Outside the clamp the two differ, and normalising
by the unclamped value would be wrong in exactly the place the coefficients already disagree.

**Two honest limits to put in the docs:**
- Normalisation fixes level AT fc. The tap's added ENERGY still scales with its bandwidth
  `fc/q`, so on broadband material perceived loudness still moves with q, now in the OPPOSITE
  direction (narrower = less energy). "q = width only" is true at fc, not in the mix.
- A unity-peak tap at list position 0 is `v0 + gain*k*v1`; the bell is `v0 + k*(A^2-1)*v1` at
  `q*A`. They become THE SAME SECTION, exactly, for a single tap. `tap` keeps a reason to exist
  only because N taps SUM where N bells MULTIPLY, and because a later tap still reads the
  pre-Eq input. Say so in the docs, or this plan about removing duplicate names ships two names
  for one operation.

**Consequences this chunk must carry, in the same commit:**
- `EqCore`'s RAW_TAP KDoc defines the tap as bit-parity with the legacy `Plus(chain,
  Times(bandpass(input), gain))` graph. That parity is GONE by design; rewrite the KDoc.
- The optimizer's planned R2 (fuse the legacy parallel-tap shape into RAW_TAP) was specified as a
  bit-identical rewrite of exactly that graph. After normalisation it can only stay
  bit-identical by folding q back into the section gain, which reintroduces the coupling for
  fused graphs only. **R2 is re-specified as a non-parity rewrite** (the legacy shape is a
  different, worse sound; fusing it is an improvement, not an identity). "R2 unchanged" in the
  out-of-scope list was wrong.
- The tap-to-bell conversion in the KDocs (`A^2 = 1 + g*Q`, `q_bell = Q/A`) becomes
  `A^2 = 1 + g`, `q_bell = q/A`, and the worked "+4.5 dB at 1200 Hz" example is recomputed.
- Der Schmetterling: 7 `Osc.param` defaults plus ~14 `oscp` overrides across four shapes, not
  "six values". And the direction is NOT uniformly "quieter": at the shipped q 0.707 the taps
  get LOUDER (mids +1.8 dB, presence +2.4 dB) while the lead's `midsQ 4.0` tap drops 11 dB. Same
  commit, opposite directions per voice. The built-in songs are the test bench; retune by ear.
- `bandf`/`bpf` with no `bandq`: the default-q change 1.0 -> 0.707 is ALSO a level change on an
  un-normalised bandpass (-3 dB). With the family normalised it is width-only, which is the
  point — but C1 (default q) and this chunk must land together or C1 alone ships a level drop
  on every bare `bandf` in the corpus.

### Wet/dry: `wet` + `floor`, one helper, TWO mixing laws, and sends are not inserts

Three crossfade curves exist today (ignitor phaser/shimmer: linear; body/vowel: dry shelf with a
floor; orbit phaser: additive `dry + wet*depth`). All collapse into ONE helper. But the plan
review (2026-08-23) showed the first draft of this section was wrong in three ways, each
corrected below.

**1. Equal-power is right for DEcorrelated pairs only.** `cos^2 + sin^2 = 1` holds level flat
when dry and wet are independent, so their POWERS add: reverb tails, delays, shimmer. For
CORRELATED pairs their AMPLITUDES add, and equal-power at the midpoint gives `0.707 + 0.707 =
1.414` = **+3 dB**, the exact bump it was meant to remove. Body and vowel (the wet is the dry
through a resonator bank, coherent in the passbands) and BOTH phasers (an allpass cascade is
unit-magnitude and fully correlated by construction; the notches only exist through coherent
cancellation) are correlated. So the helper carries one more per-effect default, an exponent:

```
out = max(floor, cos(w*pi/2)^p) * dry  +  sin(w*pi/2)^p * wet
   p = 1  ->  equal-POWER       (decorrelated: reverb, delay, shimmer tail)
   p = 2  ->  equal-AMPLITUDE   (correlated: body, vowel, both phasers; = linear, re-expressed)
```

Same helper, same cos/sin, one way to write it; the law is a property of the EFFECT, declared
once, not something the user tunes. "One way everywhere" survives; pretending two statistics are
one does not.

**2. `floor` and equal-power are mutually exclusive wherever the floor is active.** Above
`w* = (2/pi)*acos(floor)` the dry is pinned and total power RISES with `w` by construction. That
is fine, it is what `floor` is FOR, but the helper's KDoc must say so rather than claim both.
`floor` is the minimum dry coefficient; its defaults encode what each effect is, and they are the
CURRENT values, not a guess: `BODY_FLOOR = 0.4`, `VOWEL_FLOOR = 0.2` (deliberately different,
documented in the engine), ignitor phaser/shimmer `0.0`, orbit phaser `1.0`.

**3. Reverb and delay are SENDS, not insert crossfades, and `floor` is meaningless on them.**
`SendRenderer` writes the voice's dry to the cylinder mix at full level and ADDITIONALLY feeds
`dry * amount` into a per-cylinder send bus; one `Reverb` per cylinder returns the wet. The dry
is never touched, i.e. today `room` behaves like `floor = 1.0` already. Assigning it `floor =
0.0` ("crossfade; `wet(1)` is effect only") would attenuate the dry of every reverbed voice in
the corpus, and "effect only" is not even EXPRESSIBLE on a shared per-orbit return: muting voice
A's dry still leaves voice B's dry and tail in the same bus. So there are two kinds of additive
effect, and the surface must say which is which:

| kind | `wet` means | `floor` | examples |
|---|---|---|---|
| insert (per voice, wet returned in place) | crossfade balance | a knob, per-effect default | body, vowel, ignitor phaser, shimmer, orbit phaser |
| send (shared per-orbit return) | send AMOUNT; dry untouched | none, always 1 | reverb (`room`), delay |

Rebuilding reverb/delay as inserts to make them crossfades is NOT in this plan; it is a much
larger change than C4 and there is no musical demand for it. `room`/`delay` keep send semantics
under the `wet` name, and the docs say "send" in the first sentence.

**Wet/dry applies to ADDITIVE effects only.** Filters, distortion, crush, coarse, compression are
SHAPING effects: a 50% lowpass is a comb nobody asked for, and the "parallel filter" idea already
exists properly as `tap`/`band`. Their `amount` is intensity, not mix, and keeps its name.

**Naming by door:** on the ignitor the knob is typed onto the effect node (`.body("oak").wet(0.3)
.floor(0.2)`), the supersaw config-method pattern a third time. On sprudel it MUST be prefixed
(`bodyWet`, `bodyFloor`, `roomWet`, `phaserWet`, `phaserFloor`, `shimmerWet`, ...) because sprudel
sets fields on one unordered voice and has no "the effect it follows". The prefix is a consequence
of sprudel's unordered model, not a naming preference; document it so nobody later "cleans it
up" into the ordered form that cannot work. `blend`, `bodyMix`, `vowelMix` go. Note `bodyFloor()`
and `vowelFloor()` ALREADY exist on sprudel with exactly these names; they are kept, not added.
**COLLISION on the ignitor door:** `floor()` = round-down already exists as an arithmetic
function on `SprudelPattern` (`lang_arithmetic.kt`). A `.floor()` meaning "minimum dry
coefficient" on the ignitor effect nodes is one word for two concepts across doors. The
prefixed sprudel names (`bodyFloor`) do not collide; the bare ignitor `.floor()` does. Decide
in the C4 review: a different word on the ignitor (`dryFloor`?) or accept the cross-door
homonym with the receiver type disambiguating. The plan's own rule says the former.

**Helper domain:** `w` in `[0, 1]`. `ParallelMixFilter` today documents `amount > 1` as
supported raw behaviour (dry pinned at floor, resonances keep rising); under cos/sin the wet
FOLDS BACK past 1. No song uses `> 1`, so this is a capability deletion, decided here rather
than by accident. BUT `FilterDef.Body`'s KDoc documents `mix > 1` as "drives the resonances
harder" and `LangBodySpec` uses `bodyMix(2.0)`, so clamping contradicts "Motor stays raw".
Decision for the C4 review: either define the helper's law for `w > 1` (e.g. the wet
coefficient continues past 1 linearly while dry stays at floor, preserving today's raw
behaviour) or accept the deletion and retire that KDoc + spec row. Not clamp-and-pretend.

**Migration is NOT "same value" — CORRECTED in the C4.1 review (the original paragraph was
computed for p = 1/sin; the decided correlated law is p = 2/sin², which flips the sign):**
measured against today's coefficients, mid-knob values get SUBTLER, not hotter — `Tetris`
bodyMix 0.2: wet −6.4 dB; `StrangerThings` vowelMix 0.4: wet −1.3 dB, dry −0.3 dB; the orbit
phaser sites `Tetris` 0.15: wet −8.8 dB, `IrishLamentTechno` 0.25: −4.6 dB (with `floor = 1`
the dry matches exactly; the wet law changes from linear `depth` to `sin²`, identical only at
0, 0.5 and 1). `mix = 1.0` stays a fixed point. The C4.2 ear-retunes therefore go UP.
These are ear-retune sites, listed in C4, not mechanical renames.

### Passes (the original D6)

`passes: Int = 1` on Lowpass/Highpass only, structural, `coerceAtLeast(1)` never `require`.
Builder repeats the section. The optimizer's R1 must learn to expand `passes = N` into N
sections IN THE SAME COMMIT the field lands (the D6 rule), so there is no window where a
`passes = 2` filter fuses as one section and loses 6 dB/oct.

### Compat tests

The sprudel->strudel compat suite (`sprudel/src/jvmTest/kotlin/compat/`, ~155 field-setter
calls) is cut down to STRUCTURAL functions only: sequencing, alternation, euclid, stack/cat,
timing. Setting a field on a voice is stable in our implementation and the field-setter cases now
pin only strudel's naming accidents, which this plan deletes. We fully abandon the strudel
heritage on the control vocabulary.

### The function-shape contract (decided 2026-08-23, grounds every chunk below)

**Compound colon-strings leave the language.** `lpf("800:1.5:2")`, `adsr("0.01:0.3:0.5:0.1")`,
`sndDust("0.2:4")` and every other `"a:b:c"` parameter are gone. Every function takes its
parameters individually, positional or all-named (KlangScript forbids mixing):

```
lpf(200)                      trailing params default
lpf(200, 1.5, 2)
lpf("<200 800>", 1.5)         ANY slot can be a pattern
lpf(freq = 200, passes = 2)   named, any subset
```

**Open for the C0 review:** positional `(freq, q, ...)` puts the least-used parameter in slot
2 for every filter, and the codebase already documents that exact trap once
(`KlangScriptEqExtensions`: "the second positional argument is q, NOT gain: `.band(1200, 6)`
sets a width of 6, which is silent"). Options: accept it and document it per function, or make
`q` named-only so the positional form is `lpf(freq, passes)`. Decide deliberately rather than
inherit it a second time.

Two reasons, in order of weight:
1. **The colon is already taken, three ways.** In mini-notation `bd:2` is a sample index,
   `c:minor`-style tokens are scale-step notation, and `ratio("5:4")` is a division. Packing
   params with the same character gives one symbol a fourth meaning that depends on which
   function's string you are inside. No detection rule can be clean on top of that.
2. **It has no ergonomic advantage left.** `lpf(200, 1, 1)` is fewer characters than
   `lpf("200:1:1")`, has no quotes to balance, and every slot can independently become a
   pattern, which the compound form can never do. It is Tidal's calling convention inherited via
   strudel, not a design.

**Retracted from an earlier draft:** a claim that `lpf("<400:1:1 800:2:1>")` "collapses
alternation to the first entry" was a misreading of `<>` (per-CYCLE alternation) from a
single-cycle query. Across two cycles it works correctly (measured: cycle 0 -> 400/1/1, cycle 1
-> 800/2/1). The colon is not tokenised by the mini-notation parser and the compound splitter
runs per atom. The compound form is being removed on the two reasons above, not because it is
broken.

This merges `docs/tasks/sprudel-sound-function-surface.md` into this plan: `adsr`, `lpadsr`,
`distort`, `glide` and the whole `snd*` family have been blocked on exactly this decision since
June, and they all resolve the same way.

**Two tool tiers, not one.** The axis is NOT "tool or no tool" but inline vs modal:

| tier | opens as | for | status |
|---|---|---|---|
| inline | popover at the cursor, drag and done | one scalar: `gain`, `pan`, `lpq`, `passes`, `wet` | **NEW UI work** |
| full | modal | params that only mean something together: `adsr`, filter + envelope, `euclid`, unison | the host exists; whole-call binding is new |

**Correction from plan review — an earlier draft had these backwards.** Today EVERY tool opens
in a modal (`KlangCodeEditorComp` -> `CodeToolModal` -> `tool.render`). `KlangUiToolEmbeddable.
renderEmbedded` exists but has exactly two call sites, both INSIDE the mini-notation editor's
modal, for atom sub-editors. There is no cursor-popover host anywhere. So the inline tier is
the genuinely new UI work, and the modal tier is the existing host with a wider binding.

A pattern-valued parameter gets the mini-notation editor wrapped around either tier, which
`SprudelMiniNotationEditorTool(atomTool = ...)` already does. Shorthand typing (`800:1.5:2`)
may survive INSIDE the modal's input field as a UI affordance; what is written back to the
source is always the real signature. The source never contains a colon-string.

**The contract change is a chain, not one class.** Today: `@param-tool` KDoc tag (PER-PARAM
only; the KSP `KDocParser` has no function-level tool tag) -> `KlangParam.uitools` ->
`KlangUiToolRegistry.resolve` -> `CallArgInfo.tools` (both finders return null when empty) ->
`KlangUiToolContext(paramName, currentValue)` (ONE name, ONE value) -> the tool commits ONE
argument span. A whole-call tool therefore needs: a function-level KDoc tag + KSP emission, a
callable-level tools field, a registry path, a multi-value context, AND a new `AstIndex` lookup
(`callArgAt` only resolves when the cursor is INSIDE an argument, so a modal cannot open on an
empty `adsr()`). `@param-sub` retires with the colon-strings.

**Two bugs that C0 would promote from rare to common, so C0 must fix them:**
- **Named arguments bind by POSITION in the tool path.** `ArgFinder.resolveParam(callable,
  argIndex)` uses the positional index from `AstIndex.callArgAt`; nothing in `klangscript-ui`
  reads `Argument.Named.name`. `lpf(passes = 2)` resolves to the `freq` param: wrong tool, wrong
  commit span. Harmless today; C0 makes named-subset calls a headline form.
- ~~klangblocks silently corrupts every multi-param call~~ **Resolved by removal.** The plan
  review found the block editor discarded named-argument names on round-trip, which C0 would
  have turned into a common corruption. Rather than fix a surface that was already hidden
  behind a disabled toggle, the maintainer REMOVED the klangblocks module entirely
  (2026-08-23). One surface fewer to keep in agreement.

## Chunks, in order

Order: **C6a** (delete the doomed, cheapest and shrinks everything after) -> **C0** (the
function shape every later chunk writes to) -> **C1+C2** (one commit) -> **C3** -> **C4** ->
**C5** -> **C6** (canonical names; `band`/`tap` on sprudel gated on D9).

Each chunk: implementation, /review-loop to a clean round, mutation-checked tests, its own
commit. Built-in songs and tutorials that a chunk changes the sound of are migrated IN that
chunk's commit.

### C6a — Delete the doomed aliases + cut the compat suite (runs BEFORE C0)
C0 must give the per-param shape to every function it keeps, on three receivers each
(`SprudelPattern` / `String` / `PatternMapperFn`), and migrate every call site. Doing that for
`cutoff`, `ctf`, `lp`, `hp`, `bp`, `hcutoff`, `bandf`, `res`, `resonance`, `hres`,
`hresonance`, `bandq` and the ~40 envelope aliases, only for C6 to delete them, is roughly 40%
of C0's surface spent on functions that will not exist. So the pure deletions run FIRST:
- Delete every alias in the Deleted list, their per-alias specs (`LangBandfSpec`,
  `LangResonanceSpec`, `LangLpattackSpec`, ... one per alias), and their duplicate UI tools
  (`SprudelNotchQEditorTool` and `SprudelNResonanceEditorTool` both edit notch q; ~60
  `*SequenceEditor` registrations in `SprudelUiTools.kt` to re-point or drop).
- Migrate every call site to the SURVIVING name (`bandf` -> `bpf`, `resonance` -> `lpq`, ...).
  This is a rename sweep only; no shapes change yet.
- Cut the compat suite to structural cases.
- Guard: the alias names no longer resolve (a dispatch-error row per deleted family), and
  `BuiltInSongsSmokeTest` passes.
The "C0 first, everything writes to that shape" rule is right for the SURVIVORS and wasteful
for the doomed. C6a removes the doomed; C0 then reshapes what remains.

### C0 — Per-param functions + the two tool tiers
- **Inventory (done 2026-08-23).** 32 `split(":")` sites in `sprudel/.../lang`, but they are NOT
  all the same thing, and the distinction is the first decision of the chunk:
  - **Compound PARAMETERS — these go.** `lang_snd_addons` (12 sites, the whole `snd*` family),
    `lang_effects_addons` (6), `lang_effects` (4: `room`, `compressor`, `delay`, ...),
    `lang_filters` (3: `lpf/hpf/bpf`), `lang_dynamics` (2: `adsr`, `lpadsr`),
    `lang_filters_addons` (1). Each becomes per-param.
  - **Mini-notation VALUE syntax — these STAY.** `lang_tonal` (3 sites): `bd:2` is a sample
    index, `c:minor`-style step:variant:gain is scale-step notation; `lang_structural` (1).
    These are part of what a NOTE or SOUND token *is*, not a function's parameter list. They are
    exactly the "colon is already taken" reason the compound-parameter form must go, and they
    are untouched.
- **THE TRAP in the filter migration:** today's third colon slot of `lpf/hpf/bpf/notchf` is the
  ENVELOPE DEPTH (`lang_filters.kt:26`: `lpenv = parts[2]`), while the plan's per-param shape is
  `(freq, q, passes)`. A mechanical `"a:b:c"` -> `(a, b, c)` sweep COMPILES, looks right, and
  reinterprets depth as pass count: `IrishLamentTechno.kt:102` `lpf("80:1.2:60")` would become
  a bass with SIXTY filter passes. The correct target is `.lpf(f, q).lpe(env)`, and it must land
  BEFORE C3 converts depths to semitones, or the value is gone by then. Live sites:
  `TetrisRemix.kt:49`, `IrishLamentTechno.kt:102`, `SoundOfTheSea.kt:51`.
- **Migration size (measured in songs + tutorials):** `adsr` x111, `lpadsr` x20, `distort` x9,
  `compressor` x8, `room` x4, `lpf` x3, `sndPluck` x1, plus 6 `Adsr(...)` Kotlin-side. ~160 call
  sites, `adsr` dominating. And that is ONLY songs + tutorials; the full parse-breaking surface
  is far wider (see the sweep list below).
- **`compressor` is a WIRE field, not a lang-layer string.** `SprudelVoiceData.compressor:
  String?` is parsed inside the engine (`audio_be/effects/Compressor.kt parseSettings`, called
  from `Voice.kt`). Removing that colon-string is a wire-format change (VoiceData shape, KSP
  trust-codec, `WIRE_SCHEMA_HASH`, worklet), not a sprudel edit, and it is 8x in songs.
  **DECIDED (maintainer, 2026-08-23): own sub-step INSIDE C0** — the wire work lands as a
  separate commit within the chunk (C0.2 below), so the whole compound-args story ships in C0.
  **C0.2 posture on legacy colon strings (review round 1):** an out-of-repo script passing the
  old compound (`compressor("-20:4:...")` — or any C0.1 compound) is now silently inert: the
  string lands in the head param, fails to parse, and keep-on-garbage leaves the fields unset.
  ACCEPTED for pre-alpha (compounds leave the language entirely; no compat shim). The right
  future mitigation is a SprudelDiagnostics warning ("colon string passed to a per-param
  function") on the existing error-visibility channel — belongs to the query-time diagnostics
  backlog, not to C0.
- **`adsrCurves("square:exponential:scurve")`** is a compound of ENUM values with no per-param
  form proposed. It gets `adsrCurves(attack, decay, release)` with three string params.
- **`ratio("5:4")`** is a division, a third meaning of the colon, and stays colon-only as an
  explicit carve-out (it is not a parameter list).
- Each param flows through `_applyControlFromParams` INDEPENDENTLY so per-param patterns reach
  the voice (today only the whole string is patterned). Guard: `lpf("200 800", 1.5)` (the
  SEQUENCE form, two events in one cycle) yields two events with different cutoff and the same
  q; and `lpf("<200 800>", 1.5)` yields 200 on cycle 0 and 800 on cycle 1. (An earlier draft
  wrote the alternation form and expected two events in ONE cycle, which is wrong and would
  fail for a correct implementation.)
- Tools: `CallInfo` + the `MultiParam` modal contract; per-scalar inline popovers on the
  `KlangUiToolEmbeddable` contract; `@param-sub` deleted; the compound `*SequenceEditor`
  bindings re-pointed at the pattern-valued parameter only.
- **Migration, same commit — the FULL parse-breaking surface, not just songs + tutorials:**
  songs, tutorials, `src/jvmMain/FrozenSongs.kt` (23 colon strings;
  **DECIDED (maintainer, 2026-08-23): syntax-only migrations are permitted** — mechanical
  DSL-shape migrations (renames, colon-split), never value changes; note the policy in the
  file header. The "do not track song changes" spirit stays), `SongBenchmarkCases.kt` (32), the golden
  corpus (13), ~15 sprudel `Lang*Spec` files (`LangSndSpec` 31, `LangDynamicsSpec` 15, ...),
  the compat test data, `CallInfoTest`, `TestKotlinPatterns`/`TestTextPatterns`, the
  `ExpressionTypeInferrerE2eTest`, `StartPage.kt`, `SamplesLibraryPage.kt`,
  `tutorials/AdsrVisual.kt`, `TutorialCurriculumSpec`, the whitepaper (10 `adsr`), the
  music-writing skill reference (57 `adsr`, 19 `room`), and `docs/blog/.../the-same-word`.
  Verified by `BuiltInSongsSmokeTest` plus a query-level spec that migrated voices carry the
  same field values as before.
- This is the largest chunk by blast radius and it goes FIRST, because every later chunk writes
  functions to this shape. Doing it later would mean writing C5/C6 twice.
- **Perf note (C0.1 review):** per-param chaining builds one control layer per provided param
  (`adsr` went 1 -> 4 outer joins at ~111 sites). Pattern-build/query cost only, not the audio
  thread — but it is exactly the shape the designed-not-built constant-control fast-path
  (docs/tasks/constant-control-fast-path.md) collapses; that item should land before the
  Fairphone perf push.
- **Execution order (2026-08-23):** C0.1 lang layer per-param + full call-site migration
  (everything except compressor); C0.2 compressor wire sub-step (VoiceData fields, audio_be
  parsing, codec/schema, worklet, sprudel surface, songs); C0.3 the two tool tiers (MultiParam
  modal, inline popovers, binding re-point, `@param-sub` removal). Each sub-step gets its own
  review loop and commit.

### C1 + C2 land as ONE commit
The default-q change alone ships a -3 dB level drop on every bare `bandf` in the corpus (an
un-normalised bandpass peaks at Q). Normalising the bandpass family first makes the default a
pure width change, which is the point. So C1 and C2 are two sections of one commit.

### C1 — Default q = 0.707 everywhere
- Sprudel: `resonance ?: 1.0` -> `?: 0.707` at the four `FilterDef` build sites
  (`SprudelVoiceData.kt:862,885,908,931`). Ignitor: bandpass/notch `1.0` -> `0.707` on both
  doors and the `EqSection` ctor defaults. `FilterDef` wire default if one exists.
- Guard: a spec that reads the default off EVERY surface (sprudel build, script stdlib, Kotlin
  extension, EqSection ctor, eqdemo preset) and asserts one value. This is the parity-pin
  pattern from D5; it exists because that drift happened once already.
- Songs: nothing to edit (a changed default changes sound, not source). Listen.

### C2 — The bandpass FAMILY normalised to unity peak
- `EqCore` RAW_TAP and BANDPASS arms, `SvfBPF`, and `FormantFilter`'s bands: scale the `v1` tap
  by the section's stored `k` (post-clamp), so peak at fc is 1 regardless of q. NOTCH, LP, HP
  and BELL keep their math (they are not peak-at-Q). See the Decisions section for why the whole
  family and not just the tap.
- Guard: for EACH normalised filter, `q = 4` and `q = 0.5` at `gain = 1` lift by the SAME dB at
  fc; a clamped-q case (`q = 500`, clamped to 200) normalises by the CLAMPED k. Existing
  `EqCoreSpec` tap parity rows against the legacy `Plus/Times` graph WILL redden and are
  re-based: the legacy graph is the old coupled behaviour, no longer the oracle. The RAW_TAP KDoc
  and the tap-to-bell conversion KDocs are rewritten in this commit; R2 is re-specified as
  non-parity in `docs/tasks/ignitor-optimizer-followups.md`.
- Songs: Der Schmetterling's tap params (7 defaults + ~14 overrides) retuned by ear; note the
  direction differs per voice (guitars get louder, the lead's high-q tap drops ~11 dB). Every
  bare `bandf` in the corpus is checked by ear after the default-q change.

### C3 — Envelope depth in semitones + migration
- Engine: the envelope law is applied in TWO places and BOTH change: `IgnitorFilters.kt:147-148`
  (the ignitor path) and `voices/strip/filter/FilterModRenderer.kt` (the path every sprudel song
  uses). A guard that exercises only the ignitor passes while every sprudel song stays linear —
  precisely the `drivePerAnalog` failure mode.
- Guard, per path: `+12` sweeps to exactly 2x base, `-12` to exactly 0.5x, `0` is identity,
  a clamp-saturation row (a deep positive depth on a high base hits the `[5, sr/2-1]` clamp
  and HIGHPASS goes silent). "Negative depth never produces a non-positive cutoff" is dropped as
  a guard: it cannot fail for `base * 2^x` and guards nothing. Bit-identity is NOT promised here
  (it is a sound change); the guard is the formula, on both doors.
- Also migrate: the `FilterEnvDef` default depth (`0.5` -> `7`) and the two-door default split
  (`audio_be` side defaults `0.0`); the four UI numeric tools (`SprudelNumericEditorTool`,
  `defaultValue = 0.5, step = 0.1` x4); and the engine KDocs that TEACH the linear law with a
  worked table (`FilterEnvDef.kt`, `IgnitorFilters.kt:40`, `LowPassHighPassFilters.kt:79`,
  `VoiceFactory.kt:465`).
- Migration in the same commit: every constant via `12 * log2(1 + old)`; every patterned depth at
  its endpoints, flagged in a list for the maintainer. Tutorials, lexikon, skill reference,
  whitepaper updated to say semitones and to say WHY (the DAW-convention sentence).
- UI: the parameter popup / any slider range for envelope depth. Inventory needed; `docs/popup`
  and the editor parameter tools.

### C4 — `wet` + `floor` with the shared equal-power helper
- One helper in `audio_be/filters` (`DspUtil.kt` neighbourhood): `equalPowerMix`. Pure, no
  allocation, takes `(dry, wet, w, floor)` scalars per sample; coefficients precomputed per block.
- Route: ignitor Phaser/Shimmer (`blend` -> `wet`, floor 0), body/vowel (`ParallelMixFilter` ->
  the helper, floor keeps its current value), orbit Phaser (additive -> helper, floor 1.0),
  reverb/delay on the orbit side (inventory their current wet handling first).
- Surface: `.wet()/.floor()` typed onto each additive effect node on the ignitor; `xxxWet` /
  `xxxFloor` on sprudel. `blend`, `bodyMix`, `vowelMix` deleted.
- Guard: the helper at `p = 1` holds 0 dB across the knob for decorrelated inputs and at
  `p = 2` holds constant amplitude for correlated ones (two rows, two laws); every routed effect
  at `wet(0)` is bit-identical to bypass — which REQUIRES an explicit `wet == 0` early-return in
  each caller, because the per-sample helper cannot early-return itself, and `dry*1 + wet*0`
  flips `-0.0` to `+0.0` and poisons on a NaN/Inf wet (`ShimmerIgnitor` today only early-returns
  when feedback is ALSO zero; that is the one that would go red). Orbit phaser at `floor(1)`:
  pin the dry coefficient at `w = 0.5`, where the two curves genuinely differ in wet law, not at
  `w = 1` where they agree trivially.
- C4.2 note (from the C4.1 review): `IgnitorDsl.kt` lines ~1364/1400 teach the linear blend
  formula and ~1781/1799 say "crossfade" - the blend->wet rename must CORRECT the behaviour
  claims in the same pass, not merely rename. Helper landed as `WetDryMix` (one name for the
  law; the exponent picks the statistic) rather than the tentative `equalPowerMix`.
- C4.2 review flags (round 1, 2026-08-24):
  - ✅ RESOLVED (maintainer, 2026-08-24, in the pitch-param session): THE BUS OWNS THE
    PHASER — built-in pipeline presets carry no `StageDsl.Phaser`; per-voice phasing stays
    available to custom pipelines only. `phaserFloor` is exact by default now. Guard:
    `PipelinePresetSpec` ("built-in presets carry NO Phaser stage"). Original finding below.
    ⚠ EAR LIST addition: single-pass HALVES the effective phaser wet on shipped songs —
    Tetris phaserWet(0.15), IrishLamentTechno phaserWet(0.25 / saw.range 0.3..0.6) were
    dialled against the double pass; retune by ear (values likely go UP). Also note: the
    per-voice mono component is gone (bus is stereo) — listen for image changes.
    Structural footgun (documented in phaserWet KDoc): orbit knobs are first-writer-wins,
    so a non-lease voice's phaser knobs are now fully inert (no corpus instance today).
  - 44.1 kHz note for the warmth->onepole migration: four converted values (23846, 23688,
    23197, 22309 Hz) sit above 44.1k's Nyquist clamp (22049) and become exact bypass there;
    at any rate they were near-transparent before. The new invariant (frequency, not raw
    coefficient) is the point of the change — do not chase this as a bug. The two patterned
    IrishLamentTechno `saw.range` sweeps are endpoint-exact only (atan-nonlinear interior,
    inaudible). ⚠ OPERATIONAL: the checked-out `klang-worklet.js` bundle is stale (gitignored
    build artifact) — rebuild the FE before any by-ear pass, or every onepole() site plays
    as bypassed.
  - (superseded) the phaser runs TWICE per note on the default `modern`
    pipeline — per-voice `StripPhaserRenderer` AND the cylinder-bus `Phaser`, from the SAME
    `Voice.Phaser` knobs. Pre-existing (at the additive floor-1 default the second pass only
    adds more wet; that is the shipped sound and why the goldens hold). But with the new
    `phaserFloor < 1` the dry is floored in BOTH passes (`dryC²`), so a full crossfade
    (`phaserWet(1).phaserFloor(0)`) does NOT leave the phased signal alone. KDoc now states
    the real path. Decide: which path OWNS the phaser (and should the two bypass gates —
    strip `> 0`, katalyst `>= 0.01` — agree)? Also note: the C4 orbit-phaser retune numbers
    in Decisions were computed single-application.
  - ACCEPTED posture (like C0.2's inert colon strings): out-of-repo scripts calling the
    ignitor builders POSITIONALLY with the deleted 2nd slot (`.phaser(0.3, 0.3)`,
    `.shimmer(0.3)`) are silently reinterpreted (slot 2 is now center / feedback). Named
    `blend =` fails loudly (guarded); positional cannot be detected. In-repo sites swept.
  - ACCEPTED debt (accumulating since C6a/C0.1/C3): the strudel-compat suite's remaining
    song cases feed `roomWet`/`delayWet`/... to the vendored strudel oracle, which cannot
    compile them — those cases only run on GraalVM environments. The plan's answer stays
    "cut non-structural cases from the compat suite"; do that cut as its own cleanup, not
    inside a rename chunk.
  - DEFERRED TO THE USER (deliberate, same as C1+C2's "build now, listen after"): the C4
    ear retunes. Every song site in C4.2 is a pure RENAME (values kept), so the corpus ships
    at the C4.1 level shift (Tetris body wet about -6.4 dB, Tetris orbit phaser -8.8 dB,
    IrishLamentTechno -4.6 dB per the corrected p=2 numbers above) until the user's listening
    pass moves the values UP by ear. Sites: Tetris bodyWet(0.2)/phaserWet(0.15),
    IrishLamentTechno phaserWet(saw.range 0.3..0.6)/phaserWet(0.25), StrangerThings
    vowelWet(0.40), GoldenCorpus phaserWet(0.15), plus every bare bodyWet/vowelWet user song.
  - REJECTED in round 3 (reason recorded): mapping a `+Inf` floor to 1.0 instead of the
    law's uniform non-finite -> 0.0. The law treats EVERY non-finite input (w or floor) the
    same way; special-casing +Inf floor would make the coercion input-dependent for an input
    that is only reachable through a runaway user expression. Raw engine, one rule.
    (`WetDryMixSpec` pins +/-Inf floor -> 0.0.)
  - (session ledger, not a C4 item) ADSR curve default unified to EXP (user decision,
    2026-08-24, own commit after the pitch-param work): UNSET = Exponential on every stage
    and every door, single authority `AdsrCurve.Default`. Ignitor door: bare adsrCurve()
    and typos coerce to exp; sprudel door keeps its control-pattern semantics (bare = no-op,
    typo = keep prior) — only the unset default is cross-door. The ignitor door used
    to default Square attack/release (runtime fallback + engine factory + script method
    "square" parameter defaults) while sprudel/strip resolved Exponential. ⚠ EAR: every
    ignitor instrument using .adsr() without explicit curves changes attack/release shape
    Square -> Exponential (nothing in the suite pinned the old waveforms).
  - (session ledger) Phase-pool `selection` rework (user decisions, 2026-08-24): STRING
    with value-colon compound `"name[:width[:blend]]"`; canonical `"normal"` (user: easier
    to write; `distribution`/`dist`/`gauss` are aliases), `"random"`, `"roundrobin"`
    (+`roundrobbin`/`rr`) — round-robin was the DEFAULT and is now OPT-IN (its cycling
    gargles audibly). Normal serves over the vocabulary's RANK order, median-centered
    (review HIGH: a K-space target collapsed to ONE entry on ~96% of notes whenever the
    band is unreachable — e.g. kMin 0.6/kMax 0.8 at 25 voices has ~0.1% in-band draws);
    width = rank-σ as a vocabulary fraction (0.1 tight / 0.5 default), blend = fraction of
    plain-random serves ("normal::0.9" = almost random, slight center edge — the user's
    asked-for shape). Postures: legacy NUMERIC selection now fails loudly (a CCE at the
    thunk — pre-alpha; silence would be worse since 0/2 SWAPPED meanings between the old
    and proposed numeric tables); alias rows in the parser spec share the coercion arm by
    design (unknown names ARE the default — aliases are documentation, not dispatch).
    ⚠ BENCHMARK: SongBenchmarkCases' phasePool case flips roundrobin->normal (+2 rng draws
    + an O(filled) rank serve per note-on) — per-block numbers not comparable across this
    commit. ⚠ The Schmetterling phasePool line currently carries the user's LIVE by-ear
    edits (band 0.60–0.80 at high unison is band-unreachable; every mode serves off-band
    Ks there — a kMin/kMax retune is the actual lever, selection only changes VARIETY).
- C4.2 decisions AS BUILT: (a) the ignitor floor knob is `dryFloor` (the plan's own rule:
  `floor()` is the arithmetic round-down, one word one concept); (b) `room`/`delay` HEAD
  functions renamed to `roomWet`/`delayWet` (their first slot IS the wet, and keeping
  `room(x)` alongside `roomWet(x)` would be two names for one knob - the C6a disease); the
  `room*`/`delay*` tails keep their names; (c) wet/dryFloor are NOT builder params on the
  ignitor - `phaser(rate, center, sweep)` / `shimmer(feedback, tone, pitches)` plus the
  typed knobs `.wet()`/`.dryFloor()` on the node (both doors), so the knob exists in exactly
  one place; (d) NEW wire field `phaserFloor` (sprudel `phaserFloor()`, engine default 1.0 =
  additive) reaches BOTH phaser paths; `IgnitorDsl.Phaser/Shimmer` gained `dryFloor` and
  renamed `blend`->`wet` on the node (goldens carry no ignitor param names - byte-identical);
  (e) sprudel `phd`/`phasdp` aliases stay, re-pointed at `phaserWet` (alias deletion is C6);
  (f) sprudel has NO shimmer fields, so `shimmerWet` exists only as the ignitor door's
  `.shimmer().wet()` until someone asks for a sprudel shimmer.
- Songs: every `bodyMix`/`vowelMix` -> `bodyWet`/`vowelWet` is a RENAME PLUS an ear retune
  (+3 to +4 dB more resonator in the useful middle, see the Decisions section); the three orbit
  `phaserdepth` sites (`Tetris`, `IrishLamentTechno` x2) move +3.6 to +3.8 dB for the same
  reason. `room`/`delay` are sends and do NOT change level; they only change name — but `room`
  is in nearly every song and 19x in the skill reference, and `phaserdepth` is the orbit
  phaser's wet, so both are in the rename sweep.

### C5 — `passes` on lowpass/highpass (the original D6) — ✅ DONE 2026-08-24 (commit be5392d2)
- Field + builder + R1 expansion + script/Kotlin surface + sprudel `lpx`/`hpx` as the third
  positional parameter, all in one commit per the D6 rule.
- Guard: `passes = 2` measures -24 dB/oct vs -12 at one octave above fc; optimizer expands to N
  sections; codec round-trip with non-default passes; `passes = 0` and negative coerce to 1.
- **DECIDED + BUILT (2026-08-24).** The two open questions are closed:
  - *Stagger, not plain cascade* (maintainer choice): the per-stage q is the Butterworth pole
    ladder `userQ * sqrt(2)/(2*cos((2k+1)*pi/(4N)))`, so at the DEFAULT q the cascade is exactly
    Butterworth - flat passband, -3 dB AT fc, `lpf(800, 0.707, 2)` still means 800. A resonant q
    compounds instead: `q = 1.0, passes = 2` sits **+3 dB** at fc (raw engine, documented on every
    door, no clamp). `analog` compounds the same way - every stage gets the full drive.
  - *The one-pole question is moot*: C0 removed the null-q topology swap, so `q == null` is the
    DEFAULT q on an SVF, never a one-pole. `onepole(freq)` is its own named thing and carries no
    `passes`.
- **Slot order is cross-door, decided in the C5 review (round 1, finding 1):** `passes` is the
  THIRD positional slot on every door - `lpf(freq, q, passes)` in sprudel, `lowpass(freq, q,
  passes)` from Kotlin, and the KlangScript door was REORDERED to match (`analog` moved from
  slot 3 to slot 4). Before the fix, `.lowpass(800, 1, 2)` built a 24 dB/oct cascade from Kotlin
  and a saturating single stage from KlangScript - the exact "same call, two meanings" bug this
  plan exists to remove. Migration cost was two call sites (`ATruthWorthLyingFor`), both moved to
  the all-named form because **KlangScript forbids mixing positional and named arguments** (the
  smoke test taught us that: `lowpass(x, 1.8, analog = a)` throws `KlangScriptArgumentError`).
- **Round 2 (two fresh reviewers, coding + DSP, 2026-08-24) - what changed:**
  - Both reviewers independently caught that round 1's `TimesIgnitor` fix was INERT: `buildIgnitor`
    wraps every non-leaf node in a `MemoizingIgnitor`, so `EqIgnitor`'s `isVoiceConstant` could only
    ever see leaves, and the guard for it hand-built a node shape the runtime never produces. Fixed
    by looking THROUGH the wrapper (it is pure delegation and keeps no serve count, so a section that
    goes static and stops calling it cannot starve another consumer), and the guard was rebuilt on
    the real `optimize() + toExciter()` path via `staticConfigureSkips`. Side effect, deliberate: the
    pinned `MemoizingIgnitor(ConstantIgnitor)` row flipped from dynamic to static - a wrapped
    voice-constant IS voice-constant; classifying by packaging was the bug.
  - `VoiceFactory` forwarding `passes` to `createLPF`/`createHPF` was completely untested: deleting
    it left EVERY C5 spec green while the entire sprudel door rendered single stages. Pinned in
    `VoiceFactoryFilterOrderSpec`.
  - `scaledBy` now mirrors `expandPasses` EXACTLY (literal folds; everything else through `times`).
    The round-1 `ParamIgnitor` fold was correct about `oscParams` but skipped the `safeOut` the fused
    door applies, so a NaN/Inf oscparam q would land on the SVF Butterworth fallback on one door and
    on the 0.1 q floor on the other.
  - Sprudel now ROUNDS the count instead of truncating (`2.9999999996` silently dropped a stage),
    NaN-guarded because `roundToInt()` throws on the render thread.
  - The KlangScript slot order is pinned by `KlangScriptFilterSlotOrderSpec` (it was pinned by
    nothing, and it is the highest-blast-radius line in C5).
- **PARKED for the maintainer (round 2, coding finding 5):** the slot reorder is right, but a user
  song in browser storage containing `.lowpass(2000, 1.2, 3)` (which meant `analog = 3`) now silently
  means `passes = 3, analog = 0` - a clean 36 dB/oct filter where there was a warm saturating one. No
  error, no diagnostic; the only symptom is that the patch sounds different. Options: a one-release
  intellisense warning on any 3-positional `lowpass`/`highpass`, or a release note. In-repo migration
  is complete (verified with a paren-depth-aware sweep, not a regex).
- **Follow-up, REJECTED for C5 scope (round 2, DSP):** `SvfIgnitor`'s coefficient cache requires BOTH
  `cutoffHz` and `q` to be `ParamIgnitor`, so it never engages for DSL-authored filters (their params
  are `ConstantIgnitor`) - every ignitor-door filter re-runs `computeSvfCoeffs` (a `tan`) per block,
  and a `passes = 4, analog = 3` cascade now pays that four times. Widening the predicate to
  Param-or-Constant, exactly as `EqIgnitor` already does deliberately, is output-identical and would
  make cascade cost flat. It is a change to the hottest path in the engine for every existing voice,
  so it belongs to the audio_be optimisation workstream, not to C5.
- **Resource ceiling (C5 review finding 2), flagged for the maintainer:** `passes` is coerced to
  `1..16` in ONE place (`coercePasses` in `FilterDef.kt`). It is a resource count, not a tone
  knob - unbounded, a live-typed `lpx(1e9)` allocates a billion filter stages inside a note-on on
  the render thread. 16 = 192 dB/oct, far past any musical use. If you want it raw, the ceiling is
  a one-line change.

### C6 — Canonical names + sprudel `band` (+ `tap` if decided)
(The alias deletions and the compat cut moved to C6a, which runs first.)
- Sprudel: add `lowpass/highpass/bandpass/notch` as canonical names, keep `lpf/hpf/bpf`, add
  `ntf/ntq`, add `band`/`tap`, delete the alias list above, unify casing.
- **How `band`/`tap` reach sprudel is decided, not open (maintainer, 2026-08-23): sprudel gets
  NO EQ implementation of its own.** Sprudel is one frontend driving the audio engine and is
  only here by accident; the engine is the horse and sprudel rides it. So `band`/`tap` become
  two new `FilterDef` variants on the voice, and `VoiceFactory` maps them onto the engine's
  existing `EqCore`. **Correction from plan review:** this does NOT "deliver D9's static tier".
  There is no EqCore in the voice path today at all (`VoiceFactory` is `toFilter()` +
  `combine()`); D9 v1 is eligibility tiers, an `EqCoreAudioFilter` adapter, contiguous-run
  folding and a mandatory BaseSvf ≡ EqCore ULP-0 parity pin. Baking two new variants is a
  narrow new path, and if ONLY Bell/Tap enter an EqCore placed inside the class-form chain, a
  sprudel `tap` reads whatever the PRECEDING class-form filters produced while an ignitor tap
  reads the node input: same word, two signals, the exact thing this plan exists to remove.
  So `band`/`tap` on sprudel are gated on D9's static tier landing first (the voice's fusible
  filters enter ONE EqCore, and the new variants join that list), or they wait.
- **Plan-review finding that changes the scope of `tap` on sprudel:** a tap's defining
  property is its LIST POSITION (it reads the pre-Eq input and adds at its slot; a `band`
  before a `tap` is re-injected from the uncut source), and its whole reason to exist is that
  N taps SUM where N bells multiply. Sprudel's voice is an unordered field bag that builds the
  filter list in a hardcoded order, with ONE scalar per field name. So a sprudel tap would get a
  position nobody chose and there could only be ONE per voice — Der Schmetterling's guitar
  needs three. That is a strictly weaker tap on one door, in a plan whose principle is "the same
  thing, the same way". **Decision for the chunk review, not settled here:** either (a) sprudel
  gets `band` only (a single bell is position-independent and one-per-voice is its natural
  cardinality), and parallel boosts stay an ignitor-surface concept; or (b) sprudel's filter
  list becomes genuinely ordered with repeatable entries, which is the "unordered voice model"
  flaw this plan explicitly does not fix. (a) is the honest default.
- **Pre-walkthrough facts, measured 2026-08-24 (after C5), so the chunk review starts from
  numbers rather than from the draft's estimates:**
  - **The `band`/`tap` gate is CLOSED: D9's static tier has not landed.** There is no
    `EqCoreAudioFilter` anywhere in the repo, and `VoiceFactory` references `EqCore` zero
    times — the voice path is still `toFilter()` + `combine()`. By this section's own gate,
    sprudel `band`/`tap` cannot ship in C6; the open (a)/(b) decision below is therefore not
    blocking C6, it is deferred with D9.
  - Canonical names genuinely missing on the sprudel door: `lowpass`, `highpass`, `bandpass`,
    `notch` (0 declarations each) and `ntf`/`ntq` (0). `notchf`/`notchq` already exist.
  - `freq`/`cutoffHz` unification blast radius across the three DSL-surface modules:
    57 `cutoffHz` + 51 `freqHz` occurrences. The recorded trap still applies — the render-arg
    `freqHz` is note pitch and is OUT of scope.
  - Aliases still to delete: `phd`, `phasdp`, `reverb`, `vibmod`.
  - **STALE NUMBER CORRECTED:** the sweep below budgets "bandf x13, bandq x11 in
    songs+tutorials". That is spent — C6a already deleted the callable aliases and migrated
    the corpus; songs+tutorials now contain ZERO. The 83 remaining `bandf`/`bandq` references
    are voice-data FIELD names (`bandf = str.toDoubleOrNull()`) plus KDoc `@tags` search
    keywords. Field names are wire-adjacent and this plan's rule keeps them, so the mechanical
    corpus sweep C6 was sized for is much smaller than drafted. `resonance` still has 2 sites.
- Envelope: `lpe/hpe/bpe` keep their names (depth), `lpadsr/hpadsr/bpadsr` are the shape, the
  ten-per-filter ADSR aliases go.
- Compat suite cut to structural cases.
- Songs/tutorials/docs: mechanical rename sweep (`bandf` x13, `bandq` x11, `resonance` x2 in
  songs+tutorials; plus whatever the aliases hit). The `freq`/`cutoffHz` unification
  (`docs/tasks/filter-frequency-param-naming.md`) folds into this chunk: same sweep, same files.

## Merged in
- `docs/tasks/sprudel-sound-function-surface.md` (captured 2026-06-05, blocking `glide` since
  2026-08-07): its question "compound string vs per-param" is answered above, per-param, and its
  "tool windows are the hard part with no known solution" is answered by the two-tier model. The
  task file should be marked superseded by this plan when C0 starts.

## Out of scope, deliberately
- Sprudel's unordered voice model (the reason for the prefixes). Named, not fixed.
- D9's RAMP-GATED tier (envelope/drift-modulated voice filters): still blocked on the EqCore
  ramp API. C6 delivers D9's static tier only, via the `band`/`tap` baking seam.
- R2 tap fusion in the optimizer: still the top optimizer followup, but RE-SPECIFIED by C2 as
  a non-parity rewrite (the legacy parallel-tap graph is the old coupled sound).

## Review provenance
Discussion 2026-08-23 between maintainer and assistant, then TWO plan-review agents (code +
DSP), 2026-08-23. The reviews found 4 CRITICAL, ~14 MAJOR, ~14 MINOR, all folded in above.

**Claims the first draft got WRONG, retracted here so the correction is on record:**
- "`lpf("<400:1:1 800:2:1>")` collapses alternation" — a misreading of per-cycle `<>` from a
  one-cycle query; it works. Struck as a reason.
- Reverb/delay as `floor 0` crossfades — they are SENDS on a shared per-orbit bus; dry untouched.
- Equal-power for ALL additive effects — wrong for correlated pairs (body, vowel, both phasers),
  where it ADDS the +3 dB bump it was meant to remove. Two laws, one helper.
- "The migration formula is exact for constants; patterns are the risk" — backwards. Exact only
  at the envelope endpoints; deep constants deviate by up to 1.7 octaves mid-sweep, patterns by
  ~27 cents.
- "The inline tool tier exists, the modal tier is new" — backwards. Every tool is modal today.
- "C6 delivers D9's static tier" — it does not; there is no EqCore in the voice path.
- The 48-name inventory excluded the 13-name notch family (61 total); sprudel already has a
  notch WITH an envelope.
- "Six oscp values" for the Schmetterling tap retune — ~21, in both directions.
- The plan's own C0 migration shape would have turned envelope depths into pass counts.

What held up exactly: the per-door q defaults and line numbers, the `1 + gain*q` coupling and
both measured dB figures, the linear-Hz `-1` dead zone, the -3 dB linear-crossfade dip for
DEcorrelated signals, the 32 `split(":")` sites, the ~160 song/tutorial migration count.
