# Voice Takeover — a new note displaces the ones still ringing

> Status: **DESIGNED, NOT STARTED.** Phase 1 (`takeover`) is ready to build. Phase 2 (`glide`) is
> **deliberately deferred and blocked** — see [Phase 2](#phase-2--glide-deferred--blocked).
> Captured 2026-08-07.

## Why

Realistic plucked/struck instruments (guitar above all) need a long amp release and a long `lpadsr`
tail so a note *rings out*. But in a pattern the next note arrives long before the tail is gone, so every note overlaps
its predecessor. When the pitch changes you get a brief unwanted polyphony and a dissonant smear.

The fix is not a shorter release — that kills the instrument's character. The fix is that **a new note displaces the
ones still ringing**, the way a re-plucked string discards its previous vibration.

## The model

> **When a note starts, every *earlier* still-ringing voice in its group is faded out over
> `takeover` seconds. Voices that start on the same frame never take over from each other.**

That is the entire rule. Three parts:

| Part       | Definition                                                                                        |
|------------|---------------------------------------------------------------------------------------------------|
| **Group**  | `cut(n)` when given (explicit, cross-pattern), otherwise the implicit pair `(sourceId, cylinder)` |
| **Victim** | any active voice in the group whose **onset frame is strictly earlier** than the new voice's      |
| **Fade**   | a smoothstep ramp `1 → 0` over `takeover` seconds, after which the voice is dropped               |

### Why the group is `sourceId`, not the orbit

`sourceId` is the pattern-expression identity (`generateSourceId`, `lang_helpers.kt:39` — hashed from the source
location, stable across live re-evaluation, preserved by `SprudelVoiceData.merge`). It means "this line is
self-displacing" and needs no user action.

The orbit alone was rejected: orbits are a shared 16-slot resource, so a guitar and a bass parked on the same orbit
would silence each other. `cylinder` is kept in the key only as a tiebreaker so the same pattern routed to two orbits
does not cross-fade between them.

### Why same-onset voices are exempt

This is the one structural rule that has to be there — it is what makes the feature safe on real songs. Everything that
starts on the same frame travels together:

- **chords** — `[-3,-7]`, `[[-4,-5] [-1,-3]]`
- **`superimpose` layers** — `.superimpose(pan(0.80)).superimpose(hpf(3300)…)`
- **`unison` / `spread`** — one voice anyway

Verified against `builtinsongs/DerSchmetterling.kt` (chords × 2 nested superimposes = 4 voices per chord note, all
sharing one onset).

### What comes free from the engine

`body()` / `vowel()` are **orbit-level Katalyst effects** — they run once on the summed cylinder mix, not per voice
(`cylinders/katalyst/`, `audio/MEMORY.md`). Delay and reverb are likewise cylinder-level tanks. So fading a voice at the
**send** stage stops it *feeding* those resonators while their own high-Q state keeps ringing out.

That is exactly the acoustic caricature we want, for free: **the string stops, the body does not.**
Der Schmetterling already runs `.body("violin").bodyMix(0.3)`, so it benefits immediately.

### Why the fade can be short

A 5 ms fade under the incoming note's 5 ms attack is enough: the fresh transient perceptually masks the decaying tail.
That is why this needs no curve sophistication — smoothstep over linear costs 3 multiplies for 5 ms and removes the
slope-corner click documented in the VCA de-click work (`audio/MEMORY.md`, `ENV_DECLICK_SECONDS`), so use it, but do not
expect to hear the difference.

**Practical note:** the tail is often still *loud* when the next note lands (e.g. `clip(0.86)` with
`adsr("0.005:3.5:0.0:0.05")` leaves only ~14 % of a step of decay). Loud tails need longer than 5 ms — expect to tune
5–40 ms by ear per patch. `takeover` is patternable from day one.

## Rejected alternatives (do not re-litigate)

| Idea                                                                             | Why it was dropped                                                                                                                                                                                                                                                        |
|----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Hard cut** (today's `cut`)                                                     | Instant mid-buffer `iterator.remove()` — a click.                                                                                                                                                                                                                         |
| **Gate-based rule** ("only take over voices already past `gateEndFrame`")        | Elegant on paper (`clip()` would control what overlaps), but wrong abstraction: a plucked string has no gate. It is excited and decays; a re-hit displaces it whether or not a notional gate has ended. Also silently coupled the feature to `clip`, which is too clever. |
| **Slot count / voice pool** (`poly(n)`, `strings(n)` — "a guitar has 6 strings") | Over-modelling. With same-onset exemption already protecting chords and layers, `N=1` covers every case we actually have. Purely additive later if it is ever missed.                                                                                                     |
| **Time-fraction rule** ("take over voices past 50 % of their duration")          | Arbitrary, unteachable, and wrong for held notes.                                                                                                                                                                                                                         |
| **Overriding the amp ADSR release**                                              | Leaves the ignitor's own `.adsr()` tail, the filter ringing and the `lpadsr` sweep alive. A post-VCA send-stage fade kills all of them in one multiply, and is one code path for synth *and* sample voices.                                                               |

## Naming history

`cut` (taken, and hard), `choke` (drum-machine baggage, names the violence not the quantity), `damp`
(collides with `MasterStageDsl.Reverb.damp` = reverb HF damping — violates the one-word-one-meaning rule), `mute` /
`hush` (taken as structural pattern ops), `ringOut` (reads as an extra ADSR stage, i.e. unconditional — but the
behaviour is conditional), `handover` (right idea, but the time felt like a property of the outgoing note).

**`takeover`** won because the number belongs to the *event*, not to either note: "a takeover takes 5 ms". It also
matches the physical picture the feature models — the new note takes the resource over.

The word already appears in engine prose for `VoiceLease` ownership (`Cylinder.kt:129`,
`VoiceLease.kt:21`); that is a different mechanism but the same underlying idea (a newer voice displaces an older one),
so it reads as consistent. No rename needed.

---

## Phase 1 — `takeover` (build this)

### Surface

```kotlin
note("c3 e3 g3 e3").s("gtr").adsr("0.005:3.5:0.0:0.05").takeover(0.005)
s("hh*4 oh").cut(1).takeover(0.003)                      // explicit group, now click-free
note("c3 e3").s("gtr").takeover("<0.005 0.03>")          // patternable
```

KDoc first line (this *is* the spec, in user words):

> Time an earlier note takes to be taken over when a new note starts on the same line.
> Left alone, a note rings out through its normal ADSR. When a new note arrives, earlier notes fade
> out over this time instead — long enough to stay smooth, short enough that the new note's attack
> masks it. Notes starting together (chords, `superimpose`, `unison`) never take over from each other.

### `audio_bridge`

`VoiceData.kt` — one nullable field, seconds, `null` = off (no behaviour change):

```kotlin
/** Seconds an earlier voice in this voice's takeover group fades out when this note starts. */
val takeover: Double? = null,
```

Nothing to hand-write for the wire: `@WireFormat` walks the graph from `ScheduledVoice`, so the KSP trust-codec
regenerates. Add the field to the `WireCodecRoundTripSpec` fixture.

### `sprudel`

- `SvdGroups.kt` — new `SvdTakeover(takeover: Double?)` group + `mergeSvdTakeover` (field-wise, `over`
  wins), plus the flat accessor on `SprudelVoiceData` in the established idiom.
- `SprudelVoiceData.toVoiceData` — map it (near `cut`, `:1043`).
- `lang_dynamics.kt` — `takeover()` in all four DSL forms (`SprudelPattern.`, `String.`, bare
  `PatternMapperFn`, and the **chained** `PatternMapperFn.` form). Use
  `_liftOrReinterpretNumericalField`, same shape as `cut()` (`lang_sample.kt:708`). See
  `[[Sprudel DSL test coverage]]` — cover the chained form, it is the codebase-wide gap.
- Mirror into the KlangScript stdlib in the **same commit** — parameter parity.

### `audio_be`

**Decide** in `VoiceScheduler.promoteScheduled` — exactly where the `cut` block and its
`// TODO: Use a fade out / release phase instead of hard cut?` sit today (`VoiceScheduler.kt:381`). The min-heap pops in
`startTime` order, so within a block an earlier voice is always already in
`active` when a later one is promoted. No ordering hazard.

```
onsetFrame(new) = ((absoluteStartSec - clock.startTimeSec) * sampleRate).toInt()
for each ActiveVoice av in the same group:
    if (av.onsetFrame < onsetFrame(new)) av.voice.takeover(atFrame = onsetFrame(new), frames = fadeFrames)
```

> ⚠️ **Do not compare `Voice.startFrame`.** `VoiceFactory` passes `nowFrame` (block start) as the
> start frame for **sample** voices and the scheduled frame for **synth** voices
> (`VoiceFactory.kt:315` vs `:250`). Comparing those would make the same-onset test wrong across
> voice kinds. Compute a dedicated **`onsetFrame`** once per promotion in `promoteScheduled` and
> store it on `ActiveVoice` — uniform, exact, epoch-independent.

Group match is allocation-free (`ActiveVoice` already carries `sourceId`; `Voice` carries `cut` and
`cylinderId`):

```kotlin
if (newCut != null || av.voice.cut != null) av.voice.cut == newCut
else av.sourceId == newSourceId && av.voice.cylinderId == newCylinder
```

**Apply** in `SendRenderer` (`SendRenderer.kt:36`). `gainMultiplier` will not do — it is hoisted out of the sample loop
as a block constant, and a 5 ms fade is roughly two 128-frame blocks. Branch once per block on `chokeActive`, keep the
existing loop untouched on the fast path, and run
`gain = 1 - p*p*(3-2p)` per sample in the slow path.

`Voice` gains the ramp state and `fun takeover(atFrame: Int, frames: Int)`; `render()` returns
`false` once the ramp reaches 0 and the existing swap-remove in `VoiceScheduler.process` reaps it.
`endFrame` stays a `val`.

### Follow-on (same task, after the ramp exists)

Route `cut` through the same ramp with a small default time instead of `iterator.remove()`. That removes a click that is
in the engine **today** and closes the standing TODO.

### Tests

- `audio_be` `VoiceTakeoverSpec` — same-onset voices untouched; earlier onset faded; ramp monotone, reaches 0, voice
  reaped; group isolation (different `sourceId` / different cylinder unaffected); explicit `cut` group overrides the
  implicit key; `null` = byte-identical to today.
- Sample-vs-synth same-onset case (guards the `onsetFrame` decision above).
- `audio_bridge` wire round-trip.
- `sprudel` `LangTakeoverSpec` — all four DSL forms, field reaches `VoiceData`, patternable.
- **Mutation-check every new test** (mutate → red → restore) per `[[Review-loop standard]]`.

### By-ear validation

Der Schmetterling's GTR1/GTR2 lines are the target. Also add a setup to
`ignitor/GuitarClickHuntTest.kt` — the standing click-hunt harness.

---

## Phase 2 — `glide` (deferred + blocked)

The natural sequel: on takeover, also take over the **frequency** — the new note starts at the old note's pitch and
slides to its own. A sliding guitar.

Note the asymmetry: the two knobs act on **different voices**.

|            | acts on                | does what                                     |
|------------|------------------------|-----------------------------------------------|
| `takeover` | the **outgoing** voice | ramps its send gain to 0                      |
| `glide`    | the **incoming** voice | starts it at the old pitch, glides to its own |

Same trigger, opposite ends — which is why `glide` must *imply* `takeover` (gliding away from a pitch that is still
sounding is just a doubling). Naming settled: **`glide`**, not `takeoverFreq` — it is the word every synth uses, and the
namespace stays clean because `takeover` and `glide` are two different targets rather than a base and a qualifier.

### 🚧 Blocker — compound-param unification must land first

`glide` needs at least a time **and** a curve, i.e. it is a **compound param**. Klang has no settled answer for those
yet, and today's workaround is to spawn one function per sub-param (`glideTime()`, `glideCurve()`, …) — which we want to
**stop doing**.

**What has to be decided first: one function that expresses both forms.**

```kotlin
glide("0.02:scurve:2")      // compound colon-string — one value
glide(0.02, "scurve", 2)    // per-param — each param constant OR its own pattern
```

The per-param form is the point: a compound `"a:b:c"` string is *one* value, so you cannot modulate one sub-param with
its own control pattern — which breaks the project rule that all params accept control patterns.

The tracked home for this problem is **`docs/tasks/sprudel-sound-function-surface.md`** (currently scoped to the `snd*`
family, with the same two forms proposed and the same blocker stated). Before
`glide` can be built, that doc's scope has to be **widened from `snd*` to compound params in general** and the mechanism
actually settled — this is a language-surface decision, not a per-function one, and `adsr`, `distort`, `lpadsr`,
`sndDust`, … all wait on the same answer.

**And the hard part is the UI.** The `@param-tool` / `@param-sub` annotation model assumes exactly ONE composite param,
which it decomposes into labelled sub-fields for an editor (`docs/tasks/sprudel-ui-tools.md`). With N
independently-patternable params it is genuinely unclear what the editor should be:

- one tool per param, or one combined editor writing back into N args?
- how does an editor round-trip code that **mixes literals and control patterns** across the N params?
- the single-string shorthand still needs the old composite editor — two code shapes to support.

**This is an open problem with no known solution yet.** It must be answered before `glide` — and before any further
compound param — is designed, or we just grow the debt.

### Implementation sketch (for when it unblocks)

- Curve cannot be an `Ease` lambda — `VoiceData` is `@WireFormat`. Reuse **`AdsrCurve`**
  (`Linear, Square, Cube, SCurve, InvSquare, Exponential`, `AdsrDef.kt:29`), already on the wire and already the curve
  vocabulary for `adsrCurves` / `lpadsr`. Parameter parity for free.
- Decided in the same place (`promoteScheduled`, where the victims are already identified) but flows the other way: pass
  `glideFromHz` into `VoiceFactory.makeVoice`.
- New `GlideRenderer` as the first stage in `voices/strip/pitch/`, next to `VibratoRenderer` /
  `AccelerateRenderer` / `PitchEnvelopeRenderer` / `FmRenderer`.
- **Which victim's pitch, when a chord is taken over by one note?** Caricature answer: nearest in pitch among the most
  recent onset taken over — a guitarist slides on *the same string*, i.e. to the nearest fret. One line, musically
  right.
- **The re-attack gives it away.** A real slide does not re-pluck. No feature needed: pair `glide`
  with a softer `attack` and it reads as a slide instead of a hammer-on. Doc note.
- `glide` with no `takeover` set: switch takeover on with a small documented default rather than silently no-op.

## Related

- `docs/tasks/sprudel-sound-function-surface.md` — **the Phase 2 blocker.** Compound-string vs per-param surface; needs
  widening from `snd*` to all compound params.
- `docs/tasks/sprudel-ui-tools.md` — the `@param-tool` editor catalogue that the UI question lands in.
- `docs/tasks/per-playback-engine.md` — the per-playback `PlaybackEngine` / `VoiceScheduler` model Phase 1 plugs into.
- `audio/ref/data-model.md` — `VoiceData` field catalogue (update when the field lands).
