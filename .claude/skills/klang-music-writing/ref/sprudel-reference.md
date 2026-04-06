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
    .adsr("0.01:0.1:0.5:0.2").gain(0.25),
  chord("<Am C F G>").voicing()
    .sound("supersaw").lpf(500)
    .adsr("0.1:0.3:0.7:0.5").gain(0.2),
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("sine").lpf(300).gain(0.4)
)
```

### A full track with custom instruments

```javascript
import * from "stdlib"
import * from "sprudel"

let koto = Osc.register("koto", Osc.pluck()
  .plus(Osc.sine().detune(12).mul(0.1).adsr(0.001, 0.3, 0.0, 0.05))
  .lowpass(Osc.constant(5000).plus(Osc.constant(3000).adsr(0.001, 0.3, 0.0, 0.05)))
  .highpass(200)
)

let kick = Osc.register("kick", Osc.sine()
  .pitchEnvelope(24, 0.001, 0.04)
  .adsr(0.001, 0.2, 0.0, 0.02)
)

stack(
  note("a4 b4 c5 b4 a4 [b4 a4] f4@2").sound(koto)
    .legato(0.8).slow(4),
  note("a1 ~ ~ ~").sound(kick).gain(0.8),
  sound("~ ~ cp ~").gain(0.4),
  sound("hh*8").gain(0.3)
).room(0.2).rsize(5)
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

---

## Entry Points

| Function                | Description                 | Example                              |
|-------------------------|-----------------------------|--------------------------------------|
| `sound(pat)` / `s(pat)` | Play samples/synths by name | `s("bd sd hh cp")`                   |
| `note(pat)`             | Play by note name           | `note("c3 e3 g3 c4")`                |
| `n(pat)`                | Play by scale index         | `n("0 2 4 7").scale("C4:major")`     |
| `chord(pat)`            | Play chord names            | `chord("<Am C F G>")`                |
| `stack(p1, p2, ...)`    | Layer simultaneously        | `stack(s("bd sd"), s("hh*4"))`       |
| `cat(p1, p2, ...)`      | Sequence across cycles      | `cat(s("bd sd"), s("cp cp"))`        |
| `fastcat(p1, p2, ...)`  | Sequence within one cycle   | `fastcat(s("bd"), s("sd"))`          |
| `arrange([n,p], ...)`   | Timed sections              | `arrange([4, melody], [2, silence])` |
| `silence` / `rest`      | Empty pattern               | `arrange([4, melody], [4, silence])` |
| `pure(value)`           | Constant pattern            | `pure(1/8).div(cps)`                 |
| `seq(values...)`        | Sequence from values        | `seq("c3", "e3", "g3")`              |

---

## Function Reference

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
| `note(name)`            |         | Set note name                 | `note("c3 e3 g3")`                              |
| `n(index)`              |         | Set scale index               | `n("0 2 4 7").scale("C4:major")`                |
| `scale(name)`           |         | Set scale context             | `n("0 1 2 3").scale("C4:minor")`                |
| `transpose(semi)`       |         | Shift by semitones            | `note("c3").transpose(12)`                      |
| `scaleTranspose(steps)` |         | Shift by scale degrees        | `n("0 2 4").scale("C:major").scaleTranspose(1)` |
| `chord(name)`           |         | Set chord name                | `chord("<Am C F G>")`                           |
| `voicing()`             |         | Expand chord to voiced notes  | `chord("Am").voicing()`                         |
| `rootNotes()`           |         | Extract chord root notes      | `chord("Am C").rootNotes()`                     |
| `freq(hz)`              |         | Set frequency in Hz           | `freq("440 880")`                               |
| `accelerate(amt)`       |         | Pitch ramp during playback    | `s("cr").accelerate(2)`                         |
| `vibrato(rate, depth)`  | `vib`   | Pitch vibrato (sprudel-level) | `note("c3").vibrato("5:0.01")`                  |

### Dynamics & Routing

| Function         | Aliases    | Description                    | Example                               |
|------------------|------------|--------------------------------|---------------------------------------|
| `gain(amt)`      |            | Volume (0-1+)                  | `s("bd").gain(0.8)`                   |
| `velocity(amt)`  | `vel`      | Velocity (0-1)                 | `note("c3").velocity(0.5)`            |
| `pan(pos)`       |            | Stereo (0=L, 0.5=C, 1=R)       | `s("hh").pan(sine)`                   |
| `orbit(n)`       | `cylinder` | Effect send channel (0-3)      | `note("c3").orbit(1).room(0.5)`       |
| `adsr(params)`   |            | Amplitude envelope             | `note("c3").adsr("0.01:0.2:0.7:0.5")` |
| `attack(sec)`    |            | Envelope attack                | `note("c3").attack(0.01)`             |
| `decay(sec)`     |            | Envelope decay                 | `note("c3").decay(0.2)`               |
| `sustain(level)` |            | Envelope sustain level         | `note("c3").sustain(0.7)`             |
| `release(sec)`   |            | Envelope release               | `note("c3").release(0.5)`             |
| `legato(amt)`    | `clip`     | Note duration scaling          | `note("c3").legato(1.5)`              |
| `postgain(amt)`  |            | Post-processing gain           | `s("bd").distort(3).postgain(0.1)`    |
| `spread(value)`  |            | Distribute value across events | `s("bd sd").spread(1.0)`              |

### Sound Selection

| Function           | Aliases | Description                 | Example                                |
|--------------------|---------|-----------------------------|----------------------------------------|
| `sound(name)`      | `s`     | Set sound/instrument        | `sound("bd sd hh cp")`                 |
| `analog(amt)`      |         | Analog oscillator drift     | `note("c3").s("supersaw").analog(0.2)` |
| `unison(n)`        | `uni`   | Voice doubling              | `note("c3").s("saw").unison(6)`        |
| `detune(amt)`      |         | Pitch spread between voices | `note("c3").s("supersaw").detune(0.1)` |
| `density(amt)`     | `d`     | Oscillator density (noise)  | `note("a").s("dust").density(40)`      |
| `warmth(amt)`      |         | Analog warmth amount        | `s("bd").distort(3).warmth(0.3)`       |
| `sndPluck(params)` |         | Karplus-Strong shorthand    | `note("c3").sndPluck("0.999:0.8")`     |
| `sndSuperSaw()`    |         | Super-saw shorthand         | `note("c3").sndSuperSaw()`             |
| `bank(name)`       |         | Sample bank                 | `s("bd").bank("RolandTR808")`          |

### Filters

All filters accept pattern values and have envelope variants (`lpenv`, `lpadsr`, `lpattack`, `lpdecay`, `lpsustain`,
`lprelease` etc.)

| Function         | Aliases               | Description                | Example                                                      |
|------------------|-----------------------|----------------------------|--------------------------------------------------------------|
| `lpf(freq)`      | `cutoff`, `ctf`, `lp` | Lowpass filter cutoff (Hz) | `note("c3").s("saw").lpf(800)`                               |
| `resonance(q)`   | `lpq`, `res`          | Lowpass resonance/Q        | `note("c3").lpf(400).resonance(5)`                           |
| `lpenv(depth)`   | `lpe`                 | Lowpass envelope depth     | `note("c3").lpf(200).lpenv(3000)`                            |
| `lpadsr(params)` |                       | LP envelope ADSR           | `note("c3").lpf(200).lpenv(3000).lpadsr("0.01:0.3:0.5:0.5")` |
| `hpf(freq)`      | `hcutoff`, `hp`       | Highpass filter cutoff     | `s("bd").hpf(200)`                                           |
| `hresonance(q)`  | `hpq`, `hres`         | Highpass resonance         | `s("bd").hpf(200).hresonance(2)`                             |
| `hpenv(depth)`   | `hpe`                 | Highpass envelope depth    | `note("c3").hpf(100).hpenv(1000)`                            |
| `hpadsr(params)` |                       | HP envelope ADSR           | `note("c3").hpf(100).hpenv(1000).hpadsr("0.01:0.2:0.3:0.5")` |
| `bandf(freq)`    | `bpf`, `bp`           | Bandpass center freq       | `s("sd").bandf(1000)`                                        |
| `bandq(q)`       | `bpq`                 | Bandpass Q                 | `s("sd").bandf(1000).bandq(5)`                               |
| `bpenv(depth)`   | `bpe`                 | Bandpass envelope depth    | `note("c3").bpf(200).bpenv(4000)`                            |
| `bpadsr(params)` |                       | BP envelope ADSR           | `note("c3").bpf(200).bpenv(4000).bpadsr("0.01:0.3:0.5:0.5")` |
| `notchf(freq)`   |                       | Notch (band-reject) freq   | `s("sd").notchf(1000)`                                       |
| `notchq(q)`      | `nresonance`          | Notch Q                    | `s("sd").notchf(1000).notchq(2)`                             |

### Effects

| Function              | Aliases               | Description                            | Example                                   |
|-----------------------|-----------------------|----------------------------------------|-------------------------------------------|
| `room(mix)`           | `reverb`              | Reverb amount (0-1)                    | `note("c3").room(0.3)`                    |
| `roomsize(size)`      | `rsize`, `sz`, `size` | Reverb room size                       | `note("c3").room(0.3).rsize(5)`           |
| `roomdim(dim)`        | `rdim`                | Reverb damping/dimension               | `note("c3").room(0.3).rdim(0.5)`          |
| `roomfade(fade)`      | `rfade`               | Reverb fade time                       | `note("c3").room(0.3).rfade(2)`           |
| `roomlp(freq)`        | `rlp`                 | Reverb lowpass                         | `note("c3").room(0.3).rlp(3000)`          |
| `delay(mix)`          |                       | Delay amount (0-1)                     | `s("sd").delay(0.5)`                      |
| `delaytime(time)`     |                       | Delay time in cycles                   | `s("sd").delay(0.5).delaytime(0.33)`      |
| `delayfeedback(fb)`   | `delayfb`, `dfb`      | Delay feedback                         | `s("sd").delay(0.5).delayfeedback(0.3)`   |
| `distort(amt)`        | `dist`                | Distortion amount                      | `s("bd").distort(0.5)`                    |
| `distortshape(shape)` | `distshape`, `dshape` | Distortion shape                       | `s("bd").distort(2).distshape("fold")`    |
| `crush(bits)`         |                       | Bitcrusher                             | `s("hh").crush(8)`                        |
| `coarse(amt)`         |                       | Sample-rate reduction                  | `note("c3").s("saw").coarse(3)`           |
| `phaser(params)`      | `ph`                  | Phaser effect                          | `note("c3").phaser(1)`                    |
| `phaserdepth(d)`      | `phasdp`, `phd`       | Phaser depth                           | `note("c3").phaser(1).phaserdepth(0.5)`   |
| `phasercenter(hz)`    | `phc`                 | Phaser center freq                     | `note("c3").phaser(1).phc(1000)`          |
| `phasersweep(hz)`     | `phs`                 | Phaser sweep range                     | `note("c3").phaser(1).phs(500)`           |
| `tremolo(params)`     |                       | Tremolo rate                           | `note("c3").tremolo(4)`                   |
| `tremolodepth(d)`     | `tremdepth`           | Tremolo depth                          | `note("c3").tremolo(4).tremolodepth(0.5)` |
| `tremolosync(n)`      | `tremsync`            | Sync tremolo to cycle                  | `note("c3").tremolosync(8)`               |
| `tremoloshape(s)`     | `tremshape`           | Tremolo LFO shape                      | `note("c3").tremolo(4).tremshape("sine")` |
| `compressor(params)`  | `comp`                | Compressor (thresh:ratio:knee:att:rel) | `s("bd sd").comp("-20:4:3:0.01:0.3")`     |
| `iresponse(path)`     | `ir`                  | Impulse response convolution           | `note("c3").ir("hall.wav")`               |

Distortion shapes: `soft` (default/tanh), `hard`, `gentle`, `cubic`, `diode`, `fold`, `chebyshev`, `rectify`, `exp`

### FM Synthesis (via pattern params)

| Function           | Aliases | Description          | Example                                |
|--------------------|---------|----------------------|----------------------------------------|
| `fmh(ratio)`       |         | FM harmonicity ratio | `note("c3").s("sine").fmh(2)`          |
| `fmenv(depth)`     | `fmmod` | FM modulation depth  | `note("c3").s("sine").fmenv(500)`      |
| `fmattack(sec)`    | `fmatt` | FM envelope attack   | `note("c3").fmenv(500).fmattack(0.01)` |
| `fmdecay(sec)`     | `fmdec` | FM envelope decay    | `note("c3").fmenv(500).fmdecay(0.1)`   |
| `fmsustain(level)` | `fmsus` | FM envelope sustain  | `note("c3").fmenv(500).fmsustain(0.0)` |

### Pitch Envelope (via pattern params)

| Function          | Aliases | Description          | Example                             |
|-------------------|---------|----------------------|-------------------------------------|
| `penv(semitones)` | `pamt`  | Pitch envelope depth | `note("c4").penv(12)`               |
| `pattack(sec)`    | `patt`  | Pitch env attack     | `note("c4").penv(12).pattack(0.1)`  |
| `pdecay(sec)`     | `pdec`  | Pitch env decay      | `note("c4").penv(12).pdecay(0.2)`   |
| `prelease(sec)`   | `prel`  | Pitch env release    | `note("c4").penv(12).prelease(0.3)` |
| `pcurve(shape)`   | `pcrv`  | Pitch env curve      | `note("c4").penv(12).pcurve(2)`     |
| `panchor(point)`  | `panc`  | Pitch env anchor     | `note("c4").penv(12).panchor(0)`    |

### Sampling

| Function         | Aliases | Description               | Example                            |
|------------------|---------|---------------------------|------------------------------------|
| `begin(pos)`     |         | Start position (0-1)      | `s("breaks").begin(0.5)`           |
| `end(pos)`       |         | End position (0-1)        | `s("breaks").end(0.5)`             |
| `speed(factor)`  |         | Playback speed            | `s("breaks").speed(0.5)`           |
| `cut(group)`     |         | Choke group               | `s("hh*4").cut(1)`                 |
| `loop(flag)`     |         | Enable looping            | `s("pad").loop(1)`                 |
| `loopAt(cycles)` |         | Fit sample to n cycles    | `s("breaks").loopAt(1)`            |
| `loopBegin(pos)` | `loopb` | Loop start (0-1)          | `s("pad").loop(1).loopBegin(0.25)` |
| `loopEnd(pos)`   | `loope` | Loop end (0-1)            | `s("pad").loop(1).loopEnd(0.75)`   |
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
| `add(n)`     | Add to values        | `n("0 2").add(5)`          |
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

Top-level signals that produce continuous values. Use `.range(min, max)` to scale.

### Oscillators (0..1 unipolar, add `2` suffix for -1..1 bipolar)

`sine`, `sine2`, `cosine`, `cosine2`, `saw`, `saw2`, `isaw`, `isaw2`, `tri`, `tri2`, `itri`, `itri2`, `square`,
`square2`

### Noise

`perlin`, `perlin2`, `berlin`, `berlin2`, `rand`, `rand2`, `randCycle`

### Utility

`time` (cycle counter), `cps` (cycles/sec), `rpm` (CPS*60), `bpm` (CPS*240)

### Range Mapping

| Function                  | Input | Description                      |
|---------------------------|-------|----------------------------------|
| `.range(min, max)`        | 0..1  | Linear scale                     | 
| `.rangex(min, max)`       | 0..1  | Exponential (for frequencies)    |
| `.range2(min, max)`       | -1..1 | Bipolar linear scale             |
| `.toBipolar()`            | 0..1  | Convert to -1..1                 |
| `.fromBipolar()`          | -1..1 | Convert to 0..1                  |
| `.segment(n)` / `.seg(n)` | any   | Sample-and-hold at n steps/cycle |

### Usage

```javascript
// Sweeping filter
note("c3").s("saw").lpf(sine.range(200, 2000).slow(4))

// Random panning
s("hh*8").pan(rand)

// Tempo-synced delay
s("sd").delay(0.5).delaytime(pure(1/8).div(cps))

// Organic modulation
note("c3").s("supersaw").detune(perlin.range(0.0, 0.3).slow(16))
```

---

## Built-in Sounds

### Drum Samples (via `sound()` / `s()`)

`bd` (bass drum), `sd` (snare), `hh` (closed hi-hat), `oh` (open hi-hat), `cp` (clap), `cr` (crash), `rd` (ride), `lt` (
low tom), `mt` (mid tom), `ht` (high tom), `rim` (rimshot), `ch` (closed hat)

Use `:N` for variants: `sd:3`, `bd:2`

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
  .sound("saw").lpf(1200).adsr("0.01:0.1:0.3:0.2").gain(0.3)
```

### Filtered bass line

```javascript
n("0 ~ 0 3 ~ 0 5 ~").scale("C2:minor")
  .sound("saw").lpf(400).adsr("0.01:0.2:0.5:0.1").gain(0.5)
```

### Ambient drone with LFO modulation

```javascript
n("<0 3 5 7>").scale("C3:minor")
  .sound("supersaw").lpf(sine.range(400, 1200).slow(8))
  .adsr("0.5:0.5:0.8:1.0").legato(2)
  .room(0.3).rsize(8).gain(0.2)
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
  .room(0.15).rsize(4)
```

### Timed layer entry with filterWhen

```javascript
stack(
  s("bd sd bd sd"),
  s("hh*8").gain(0.3).filterWhen(x => x >= 4),           // enters at cycle 4
  note("c3 e3 g3 c4").s("saw").gain(0.2)
    .filterWhen(x => x >= 8),                              // enters at cycle 8
  chord("<Am C F G>").voicing().s("supersaw").gain(0.15)
    .filterWhen(x => x >= 16)                              // enters at cycle 16
).room(0.1).rsize(5)
```

### Delay synced to tempo

```javascript
note("c4 ~ e4 ~").sound("pluck")
  .delay(0.3).delaytime(pure(1/8).div(cps)).delayfeedback(0.4)
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
  >`).sndPluck("0.999:0.8")      // high decay + brightness pluck
    .clip(0.8)                    // note duration 80%
    .scale("c3:dorian")          // dorian mode for folk feel
    .gain(0.8)
    .lpf("2000")                 // gentle lowpass
    .lpadsr("0.01:0.1:0.2:0.1") // filter envelope
    .tremolosync(8)              // tremolo synced to 8 per cycle
    .tremolodepth(0.33)
    .tremoloshape("sine")
    .analog(1)                   // warm analog drift

  // Bass: pluck + triangle layered
  , n(`<[8 15 13 15]!2  [7 14 10 14]!2
        [8 15 13 15]!2  [7 14 10 14] [6 7 8 9]
>`).scale("C1:minor")
    .sound("pluck")
    .adsr("0.01:0.2:0.5:0.2")
    .clip(0.5).distort(0.1).warmth(0.2).postgain(0.2)
    .superimpose(x => x.sound("tri"))  // layer triangle on top

  // Hi-hats
  , s("hh!8").adsr("0.01:0.1:0.1:1.0").gain(0.8)

  // Kick-snare
  , s("<[[bd sd]!2]!8>").adsr("0.02:0.1:0.7:1.0").gain(0.75)
)
  .room(0.02).rsize(3)  // subtle room reverb
```
