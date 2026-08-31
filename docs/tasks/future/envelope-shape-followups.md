# Envelope shape: the three findings the ownership task measured but did not act on

Status: **future / follow-ups — the parent workstream SHIPPED 2026-08-31, these are what it left on
the table.** Design and build record:
`docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md`.

Each of these was measured while fixing the double-ADSR problem, and each was deliberately kept out
of that fix so it would not grow a second envelope through the safety door. They are independent of
each other; none is release-gating.

## 1. The attack curve is convex, which is why onsets sound soft

`AdsrCurve.Default = Exponential` (`audio_bridge/AdsrDef.kt:36`) applies to **all three stages**,
with `ADSR_EXP_K = 3.0` (`audio_bridge/constants/EnvelopeDefaults.kt:25`). For decay and release that
is right: fast drop, long tail. For **attack it is convex, i.e. a slow start**:

| progress through attack | level |
|---|---|
| 25 % | 0.059 (−24.7 dB) |
| 50 % | 0.182 (−14.8 dB) |
| 75 % | 0.445 (−7.0 dB) |

Half-way through the attack the voice is 15 dB down. The onset starts late and arrives as a ramp,
which is "onsets sound soft / weak / hollow", the most persistent complaint across ~50 review rounds,
and why turning the attack knob never did what the author expected.

Before `.adsrOff()` existed this compounded with an ignitor's own envelope and the numbers doubled in
dB (30 dB down at the half-way point). That half is fixed. **The single-envelope column above is
still today's behaviour.**

**Suggested:** default `attackCurve` to a **concave** shape (`InvSquare`, `p(2−p)`) while decay and
release stay `Exponential` — i.e. `AdsrCurve.Default` becomes per-stage rather than one value. Note
what that touches: `Default` is referenced by every fallback site on purpose ("flip it here, it flips
everywhere"), so this is a one-line change with a whole-catalogue consequence. **Expect a re-tune and
land it with a version note.**

Related, worth folding into the same round: **`decay` is a no-op while `sustain = 1.0`** (the stage
ramps peak→sustain, which are equal). It only surfaces when sustain alone is overridden through a
partial merge, which reads as "the decay knob is broken".

## 2. The millisecond minimums should scale with the note's period

Measured on the parent task's Measurement 3 — not an engine bug, a physics trap. A release shorter
than a couple of periods of the fundamental truncates the waveform mid-swing:

| release | broadband splatter at note-off (click energy / note energy) |
|---|---|
| 13 ms (Der Schmetterling's guitar value) | −69.7 dB |
| 60 ms | −85.1 dB |

15 dB more click energy, on a note (E1, 41 Hz = 24 ms period) whose release is shorter than **one
cycle**. Two independent blind reviewers flagged "clicking/knocking on the low rhythm-guitar attacks"
in the same round.

The same insight applies one layer down: `ENV_DECLICK_SECONDS = 0.001`
(`audio_bridge/constants/EnvelopeDefaults.kt:44`) is a flat millisecond and **cannot round a corner
inside a 24 ms cycle**.

**Rule of thumb to implement:** minimum release, and the de-click window, scale with the note's period
(≈2–3 periods), not a fixed millisecond count. Same idea as the harmonic-relative string filter:
express it relative to the note. The open question is *where* the floor lives — coercing the user's
number (the project coerces, never throws) versus a floor applied at render time, which keeps the
authored value visible in the DSL.

## 3. An `.adsrOff()` voice can refuse to end

`Vca(on = false)` renders a unity gate, so amplitude is entirely the ignitor's business. That is the
point, and for anything with its own envelope or physics it ends on its own. **A plain sine or a
self-oscillating filter never falls silent**, and nothing upstream will stop it: voice lifetime is
`gate end + max(voice release, ignitor tail)`, and the ignitor tail is `null` (contributes nothing)
exactly when there is no envelope to read.

Today the teardown fade (`VCA_OFF_TEARDOWN_FADE_SECONDS`, 4 ms) still ends such a voice cleanly, so
this is not a runaway-voice bug — it is that the *duration* of a gate-only voice is decided by the
voice's own release default rather than by anything the author expressed.

**What was sketched:** a **maximum tail after gate end, then a forced fade**, configurable on the
`Vca` stage (which today carries only `expK`, `declickSeconds`, `on` — `audio_bridge/PipelineDsl.kt:118`).

⚠️ **Keep the two mechanisms separate.** Resist the framing "the VCA needs a safety envelope": that
invites the second ADSR back in through the safety door.

| | ADSR | fade guard / cap |
|---|---|---|
| purpose | musical shaping | continuity at boundaries, ending things that refuse to end |
| controlled by | the author, per note | the engine, always on |
| duration | ms to seconds | ~1–2 periods of the note |
| audible as shaping | yes | no |

A cap never shapes anything. If it starts to, it has become an envelope and the parent task's whole
argument has been undone.
