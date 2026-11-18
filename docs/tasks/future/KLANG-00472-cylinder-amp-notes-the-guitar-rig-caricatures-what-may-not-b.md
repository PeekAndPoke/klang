# KLANG-00472: Cylinder amp notes: the guitar rig caricatures, what may not be ideal

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00466 (Metal band instruments: the guitar amp on the cylinder, one amp per guitarist)

## Why this exists

The maintainer (2026-11-18) asked for "all the notes about what might not be ideal in the current caricatures,
for example the filter frequencies". This is that list, for Der Schmetterling's guitar rig as it stood on
2026-11-18 (Kokon's copy differs in places: KLANG-00470 (step 4), section 6).

It is a list to work through BY EAR, one note at a time, with the rig's own rule: a change you cannot name in an
A/B is not kept. Nothing here was listened to. Do not apply a note because it is written here.

**How to read a note.** Each carries where it comes from:

- **(code)**: read from the song or the engine, or arithmetic on what was read. These are facts.
- **(gear)**: general knowledge of the real thing. Not measured against a recording or an impulse response here,
  and a caricature may leave the real thing on purpose.
- **(guess)**: an inference about what it does to the sound. The least reliable kind.

## The engine facts the notes rest on (code)

- `distort(amount, shape)` is `shape(x * 10^(amount * 1.2))`, then a DC blocker. No make-up gain, no threshold.
  0.09 is a gain of 1.28, 0.15 is 1.51, 0.20 is 1.74, 0.25 is 2.00, 0.35 is 2.63, 0.70 is 6.92.
- Every curve bends around an input of 1.0. `soft` is a tanh, bounded at +-1. `tube` is a shifted tanh: it
  saturates at +0.37 on one side and -1 on the other. `asym` is a cubic on the positive side and a SQUARE ROOT on
  the negative: an input of -0.01 comes out at -0.1, its slope at zero is without bound.
- So the level INTO a stage is its drive, and every `mul` in an earlier part changes every later part.
- A resonant lowpass with a q above 1 lifts its corner by roughly q: q 1.4 is about +3.5 dB, 1.7 about +5 dB,
  3.3 about +10.5 dB.
- The song is transposed by -3 semitones: the rhythm guitars' lowest note is C#2, 69 Hz.

## The structure

| | Note | Basis | To try |
|---|---|---|---|
| S1 | One amp per note: notes never meet in a clipper, and every note starts a cold amp. | code | KLANG-00466 is this note's plan. |
| S2 | Level and tone are one knob until the last clipper. No part gives out a known level, so every new combination has to be rebalanced. | code | KLANG-00460 (the level contract). |
| S3 | The whole rig sits in the 4x region, the linear cab and the snare cut included: eight filter sections at four times the rate, per voice. | code | On the cylinder the cab can run behind the region at the base rate. |
| S4 | The header names a tone stack ("preamp -> tone stack -> power amp"). There is no such stage. The cab's broad band at 800 Hz and the snare cut do its work. | code | A tone stack stage between preamp and power amp, as the one home of the rig's mid decisions (scooped, flat, pushed); the cab then only has to be a cab. |
| S5 | `snareCut` is a mixing decision inside the amp, and the one thing that ties the rig to a song. | code | It stays the song's own stage, appended by the song (KLANG-00469 does this). |
| S6 | `guitarShape`, "the microphone", is a level trim (0.18) and a soft curve in parallel with the clean signal. By the numbers the curve can only round the highest peaks slightly. A real microphone adds no curve: it adds a frequency response (a presence lift, a low roll-off) and a position (on the cone's centre bright, off it dark). | code, estimate, gear | A/B without the curve. If nothing is lost, the stage is the rig's output level and can be called that; a microphone preset would be a filter. |
| S7 | Stale comments: the cabs "move to the cylinder unchanged once the Katalyst DSL lands" (it has landed); Kokon's "the pickup, the snare cut and the cab are linear and stay outside" the region (they are inside). | code | Fix with the rewrite. |

## The string

| | Note | Basis | To try |
|---|---|---|---|
| G1 | A saw is the right start: a pickup hears the string's speed, and a plucked string's speed has a saw's spectrum. | gear | Keep. |
| G2 | 5 to 11 detuned saws in front of the amp are a chorus INTO the clipper: they intermodulate inside it. On a record the thickness of metal guitars comes from double tracking, behind the amps, which guitars 2 and 3 already do. | code, gear | On the cylinder amp, fewer unison voices (1 to 3). |
| G3 | The pick's noise is pink noise above 2 kHz at 0.05, with a sustain of 0.05: a small hiss runs through the whole note into the clippers. Through `asym` it is lifted at every zero crossing. | code | A/B a shorter burst with no sustain. |
| G4 | The top of the string never dies. A real string loses its highs first. Behind high gain the clipper hides that; on the clean rigs (Kokon's `spin`, `chime`, `strum`) a note is as bright at its end as at the pick. The lowpass line that is commented out in `makeGuitar` was the attempt. | code, gear | A lowpass with an envelope on the clean lines only. |
| G5 | No tell for where the pick hits the string. Picking near the bridge (the metal habit) thins the low harmonics and brightens the note. | gear | Probably not worth a knob; note only. |
| G6 | No tell for a palm mute: `chug` is the open string with a shorter decay. A muted string is darker, shorter and thumps. | gear | A lowpass and a faster decay driven by one pattern value. |
| G7 | The pitch envelope is 0.2 semitones, up in 1 ms, down in 50 ms: the natural shape. Its depth is the same for every guitar and every hit. | code | Deeper on the low rhythm notes, shallower on the melody; tied to `guitarDyna`. |

## The pickups

| | Now | Note | Basis | To try |
|---|---|---|---|---|
| P1 | `pickupSingle`: `lowpass(4700, 3.3)`, 0.8 | The peak is about +10.5 dB at 4.7 kHz. A loaded single coil peaks by 4 to 8 dB. It is the strongest lift in the band where the ear is most sensitive, and it is on the melody rig, the one whose attack is the complaint. | code, gear, guess | q 2.0 to 2.5. |
| P2 | `pickupHumbucker`: `lowpass(3000, 1.7)`, 1.3 | About +5 dB at 3 kHz: in the range of the real thing. | code, gear | Keep. |
| P3 | `pickupNeck`: `lowpass(3500, 1.4)`, a notch at four times the note, 1.2 | Its comment says "darker still", but its corner is HIGHER than the bridge humbucker's. A neck pickup is darker because of where it sits, not because of a lower resonance. And its notch follows the note, where a real one stays at a fixed frequency per string; the song's own note of 2026-09-14 says a filter that moves with every note sounds synthetic. | code, gear | A corner at or under the humbucker's; the notch fixed, or dropped. Kokon's clean and deep rigs use this pickup. |
| P4 | all bridge pickups | A pickup near the bridge hears less of the fundamental. Here the two bridge pickups differ only in resonance and output. | gear | A gentle low cut on the bridge presets. |
| P5 | outputs 0.8, 1.3, 1.2 | Differ on purpose ("its output sets how hard the amp is pushed"). It is the one level in the rig that is meant as tone. | code | Keep, and say so in the level contract. |

## The pedals

| | Now | Note | Basis | To try |
|---|---|---|---|---|
| D1 | `pedalScreamer`: dirt `highpass(720).distort(0.20, "soft").lowpass(4200).mul(0.4)` beside the clean signal, level 0.50 | The 720 Hz corner and the clean signal going around the clipper match the real circuit. But the dirt is capped at 0.4 and the level is 0.50, so the pedal hands the preamp 6 dB LESS bass and 1.5 to 4 dB less mids than no pedal. A real one in front of a high-gain amp is set to hit it harder; that is half of why it is there. Its level was matched to the stock rig by loudness, and behind a saturated amp loudness says little about drive. | code, gear | Level 0.8 to 1.2, with the preamp's volume turned down to match. |
| D2 | the same | The tone lowpass at 4200 Hz is far brighter than the real pedal, whose top is rolled off early. | gear | 2500 to 3000 Hz. |
| D3 | `pedalFuzz`: `distort(0.70, "asym")`, `lowpass(4500)`, 0.28 | Lopsided and hard: right for a fuzz. A fuzz also loads the pickup and takes its peak away; that tell is missing. | code, gear | Note only; no rig uses it. |
| D4 | `pedalBoost`: the clean signal beside `highpass(300).mul(1.4)`, level 0.6 | A step of 7.6 dB at 300 Hz: -4.4 dB under it, +3.2 dB over it. The classic treble booster's corner sits in the kHz range; this one is a bass cut that tightens, which may be what the melody rig wants. | code, gear | Name it for what it does, or move the corner up. |

## The preamps

| | Now | Note | Basis | To try |
|---|---|---|---|---|
| A1 | `preampCrunch`: `highpass(120)`, `tube` 0.35, `highpass(120)`, `asym` 0.18, `highpass(100)`, `lowpass(5500)`, 0.62 | Its comment says "coupled loosely so the bass goes along". Its highpasses are 120, 120, 100: the same as the high gain's, which is called tight. | code | 50 to 70 Hz, if loose is what is wanted. |
| A2 | the same | Its drive is 2.63 times 1.64, +12.7 dB; the high gain's is +14.1 dB. By the numbers the crunch is nearly as hot as the high gain, and its FIRST stage is the hotter one. | code | Less on the first stage. |
| A3 | `asym` in the crunch (stage 2), in the high gain (stage 2) and in `powerClassA` | The square root on its negative side gives small signals a large gain: a quiet tail, the pick's hiss, the note's decay all come out loud and buzzing, and the stage never cleans up as the playing gets softer. That is a fuzz. A crunch amp is known for cleaning up. | code, gear | `tube` in its place, level-matched. The first thing to try for the melody guitar's attack. |
| A4 | `preampHighGain`: `highpass(120)`, `tube` 0.15, `highpass(120)`, `asym` 0.09, `highpass(100)`, `soft` 0.35, `lowpass(4800)`, 0.67 | "The last one hard" is a `soft` (tanh). The only lowpass is at the end; real high-gain preamps also roll off the top BETWEEN the stages, so the later ones do not clip the fizz of the earlier ones. | code, gear | A lowpass around 6 to 8 kHz between the stages. |
| A5 | `preampClean`: `tube` 0.12, `highpass(80)`, a band at 3.5 kHz, volume 3.5 | The volume of 3.5 drives the power stage far into clipping: its input reaches about 2.4, and both power stages double that before their curve. The rig is clean only in its preamp. One note at a time that is a colour; on a cylinder amp a ringing chord through it will intermodulate. | code | A lower volume, the level made up behind the power amp. |
| A6 | `tube` anywhere | It is very lopsided (+0.37 against -1), so the signal's centre shifts with the level, and the highpass behind it turns each change of level into a low thump. A real amp does this too. On a cylinder the DC blocker is slower (7.64 Hz against 35), so the highpasses after these stages matter more. | code, gear | Listen for it in stage A of KLANG-00469. |

## The power amps

| | Now | Note | Basis | To try |
|---|---|---|---|---|
| W1 | `powerStock`: `drive(0.3)` | A gain of 2.29 and no curve: the stock rig has no power stage that bends. | code | Note only. |
| W2 | `powerPushPull`: `soft` 0.25, a band at 4 kHz +2 dB, master 1.6 | The symmetric curve and the presence lift behind it are the right tells. Missing: the low lift BEHIND the preamp (the "depth" or "resonance" of a metal amp), which is how a real rig gets full lows without clipping them: cut the bass before the clippers, add it back after. The cab's thump band does this alone today. | code, gear | A band around 90 to 120 Hz, +3 to +5 dB, in the power amp. The first thing to try for the full low end. |
| W3 | `powerClassA`: `asym` 0.25, master 1.3 | See A3. "Chime" is even harmonics from a smooth lopsided curve; `asym` has a kink at zero. | code, gear | `tube` at a low amount. Kokon's clean and deep rigs use it. |

## The cabs

| | Now | Note | Basis | To try |
|---|---|---|---|---|
| C1 | `cab4x12`: 160 Hz q 0.6 +3.80, 800 Hz q 0.5 +4.60, 2700 Hz q 1.9 +2.00, a wall at 5 kHz (three passes), `highpass(100)` (two passes) | The thump has moved (120, 180, 160 Hz within days), which says it is doing a job it is not suited to: with the lowest note at 69 Hz and a highpass at 100 Hz, the guitars' weight is their second and third harmonics, 139 and 208 Hz. | code | Decide the highpass and the thump together, against the bass. |
| C2 | the same | The band at 800 Hz is three octaves wide and the biggest of the three. A closed 4x12 is not known for that; it is a tone control (see S4). The 2.7 kHz peak and the wall at 5 kHz are the cab. | code, gear | Move it to a tone stack stage and hear what the cab is without it. |
| C3 | all cabs | A real cab's response is ragged: many narrow peaks and dips above 1 kHz. A few smooth bands sound cleaner than a speaker. The cylinder's `body` adds some of that. | gear | Note only; a family of models, so by the house rule on taste probably not a knob. |
| C4 | `cab1x12`, `cabCombo` | Plausible caricatures; only Kokon uses the 1x12. | gear | Keep. |

## What the rig leaves out

Not faults; things a real rig has and this one does not, so nobody looks for them in a knob:

- **Sag**: the power supply giving way under a loud hit. Metal amps are built tight, so the lack suits the rhythm
  rig; a class A amp's bloom is partly this.
- **The tone stack and the depth lift** (S4, W2).
- **Where the pick and the pickup sit on the string** (G5, P4).
- **The speaker's texture** (C3).

## An order to try things in

1. A3: `tube` for `asym` in the crunch preamp, for the melody guitar's attack.
2. W2: a low lift in the power amp, for the full low end.
3. D1: the screamer as a boost.
4. P1: the single coil's q.

Each on its own, level-matched, in the song as it is. They do not need the cylinder amp.

## Acceptance

Every note has an outcome written beside it here: kept as it is, changed (to what), or not tried and why.

## History

- 2026-11-18: created, from the reading of the rig for KLANG-00459 and KLANG-00466.
