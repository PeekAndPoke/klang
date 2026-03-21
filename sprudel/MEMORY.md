# Sprudel — Memory

## Current Status

- **Features**: ~263 / 303 implemented (~87%)
- **Tests**: All JVM tests passing ✅
- **KDoc**: All `@SprudelDsl` items fully documented across all `lang_*.kt` files ✅

## Recent Work (2026-02)

- Full KDoc pass on all `lang_*.kt` files
- `@sample` replaced with fenced ` ```KlangScript ``` ` blocks (348 occurrences)
- KSP: `MethodTooLargeException` fixed by chunking generated map; `@alias` tag support added
- `press()` / `pressBy()` implemented with `_innerJoin` control pattern support
- `SprudelDocsPage` smart search (`category:`, `tag:`, `function:` prefixes + logical AND)

## Lessons Learned

**`_innerJoin` is mandatory** for any DSL function accepting pattern arguments — static values work
without it, but control patterns (e.g. `pressBy("<0 0.5>")`) silently break without it.

**Test across ≥12 cycles** — timing bugs compound and only surface after several cycles.

**`whole` is always non-nullable** — ignore any old comments suggesting otherwise.

**Step-based, not cycle-based** — `take(2)` takes 2 steps (events), not 2 cycles. Get this wrong
and all time-manipulation functions produce incorrect output.

**`repeatCycles(n)` repeats each cycle n times** (not truncation). Source cycle index =
`floor(output_cycle / n)`. E.g. `slowcat("a","b").repeatCycles(2)` → a a b b a a b b …

**`extend(n)` = `fast(n)`** (speeds up). It is NOT an alias for `slow()`.

**`iter(n)` is implemented via slowcat** of time-shifted patterns, not a custom pattern class:

```kotlin
val patterns = (0 until n).map { i ->
    val shift = i.toRational() / n.toRational()
    TimeShiftPattern.static(source, -shift)
}
return applyCat(patterns)
```

---

## Completed Features

### Factory Functions

- `stepcat()` / `timeCat()` / `timecat()` / `s_cat()`
- `within(start, end, transform)`
- `chunk(n, transform)` / `slowchunk()` / `slowChunk()`
- `echo(times, delay, decay)` / `stut()`
- `echoWith(times, delay, transform)` / `stutWith()` / `stutwith()` / `echowith()`

### Time Manipulation

- `pace(n)` / `steps(n)`
- `take(n)`, `drop(n)`, `extend(n)`
- `iter(n)`, `iterBack(n)`
- `repeatCycles(n)`

### Mini-Notation

- `:n` sample number, `-` rest, `@n` elongation, `!n` replication
- `?` random removal, `|` random choice
- `(pulses, steps)` / `(pulses, steps, rotation)` Euclidean rhythm
- `euclidish()` / `eish()`
- Mini-notation parser cache

### Pattern Creation

- `cat()`, `seq()`, `mini()`, `stack()`, `arrange()`
- `polyrhythm()`, `polymeter()`, `polymeterSteps()`
- `fastcat()`, `slowcat()`, `slowcatPrime()`
- `stackLeft()`, `stackRight()`, `stackCentre()`, `stackBy()`
- `run(n)`, `binary(n)`, `binaryN()`, `binaryL()`, `binaryNL()`
- `sequenceP()`, `pure(value)`, `silence`, `rest`, `nothing`

### Time Modification

- `fast()` / `density()`, `slow()` / `sparsity()`
- `early()`, `late()`, `rev()`
- `euclid()`, `euclidRot()` / `euclidrot`, `bjork()`
- `euclidLegato()`, `euclidLegatoRot()`
- `compress()`, `focus()`, `zoom()`
- `gap()`, `fastGap()` / `densityGap`
- `swingBy()`, `swing()`
- `ply()`, `plyWith()`, `plyForEach()`
- `hurry()`
- `loopAt()`, `loopAtCps()`

### Pattern Transformation

- `revv()`, `struct()`, `structAll()`, `mask()`, `maskAll()`
- `superimpose()`, `layer()`, `apply()`
- `jux()`, `juxBy()`
- `off()`, `within()`, `applyN()`, `when()`
- `brak()`, `inv()` / `invert()`

### Pattern Operations (Medium Priority)

- `palindrome()`, `linger()`, `ribbon()` / `rib`
- `inside()`, `outside()`
- `press()`, `pressBy()`
- `fastchunk()` / `fastChunk`, `chunkInto()` / `chunkinto`, `chunkBackInto()` / `chunkbackinto`, `chunkBack()` /
  `chunkback`

### Pattern Picking & Selection

- `chooseWith()`, `chooseInWith()`, `choose()`, `chooseOut()`, `chooseIn()`, `choose2()`
- `chooseCycles()`, `randcat()`
- `wchoose()`, `wchooseCycles()`, `wrandcat()`
- `pick()`, `pickmod()`, `pickF()`, `pickmodF()`
- `pickOut()`, `pickmodOut()`
- `pickRestart()`, `pickmodRestart()`
- `pickReset()`, `pickmodReset()`
- `inhabit()` / `pickSqueeze()`, `inhabitmod()` / `pickmodSqueeze()`
- `squeeze()`, `bite()`

### Tonal Functions

- `note()`, `n()`, `freq()`, `scale()`, `sound()` / `s()`, `bank()`
- `transpose()`, `scaleTranspose()`
- `chord()`, `voicing()`, `rootNotes()`
- 112 chord types, all 90 Strudel chord notations

### Arithmetic & Math

- `add()`, `sub()`, `mul()`, `div()`, `mod()`, `pow()`, `log2()`
- `round()`, `floor()`, `ceil()`

### Bitwise Operators

- `band()`, `bor()`, `bxor()`, `blshift()`, `brshift()`

### Comparison & Logic

- `lt()`, `gt()`, `lte()`, `gte()`, `eq()`, `eqt()`, `ne()`, `net()`
- `and()`, `or()`

### Audio Effects — Filters

- `lpf()` / `cutoff` / `ctf` / `lp`, `lpq()` / `resonance` / `res`
- `hpf()` / `hp` / `hcutoff`, `hpq()` / `hresonance` / `hres`
- `bpf()` / `bandf` / `bp`, `bpq()` / `bandq`
- `notchf()`, `nresonance()` / `nres`
- `vowel()`

### Audio Effects — Filter Envelopes

- LP: `lpattack()` / `lpa`, `lpdecay()` / `lpd`, `lpsustain()` / `lps`, `lprelease()` / `lpr`, `lpenv()` / `lpe`
- HP: `hpattack()` / `hpa`, `hpdecay()` / `hpd`, `hpsustain()` / `hps`, `hprelease()` / `hpr`, `hpenv()` / `hpe`
- BP: `bpattack()` / `bpa`, `bpdecay()` / `bpd`, `bpsustain()` / `bps`, `bprelease()` / `bpr`, `bpenv()` / `bpe`
- Notch (Klang extension): `nfattack()` / `nfa`, `nfdecay()` / `nfd`, `nfsustain()` / `nfs`, `nfrelease()` / `nfr`,
  `nfenv()` / `nfe`
- Filter envelope audio engine integration complete

### Audio Effects — Pitch Envelope

- `pattack()` / `patt`, `pdecay()` / `pdec`, `prelease()` / `prel`
- `penv()` / `pamt`, `pcurve()` / `pcrv`, `panchor()` / `panc`

### Audio Effects — Waveshaping / Distortion

- `crush()`, `coarse()`, `distort()` / `dist`

### Audio Effects — Tremolo / AM

- `tremolosync()` / `tremsync`, `tremolodepth()` / `tremdepth`
- `tremoloskew()` / `tremskew`, `tremolophase()` / `tremphase`
- `tremoloshape()` / `tremshape`

### Audio Effects — Dynamics & Panning

- `velocity()`, `postgain()`, `compressor()`
- `jux()`, `juxBy()` / `juxby`

### Audio Effects — Reverb

- `room()`, `roomsize()` / `rsize` / `sz` / `size`
- `roomfade()` / `rfade`, `roomlp()` / `rlp`, `roomdim()` / `rdim`, `iresponse()` / `ir`

### Audio Effects — Delay

- `delay()`, `delaytime()`, `delayfeedback()` / `delayfb` / `dfb`

### Audio Effects — Phaser

- `phaser()` / `ph`, `phaserdepth()` / `phd` / `phasdp`
- `phasercenter()` / `phc`, `phasersweep()` / `phs`

### Audio Effects — Duck / Sidechain

- `duckorbit()` / `duck`, `duckattack()` / `duckatt`, `duckdepth()`

### Audio Effects — Other

- `orbit()` / `o`

### Sample Manipulation

- `begin()`, `end()`, `speed()`, `loop()`
- `loopBegin()` / `loopb`, `loopEnd()` / `loope`
- `loopAt()`, `loopAtCps()`
- `cut()`, `slice()`, `splice()`

### Continuous Signals

- `steady()`, `signal()`, `time`
- `sine`, `sine2`, `cosine`, `cosine2`
- `saw`, `saw2`, `isaw`, `isaw2`
- `tri`, `tri2`, `itri`, `itri2`
- `square`, `square2`
- `perlin`, `perlin2`, `berlin`, `berlin2`
- `range()`, `rangex()`, `range2()`
- `rand`, `rand2`, `irand()`, `randL()`, `brand`, `brandBy()`
- `segment()` / `seg`, `toBipolar()`, `fromBipolar()`
- `round()`, `floor()`, `ceil()`, `ratio()`

### Random & Seeding

- `seed()` / `withSeed()`, `randrun()`, `shuffle()`, `scramble()`

### Conditional & Probabilistic

- `sometimesBy()`, `sometimes()`, `often()`, `rarely()`, `almostAlways()`, `almostNever()`, `always()`, `never()`
- `degradeBy()`, `degrade()`, `undegradeBy()`, `undegrade()`
- `someCyclesBy()`, `someCycles()`
- `firstOf()` / `every()`, `lastOf()`
- `filter()`, `filterWhen()`, `bypass()`

### Noise Generators

- `white` / `whitenoise`, `pink` / `pinknoise`, `brown` / `brownnoise`
- `dust`, `crackle`

### Synthesis Parameters

- `gain()`, `pan()`, `legato()` / `clip()`
- `vibrato()` / `vib`, `vibratoMod()` / `vibmod`
- `accelerate()`, `unison()` / `uni`, `detune()`, `spread()`, `density()` / `d`
- `attack()`, `decay()`, `sustain()`, `release()`, `adsr()`
- `warmth()` (Klang extension)
- `velocity()`, `postgain()`
- FM synthesis: `fmh()`, `fmattack()`, `fmdecay()`, `fmsustain()`, `fmenv()`
- Pitch envelope: `pattack`, `pdecay`, `prelease`, `penv`

### Arithmetic Addons (Non-Strudel)

- `flipSign()`, `oneMinusValue()`, `not()`

### Structural Addons (Non-Strudel)

- `morse(text)`

### System Functions

- `hush()`, `pure()`, `nothing`

### Architectural

- Rational number time coordinates (exact arithmetic, no float drift)
- Mini-notation parser cache
