# Klang Pattern Language Reference (Sprudel)

> Paste this into any LLM to write KlangScript music patterns.
> For KlangScript syntax basics, see `klangscript-basics.md`.
> For instrument design, see `ignitor-reference.md`.

Every file starts with:

```javascript
import * from "stdlib"
import * from "sprudel"
```

---

## Quick Start

### A simple beat

```javascript
import * from "stdlib"
import * from "sprudel"

stack(
  sound("bd sd bd sd"),
  sound("hh hh oh hh").gain(0.5),
  sound("~ ~ cp ~").gain(0.7)
)
```

### A melody with chords

```javascript
import * from "stdlib"
import * from "sprudel"

stack(
  n("0 2 4 7 6 4 2 0").scale("C4:minor")
    .sound("saw").lpf(800)
    .adsr(0.01, 0.1, 0.5, 0.2).gain(0.25),
  chord("<Am C F G>").voicing()
    .sound("supersaw").lpf(500)
    .adsr(0.1, 0.3, 0.7, 0.5).gain(0.2),
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("sine").lpf(300).gain(0.4)
)
```

### A full track with custom instruments

```javascript
import * from "stdlib"
import * from "sprudel"

let koto = Ignitor.pluck()
  .plus(Ignitor.sine().detune(12).mul(0.1).adsr(0.001, 0.3, 0.0, 0.05))
  .lowpass(Ignitor.constant(5000).plus(Ignitor.constant(3000).adsr(0.001, 0.3, 0.0, 0.05)))
  .highpass(200)
  .classic()

let kick = Ignitor.sine()
  .pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))
  .adsr(0.001, 0.2, 0.0, 0.02)
  .classic()

stack(
  note("a4 b4 c5 b4 a4 [b4 a4] f4@2").sound(koto)
    .legato(0.8).slow(4),
  note("a1 ~ ~ ~").sound(kick).adsrOff().gain(0.8),
  sound("~ ~ cp ~").gain(0.4),
  sound("hh*8").gain(0.3)
).reverb(wet = 0.2, size = 5)
```

---

## Mini-Notation Syntax

Patterns are written as strings using mini-notation:

| Syntax     | Name           | Description                                 | Example                                |
|------------|----------------|---------------------------------------------|----------------------------------------|
| `a b c`    | Sequence       | Space-separated values divide cycle equally | `"bd sd hh cp"`                        |
| `~`        | Rest           | Silent step                                 | `"bd ~ sd ~"`                          |
| `[a b]`    | Group          | Subdivide a slot into equal parts           | `"[bd bd] sd"` (2 kicks in first half) |
| `<a b c>`  | Alternation    | Cycle through options, one per cycle        | `"<bd sd cp>"`                         |
| `,`        | Stack          | Play simultaneously (inside brackets)       | `"[bd, sd]"` (kick+snare together)     |
| `a*N`      | Fast           | Repeat N times in slot                      | `"hh*4"` (4 hi-hats)                   |
| `a/N`      | Slow           | Stretch over N cycles                       | `"bd/2"` (plays every other cycle)     |
| `a@N`      | Weight         | Take N relative time units                  | `"c4@2 e4"` (c4 lasts twice as long)   |
| `a!N`      | Repeat         | Clone in-place N times                      | `"bd!4"` = `"bd bd bd bd"`             |
| `a?`       | Degrade        | 50% chance of playing                       | `"hh?"`                                |
| `a?N`      | Degrade by     | N% chance of playing                        | `"hh?0.3"` (30% chance)                |
| `a\|b\|c`  | Choice         | Random pick each cycle                      | `"bd\|sd\|cp"`                         |
| `a(p,s)`   | Euclidean      | p pulses over s steps                       | `"bd(3,8)"`                            |
| `a(p,s,r)` | Euclidean+rot  | With rotation                               | `"bd(3,8,2)"`                          |
| `a:N`      | Sample variant | Select variant N                            | `"sd:3"`                               |
| `//`       | Comment        | Line comment                                | `"bd sd // comment"`                   |

Nesting is unlimited: `"[[bd bd] sd] [hh [oh hh]]"`

### Cycle Timing — How Space-Separated Values Work

**Critical rule:** All space-separated values in a sequence share ONE cycle equally. More values = each gets less time.

```javascript
// 4 values → each gets 1/4 of the cycle (natural quarter notes)
note("c3 d3 e3 f3")

// 16 values → each gets 1/16 of the cycle (very fast!)
note("0 0 0 0 1 1 1 1 0 0 0 0 1 1 1 1")  // BAD: all crammed into one cycle
```

**Use groups `[...]` to keep 4 events per cycle while writing longer sequences:**

```javascript
// Each [...] group occupies one cycle position
// With .slow(4), 4 groups = 4 cycles of 4 notes each
note("[0 0 0 0] [1 1 1 1] [0 0 0 0] [1 1 1 1]").slow(4)

// Or use cat() to sequence across cycles explicitly
cat(
  note("0 0 0 0"),  // cycle 1
  note("1 1 1 1"),  // cycle 2
  note("0 0 0 0"),  // cycle 3
  note("1 1 1 1"),  // cycle 4
)
```

**Rule of thumb:** If you want N events per cycle, put exactly N space-separated values (or use `*N`). For longer
sequences spanning multiple cycles, use groups + `.slow()`, `cat()`, or alternation `<...>`.

**Use `<...>` alternation with groups to sequence across cycles (like `cat()`):**

```javascript
// Plays [1 2 3 4] in cycle 1, [5 6 7 8] in cycle 2, [1 2 3 4] in cycle 3, ...
n("<[1 2 3 4] [5 6 7 8] [1 2 3 4]>")

// Equivalent using cat():
cat(n("1 2 3 4"), n("5 6 7 8"), n("1 2 3 4"))
```

The `<...>` picks ONE item per cycle and rotates. Each `[...]` group inside is treated as a single item containing
multiple events. This is the most compact way to write multi-cycle sequences in a single string.

| Pattern                         | Events per cycle | Total cycles                   |
|---------------------------------|------------------|--------------------------------|
| `"a b c d"`                     | 4                | 1                              |
| `"a b c d e f g h"`             | 8                | 1                              |
| `"[a b c d] [e f g h]"`         | 4                | 1 (2 groups of 4 in one cycle) |
| `"[a b c d] [e f g h]".slow(2)` | 4                | 2                              |
| `"<[a b c d] [e f g h]>"`       | 4 (alternating)  | 2 to hear all                  |
| `"a*4"`                         | 4 (repeated)     | 1                              |
| `"<a b c d>"`                   | 1 (alternating)  | 4 to hear all                  |

---

## Entry Points

| Function                   | Description                                                                                                      | Example                                                                        |
|----------------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `sound(pat)` / `s(pat)`    | Play samples/synths by name                                                                                      | `s("bd sd hh cp")`                                                             |
| `note(pat)`                | Play by note name                                                                                                | `note("c3 e3 g3 c4")`                                                          |
| `n(pat)`                   | Play by scale index                                                                                              | `n("0 2 4 7").scale("C4:major")`                                               |
| `chord(pat)`               | Play chord names                                                                                                 | `chord("<Am C F G>")`                                                          |
| `stack(p1, p2, ...)`       | Layer simultaneously                                                                                             | `stack(s("bd sd"), s("hh*4"))`                                                 |
| `master(chain)`            | Set the song's master bus (silent control layer: put it in the `stack`)                                         | `stack(lead, bass, master(Katalyst(k => k.gain(2.0).limiter())))` |
| `master(Katalyst())`       | Switch the master back **off** (an empty chain): deleting the `master(...)` line does not, since a master means "change to this" | `master(Katalyst())`                                                           |
| `cat(p1, p2, ...)`         | Sequence across cycles                                                                                           | `cat(s("bd sd"), s("cp cp"))`                                                  |
| `fastcat(p1, p2, ...)`     | Sequence within one cycle                                                                                        | `fastcat(s("bd"), s("sd"))`                                                    |
| `arrange([n,p], ...)`      | Timed sections                                                                                                   | `arrange([4, melody], [2, silence])`                                           |
| `silence` / `rest`         | Empty pattern                                                                                                    | `arrange([4, melody], [4, silence])`                                           |
| `pure(value)`              | Constant pattern                                                                                                 | `pure(1/8).div(cps)`                                                           |
| `seq(values...)`           | Sequence from values                                                                                             | `seq("c3", "e3", "g3")`                                                        |

---

## Function Reference

> ## ⚠️ PER-ORBIT (BUS) vs PER-VOICE EFFECTS — READ THIS
>
> Some effects run **once per orbit** (on the whole orbit's mixed signal), NOT per note. All voices on the
> same `orbit(n)` **share** these: the orbit's bus settings are owned by the newest SOUNDING voice, and a
> voice gives the orbit up when its gate closes or it is cut (its release rings on under whatever settings come
> next); an older voice asking for different settings is ignored while a newer one sounds. **To give voices independent bus effects, put them on different orbits.**
>
> Since 2026-09-08 this is also metadata, not just prose: every DSL function carries a `@scope` tag that the
> docs popup and the library page render as a badge (`KlangScope`, values `voice` / `orbit` / `master`). If this table and a badge ever disagree, the badge is generated from the function and wins.
>
> | Scope | Effects |
> |-------|---------|
> | **PER-ORBIT (bus)**: one processor per orbit, settings from the orbit's current owner voice (the orbit's bus settings are owned by the newest `Sounding` voice; a voice gives the orbit up when its gate closes or it is cut; settings glide over 50 ms when the owner changes) | `body` / `vowel` (their `wet` is the mix), `delay` and `reverb` (since 2026-09-19 inserts fed from the orbit mix at their place in the chain, so the room hears body, vowel and the delay's echoes; ONE `wet` per orbit, the owner's), `phaser` (slots `wet`/`rate`/`center`/`sweep`/`floor`; bus-owned since 2026-08-24, one sweep over the summed orbit; there is no per-voice phaser), `compressor`, ducking |
> | **PER-VOICE**: independent per note | `lpf`/`hpf`/`bpf`/`notch` (with their `q`, `env` and envelope slots), `distort`, `crush`, `coarse`, `gain`/`velocity`/`pan`, `adsr` (slots `attack`/`decay`/`sustain`/`release`), `vibrato`, `tremolo`, `fm*`, pitch env (`penv`…), `unison`/`spread`, `analog`, `sound`/`n`/`note` |
> | **PER-PLAYBACK (master)**: the whole song's bus, after every orbit | `master(Katalyst(k => k...))`: the same Katalyst chain an orbit runs (since phase 3 step 12), so every stage door works there (`gain(gain)` as make-up level, `limiter(...)`, `compressor(...)`, `eq(...)`, `reverb(wet, size, lowpass)`, `delay(wet, time, feedback, d => d.cap(level))`, `phaser`, `body`, `vowel`), each appending a stage, and `through(a, b, ...)` to run the builder through functions of stages in order (`let hall = k => k.reverb(0.25, 7)`, then `k.through(hall, ceiling)`); `duck` is inert at the output and a `Katalyst.param(...)` stays at its default there |

**The limiter.** `k.limiter(threshold, ratio, knee, attack, release, lookahead)`, flat and every parameter optional
(an omitted one keeps its default): `threshold` in dBFS (-1), `ratio` (20), `knee` in dB (2), `attack`, `release` and
`lookahead` in seconds (0.001, 0.1, 0). Name them: `k.limiter(threshold = -3, ratio = 4)`. It is a compressor stage
with limiter defaults, so it works on an orbit too.

- An **always-on safety limiter** already runs on the summed mix (−1 dB, 20:1, 5 ms lookahead), so every song is delayed
  5 ms and peaks are already caught. An authored `limiter` stage is for *shaping*, not peak-catching.
- **`lookahead` defaults to 0 and is opt-in**, because it costs exactly that much latency and stages stack: three
  limiters with lookahead are three delay lines. At the master it shifts this song against anything else playing; on an
  orbit it shifts that orbit against the song's other orbits (nothing compensates).
- With lookahead **off**, `attack` is a one-pole time constant (short = keeps transient punch). With it **on**, `attack`
  is the gain-smoothing length — set it equal to the lookahead, since peak performance is invariant to it while
  low-frequency cleanliness tracks it.
- **Staged gain** works better than one big push: split the total in dB evenly across stages, with descending thresholds
  and ascending ratios, e.g. `gain(1.45)` → `limiter(threshold = -8.0, ratio = 2.0, attack = 0.015, release = 0.25)`
  → `gain(1.40)` → `limiter(threshold = -4.0, ratio = 4.0, attack = 0.008, release = 0.15)` → `gain(1.30)`. Slow attacks (30
  ms+) arrive after the transient and read as "shocks" on dense material.
>
> Example — two guitars that each need their **own** wood body must be on separate orbits:
> ```javascript
> stack(
>   guitarA.orbit(1).body(material = "wood"),   // orbit 1's body
>   guitarB.orbit(2).body(material = "wood"),   // orbit 2's body, independent
> )
> ```
> If both were `orbit(1)`, they'd share ONE body over their summed signal (fuller, but not two bodies).

### Tempo & Time

| Function         | Aliases | Description                       | Example                                |
|------------------|---------|-----------------------------------|----------------------------------------|
| `fast(n)`        |         | Speed up by factor n              | `s("bd sd").fast(2)`                   |
| `slow(n)`        |         | Slow down by factor n             | `s("bd sd").slow(2)`                   |
| `hurry(n)`       |         | Speed up pattern + audio speed    | `s("breaks").hurry(2)`                 |
| `rev()`          |         | Reverse pattern                   | `note("c d e f").rev()`                |
| `palindrome()`   |         | Forward then backward             | `note("c d e f").palindrome()`         |
| `early(n)`       |         | Shift earlier by n cycles         | `s("bd").early(0.25)`                  |
| `late(n)`        |         | Shift later by n cycles           | `s("bd").late(0.25)`                   |
| `compress(s,e)`  |         | Squash into time range [s,e]      | `note("c d e f").compress(0, 0.5)`     |
| `focus(s,e)`     |         | Zoom into time range              | `note("c d e f").focus(0, 0.5)`        |
| `ply(n)`         |         | Repeat each event n times         | `s("bd sd").ply(2)`                    |
| `swing(amount)`  |         | Push alternate events late        | `s("hh*8").swing(0.1)`                 |
| `brak()`         |         | Breakbeat syncopation             | `s("bd sd hh cp").brak()`              |
| `inside(n, fn)`  |         | Transform inside n-cycle span     | `note("c d").inside(4, x => x.rev())`  |
| `outside(n, fn)` |         | Transform over n-cycle span       | `note("c d").outside(4, x => x.rev())` |
| `stretchBy(n)`   |         | Stretch by n without scaling time | `note("c d e f").stretchBy(2)`         |

### Structure & Composition

| Function               | Aliases    | Description                      | Example                                                   |
|------------------------|------------|----------------------------------|-----------------------------------------------------------|
| `every(n, fn)`         | `firstOf`  | Apply fn every n cycles          | `s("bd sd").every(4, x => x.fast(2))`                     |
| `lastOf(n, fn)`        |            | Apply fn on last of n cycles     | `s("bd sd").lastOf(4, x => x.rev())`                      |
| `superimpose(fn...)`   |            | Layer with transforms            | `note("c e g").superimpose(x => x.add(12))`               |
| `jux(fn)`              |            | Left=original, right=fn          | `s("bd sd hh").jux(rev)`                                  |
| `juxBy(amt, fn)`       |            | jux with stereo amount           | `s("bd sd").juxBy(0.5, rev)`                              |
| `apply(fn...)`         | `layer`    | Apply transforms, stack results  | `s("bd").apply(fast(2), rev)`                             |
| `off(time, fn)`        |            | Overlay shifted+transformed copy | `note("c e g").off(0.125, x => x.add(7))`                 |
| `echo(n, time, decay)` | `stut`     | Layered echo                     | `s("bd").echo(3, 0.125, 0.7)`                             |
| `echoWith(n, t, fn)`   | `stutWith` | Echo with transform per repeat   | `n("0").echoWith(4, 0.125, x => x.add(2))`                |
| `struct(pat)`          |            | Apply rhythmic structure         | `note("c3").struct("x ~ x x ~ x ~ x")`                    |
| `mask(pat)`            |            | Mute where pat is 0/rest         | `s("bd sd hh cp").mask("1 1 0 1")`                        |
| `euclid(p, s)`         |            | Euclidean rhythm                 | `s("hh").euclid(3, 8)`                                    |
| `euclidRot(p, s, r)`   |            | Euclidean + rotation             | `s("hh").euclidRot(3, 8, 2)`                              |
| `iter(n)`              |            | Rotate through n divisions       | `note("c d e f").iter(4)`                                 |
| `iterBack(n)`          |            | Rotate backwards                 | `note("c d e f").iterBack(4)`                             |
| `bite(n, pat)`         |            | Rearrange n slices by pattern    | `n("0 1 2 3").bite(4, "3 2 1 0")`                         |
| `chunk(n, fn)`         |            | Transform one chunk at a time    | `s("bd sd hh cp").chunk(4, x => x.fast(2))`               |
| `linger(n)`            |            | Loop first fraction of pattern   | `note("c d e f").linger(0.25)`                            |
| `within(s, e, fn)`     |            | Transform within time range      | `s("bd sd hh cp").within(0.5, 1, fast(2))`                |
| `when(cond, fn)`       |            | Apply fn when condition true     | `note("c d").when(pure(1).struct("t ~"), x => x.add(12))` |
| `filterWhen(fn)`       |            | Only play when fn(cycle) true    | `note("c3").filterWhen(x => x >= 8)`                      |
| `pick(list, pat)`      |            | Select from list by pattern      | `pick(["bd", "sd", "hh"], "0 1 2 1")`                     |
| `squeeze(pat)`         |            | Time-squeeze into structure      | `note("c e g").squeeze("x x ~ x")`                        |
| `polymeter(p...)`      |            | Different-length polyrhtyhms     | `polymeter(s("bd sd"), s("hh hh hh"))`                    |
| `repeat(n)`            |            | Repeat pattern n times per cycle | `note("c d").repeat(4)`                                   |
| `arrange([n,p]...)`    |            | Sequence sections by duration    | `arrange([4, verse], [2, chorus])`                        |
| `morse(text)`          |            | Text-to-rhythm morse code        | `n("0").morse("SOS")`                                     |
| `binary(n)`            |            | Integer to binary rhythm         | `s("hh").struct(binary(5))`                               |
| `run(n)`               |            | Sequence 0 to n-1                | `run(8).scale("C:major").note()`                          |

### Tonal & Pitch

| Function                | Aliases | Description                   | Example                                         |
|-------------------------|---------|-------------------------------|-------------------------------------------------|
| `note(name)`            |         | Set note name                 | `note("c3 e3 g3")` or `note("a:1 b:2")`         |
| `n(index)`              |         | Set scale index               | `n("0 2 4 7").scale("C4:major")`                |
| `scale(name)`           |         | Set scale context             | `n("0 1 2 3").scale("C4:minor")`                |
| `transpose(semi)`       |         | Shift by semitones            | `note("c3").transpose(12)`                      |
| `scaleTranspose(steps)` |         | Shift by scale degrees        | `n("0 2 4").scale("C:major").scaleTranspose(1)` |
| `chord(name)`           |         | Set chord name                | `chord("<Am C F G>")`                           |
| `voicing()`             |         | Expand chord to voiced notes  | `chord("Am").voicing()`                         |
| `rootNotes()`           |         | Extract chord root notes      | `chord("Am C").rootNotes()`                     |
| `freq(hz)`              |         | Set frequency in Hz           | `freq("440 880")`                               |
| `accelerate(semitones)` |         | Pitch ramp during playback (SEMITONES over the event; 12 = one octave) | `s("cr").accelerate(24)`                        |
| `vibrato(rate, depth)`                                                 | `vib`      | Vibrato LFO rate in Hz and depth in semitones; readers `vibrato.rate`, `.depth`                                                                        | `note("c4").s("saw").vibrato(5, 0.5)`                                                                |

#### Scale degrees: two things that are easy to get wrong

Both measured by rendering a sine and reading its frequency (2026-09-30, Kokon).

**`add` on an `n(...)` pattern does not move the pitch.** `add` changes the raw event value, not the scale
degree that `n` set. Add to the notes *before* they reach `n`:

```javascript
n("0").add(7).scale("d3:minor")        // still D3: the add is silently ignored
n("0".add(7)).scale("d3:minor")        // D4: add on the string, then n
n("0").scale("d3:minor").transpose(12) // D4 too, but only AFTER the scale (a transpose before it is ignored as well)
```

So a line that plays its notes an octave up is `notes => n(notes.add(7))`; this works for a mini-notation
string and for a pattern alike, chords included (`"<[4,7,9]>".add(7)`).

**The first `.scale()` on a note wins.** A note's scale degree is resolved once; a later `.scale()` is inert
for pitch. This lets a song set its key once at the top and a single part bring its own:

```javascript
let lastChord = strum("<[0 4 7 9 11 ~@27] ~>").scale("d3:major") // the Picardy third: F# is in d3:major only

arrange([32, everythingElse], [2, lastChord]).scale("d3:minor")  // inert for lastChord, it has its scale already
```

It is also why a pattern meant to be imported (a Klangbuch part) carries no scale: the importer's `.scale()`
would be ignored.

#### `:soundIndex:gain` suffix (universal variant picker)

A `name:soundIndex[:gain]` suffix on `note()`, `s()` / `sound()`, and
`seq(...).scale(...)` selects a per-event variant from a sound bundle
(sample bank or `Ignitor.variants(...)`). Same syntax as strudel's sample
selection — extended to ignitor variants and per-note gain.

| Pattern                                  | What it does                                  |
|------------------------------------------|-----------------------------------------------|
| `note("a b:1 c:2")`                      | Notes a/b/c; variant 0/1/2                    |
| `note("a:1:0.5")`                        | Note a, variant 1, gain 0.5                   |
| `s("bd:0 bd:1 bd:2")`                    | Three samples from the `bd` bank              |
| `seq("0 2 4 4:1 5:1").scale("c4:minor")` | Scale steps 0/2/4/4/5; last two get variant 1 |
| `sound("bd sd").n(2)`                    | Both `bd` and `sd` use sample variant 2       |

**Where the parse happens:**

- `note(...)` and `s()`/`sound(...)` split immediately — the value is known
  to be a note/sound name at insertion time.
- `seq("X:Y")` keeps the raw `"X:Y"` string verbatim — the split happens
  lazily inside `scale()` / `note()` reinterpretation, since `seq` doesn't
  know what its values will be used for. So `seq("0:1").scale("c:minor")`
  → scale step 0, variant 1.
- `n("X")` is the strudel-port shim and does **not** parse `:variant`. Use
  `seq("X:Y").scale(...)` for scale + variant combos.

**Defaults & wrap-around** mirror sample-bank semantics: missing
`:soundIndex` → variant 0; overflow / negative wraps via floor-mod
(`children[i.mod(N)]`).

### Dynamics & Routing

| Function         | Aliases    | Description                    | Example                               |
|------------------|------------|--------------------------------|---------------------------------------|
| `gain(amt)`      |            | The one level word: tone-neutral, after the voice's filters and distortion; a later `gain` REPLACES an earlier one (`gain(mul(x))` scales a gain that is set) | `s("bd").distort(3).gain(0.1)` |
| `velocity(amt)`  | `vel`      | Velocity (0-1)                 | `note("c3").velocity(0.5)`            |
| `pan(pos)`       |            | Stereo (0=L, 0.5=C, 1=R)       | `s("hh").pan(sine)`                   |
| `orbit(n)`       | `cylinder` | Effect send channel (0-3)      | `note("c3").orbit(1).reverb(0.5, 4)`       |
| `adsr(a, d, s, r)` |          | Amplitude envelope; omitted slots keep their value, named slots take a mapper | `note("c3").adsr(0.01, 0.2, 0.7, 0.5)`, `.adsr(attack = mul(2))` |
| `adsr.attack` `.decay` `.sustain` `.release` | | Read a slot back into another setter | `note("c3").adsr(0.3, 0.2).adsr(release = adsr.attack)` |
| `adsrOff()`      |            | Voice envelope OFF — the instrument owns amplitude | `note("c3").sound(gtr).adsrOff()` |
| `adsrOn(flag?)`  |            | Voice envelope ON (the default) | `note("c3").adsrOn()`                |
| `legato(amt)`    | `clip`     | Note duration scaling          | `note("c3").legato(1.5)`              |

### Sound Selection

| Function           | Aliases         | Description                     | Example                                |
|--------------------|-----------------|---------------------------------|----------------------------------------|
| `sound(name)`      | `s`             | Set sound/instrument            | `sound("bd sd hh cp")`                 |
| `analog(amt)`      |                 | How analog: a character scale, 0 ideal, 1 to 8 usual, 10 strong (an oscillator drifts about a cent per unit) | `note("c3").s("supersaw").analog(4)` |
| `ignitorParam(slot, value)` | `ignp` | Write one slot of the playing instrument, per voice. `slot` is the slot's NAME or the param OBJECT itself (`Ignitor.param(...)` held in a variable, or `Ignitor.slot.*`); only the name is written, the default stays the instrument's. A Katalyst param is a script error at the call (`a Katalyst param passed to ignp; use katp`), and so is a number or a sound | `note("c2").sound(bass).ignp("cutoff", 1200)`, `.ignp(cutoff, 1200)`, `.ignp(Ignitor.slot.analog, 4)` |
| `unison(voices, spread, pan)`                                          | `uni`      | Unison voices, detune spread in semitones, stereo spread (reserved); readers `unison.voices`, `.spread`, `.pan`                                        | `note("c3").s("supersaw").unison(5, 0.3)`                                                            |
| `density(amt)`     | `d`             | Oscillator density (noise)      | `note("a").s("dust").density(40)`      |
| `onepole(freq)`    |                 | One-pole lowpass in Hz (warmth) | `s("bd").distort(3).onepole(17814)`    |
| `sndPluck(params)` |                 | Karplus-Strong shorthand        | `note("c3").sndPluck(0.999, 0.8)`     |
| `sndSuperSaw()`    |                 | Super-saw shorthand             | `note("c3").sndSuperSaw()`             |
| `bank(name)`       |                 | Sample bank                     | `s("bd").bank("RolandTR808")`          |

### Filters

All filters accept pattern values on every slot, and each carries its own envelope as slots: `env` for the depth in semitones, `attack`, `decay`, `sustain`, `release` for the shape (`lpf(freq = 400, env = 24, attack = 0.01, decay = 0.3, sustain = 0.2)`; same on `hpf`, `bpf`, `notch`)

Lowpass and highpass also take a **cascade count** as their third argument: `lpf(freq, q, passes)`.
At the default q the cascade keeps its -3 dB point AT the cutoff (`lpf(800, 0.707, 2)` still means
800, it is just twice as steep); a resonant q compounds instead (`q = 1.0, passes = 2` sits +3 dB
at the cutoff). Same third slot on the ignitor door.

| Function         | Aliases               | Description                | Example                                                      |
|------------------|-----------------------|----------------------------|--------------------------------------------------------------|
| `lpf(freq, q, passes, env, attack, decay, sustain, release)`           | `lowpass`  | Lowpass: cutoff Hz, resonance, cascade count (2 = 24 dB/oct), envelope depth in SEMITONES (+12 doubles the cutoff, negative sweeps down) and the envelope stages | `note("c3").s("saw").lpf(freq = 400, q = 5, env = 24, attack = 0.01, decay = 0.3, sustain = 0.2)`    |
| `lpf.freq` / `lpf.q` / `lpf.passes` / `lpf.env` / `lpf.attack` ...     |            | Read a lowpass slot; `lpf(q = mul(2))` maps one slot on its own value                                                                                  | `p.lpf(800).hpf(lpf.freq.div(2))`                                                                    |
| `hpf(freq, q, passes, env, attack, decay, sustain, release)`           | `highpass` | Highpass, same slots and readers as `lpf` (`hpf.freq`, `hpf.env`, ...)                                                                                 | `s("bd").hpf(freq = 200, q = 2)`                                                                     |
| `bpf(freq, q, env, attack, decay, sustain, release)`                   | `bandpass` | Bandpass: centre Hz, Q, envelope depth in semitones and the stages; readers `bpf.freq`, `bpf.q`, ...                                                   | `note("c3").bpf(freq.mul(4), 3)`                                                                     |
| `notch(freq, q, env, attack, decay, sustain, release)`                 |            | Notch (band reject), same slots as `bpf`; readers `notch.freq`, `notch.q`, ...                                                                         | `s("sd").notch(freq = 1000, q = 2)`                                                                  |
| `lpfCurves(attack, decay, release)` / `hpfCurves` / `bpfCurves` / `notchCurves` |            | Stage curves of that filter's envelope, like `adsrCurves` (`"exp"` default, `"linear"`, `"square"`, ...); an omitted or unknown stage keeps its curve; a curve alone switches no envelope on | `note("c3").s("saw").lpf(freq = 400, env = 24, decay = 0.3).lpfCurves("linear", "linear", "linear")` |

### Effects

| Function                | Aliases                                                                                  | Description                                   | Example                                   |
|-------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------|-------------------------------------------|
| `reverb(wet, size, lowpass)`                                                |          | Reverb: send 0..1, tail length ~0..10 (3 ≈ 1 s, 10 ≈ 12.5 s, bounded at 10), damping cutoff Hz. Unset slots default to wet 0.25, size 5 (same as the master reverb) | `note("c3").reverb(wet = 0.3, size = 5)`                     |
| `reverb(size = mul(2))`                                                     |          | A mapper on one slot maps that slot on its own value; the other slots stay                                                                                       | `p.reverb(0.3, 4).reverb(size = mul(2))`                     |
| `reverb.wet` / `reverb.size` / `reverb.lowpass`                             |          | Read a reverb slot into another setter                                                                                                                           | `p.reverb(size = 4).delay(time = reverb.size.div(8))`        |
| `delay(wet, time, feedback, cap)`                                           |          | Delay: send 0..1, time in seconds, feedback 0..1, feedback cap. Unset slots default to wet 0.25, time 0.25, feedback 0.3, cap 1 (same as the master delay)      | `s("sd").delay(wet = 0.5, time = 0.33, feedback = 0.3)`      |
| `delay.wet` / `delay.time` / `delay.feedback` / `delay.cap`                 |          | Read a delay slot                                                                                                                                                | `p.delay(time = 0.25).reverb(size = delay.time.mul(20))`     |
| `distort(amount, shape, oversample)`                                        |          | Distortion amount, shape name (`soft`, `hard`, `fold`, `exp`, ...), oversample factor (Int)                                                                      | `s("bd").distort(amount = 2, shape = "fold")`                |
| `distort.amount` / `distort.oversample`                                     |          | Read a distortion slot (`shape` is a string, no reader)                                                                                                          | `p.distort(0.4).pan(distort.amount)`                         |
| `crush(bits, oversample)`                                                   |          | Bitcrusher bits, oversample factor (carried on the wire but not read today, see `docs/tasks/oversampling-regions.md`); readers `crush.bits`, `crush.oversample` | `s("hh").crush(8)`                                           |
| `coarse(factor, oversample)`                                                |          | Sample-rate reduction factor, oversample factor (carried on the wire but not read today, see `docs/tasks/oversampling-regions.md`); readers `coarse.factor`, `coarse.oversample` | `note("c3").s("saw").coarse(3)`                              |
| `phaser(wet, rate, center, sweep, floor)`                                   |          | Phaser: wet FIRST (additive by default), then the LFO rate in Hz, center Hz, sweep range Hz, floor. A bare `phaser()` reads the pattern's values as `wet` | `note("c3").phaser(wet = 0.5, rate = 1, center = 1000)`      |
| `phaser.wet` / `phaser.rate` / `phaser.center` / `phaser.sweep` / `phaser.floor` |          | Read a phaser slot                                                                                                                                               | `p.phaser(center = 1000).lpf(phaser.center)`                 |
| `tremolo(depth, rate, shape)`                                               |          | Tremolo: depth 0..1 FIRST, then the LFO rate in Hz (`rate`, `beatRate(n)` follows the tempo), LFO shape name (the oscillator of that name; square, sawtooth and ramp get a 16 ms soft edge). The level always dips from 1 to `1 - depth`; a swell upward is the Ignitor door's `range` knob, `.tremolo(rate, depth, x => x.range(0, 1))`: no pattern slot, by decision (2026-10-06) | `note("c3").tremolo(depth = 0.5, rate = 4, shape = "sine")`  |
| `tremolo.depth` / `tremolo.rate`                                            |          | Read a tremolo slot (`shape` is a string, no reader)                                                                                                             | `p.tremolo(0.5, 4).phaser(rate = tremolo.rate)`                     |
| `compressor(threshold, ratio, knee, attack, release)`                  | `comp`     | Orbit compressor; readers `compressor.threshold`, `.ratio`, `.knee`, `.attack`, `.release`                                                             | `s("bd sd hh sd").compressor(-20, 4, 6, 0.003, 0.1)`                                                 |
| `duck(orbit, depth, attack)`                                           |            | Sidechain: the orbit that triggers, depth 0..1, recovery seconds (the duck-down is instant); readers `duck.orbit`, `.depth`, `.attack`                 | `note("c2*8").s("saw").duck(1, 0.8, 0.2)`                                                            |
| `vowel(wet, vowel, floor)`                                             |            | Vowel formant: wet FIRST (a mix 0..1), then the vowel name (no reader), dry floor; readers `vowel.wet`, `.floor`. A vowel alone is named: `vowel(vowel = "a")`; `"none"` is off | `note("c3").s("saw").vowel(0.8, "a")`                                                                |
| `body(wet, material, floor)`                                           |            | Resonant body: wet FIRST (a mix 0..1), then the material name (no reader), dry floor; readers `body.wet`, `.floor`. A material alone is named: `body(material = "wood")`; `"none"` is off | `note("c3").s("saw").body(0.7, "wood")`                                                              |
| `katalystParam(slot, value)` | `katp` | Write one slot of the orbit's Katalyst chain, per orbit (the newest sounding voice owns the orbit's values). `slot` is the slot's NAME (`"reverb.size"`, or the name of your own `Katalyst.param("room", 5)`) or the param OBJECT (`Katalyst.slot.reverb.size`, or a `Katalyst.param(...)` held in a variable). Writes exactly that one slot, so on the familiar chain a `katp("reverb.wet", 0.3)` alone stays silent until `reverb.size` is written too. An Ignitor param is a script error at the call (`an Ignitor param passed to katp; use ignp`) | `note("c3").s("saw").reverb(wet = 0.4).katp("reverb.size", "<2 8>")`, `.katp(Katalyst.slot.reverb.size, "<2 8>")`, `.katp(room, "<2 9>")` |

#### Reverb size: how long the room rings

Measured 2026-09-30 (offline renders at 48 kHz, a 20 ms saw note on C4, decay time read from the tail by Schroeder
integration, the -5 to -35 dB slope extrapolated to 60 dB). The orbit and the master reverb are the same unit and
measure the same (checked at sizes 3 and 7).

| size | a note fades in (measured) | the lowest frequencies ring (from the design) | feels like |
|------|----------------------------|-----------------------------------------------|------------|
| 0    | off (the reverb switches off below 0.1) | | dry |
| 1    | 0.69 s | 0.8 s | a small room |
| 2    | 0.74 s | 0.9 s | a small room |
| 3    | 0.95 s | 1.1 s | a room |
| 4    | 1.01 s | 1.2 s | a room |
| 5    | 1.23 s | 1.5 s | a studio, the default |
| 6    | 1.54 s | 1.8 s | a chamber |
| 7    | 2.07 s | 2.3 s | a concert hall (about 2 s) |
| 8    | 2.84 s | 3.3 s | a big hall |
| 9    | 4.60 s | 5.2 s | a cathedral |
| 10   | 11.1 s | 12.7 s | an endless wash, the longest there is |

- **Two columns, two questions.** "A note fades in" is what you hear: the whole note's energy down 60 dB. "The
  lowest frequencies ring" is worked out from the design (comb feedback `0.7 + 0.028 · size`, longest comb 37 ms):
  the lows ring longest, the highs die sooner. The "3 is about 1 s, 5 about 1.4 s, 10 about 12.5 s" in the door
  docs is this second column.
- **`lowpass` changes the colour of the tail, not its length**: from no `lowpass` to 2000 Hz the measured fade
  moves by 2 % or less (3 % at size 10). A darker room, not a shorter one.
- **The room answers late at every size.** There is no pre-delay and no early reflections: the tail starts
  about 25 to 37 ms after the note, whatever the size. A bigger `size` rings longer, it does not sound further away.
- **One room for both ears**: both sides of the reverb are fed `(L + R) / 2` (since 2026-09-30), so a voice panned
  hard left rings in both ears. Its room is centred, not on its side.

Distortion shapes: `soft` (default/tanh), `hard`, `gentle`, `cubic`, `diode`, `fold`, `chebyshev`, `rectify`, `exp`

### FM Synthesis (via pattern params)

| Function           | Aliases | Description          | Example                                |
|--------------------|---------|----------------------|----------------------------------------|
| `fm(env, h, attack, decay, sustain)`                                   |            | FM: modulation depth in Hz, harmonicity (modulator to carrier ratio), and the modulation envelope; active once `env` and `h` are set                   | `note("c3").s("sine").fm(300, 1.4, 0.01, 0.3, 0)`                                                    |
| `fm.env` / `fm.h` / `fm.attack` / `fm.decay` / `fm.sustain`            |            | Read an FM slot; `fm(h = mul(2))` maps one slot                                                                                                        | `p.fm(200, 2).fm(env = mul("1 3"))`                                                                  |

### Pitch Envelope (via pattern params)

| Function          | Aliases | Description          | Example                             |
|-------------------|---------|----------------------|-------------------------------------|
| `penv(semitones, attack, decay, sustain, release)`                     | `pamt`     | Pitch envelope, the Ignitor `pitchEnvelope` (`classic()`'s stage): depth in semitones and an ADSR (sustain a share of the depth, 0 = the note); stages exponential by default; unset stages 0.01 / 0.1 / 0 / 0 | `s("bd*4").penv(24, 0.001, 0.08)`                                                                    |
| `penvCurves(attack, decay, release)`                                   |            | Stage curves of the pitch envelope, like `adsrCurves` (`"exp"`, `"linear"`, `"square"`, ...; an omitted stage keeps its curve) | `s("bd*4").penv(24, 0.001, 0.08).penvCurves("linear", "linear", "linear")`                          |
| `penv.semitones` / `penv.attack` / ... / `penv.release`                |            | Read a pitch envelope slot                                                                                                                             | `p.penv("12 -12", 0.01, 0.2).lpf(penv.semitones.mul(100).add(2000))`                                 |

### Sampling

| Function         | Aliases | Description               | Example                            |
|------------------|---------|---------------------------|------------------------------------|
| `begin(pos)`     |         | Start position (0-1)      | `s("breaks").begin(0.5)`           |
| `end(pos)`       |         | End position (0-1)        | `s("breaks").end(0.5)`             |
| `speed(factor)`  |         | Playback speed            | `s("breaks").speed(0.5)`           |
| `cut(group)`     |         | Choke group               | `s("hh*4").cut(1)`                 |
| `loop(flag)`     |         | Loop the begin..end region | `s("pad").loop(1).begin(0.25).end(0.75)` |
| `loopAt(cycles)` |         | Fit sample to n cycles    | `s("breaks").loopAt(1)`            |
| `slice(n, pat)`  |         | Slice sample into n parts | `s("breaks").slice(8, "0 3 5 2")`  |
| `splice(n, pat)` |         | Slice + pitch-adjust      | `s("breaks").splice(8, "0 3 5 2")` |

### Random & Probability

| Function                  | Description                    | Example                                       |
|---------------------------|--------------------------------|-----------------------------------------------|
| `degradeBy(prob)`         | Remove events with probability | `s("hh*8").degradeBy(0.5)`                    |
| `sometimes(fn)`           | Apply fn 50% of the time       | `s("hh*8").sometimes(x => x.gain(0.2))`       |
| `sometimesBy(p, fn)`      | Apply fn p% of the time        | `s("hh*8").sometimesBy(0.3, x => x.speed(2))` |
| `often(fn)`               | Apply fn 75% of the time       | `s("hh*8").often(x => x.pan(rand))`           |
| `rarely(fn)`              | Apply fn 25% of the time       | `s("hh*8").rarely(x => x.crush(4))`           |
| `almostAlways(fn)`        | Apply fn 90%                   | `s("hh*8").almostAlways(x => x.gain(0.2))`    |
| `almostNever(fn)`         | Apply fn 10%                   | `s("hh*8").almostNever(x => x.speed(0.5))`    |
| `choose(a, b, ...)`       | Random pick from values        | `sine.choose("c", "e", "g")`                  |
| `chooseCycles(a, b, ...)` | Random pick per cycle          | `chooseCycles(s("bd"), s("sd"))`              |
| `wchoose([v,w], ...)`     | Weighted random choice         | `wchoose(["bd", 3], ["sd", 1])`               |
| `shuffle()`               | Shuffle event order            | `note("c d e f").shuffle()`                   |
| `scramble()`              | Scramble within structure      | `note("c d e f").scramble()`                  |
| `seed(n)`                 | Set random seed                | `s("hh*8").degradeBy(0.5).seed(42)`           |
| `randrun(n)`              | n random values 0..n-1         | `n(randrun(8)).scale("C:minor")`              |
| `degrade()`               | Remove 50% of events           | `s("hh*8").degrade()`                         |

### Arithmetic

| Function     | Description          | Example                    |
|--------------|----------------------|----------------------------|
| `add(n)`     | Add to values (on scale degrees add before `n`, see Tonal & Pitch) | `n("0 2".add(5))` |
| `sub(n)`     | Subtract             | `n("7 5").sub(2)`          |
| `mul(n)`     | Multiply             | `gain(0.5).mul(2)`         |
| `div(n)`     | Divide               | `pure(1/8).div(cps)`       |
| `mod(n)`     | Modulo               | `n("0 3 6 9").mod(7)`      |
| `pow(n)`     | Power                | `saw.pow(2)`               |
| `abs()`      | Absolute value       | `seq("-3 -1 0 2").abs()`   |
| `round()`    | Round to nearest int | `sine.range(0, 7).round()` |
| `floor()`    | Floor                | `sine.range(0, 7).floor()` |
| `ceil()`     | Ceiling              | `sine.range(0, 7).ceil()`  |
| `flipSign()` | Negate               | `seq("1 -2 3").flipSign()` |

---

## Continuous Signals (LFOs)

Top-level signals that produce continuous values between 0 and 1. Use `.range(from, to)` to scale, or call the
signal: `perlin(200, 400)` is exactly `perlin.range(200, 400)`. Both values are required (`sine(200)` is a script
error, `Ignitor.sine(200)` would be 200 Hz). The bare name stays a pattern: `perlin.slow(8)`, `.pan(perlin)`.

### Oscillators (0..1)

`sine`, `cosine` (exactly `sine.early(0.25)`), `saw`, `tri`, `square`

A falling saw is `saw.range(1, 0)`, an inverted triangle `tri.range(1, 0)`. To range one, swap the values: a falling
saw from a to b is `saw.range(b, a)` (the old `isaw.range(a, b)`), the same for `tri` (the old `itri`), because the
innermost range wins.

### Noise

`perlin`, `berlin`, `rand`, `randCycle` (`randCycle` has no call shorthand)

### Utility

`time` (cycle counter), `cps` (cycles/sec), `rpm` (CPS*60), `bpm` (CPS*240)

Wall clock: `timeOfDay` (0 at midnight, 1 at the next), `sineOfDay` (0 at midnight, 1 at noon), `timeOfNight`,
`sineOfNight` (their inverses).

Tempo-following lengths and rates (both follow every rpm change; `base` = beats per cycle, default 4):

- `beats(n, base = 4)`: the length of n beats in SECONDS, for time params (`delay.time`, envelope stages).
  `beats(0.5)` is an eighth note. Not for `late`/`early`, which take cycles.
- `beatRate(n, base = 4)`: one cycle every n beats in HZ, for rate params (`tremolo`, `vibrato`, `phaser`).
  `beatRate(0.5)` wobbles every half beat. Exactly `pure(1).div(beats(n))`.
- In mini-notation `"1/8"` is NOT a fraction (`/` slows down): write `"0.125"` or use `beats`.

### Range Mapping

| Function                  | Input | Description                      |
|---------------------------|-------|----------------------------------|
| `.range(from, to)`        | 0..1  | Linear scale                     |
| `signal(from, to)`        | 0..1  | The same, as the call shorthand  |
| `.rangex(from, to)`       | 0..1  | Exponential (for frequencies)    |
| `.segment(n)` / `.seg(n)` | any   | Sample-and-hold at n steps/cycle |

`range` and `rangex` shape continuous signals only. Discrete values (a mini-notation string, `seq(...)`) scale with
`mul` and `add`: `"0 0.5 1".mul(900).add(100)` gives 100, 550 and 1000.

Where the swing sits is up to the two values (the same word and result on the Ignitor side):

| Call             | The signal moves                        |
|------------------|-----------------------------------------|
| `range(0, 1)`    | only upward, between 0 and 1 (as it is) |
| `range(-1, 0)`   | only downward, between -1 and 0         |
| `range(-1, 1)`   | both ways, centred on 0                 |
| `range(-0.5, 1)` | mostly upward, dipping a little below 0 |
| `range(1, 0)`    | the same swing turned upside down       |

The innermost range wins: `perlin(200, 400).range(0, 1)` still swings between 200 and 400, so range a signal once.
An outer `rangex` does not reshape a ranged signal either (`perlin(200, 400).rangex(...)` gives wrong values): use
`rangex` on the bare signal, `perlin.rangex(200, 400)`. The two values are named `from` and `to` on every surface
(`perlin.range(from = 200, to = 400)`); `from` may be larger than `to`.

### Usage

```javascript
// Sweeping filter
note("c3").s("saw").lpf(sine.range(200, 2000).slow(4))
note("c3").s("saw").lpf(sine(200, 2000).slow(4))   // the same, the call shorthand

// Random panning
s("hh*8").pan(rand)

// Tempo-synced delay (an eighth note) and tremolo (every half beat)
s("sd").delay(wet = 0.5, time = beats(0.5))
note("c3").s("saw").tremolo(depth = 0.6, rate = beatRate(0.5))

// Organic modulation
note("c3").s("supersaw").unison(spread = perlin.range(0.0, 0.3).slow(16))
```

---

## Built-in Sounds

### Drum Samples (via `sound()` / `s()`)

`bd` (bass drum), `sd` (snare), `hh` (closed hi-hat), `oh` (open hi-hat), `cp` (clap), `cr` (crash), `rd` (ride), `lt` (
low tom), `mt` (mid tom), `ht` (high tom), `rim` (rimshot), `ch` (closed hat)

Use `:N` for sample-bank variants: `sd:3`, `bd:2`. The same `:N` suffix
also picks ignitor flavours from `Ignitor.variants(...)` — see
[Tonal & Pitch › `:soundIndex:gain` suffix](#soundindexgain-suffix-universal-variant-picker)
for the full story.

### Synth Oscillators (via `sound()` / `s()`)

| Name          | Aliases                  | Description                              |
|---------------|--------------------------|------------------------------------------|
| `sine`        | `sin`                    | Pure sine wave                           |
| `sawtooth`    | `saw`                    | Bright sawtooth (anti-aliased)           |
| `square`      | `sqr`, `pulse`           | Hollow square wave                       |
| `triangle`    | `tri`                    | Soft triangle wave                       |
| `ramp`        |                          | Reverse sawtooth                         |
| `zawtooth`    | `zaw`                    | Naive sawtooth (brighter, no anti-alias) |
| `pulze`       |                          | Variable pulse width                     |
| `impulse`     |                          | Click/impulse train                      |
| `supersaw`    |                          | Multiple detuned saws (thick, lush)      |
| `supersine`   |                          | Multiple detuned sines                   |
| `supersquare` | `supersqr`, `superpulse` | Multiple detuned squares                 |
| `supertri`    |                          | Multiple detuned triangles               |
| `superramp`   |                          | Multiple detuned ramps                   |
| `pluck`       | `ks`, `string`           | Karplus-Strong plucked string            |
| `superpluck`  |                          | Unison plucked strings                   |
| `whitenoise`  | `white`                  | Flat spectrum noise                      |
| `brownnoise`  | `brown`                  | Deep rumbling noise                      |
| `pinknoise`   | `pink`                   | Natural balanced noise                   |
| `perlinnoise` | `perlin`                 | Smooth organic noise                     |
| `berlinnoise` | `berlin`                 | Angular random noise                     |
| `dust`        |                          | Sparse random impulses                   |
| `crackle`     |                          | Random crackle texture                   |

### Preset Compositions

| Name     | Architecture                                 |
|----------|----------------------------------------------|
| `sgpad`  | Two detuned saws -> one-pole lowpass 3kHz    |
| `sgbell` | FM bell: sine.fm(sine, ratio=1.4, depth=300) |
| `sgbuzz` | Square -> lowpass 2kHz                       |

### Soundfont Samples

`piano`, `glockenspiel` (and others loaded via sample banks)

---

## Available Scales

Format: `.scale("root:mode")` e.g. `.scale("C4:minor")`

**Common:** major (ionian), minor (aeolian), dorian, phrygian, lydian, mixolydian, harmonic minor, melodic minor,
pentatonic (major pentatonic), minor pentatonic, blues (minor blues), major blues, chromatic, bebop

**Modes:** dorian, lydian, mixolydian (dominant), phrygian, locrian

**Extended:** whole tone, diminished (whole-half diminished), half-whole diminished (dominant diminished), altered (
super locrian), lydian dominant, phrygian dominant (spanish), double harmonic major (gypsy), hungarian minor, hungarian
major, flamenco, enigmatic, persian, oriental, bebop major, bebop minor

**5-note:** ionian pentatonic, ritusen, egyptian, hirajoshi, iwato, in-sen, kumoijoshi, pelog, malkos raga, scriabin

**Other:** augmented, prometheus, whole tone pentatonic, composite blues, neopolitan major, lydian augmented, locrian
major (arabian), ultralocrian, purvi raga, todi raga, kafi raga

---

## Common Patterns & Idioms

### Four-on-the-floor beat

```javascript
stack(
  s("bd bd bd bd"),
  s("~ sd ~ sd"),
  s("hh*8").gain(0.4),
  s("~ ~ ~ oh").gain(0.3)
)
```

### Arpeggiated chord progression

```javascript
n("<0 2 4 7> <0 3 5 7> <0 2 4 6> <0 3 5 8>")
  .scale("C4:minor").fast(2)
  .sound("saw").lpf(1200).adsr(0.01, 0.1, 0.3, 0.2).gain(0.3)
```

### Filtered bass line

```javascript
n("0 ~ 0 3 ~ 0 5 ~").scale("C2:minor")
  .sound("saw").lpf(400).adsr(0.01, 0.2, 0.5, 0.1).gain(0.5)
```

### Ambient drone with LFO modulation

```javascript
n("<0 3 5 7>").scale("C3:minor")
  .sound("supersaw").lpf(sine.range(400, 1200).slow(8))
  .adsr(0.5, 0.5, 0.8, 1.0).legato(2)
  .reverb(wet = 0.3, size = 8).gain(0.2)
```

### Polyrhythmic pattern

```javascript
stack(
  s("bd bd bd").slow(1),       // 3 beats per cycle
  s("hh hh hh hh hh").slow(1) // 5 beats per cycle
)
```

### Euclidean rhythm layers

```javascript
stack(
  s("bd").euclid(3, 8),
  s("sd").euclid(5, 8).gain(0.7),
  s("hh").euclid(7, 16).gain(0.4)
)
```

### Song arrangement with sections

```javascript
let verse = stack(
  n("0 2 4 7").scale("C4:minor").sound("saw").lpf(800).gain(0.3),
  n("0 ~ 0 ~").scale("C2:minor").sound("sine").gain(0.4),
  s("bd sd bd sd"), s("hh*4").gain(0.3)
)

let chorus = stack(
  chord("<Am C F G>").voicing().sound("supersaw").lpf(600).gain(0.25),
  n("0 ~ 0 ~").scale("C2:minor").sound("sine").gain(0.5),
  s("bd sd bd [sd sd]"), s("hh*8").gain(0.3)
)

arrange([8, verse], [8, chorus], [8, verse], [8, chorus])
  .reverb(wet = 0.15, size = 4)
```

### Arranging a song: notes, lines, parts, arrange

For a whole song, prefer parts in an `arrange()` over switching voices on and off with `mute("<1!24 0!8 ...>")`
masks. The masks read as numbers, and every voice keeps its own period: a 4-cycle arpeggio, an 8-cycle melody
and a 38-entry mask drift apart, and a last chord that rings on stretches the song past voices that have
already restarted.

**What `arrange` does:** each segment is queried at its own local time, so **every part starts on its own
cycle 0**. A 4-cycle chord pattern in a part of 4 or 8 cycles always starts on its first chord, whatever
came before. The whole arrangement loops after the sum of the durations, as one piece. A 3-cycle part simply
plays the first three cycles of its patterns; a note that starts in a part keeps ringing past the part's end.

Build it bottom up. An excerpt from Kokon (`builtinsongs/Kokon.kt`, where the rigs `clean`, `bright`, `deep`
and the other parts are defined):

```javascript
// Notes: harmony and melodies, scale degrees without scale or timing
let cocoonArp   = `<[0 4 7 8 9 8 7 4] [-2 2 4 8 9 8 4 2] [-4 0 2 4 5 4 2 0] [-3 1 4 7 8 7 4 1]>`
let cocoonRoots = `<0 5 3 4>`
let melodyOne   = `<[4@4 3 2 1 2] [1@4 ~ 0 1 2] [4@3 5 4@2 2 0] [1@6 ~@2]>`

// Lines: one player, one way of playing, a function from notes to sound. The line picks its octave
// and its default level.
let spin = notes => n(notes).sound(clean).clip(4).gain(0.22).orbit(1)
let sing = notes => n(notes.add(7)).sound(bright).clip(1.5).gain(0.14).orbit(2)
let beat = roots => n(roots.add(-7)).struct("x ~ ~ x ~ ~ x ~").sound(deep).gain(0.325).orbit(3)

// Parts: lines played together. A part may set a line's level for this moment.
let answering  = stack(spin(cocoonArp).gain(0.21), sing(melodyOne))
let quickening = stack(spin(cocoonArp).gain(0.21), sing(melodyTwo), beat(cocoonRoots))

// Song: which part, for how long, in which order. The key is set once.
arrange(
  [8, spinning],
  [4, answering],
  [4, quickening],
  [4, breakingOpen],
  [3, lifting],
  [1, landing],
).scale("d3:minor")
```

- Repeating or stretching a part is a number: `[8, breakingOpen]` plays it twice.
- A part that is one round of the progression (here 4 cycles) keeps every part aligned to the harmony.
- A different harmony is a different notes pattern into the same line (`wings(cocoonPower)`,
  `wings(liftPower)`); a different way of playing is a different line (`wings` tremolo-picks, `strike` hits
  once and lets ring).
- Something that belongs to one moment (a crescendo, a held breath) goes on the line inside that part:
  `spin(cocoonArp).gain("<0.24 0.25 0.26 0.27>")`, `beat(cocoonRoots).mask("<1!3 [1 0]>")`. The pattern is
  local to the part, so a 4-entry `<...>` covers its 4 cycles.
- Song-wide things (`scale`, `analog`, humanising `late(berlin...)`) go on the `arrange(...)` once, and the
  `master(...)` sits next to it in a `stack`.
- Checking by render: a loop is round when a render longer than the song shows cycle N (the song's length)
  matching cycle 0.

### Timed layer entry with filterWhen

```javascript
stack(
  s("bd sd bd sd"),
  s("hh*8").gain(0.3).filterWhen(x => x >= 4),           // enters at cycle 4
  note("c3 e3 g3 c4").s("saw").gain(0.2)
    .filterWhen(x => x >= 8),                              // enters at cycle 8
  chord("<Am C F G>").voicing().s("supersaw").gain(0.15)
    .filterWhen(x => x >= 16)                              // enters at cycle 16
).reverb(wet = 0.1, size = 5)
```

### Delay synced to tempo

```javascript
note("c4 ~ e4 ~").sound("pluck")
  .delay(wet = 0.3, time = beats(0.5), feedback = 0.4)   // an eighth note at any tempo
```

---

## Complete Annotated Example

### "Drunken Synthlor" -- Folk-style melody with custom pluck

```javascript
import * from "stdlib"
import * from "sprudel"

stack(
  // Melody: Karplus-Strong plucked string with tremolo
  n(`<[8@2 8 8 8@2 8 8] [8 4  6  8]  [7@2 7 7 7@2 7 7] [7 3  5  7]
      [8@2 8 8 8@2 8 8] [8 9 10 11]  [10 8 7 5]        [4@2 4@2  ]
  >`).sndPluck(0.999, 0.8)      // high feedback + brightness pluck
    .clip(0.8)                    // note duration 80%
    .scale("c3:dorian")          // dorian mode for folk feel
    .gain(0.8)
    .lpf("2000")                 // gentle lowpass
    .lpf(attack = 0.01, decay = 0.1, sustain = 0.2, release = 0.1) // filter envelope
    .tremolo(depth = 0.33, rate = 8, shape = "sine") // 8 Hz tremolo
    .analog(1)                   // warm analog drift

  // Bass: pluck + triangle layered
  , n(`<[8 15 13 15]!2  [7 14 10 14]!2
        [8 15 13 15]!2  [7 14 10 14] [6 7 8 9]
>`).scale("C1:minor")
    .sound("pluck")
    .adsr(0.01, 0.2, 0.5, 0.2)
    .clip(0.5).distort(0.1).onepole(20257).gain(0.2)
    .superimpose(x => x.sound("tri"))  // layer triangle on top

  // Hi-hats
  , s("hh!8").adsr(0.01, 0.1, 0.1, 1.0).gain(0.8)

  // Kick-snare
  , s("<[[bd sd]!2]!8>").adsr(0.02, 0.1, 0.7, 1.0).gain(0.75)
)
  .reverb(wet = 0.02, size = 3)  // subtle reverb
```
