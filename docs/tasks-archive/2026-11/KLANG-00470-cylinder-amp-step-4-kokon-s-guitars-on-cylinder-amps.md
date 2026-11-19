# KLANG-00470: Cylinder amp step 4: Kokon's guitars on cylinder amps

> **Status (2026-11-19): DONE.** The maintainer, 2026-11-19: 'Kokon is ported well ... please close this step'
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

Step 4 of KLANG-00466 (the guitar amp on the cylinder): Kokon plays "one guitar" through four rigs picked from
Der Schmetterling's menu, on ten lines. It gains the most from one amp per guitarist, because it plays what the
per-note rig cannot do: power chords (`wings`, `strike`), chords (`swell`, `strum`) and notes left ringing under
the next (`spin`, `chime`).

It is also the harder port, for the same reason. It waits for the verdict of KLANG-00469 (step 3): if the
cylinder amp does not stay in Der Schmetterling, this step is not done.

## Reworked by the maintainer (2026-11-19, to Klang-Main-Dev)

After listening to step 3 ("brilliant! it sound so much better and smoother... like real guitars!"): "let us port
Kokon... there we also need to port the guitar compression from Schmetterling. Basically the same rig and adjust
the levels roughly, i will do the finetuning." Asked which numbers the parts take, the maintainer chose
**Schmetterling's parts**; asked for a copy of the old song, **no copy**. This replaces every line below that
speaks of a switch, of "both positions", and of porting Kokon's own numbers. What holds now:

- **A full port, no switch, no copy.** The old Kokon is in git history.
- **The parts are Der Schmetterling's as its file holds them on the day** (pickups, pedals, preamps, power amps,
  cabs, `snareCut`, `ampLevel`), copied into Kokon as `Kat.fn` chain parts (pickups `Ign.fn`, in the voice).
  Kokon's own drifted numbers (section 6 below) go. `snareHz` stays Kokon's own value. Sharing one set of parts
  between the songs by import is KLANG-00463, not this step.
- **Der Schmetterling's guitar compressor on every guitar cylinder**, with the values its bus functions hold on the
  day, written once per cylinder in a bus function, as in step 3.
- **The shape is step 3's**: `makeGuitar(pickup)` ends at the pickup; `makeAmp` holds the stages in a 4x region;
  sections 1 to 3 below stand (one amp chain per cylinder; a line's `hpf` / `lpf` behind the rig move to the end of
  its chain as slots; the old gain goes to the fader).
- **Levels roughly, the fine tuning is the maintainer's.** Each line's level in the mix is brought near what it was
  (a rough match by measurement, within a couple of dB), since the parts and the level trim changed. The numbers
  set that way are marked in the song as rough starting values.
- **D13: option b**, the plan's proposal: port as it is, the four off-centre single lines keep their pan before the
  amp, and the maintainer listens for the drift. A `pan` stage on a chain is built only if it is heard.
- **The tremolo stays where it is**, before the amp (section 5's first way). The clean lines start at
  `.gain(0.5)` (section 4).

## Read first

- `/klang-music-writing`, and the song: `apps/common/src/commonMain/kotlin/builtinsongs/Kokon.kt`.
- KLANG-00469 (step 3), all of it: the shape of the port, the switch, the listening stages. This ticket only says
  what is different in Kokon.
- `git status` and `git log -3` on the song first; port the numbers the file holds.

## Decide before implementing

**D13, found while planning (2026-11-18): there is no pan after the amp.** With the amp on the cylinder, a
voice's `pan` comes BEFORE the amp, and left and right are two amps. A line doubled hard left and right is fine
(each amp gets one copy). A SINGLE source panned off centre is not: both amps get the same signal at different
levels, an amp that saturates evens the levels out, and the line drifts to the middle. Kokon has four such lines:
`spin` (0.25 in its parts), `sing` (0.6 and 0.75), `chime` (0.75), `strum` (0.45). Der Schmetterling has none.

| Option | What it means |
|---|---|
| a. a `pan` stage on a chain | The line plays centred into the amp and the chain places it afterwards (`k.pan(...)`, the voice's constant-power law on the bus). New engine surface: a stage, both doors, the specs, as in KLANG-00467. Small, and the honest model: a real amp is mono and is panned after the microphone. A ticket of its own once decided. |
| b. accept the drift | Port as it is and listen. The clean rig saturates only in its power stage, so its lines may hold their place well enough. |
| c. these four lines keep the per-note rig | The song then holds both kinds of rig for good. |

The plan's proposal: try b first in the listening (it costs nothing), build a if the maintainer hears the drift.

Also the maintainer's: which wood a shared cylinder keeps where two lines differ (none do in Kokon today), and
whether Kokon takes Der Schmetterling's newer part values in the same step (see "The parts differ" below).

## What the song has today (2026-11-18)

Four rigs, each a region around everything, and `makeGuitar(rig)` as in Der Schmetterling:

| Rig | Stages |
|---|---|
| `cleanRig` | `pickupNeck, pedalStock, preampClean, powerClassA, snareCut, cab1x12, guitarShape` |
| `brightRig` | `pickupSingle, pedalBoost, preampCrunch, powerPushPull, snareCut, cab4x12, guitarShape` |
| `deepRig` | `pickupNeck, pedalStock, preampClean, powerClassA, snareCut, cab4x12, guitarShape` |
| `heavyRig` | `pickupHumbucker, pedalScreamer, preampHighGain, powerPushPull, snareCut, cab4x12, guitarShape` |

Ten lines:

| Line | Rig | Cylinder | Pan | Gain | After the rig, in the voice | Plays |
|---|---|---|---|---|---|---|
| `spin` | clean | 1 | 0.5 (parts: 0.25) | 0.20 (parts: 0.28) | parts: `hpf(120)`, `lpf(3300)` | an arpeggio, each note ringing under the next |
| `sing` | bright | 2 | 0.6 (parts: 0.75) | 0.15 (parts: 0.24) | `hpf(180)` (parts: 300), `lpf(4200)`, `tremolo` | a melody |
| `beat` | deep | 3 | 0.5 | 0.25 | `hpf(80)` | the root, 3-3-2 |
| `chug` | heavy | 4 | 0.50 | 0.40 | | the root, palm-muted |
| `wings` | heavy | 4 | 0.95 and 0.05 | 0.55 | | POWER CHORDS, sixteen to the step |
| `strike` | heavy | 5 | 0.10 and 0.90 | 0.48 | | one heavy chord, let ring |
| `soar` | bright | 6 | 0.5 | 0.45 | `hpf(240)` | the melody two octaves up |
| `swell` | bright | 7 | 0.0 and 1.0 | 0.05 | `lpf(6500)`; the cylinder has a reverb | chords, swelling |
| `strum` | clean | 8 | 0.45 | 0.18 | | one slow strum, let ring |
| `chime` | clean | 10 | 0.75 | 0.32 | `tremolo` | a melody, let ring |

`chug` and `wings` share cylinder 4 already.

## What is different from step 3

### 1. One amp chain per cylinder, nine of them

Every cylinder a guitar line plays on gets the chain of its rig. The voice ends at the pickup; `makeGuitar` takes
the pickup and the stages, as in step 3. `chug` and `wings` feed ONE heavy amp on cylinder 4, as they share the
cylinder today.

### 2. The filters behind the rig move into the chain

Today a line's `hpf` and `lpf` are the voice's filters, AFTER the per-note rig: mixing moves ("the 4x12 roar sits
on the arp"). With the amp on the cylinder the same calls would filter what goes INTO the amp. They move to the
end of the amp's chain, as slots, because the parts override them:

```
// The amp, then the line's own low cut and top cut, then the cylinder's usual strip.
let makeAmp = (stages, hp, lp) => Katalyst(k => k
  .oversampleAtLeast(4, r => r.serial(stages))
  .highpass(Kat.param("amp.hp", hp))
  .lowpass(Kat.param("amp.lp", lp))
  .classic())

let singAmp = makeAmp(brightStages, 180, 4200)
```

- A line's `.hpf(x)` and `.lpf(x)` go; a part that overrides them writes `.katp("amp.hp", 300)` instead.
- A line with no such filter gets a chain without those two stages (a second maker, or the stages left out):
  a filter at 20 Hz or at 20 kHz is not nothing.
- A cutoff written through `katp` is a mix knob: it lands with the next note and glides. Fine here: the parts set
  it once.

### 3. The level moves to the fader

A line's `.gain(g)` was its level in the mix. It becomes how hard the amp is hit, so:

- every guitar line gets `.gain(1.0)` as its start value (the amp sees what the per-note rig saw);
- the old `g` goes to the cylinder's fader, `.katp("gain.gain", g)`, and a part that overrides the gain overrides
  the fader the same way (`spin(...).gain(0.28)` becomes `.katp("gain.gain", 0.28)`);
- `velocity` now also drives the amp: an accent is a harder hit, not only a louder note.

### 4. Lines whose notes overlap will sound different, and should

| Line | What meets in the amp now | Expect |
|---|---|---|
| `wings`, `strike` | the notes of a power chord | the point of the whole plan: a tone an octave under the root, a denser low end |
| `swell` | three-note chords on the crunch rig | rougher than now; thirds growl |
| `spin`, `chime`, `strum` | notes ringing under the next, on the CLEAN rig | the risk. The clean preamp ends in a volume of 3.5 that drives the power stage (`asym`) into clipping; one note at a time that was a colour, a ringing chord through it will intermodulate. Start these lines at `.gain(0.5)` and raise the fader to match; if it stays gritty, the clean rig needs a lower volume (KLANG-00472, A5). |
| `chug` with `wings` | two lines in one heavy amp | fused, and louder into the fader |

### 5. The tremolo ends up before the amp

`sing` and `chime` use sprudel's `tremolo` ("the guitar finger"). It is part of the voice, after the per-note rig
today, before the amp after the port. An amp that saturates flattens a tremolo in front of it. Two ways, to be
heard: leave it (a finger on the string IS before the amp), or rebuild it behind the amp on the chain as
`.mul(Kat.sine(rate).range(1 - depth, 1))`. UNVERIFIED: that a chain modulator takes its rate from a slot; read
`Katalyst.sine` before promising it.

### 6. The parts differ from Der Schmetterling's

Read from both files on 2026-11-18 (the copies drift daily; compare again before porting):

| Part | Der Schmetterling | Kokon |
|---|---|---|
| `pickupHumbucker` | `lowpass(3000, 1.7)` | `lowpass(3300, 2.2)` |
| `pedalScreamer` | dirt: `highpass(720).distort(0.20, "soft").lowpass(4200).mul(0.4)`; level 0.50 | dirt: `highpass(600).distort(0.25, "soft").mul(0.5)`; then `lowpass(4200)` on the sum; level 0.55 |
| `pedalBoost` | `parallel(clean, highpass(300).mul(1.4)).mul(0.6)` | `highpass(300).mul(1.3)`, no clean path |
| `preampCrunch` | ends in `lowpass(5500)` | ends in `lowpass(5800)` |
| `preampHighGain` | first stage `distort(0.15, "tube")`, fizz `lowpass(4800)` | first stage `distort(0.10, "tube")`, fizz `lowpass(5200)` |
| `cab4x12` | 160 Hz +3.80, 800 Hz +4.60, 2700 Hz q 1.9 +2.00; highpass 100 | 120 Hz +3.10, 800 Hz +4.90, 2700 Hz q 1.8 +2.60; highpass 105 |
| `guitarShape` | `mul(0.18).parallel(c, shape("soft"))` | the same, then `.mul(0.35)` |

Port Kokon's OWN numbers. Bringing the two songs to one set of parts is KLANG-00463 (the shared guitar rig), not
this step, unless the maintainer says otherwise above.

One stale line to fix while there: the comment above the rigs says the pickup, the snare cut and the cab "stay
outside" the region. They do not: the region wraps the whole `serial`.

## The listening stages

As in step 3, one change at a time: first the heavy block alone (`chug`, `wings`, `strike`: the lines the plan is
for), then the bright lines, then the clean ones, which carry the risk. The switch stays until the maintainer has
heard all three.

## What the agent can check without ears

The same as in step 3: both positions of the switch compile (`:apps:common:jvmTest`), the old position is the song
as it was, and a render comparison if one can be made. Nothing about the sound.

## Acceptance

- Both positions of the switch compile, and the old one is the song as it was.
- D13 is decided, and recorded in KLANG-00466.
- The maintainer has listened to the three groups of lines and said which variant the song keeps.

## History

- 2026-11-18: created, as step 4 of KLANG-00466.
- 2026-11-19: reworked by the maintainer: a full port with Schmetterling's parts and its guitar compressor, levels roughly, no copy.
- 2026-11-19: IN PROGRESS. Klang-Main-Dev: step 4 starts, on the maintainer's word of 2026-11-19

## Built (2026-11-19), waiting for the maintainer's ear

Built by an Opus worker, coordinated and checked by Klang-Main-Dev. The ticket stays in progress: the fine tuning
and the verdict on the drift (D13) are the maintainer's.

- **Only `Kokon.kt` changed.** The parts are Der Schmetterling's of that moment, character for character (a
  reviewer diffed the code); the whole kit, also the parts Kokon's rigs do not use. Kokon's string and `snareHz`
  (400) are its own. Kokon's old part numbers that went are in the commit's diff.
- **Nine amps on nine cylinders** (1, 2, 3, 4, 5, 6, 7, 8, 10): `makeAmp(stages, cuts)` holds the stages in a 4x
  region, then `mul(0.35)` (the old extra trim of Kokon's `guitarShape`), then the line's cuts as slots (`amp.hp`,
  `amp.lp`), then `classic()`. `chug` and `wings` share the heavy amp on cylinder 4. Spin plays open in the parts
  that set no cut (20 Hz, 20 kHz), beat's top likewise (20 kHz).
- **Der Schmetterling's guitar compressor** (threshold -14.9, ratio 4, knee 6, attack 0.001, release `beats(0.1)`)
  on all nine, in one bus function per cylinder. On `swell`'s cylinder the room goes through it (the strip's order).
- **Every override in the parts is ported** (a reviewer checked each): gains to the fader, cuts to `katp`.
- **Levels, one rough pass:** each guitar cylinder rendered alone, old against new, its fader trimmed to the line's
  old overall RMS (`spinTrim` 2.11, `singTrim` 1.14, `beatTrim` 0.86, `heavyTrim` 2.19, `strikeTrim` 2.26,
  `soarTrim` 1.32, `swellTrim` 2.32, `strumTrim` 2.12, `chimeTrim` 1.60), marked as rough starting values. Uneven
  within a line: spin about +5 dB at the start of the cocoon, strike about +4 dB in its ring-out. After the measured
  pass, `chug` got `.gain(0.73)` into the shared amp (its old ratio to `wings`, 0.40 / 0.55); the pair's trim was
  measured with both at 1.0 and not measured again.
- **To listen for:** a drift to the middle on `spin`, `sing`, `chime`, `strum` (pan before the amp, D13 option b);
  grit on the clean lines when notes ring into each other (they start at `.gain(0.5)`); the tremolo before the amp.
- **Checks:** `:apps:common:jvmTest` 28, `:apps:studio:jvmTest` 172, 0 failed; the studio compiles for the browser;
  one chain name per guitar cylinder and equal bus settings on cylinder 4 (1606 onsets); the renders hold no bad
  sample and no silent stretch. Review: one round (`reviewer-opus-high`), CLEAN; 1 MINOR and 2 NIT applied.
- **Not done:** no one has listened. What `beats(0.1)` gives at Kokon's tempo in live play was not checked. No
  browser tests, no benchmark.
- 2026-11-19: the maintainer listened: "Kokon is ported well. I need to tweak as the distortion on the shared bus now sounds different and fuzzy in places where it should not. No problem though as this was expected and i will take care of it." The step is closed on their word; the tweaking is theirs. D13 (the drift of the off-centre lines): not raised; no pan stage is built.
- 2026-11-19: DONE. The maintainer, 2026-11-19: 'Kokon is ported well ... please close this step'
