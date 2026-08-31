# Cut / choke groups: what `cut(0)` means, and what else was never decided

Status: **future / needs a design round.** Not a bug fix — the feature has never been used in a
shipped song, so nothing is broken for anyone today, and the question is what it *should* do rather
than what it does. Raised by audit finding
[F19](../../audio-audit/FINDINGS.md#f19) on 2026-08-31 and deliberately not settled on the fly.

## What cut/choke is for

A cut group makes a set of sounds mutually exclusive, because they are one physical object. The
canonical case is the hi-hat: a kit has one, so closing it stops the open sound dead, but a sampler
represents it as two samples that would otherwise ring over each other.

```klangscript
stack(s("hh*4").cut(1), s("~ oh ~").cut(1))   // the open hat is choked by the next closed hat
```

Secondary use: voice economy. A long sample retriggered fast piles up overlapping copies that smear
and cost a voice each; a cut group keeps exactly one alive.

## The mismatch that started this

`sprudel/lang_sample.kt:690` documents:

> *"Group `0` means no choke."* — with the example `s("bd sd").cut("<0 1>")` annotated
> *"alternate between no-cut and cut-group-1"*.

The engine gates on `if (cut != null)` (`VoiceScheduler.kt:553`) and nothing maps `0` to `null`, so
group `0` is an ordinary group whose members choke each other. In that documented example the `bd`
would have its tail chopped by the following `sd`, which is exactly what the comment promises will
not happen.

## Why this is a design round and not a one-line fix

The obvious repair — gate on `cut != null && cut != 0` — is one line, but it answers only the
smallest of the questions the feature leaves open, and the others are easier to settle **now**,
before anything ships that depends on the answers.

### 1. What does "off" mean, and does a pattern need to say it?

Not calling `.cut()` is already "off". `0` only earns its keep if a *pattern* has to switch choking
on and off per event — `"<0 1>"` — which is precisely the case the KDoc example reaches for and the
only one plain absence cannot express. So the real question is whether per-event switchable choking
is a thing we want. If yes, some value has to mean off, and `0` is the natural one. If no, the doc
should simply stop claiming it.

### 2. How far does a cut group reach?

`activateVoice` sweeps `active` with no filter on sound, orbit or playbackId. So a cut group is
**global to the scheduler**: two unrelated parts that both happen to pick group `1` will choke each
other across the whole song. Nobody decided that; it is what falls out of the implementation.
Alternatives: scope per orbit, or per source id. This is the question most likely to bite a real
song, because group numbers are small integers that two people will collide on.

### 3. The hard kill is a click

`VoiceScheduler.kt:558` still carries its original TODO — *"Use a fade out / release phase instead of
hard cut?"*. The victim is removed from the active list mid-waveform, so a ringing sample is chopped
at an arbitrary sample value, which is a step discontinuity. It has never been heard because nothing
uses the feature. If cut becomes usable, this becomes audible, and a real hi-hat choke is fast but
not instantaneous anyway — a very short release would be both cleaner and more faithful.

### 4. Synth voices only just joined

Until [F18](../../audio-audit/FINDINGS.md#f18) was fixed on 2026-08-31, `Voice.cut` was never
populated on the synth branch, so oscillator voices could choke sample voices while being immune
themselves. They now participate symmetrically. Whether a *monophonic synth line* should be expressed
as a cut group, or whether that wants its own mechanism, is worth a thought while the surface is
still unused.

### 5. Does the trigger have to be a member?

Today the arriving voice sweeps its own group and is then added, so it cannot cut itself. That is
right. But nothing says a voice must *belong* to the group it chokes — a one-way "stop group 1"
trigger is expressible and might be useful (a hand damping a triangle it did not strike).

## Decide, then do

The one-line repair is worth applying only once question 1 is answered; questions 2 and 3 change
more code and are the ones with real consequences for songs. Nothing here is urgent — the whole
feature is unused, which is exactly the moment to settle it cheaply.

Guard when it lands: `VoiceSchedulerSoloCutSpec` already covers the four current cut behaviours
(a group hard-kills its members, a voice does not cut itself, different groups coexist, an
ungrouped voice kills nothing) and is where new rows belong.
