# Recording your own drum kit for klang

Notes for sampling a real acoustic kit and packaging it as a klang sample bank. Written for the
"we need our own metal and rock kits" project (2026-08, klang-ai workspace).

## Why bother

Two reasons, one legal and one musical.

**Legal.** Every kit currently mirrored under `peekandpoke.github.io/klang/` is somebody else's
work, and two of the three drum banks have *no declared upstream license* (see `ATTRIBUTION.md`).
That is fine for a mirror that credits generously, but it is a permanent asterisk on the project —
you cannot promise a user that what they build in klang is theirs to release. A kit you recorded
yourself removes that asterisk forever.

**Musical.** The current default kit (`uzu-drumkit`) is 40 files total, and the cymbals are the
thin part:

| key | meaning | samples in bank |
|---|---|---|
| bd | kick | 8 |
| sd | snare | 5 |
| hh / oh | hats | 5 / 4 |
| cr | crash | **2** |
| rd | ride | **1** |
| lt / mt / ht | toms | **1 each** |

A pattern like Der Schmetterling's `[~ rd ~ rd]!32` triggers the ride *hundreds* of times per
render, always from the same single file. Two consequences: the machine-gun effect (identical
transient every hit, which the ear reads as a loop, not a drummer), and a **fixed spectrum** — the
mix's entire top end above 10 kHz is one crash sample's frequency response, which is where the
measured ~19 dB/oct cliff above 10 kHz comes from. No amount of EQ fixes a cliff that is baked into
the source material; more samples do.

The bank is also inconsistent: 36 files at 44.1 kHz and 2 at 48 kHz, 15 mono and 23 stereo, 16-bit
and 24-bit mixed, two files in IEEE-float format. klang renders at 48 kHz, so most of the kit is
being resampled on the way in.

## One session, two kits

Do not book two sessions. "Metal kit" and "rock kit" are mostly the *same drums* differing in two
things you control at capture time:

1. **Mic blend.** Record close mics, overheads and room mics to separate tracks. Metal is close
   mics with the room mostly gone; rock is the same close mics with a generous room blend. One
   performance, two mixdowns.
2. **Tuning and damping.** This is the half that genuinely needs a re-tune mid-session:

| | metal | rock |
|---|---|---|
| kick | tight heads, pillow against the batter, hard beater → short "click + thud" | looser, less damping, more boom and decay |
| snare | tuned high and tight, wires snug, moongel → crack | tuned lower, more ring left in |
| toms | tight, short decay | open, let them sustain |
| room | minimal | the point |

Budget the session as: tune for metal → sample everything → re-tune → sample everything again.
The second pass is faster because you already know the routine.

## What to record

klang's bank keys follow the Tidal/Strudel convention, so match them exactly:

`bd` kick · `sd` snare · `rim` rimshot and cross-stick · `hh` closed hat · `oh` open hat ·
`ht` `mt` `lt` high/mid/low tom · `cr` crash · `rd` ride · plus optional `cb` cowbell,
`sh` shaker, `tb` tambourine, `cp` clap, `misc` for anything else.

Worth capturing beyond the obvious, because they cost minutes and add enormous life: hi-hat at
several openings (closed, tight, half, loose — half-open hats are the sound of rock and are
missing from most free kits), ride bell separately from ride bow, crash chokes, snare ghost notes,
stick clicks (a count-in sound — Der Schmetterling already fakes one), and kick doubles at speed.

### Velocity layers versus round robins

You need **both**, and they solve different problems.

- **Velocity layers** capture that a hard hit is not just louder but *brighter* — more crack, more
  attack, a different spectrum. Level automation cannot fake this. Record a graded series from
  ghost note to full force.
- **Round robins** are several takes at the *same* dynamic, cycled so consecutive hits differ.
  They kill the machine-gun effect. For fast metal kick patterns this matters more than anything
  else in this document.

A good target for the money drums (kick, snare) is **4 velocity layers × 3 round robins = 12
files**. Hats and ride want round robins most (they play the most notes); toms and crashes can
live with fewer. Even 3 round robins per articulation would be a categorical upgrade on what the
default kit has now.

Practical target for a first session, per kit: roughly 120–200 usable files. That is a couple of
hours of actual hitting, plus setup.

## Mic setup

Minimum viable, in priority order — each line adds real value over the one above:

1. **Stereo overheads.** These are the kit; everything else is seasoning. A spaced pair or XY
   above the kit defines the stereo image and captures the cymbals, which is exactly where the
   current bank is weakest.
2. **Kick, inside.** Through the porthole, near the beater for click. Add an outside mic for body
   if you have the channel.
3. **Snare top.** Angled across the head, pointing away from the hat. A bottom mic for wire
   detail if you have the channel.
4. **Room pair**, several meters back. This is the entire difference between your rock kit and
   your metal kit, so do not skip it even though it feels optional.
5. **Individual toms**, if channels remain.

Six channels (kick, snare, two overheads, two room) gets you both kits. Eight is comfortable.

**Record every mic to its own track and keep the raw multitrack.** You will want to re-balance in
a year, and re-recording is expensive while re-mixing is free.

## The room is the instrument

The measured "mud region" that rock mixes fight — 120–250 Hz, the boxy garage-demo signature — is
mostly the recording room, not the drums. An untreated room with parallel walls boosts exactly
that band and adds flutter echo. Cheap, effective moves: duvets or blankets on the two parallel
walls, kit off-center in the room (never dead center, never in a corner), and if the room is small
and bad, lean into close mics and skip the room pair rather than capturing a bad room you can
never remove.

A big, ugly, *reverberant* room is far more useful than a small dead one — you can always take
room away in the mix, but you cannot add real room to a sample that never had it.

## Session discipline

The difference between a usable library and a wasted weekend:

- **Tune the kit first.** This is the single largest quality factor and it costs nothing. Fresh
  heads if the budget allows.
- **Hit consistently, in the same spot.** Sampling is not performing. Center of the head for
  toms and snare (except when deliberately sampling edge hits as a separate articulation).
- **Leave silence between hits** and let every drum ring out completely. You can always shorten a
  sample; you can never extend one.
- **Disengage the snare wires** when sampling kick and toms, or every kick sample carries snare
  buzz — and buzz that is fine on one hit becomes a constant rattle once dozens of samples layer
  in a mix.
- **Watch levels, leave headroom.** Peaks around −6 dBFS. A clipped transient is unfixable and
  the transient is the entire point of a drum sample.
- **Slate what you are doing** — say "snare, layer three, take one" into the room mic. Editing 200
  anonymous hits is miserable without it.
- Record a normal groove at the start and listen back before committing to two hours of hits. It
  is the only way to catch a phase problem or a rattling lug while it is still cheap to fix.

## Editing rules

- **Capture format: 48 kHz, 24-bit.** klang renders at 48 kHz, so this avoids resampling entirely.
- **Trim to just before the transient** — leave a couple of milliseconds of pre-roll rather than
  slicing into the attack, and make sure the file starts at a zero crossing to avoid a click.
- **Do not normalize each file.** Normalizing per-file destroys the velocity relationship you just
  spent the session recording. Normalize the *set* by its loudest member, applying one common gain
  to all of them.
- **Fade the tails** gently to zero so loops and truncation do not click. Leave tails long —
  klang's `adsr()` can always shorten a sample, but it cannot invent decay.
- **Deliver stereo**, mixed down from the multitrack: one mixdown emphasizing close mics (metal),
  one with the room pair up (rock).
- Keep the naming convention consistent with the existing banks:
  `bd/00_bd_<kitname>.wav`, `bd/01_bd_<kitname>.wav`, …

## Two engine constraints that change the capture spec

Both come from the loader (`audio_fe/.../samples/SampleIndexLoader.kt`, `JvmWavDecoder.kt`,
`BrowserAudioDecoder.kt`, `audio_be/.../voices/VoiceFactory.kt`) and neither is documented
anywhere else — check they still hold before relying on them.

**1. klang samples are effectively mono, and the two platforms disagree about how.**
The JVM decoder downmixes any channel count to mono by *averaging* the channels
(`JvmWavDecoder.kt` — `mono[i] = sum / channels`). The browser decoder takes **channel 0 only**
(`BrowserAudioDecoder.kt` — `getChannelData(0)`). So a stereo sample plays as L+R averaged in an
offline render and as the bare left channel in the browser — two different sounds from one file.
Worse, averaging a wide stereo overhead pair comb-filters it: anything out of phase between L and R
partially cancels, and that is precisely what a spaced pair produces.

**Therefore: deliver mono files.** Do the stereo mixdown decision at capture time, sum to mono
deliberately while listening for phase cancellation, and let klang's own `pan()` place each drum in
the stereo field. This is not a limitation in practice — pro drum programming pans mono one-shots
anyway — but it does mean the overheads' natural stereo image has to be recreated in the pattern
rather than carried in the files. (The existing `uzu-drumkit` bank is 23 stereo and 15 mono files,
so it is already hitting this inconsistency.)

**2. Record at 48 kHz.** Playback rate is computed as
`rate = (sample.sampleRate / renderSampleRate) * pitchRatio * loopSpeed` and the playhead is
**linearly interpolated** (`SampleIgnitor.kt` — `a + (b - a) * frac`). Linear interpolation is a
poor reconstruction filter: it dulls the top end and adds distortion. At 48 kHz native the rate is
exactly 1.0 and the interpolator is bypassed in effect — every sample plays back bit-accurate.
The current kit is 44.1 kHz, so it is being interpolated on every single hit. (Separately, the
browser resamples to 48 kHz at decode time while the JVM keeps the native rate — another reason
to remove the variable entirely.)

Formats accepted: WAV PCM or IEEE float, 8/16/24/32-bit, any sample rate; MP3 also works
(the decoder sniffs magic bytes, the extension is ignored).

## Packaging as a klang bank

Adding a bank is one line of Kotlin plus files on the mirror.

**1. Lay out the files and manifest** under
`peekandpoke.github.io/klang/<kit-dir>/`, matching the existing convention:

```
motor-metal/
  bd/00_bd_motormetal.wav
  bd/01_bd_motormetal.wav
  sd/00_sd_motormetal.wav
  index.json
```

`index.json` is a flat JSON object, key → array of paths relative to that directory, and
**must not** contain a `_base` key (paths resolve against the manifest's own directory):

```json
{
  "MotorMetal_bd": ["bd/00_bd_motormetal.wav", "bd/01_bd_motormetal.wav"],
  "MotorMetal_sd": ["sd/00_sd_motormetal.wav"]
}
```

**Key naming is load-bearing.** The loader splits on the *last* underscore:
`splitBankAndSound` takes everything after the final `_` as the sound and everything before it as
the bank. So `MotorMetal_bd` → bank `MotorMetal`, sound `bd`, addressable as
`s("bd").bank("MotorMetal")`. A bare key like `bd` lands in the **default** bank (`""`), merged
with uzu-drumkit and friends — and duplicate keys are resolved `distinctBy { it.key }`, first
source wins, ordered by `mirrorSets`. So a bare `bd` would either be shadowed by uzu's or shadow
it, depending on list order. Use the prefix.

The trap that follows: **never put an underscore inside a sound name.** A key `my_kick` silently
becomes bank `my`, sound `kick`.

**2. Register it** — one line in `audio_fe/src/commonMain/kotlin/samples/SampleCatalogue.kt`,
in the `mirrorSets` list:

```kotlin
MirrorSet(name = "Motor Metal", dir = "motor-metal"),
```

That is the only code change the runtime needs. Add `hasAlias = true` only if you also ship an
`alias.json` in `{"CanonicalBank": "ShortAlias"}` form (it is inverted on load).

**3. Verify and deploy.** `./gradlew runSampleMirror --args="--verify"` should report 0 missing
for the new dir — a hand-built kit has no upstream origin manifest, so `verify` is the relevant
mode; a full mirror run will report `no origin manifest configured` and that is expected. Then
`./console/deploy-samples-finzo.sh` rsyncs the tree. The root `.htaccess` already sets
`Access-Control-Allow-Origin: *`.

**4. Bust the local cache when iterating.** The JVM loader wraps everything in a permanent,
non-expiring disk cache (`cache/index`, `cache/samples`), so a changed `index.json` will not be
picked up until you delete the matching cache entries. For fast local iteration set
`KLANG_SAMPLE_BASE=http://localhost:8000` (JVM) or `window.__KLANG_SAMPLE_BASE__` (browser).

**5. Housekeeping**: add a provenance section to the mirror's `ATTRIBUTION.md` — for an
own-recorded kit this is the happy case: state the license *you* are granting users — and a credit
bullet in `src/jsMain/kotlin/pages/CreditsPage.kt`. The Samples Library UI is fully index-driven
and needs no change.

## Using variants once you have them

`n()` selects the variant and is **modulo**, so it can never fail:
`getSampleByIndex(idx) = samples[idx % samples.size]`. That means `.n(5)` on a five-sample bank
silently plays sample 0 — a pinned variant that looks like variation.

```
s("bd*16").n("<[0 3 5 1 6 2 7 4] [2 5 0 7 3 6 1 4]>".fast(2))   // rotate all 8 kicks
s("hh:2")                                                        // inline variant syntax
```

One caveat worth knowing: variant selection by index only applies when **no note is set**. If a
note or frequency is present the loader switches to pitch matching
(`getSampleByNote` → first sample whose `pitchHz >= requested`), and since all array-loaded
samples share `defaultPitchHz = 261.63`, `n()` is effectively ignored. Keep drum patterns
note-free.

