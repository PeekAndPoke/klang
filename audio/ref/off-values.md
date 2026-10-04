# The off-value table

> The one home of the gate off values (built in phase 3 step 2, 2026-09-20). Moved here on 2026-09-28 from section 5b
> of the archived phase 3 record (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`); code and memory
> point here and do not restate these values.

A stage is not built when its gating knob is a `Param` or `Constant` LEAF and resolves either to a
non-finite value (the unset sentinel, `SLOT_UNSET`, is `Double.NaN`) or to the stage's off value
below. A knob that can MOVE within a note is never gated. This is the one home of these values;
code and memory point here and do not restate them.

| stage | off value | why, and what is not a fold |
|---|---|---|
| coarse | unset, or `<= 1.0` | at or below 0 and at a non-finite amount the render already takes a bit-exact bypass, so there the gate folds a bypass. In `(0, 1]` the engaged loop takes every sample but latches it through `nanGuard()`, so a NON-FINITE UPSTREAM SAMPLE used to come out as 0.0 and now passes through |
| crush | unset, or `< 1.0` | a fold: `levels = 2^amount`, and the renderer bypasses below two levels. At exactly 1.0 the quantizer RUNS (with the FLOOR law of D1, landed in step 4 as `CrushCore` (`quantize`; `halfLevels` decides when it engages), a saw becomes a two-level -1/0 pulse, +1 only at an exact +1; it was a three-level staircase under the old round), so 1.0 is ON |
| distort (`IgnitorDsl.Distort`, the fused node) | unset, or `<= 0.0` | **SINCE STEP 4 (2026-09-25) the fused node runs the STRIP's law (`DistortionCore`: no cap, the drive inside the oversampler) and this gate is its ONLY bypass: a leaf `<= 0` is not built; a modulated amount `<= 0` runs at unity drive (W5's state hazard designed out). What follows describes the node before step 4.** **A BEHAVIOUR CHANGE, not a fold.** The node is `drive(amount).shape(shape)` and only the DRIVE half ever bypassed, so the tree's chosen shaper stayed on the signal at unity gain. Modelled on a 220 Hz sine through the real chain (shaper, DC blocker, `softCap`): soft -1.77 dB, tube -6.24 dB at 16.8 % THD, gentle +0.64 to +5.21 dB, zerosquare +1.55 to +16.83 dB at 37 % THD, and `rectify` removes the fundamental altogether (full wave, an octave up). Neither authoring door builds it (both spell `distort` as `Shape(Drive(...))`); since step 5 it is `classic()`'s distort stage (before that, the only production site was `WarmupVocabulary` at 0.3). Gating it aligns that node with the `drive` row and with `Ignitor.distort(Double)`, which always short-circuited at the same value. It does NOT reopen ledger W5's gate-flip pop: W5 is a MODULATED amount crossing 0, and a modulated amount is not a leaf, so it is never gated |
| drive (`IgnitorDsl.Drive`, the row both doors reach) | unset, or `<= 0.0` | two things at once. At or below 0 a TRUE FOLD: `DriveIgnitor` already copies its input through unchanged. At a NON-FINITE amount it CLOSES A HOLE: `amt <= 0.0` is false for a NaN, so the gain was `10^(NaN * 1.2)` and every sample of the voice came out NaN with nothing between it and the orbit mix. Step 3 would have walked into it, because a `classic()` distort slot defaulting to `SLOT_UNSET` wires exactly this node. **`Shape` is not gated and cannot be**: it carries a transfer function and no amount knob, so there is nothing to read an off value from |
| tremolo | unset, or depth `<= 0.0` | a fold. The RATE is not a gating knob, nor is `shape`. Since 2026-09-29 the built stage is a composition (the oscillator of the shape, `range(1 - depth, 1)`, a multiply), so a gated-off tremolo runs no oscillator. A BUILT tremolo on the signal path sets `BuiltIgnitor.gatesOutput`, so the voice is never culled (section 6, pulled into 3b) |
| `mul` (`Times`, and the optimizer's `Affine(x, -0.0, k, -0.0)`) | exactly `1.0`. **Unset is NOT off** | the one asymmetry, forced by three existing specs: `TimesIgnitor` already sanitises a non-finite factor to an exact zero, and the node also sits in PARAMETER positions where that zero is the point. **Consequence: a `mul` slot must default to a safe literal, never `SLOT_UNSET`, or an unwritten one silences the voice** (guarded since step 2 by a row over `pregain()`, the one door that places a `mul` slot today). The `Affine` form is required because the optimizer rewrites a bare `x.mul(k)` and every registered tree renders optimized. The fold also takes only a SIGNAL survivor (`Ignitor.isBlockConstant`, structural and fixed at construction): what it drops is the multiply's `safeOut`, which on the audio spine fires only on a sample the survivor cannot produce, but in a PARAMETER position is what keeps a non-finite coefficient in range (`q = param("res", +Inf).mul(pregain)` resolved to `SAFE_MAX` and then the q ceiling; folded it would stay `+Inf` and land on the q fallback, a different filter). One qualification, audited in review round 3: on the audio spine every
arithmetic and unary node carries its own scrub, but the KARPLUS family writes `filtered * decay`
straight into its delay line with `decay` read raw, so an authored `decay > 1` diverges and the
fractional read turns the first infinity into a NaN. That divergence is authored character, not a
defect (the Motor stays raw), but it means the rule is "any NEW spine node that can emit a
non-finite sample from finite input owes a substitution at its own read", not "none can" |
| onepole | unset, or `<= 0.0` | NOT a fold on an authored tree: `onepole(0)` was a 5 Hz lowpass (the `clampSvfCutoff` floor), which is -33 dB at 110 Hz and -52 dB at 1 kHz, so a tree that used it as an accidental mute gets 30 to 50 dB louder. It is still right: `0` means off on every other door. Note the wart: `lowpass(freq = 0)` is still a 5 Hz filter, because a NUMBER is never off for the four SVFs |
| the four SVF filters | only when the cutoff is UNSET | a lowpass at 20 kHz is not an off state, which is why the rule is "unset" and not a number. A non-finite AUTHORED cutoff used to build a 1 kHz filter (the `clampSvfCutoff` fallback) and now builds none: a behaviour change as well as a NaN fix |
| the envelope | `on` at exactly 0.0 (either sign); unset is ON (since 3c) | inverted from the plan: the strip VCA ran on every voice (until step 9), so `classic()`'s ADSR is built BY DEFAULT and switches off only through the `adsrOn`/`adsrOff` slot of step 3. Its unset case was NOT safe, and step 2 had to make it safe: the unity-`mul` fold removed the `TimesIgnitor` scrub that used to turn a NaN into silence, so `sustainLevel` now substitutes its own default (`ADSR_SUSTAIN_LEVEL`) at the ADSR's read (`expK` did too until 3c removed it; every exp stage now bends at `ADSR_EXP_K`). SINCE 3c the envelope has its own switch: `Adsr.on` OFF at exactly 0.0 (either sign), unset ON (the second 'unset is not off' after `mul`), any other number and a non-leaf ON. OFF is not built, but it still reports the release tail from a LEAF release (a non-leaf release on an off envelope reports none, to avoid building it), because the strip's `adsrOff` keeps the voice's lifetime. Not a clamp: every finite value passes through untouched. The substitution runs BEFORE the existing coercion, so the infinities move too: a `+Inf` sustain used to hold at the 1.0 rail and a `-Inf` at 0.0, and both now read as unset and take the default, which is how every other knob reads a non-finite value. A non-finite `releaseSec` also stopped reporting a NaN release TAIL, which used to swallow a SIBLING's real one. The `adsrOff` slot itself landed in 3c as `Adsr.on`; `classic()` fills it in step 5 (see section 6's caveats). |
| the phaser | NOT gated | the plan lists it; no per-voice phaser is in `classic()` and the stage retired with `PipelineDsl` (step 9), so the row would be dead code |

**The compound fill does not survive SLOTTING, and step 5 must answer it again (3a review, round 2).**
The filter doors adopted the rules register's compound-door fill in step 3a AT THE DOOR: any of the
five envelope knobs names the stage, and a call that names one writes every companion it left out,
`env` included. That reading happens at CALL time, on named-against-null. A slotted built-in does
not call the door per note: `classic()` will hand `Lowpass(env = Param("lpenv", SLOT_UNSET),
attackSec = Param("lpattack", SLOT_UNSET), ...)` once, and the per-note decision then happens in
`filterEnvDef`, which has no notion of "named". A pattern that writes only `lpattack` leaves `env`
unset, the depth resolves to 0, and the tree renders a STATIC filter where the same `lpf(attack =
...)` through the strip builds a 7-semitone sweep. So the fill has to be answered a second time at
the slot layer, in step 5, and the answer is a design question: what does "the stage is named" mean
when the caller is a pattern writing slots?

**A cheap shape for it, proposed in the 3a review and NOT built** (step 5 decides): ask the same
question one layer down, against the bag instead of against `null`. A knob is "written" when it is a
`Param` whose name resolves to a finite value in `ignitorParams`; in `filterEnvDef`, when the resolved
depth is 0, if any of the five knobs is written, take `FILTER_ENV_DEPTH_SEMITONES` instead of
returning `NONE`. It is the gate's existing move (the gate already reads the bag at build to decide
whether a stage exists), it composes with the door fill rather than replacing it (a door-filled
depth is a `Constant`, not a `Param`, so the question never arises), it costs five map lookups per
filter per note-on and nothing per block, and an instrument that declares a real default
(`env = Ignitor.param("lpenv", 24.0)`) is untouched. **The law to decide with it:** does "written" mean
finite-in-the-bag only, or also a slot whose AUTHORED default is a real number? Finite-in-the-bag is
what sprudel's `!= null` means and is the recommendation. **DECIDED 2026-09-25 (maintainer): finite-in-the-bag only.** A
knob is written when the note's bag holds a finite value for its slot; an authored default alone never
switches the envelope on. **BUILT in step 5 (2026-09-25)** as `slotLayerDepth`/`writtenIn` in
`IgnitorDslRuntime.filterEnvDef`, with the implementer's correction: a WRITTEN depth always stands (an explicit
0 included, the strip's `depth ?: 7`); only an UNSET depth slot (a `Param` whose default is `SLOT_UNSET`, as
`classic()` places it) takes `FILTER_ENV_DEPTH_SEMITONES`, when any of the FOUR stage knobs is written; an
authored depth default, 0 included, is never filled (corrected in step 5's round 1); a `Constant` depth (a
door fill) is never the question. The rule's text lives in `/dsl-design` section 4.

**The distort question this leaves open, for D2.** `IgnitorDsl.Distort` WAS legacy by its own KDoc (step 3b
corrected it: it is kept as the one node that gates drive and shape as a unit, and the only one that could
drive inside the oversampler as the strip does; `Shape` itself is still not gated, its shape and factor are
read at build) and no production site builds it; both doors emit `Shape(Drive(...))`. `Drive` is gated, `Shape` cannot
be (it has no amount knob). So `classic()`'s distort stage is either the legacy `Distort` node, which
gates as a unit, or `Drive` plus a `Shape` that runs at unity gain on every voice, which is D2's
divergence made unconditional. Decide it with D2.

**One consequence recorded rather than fixed.** A gated-off stage does not build its NON-gating
param subtrees, so a `perlin`, `berlin` or `crackle` knob in a gated stage's rate or q position no
longer takes its build-time draws and every later drawing source shifts. Chosen deliberately over
the alternative (refusing to gate unless every param is a leaf), because that would refuse to gate a
filter whose cutoff is unset whenever its `q` draws, and the filter would then build with a NaN
cutoff that `bilinearK` silently substitutes with 1 kHz: the step-1 defect, on the very stage the
gate exists for. Pinned by a row. A third option exists and was recorded as not taken: gate the
stage but still build its knob subtrees in declaration order and discard them, which reproduces the
stream exactly. It is not free, because a discarded subtree that also sits on the live spine is
reached again through the build cache and flips that node's memo from pure delegation to a per-block
cache plus a buffer copy, for every block of the voice. Worth revisiting in step 3, when every
filter cutoff becomes an unset-default slot.

**The gate is also the NaN guardrail.** `SLOT_UNSET` is NaN and the `Param` leaf hands back its
DEFAULT for a non-finite override, so a slot whose default IS the sentinel resolves to NaN. For a
gated stage the gate keeps it out of the DSP. For an UNGATED stage with a NaN knob there is no
second line of defence: the envelope's `sustainLevel` is the live example (`expK` was the other until 3c removed it).
