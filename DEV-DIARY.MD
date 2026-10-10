# Project history

Condensed per-quarter summaries live in [docs/history](docs/history/). This file is the running diary, newest first.

## Diary

### (2026-10-10) Lighter on the phone ... v0.6.2

- fm moved into the tree as classic()'s innermost stage: fm(depth, ratio, attack, decay, sustain, release), the envelope runs per sample, the modulator follows vib, penv and accelerate ... the pitch strip is gone, voices/strip/ with it
- pitchModSemitones, the exponential twin of pitchMod ... the vibrato is composed from it and a sine now, and it got range(from, to) and phase, on sprudel too
- the pitch envelope and accelerate stay nodes for now: composed, they cost about twice as much once a note has settled ... a question for the maintainer
- explicit partials on the sine, partial(ratio, gain, phase), up to 256 ... the snare thud of Kokon and Der Schmetterling is one sine instead of thirteen, a quarter of the memory per hit, and an envelope on a partial's gain plays from its first sample
- the shaper boxed every shaped sample on V8 (2 to 33 KB per block, the fused distort too) ... one loop per shape, now about nothing, bit for bit, and up to half faster
- the house limiter boxed about 2 KB per block whenever anything sounded, one function too big for V8 to inline ... inline now, about nothing, bit for bit
- serial, parallel and bands arrived from the other side of the house, Kokon's master distorts the mids only, and a shared let stays one sound in the browser
- the listening round for the pitch steps is still to come

### (2026-10-10) The pitch moves into the tree ... v0.6.1

- the pitch envelope, the vibrato and accelerate are classic() stages now ... three of the strip's four pitch arms are in the Ignitor tree, fm is next
- accelerate glides to the gate and holds its target through the release ... a zero-length gate holds it from the first frame
- an fm modulator follows the pitch: the ratio stays at exactly 1.5000 under every pitch door, where it wandered from 0.75 to 1.78 ... a pitch node means what it wraps
- one word per knob, again: penv(semitones), vib(rate, semitones), pamt is gone, every analog door takes character ... positional calls are unchanged
- variants takes plain numbers, Ign.variants(400, 1200) ... a null is a clear type error now
- a soloed voice rings to its end, its release tail included
- the four helper merges are done (one did-you-mean, one number format, one home for text positions, one timeout class), and the warehouse panel shows the reverb's failures and dropped networks
- the Katalyst got a distort stage, for buses and the master ... clip, then limit: 2.3 dB more headroom at the same loudness, "the glueing effect is real" ... and now the hats and the kick distort too, a band split is in the plans
- the engine follow-ups gone through with the maintainer: the phone runs Chrome, Kokon stumbles for a few seconds when the drums come in, then settles ... the rest goes one at a time
- the corpus renders bit for bit the same, except Kokon's two landing strikes (at most 2.5 cents) ... that one we meant

### (2026-10-09) Every twin written once ... v0.6.0

- the engine tidy-up, steps 1 to 13: the shaper, the arithmetic laws, the Karplus string, the unison stacks and the fade law each written once ... body and vowel are one effect now
- FilterDef is retired, first-block allocations moved to the voice build, the audio helpers live in one utils/ home
- faster in the browser: the pluck and superpluck 5 to 48 % faster, body and vowel at about 0.6 of their old time
- heard on a real phone: Kokon runs (almost) smooth on the FF4 again ... definitely better than before
- the partial bank went from about 10 KB to about 300 bytes per block, the delay, reverb and phaser together a quarter to a third less ... not clean yet, the rest is written down
- solo fixed: rests stay silent, solo(0.7) does something, solo() is 0.95 and solo(1.0) is silence
- an empty variants() is silence, the shimmer cannot hang anymore, unison voices are capped at 256
- one word per knob: attack, decay, sustain, release, declick, crush(bits), coarse(factor), the pluck's feedback, brown's leak ... 8 slots the script door lacked, analog is a character amount
- the rules at work: 2,557 engine calls name the arguments that could be swapped, the helpers found one utils/ home ... new: units in the KDoc, and a new rule starts provisional
- every step rendered the 18-song corpus bit for bit the same ... the sound changes are the ones we meant: solo, and Der Schmetterling and Kokon touched up by ear

### (2026-10-07) Every voice knows where it is in life ... v0.5.5

- a voice is a small state machine now: pending, sounding, releasing, fading, done ... one table in the Voice KDoc, every cell pinned by a test
- its start, gate and end live in one place, written by one hand ... the "forgot one copy" bug class is gone
- note-off and hard kill are events the voice decides, every voice ends in Done
- a cut fades its victim over 4 ms instead of stopping it dead ... the click is gone, from every delay echo too, and the old cut landed up to 2.65 ms early
- only a note that is still held owns its orbit's effects, the newest one wins ... a release tail no longer holds a new note's reverb hostage
- the zombie is retired: a culled voice is simply done ... and the voice list keeps its start order, without allocating
- Kirschbluete's noise and the Piper's clap, crash and stab got orbits of their own
- found on the way: a solo makes the rests play a sine, and solo(1.0) never fully quiets the rest ... diagnosed, solo() becomes 0.95 and 1.0 means silence
- the engine audited for a tidy future (and a Zig port some day) ... one crash found, an empty variants()

### (2026-10-07) Little things that were wrong ... v0.5.4

- one LFO shared by layers at different pitches ran twice as fast ... now it runs once per block
- a vibrato over a stack of sounds ran once per layer ... Sakura and the flute retuned to what we heard
- a NaN pitch amount no longer freezes the oscillator
- true.toString() works, and hover shows what a name is where it stands
- the browser played every sample through 16 bits for no reason ... floats all the way to the speakers now, 2.5x cheaper per block
- the deploy names a build after itself, a dirty one says so

### (2026-10-06) A lighter bundle ... v0.5.3

- the KSP registration slimmed for every library it runs on ... one call per door, no arity ladders any more
- docs metadata in its own file, each KlangType emitted once, the registrations split per area ... the 2.9 MB generated file is 18 small ones
- the production bundle went from 9.5 MB to 8.1 MB (-14.5 %) ... docs, registries and editor results byte-identical
- the 26 "no cast needed" warnings are gone
- a non-literal door default is a build error now ... the guardrail enforces itself
- a wrong argument type is a clear type error on JVM and in the browser alike ... no more internal ClassCastException
- found on the way: true.toString() fails ... parked as its own task

### (2026-10-06) A phase on every oscillator ... v0.5.2

- every periodic oscillator gets phase(...) ... a fraction of a cycle, 1.25 is 0.25, a moving one is phase modulation
- each builder says where its wave starts at 0 ... and pulze turned out to start high, not low
- the tremolo can swell up instead of dip: range(0, 1) ... the vibrato's range waits for the pitch pipeline
- the snare thud measured against Schroeder phases ... the sign pattern we have is already within 0.15 dB of the best
- Ign.triangle is Ign.tri now, like supertri and sprudel's tri
- found on the way: a shared LFO runs twice as fast across two pitches ... parked as its own task

### (2026-10-05) One range ... v0.5.1

- range(from, to) everywhere ... sprudel, Ignitor, Katalyst, down to the wire and the engine
- the bipolar twins (sine2, saw2, ...), unipolar/bipolar, isaw, itri, choose2, sinOfDay2 are gone ... a smaller surface, nothing lost
- perlin(200, 400) is perlin.range(200, 400) ... every signal is callable now
- sinOfDay is sineOfDay, cosine is literally sine.early(0.25)
- from and as are plain names in KlangScript now ... range(from = 200, to = 400) parses
- rangex on the Ignitor too, the exponential twin
- the 18-song corpus renders bit for bit the same through all of it

### (2026-10-04) Ignitor and Katalyst, one name each ... v0.5.0

- Osc is Ignitor now (short Ign), OscSlot is Ignitor.slot, oscp is ignp ... and katp for the Katalyst
- the old names are gone and guarded, the wire says ignitorParams
- an error inside a native call finally knows where it was called
- the stdlib export block that governed nothing is gone, klangblocks out of every live page

### (2026-10-03) through() and the tremolo rate ... v0.4.5.1 to v0.4.6

- through(): a signal chain written as the list it is ... makeGuitar takes one rig
- the tremolo's sync is rate on every surface
- two glockenspiels by ear, the harmonic one and a metal bar
- the Irish Lament stab opens again like it did before depth became semitones
- sprudel says it out loud: it does not mirror Strudel anymore

### (2026-10-01) Beats

- beats(n) and beatRate(n) ... durations and rates that follow the tempo
- mask lights up the atom that let an event through

### (2026-09-30) The whitepaper, the blog and Kokon ... v0.4.3 to v0.4.5

- the whitepaper rewritten with a reader and a writer agent ... twelve interactive figures
- the blog renders to static HTML, the dev status page gets a blog card
- figures for the three oldest posts ... design lab for the main UI, a bit different every time
- Kokon arranged from parts, one concert hall for the whole band
- one room for both ears: the reverb is stereo
- Der Schmetterling gets a snare that hits the same every time
- both songs at one house level, -14 LUFS

### (2026-09-29) The tremolo is a composition ... v0.4.1 / v0.4.2

- the tremolo is built from the oscillators ... its own LFO is gone
- filter drift is twice the pitch drift ... decided by ear
- the limiter surge: measured, not audible, closed
- memory files say what is true now and stay short ... the history lives next to them
- Kokon: the cocoon before the butterfly

### (2026-09-28) Phase 3 closes ... v0.4.0

- the master is a Katalyst at the output ... the Master DSL retires
- test consolidation: tables for tables' sake and weak rows out, law specs where the gaps were
- echo layers multiply the source gain
- the review ladder: round 3 and later on the strongest model at xhigh, never max

### (2026-09-27) The strip retires ... v0.3.18

- every voice is an Ignitor tree, classic() is the sprudel voice ... the voice strip and the Pipeline DSL are gone
- VoiceData cut down to what someone still reads
- the output runs a Katalyst chain, chain swaps are one state machine
- dev status pages, the whitepaper moves next to the app

### (2026-09-26) classic() everywhere ... v0.3.16 / v0.3.17

- door shapes settled: wet first, knobs on the builder, the limiter says threshold and knee
- one envelope law with six hosts, one adsr shape, one tremolo law, one crush and one distort law
- built-in sounds, samples and the songs' instruments run as classic() trees
- the modulation envelopes default to exponential

### (2026-09-20) Katalyst

- every orbit gets a Katalyst chain: reverb, delay, eq, body, vowel, compressor, duck, phaser
- each stage switches and changes without clicks ... state machines, glides, fades on every edge
- body and vowel on one resonator bank ... the morph between materials was rejected by ear ("an 8-bit laser shot")
- gain is the one level word, velocity folds at the wire, postgain retires
- an orbit stays alive while a voice plays on it

### (2026-09-16) The optimization series ... v0.3.14 / v0.3.15

- a blog series on making the motor fast ... the Fairphone as the target that does not get faster
- the ledger: a per-instrument harness with a graph census
- reverb is one word on every surface, room retired ... delay names end to end
- a delay(...) or reverb(...) call sets every slot, defaults filled at write time

### (2026-09-15) Polynomial sines and a marimba ... v0.3.13

- sine, 2^x and e^x as polynomials in the hot paths ... eleven digits are plenty
- the optimizer's promise is a margin, the Affine node folds block-constant math
- analog drift steps once per block and ramps across it
- zombies: a silent release frees its voice
- Der Schmetterling: the lead is a marimba, the Orchestertrommel far away, guitars as five swappable stages
- a Resources page: videos and links on one shelf

### (2026-09-10) DriftLanes ... v0.3.11 / v0.3.12

- one drift-lane container for every super oscillator ... analogSpread on the whole family
- min and max are clamps on every door: a.max(b) reads "a, at most b"

### (2026-09-09) Intellisense, step 0 ... v0.3.10

- the linter has something to say now
- a minus before a literal binds like Kotlin ... -6.db() is refused
- the generated registration code stops shouting

### (2026-09-08) Number methods ... v0.3.8.2 to v0.3.9

- methods on number literals: 0.5.clamp(0, 1), 3.db() ... mod and rem both
- @scope: every audio setting says where it takes effect, and a lexikon page per term
- two statements on one line need a semicolon
- a Videos page with tutorials found online

### (2026-09-07) Field accessors ... v0.3.8

- every compound is an object with named slots now ... adsr(attack = 0.1) sets, adsr.attack reads
- the per-knob doors are gone: room*, delay*, phaser*, tremolo*, lpq/hpq/bpq, vibratoMod, fm*, duck* ... one word per concept
- @KlangScript.Invoke replaces the "invoke" method spelling
- lang/addons dissolved ... sprudel is not a Strudel port, so "what Strudel lacks" named nothing
- every DSL file is lang_<group>_<subgroup>.kt now, split by what it does

### (2026-09-06) Configure lambdas + a rules register ... v0.3.7.1

- knobs moved onto a builder behind a trailing configure lambda, construction inputs stay on the door
- the standard library split out of the language module (klangscript-libs)
- memory and rules housekeeping ... CLAUDE.md now carries every rule with a hardness (stone / rule / guideline / guardrail)

### (2026-09-03) Resource warehouse + first-run warmup ... v0.3.7

- delay rings, reverb networks, scratch buffers and cylinders are rented from one shelf ... never allocated in render
- a grow migrates the ring and the seam is inaudible; best-fit-up is bounded, and so is the ready wait
- a warmup vocabulary executes every ignitor node kind before the first song
- "is it still ringing" answered in closed form ... no more tail scans on the audio thread
- the motor title toggles to warehouse stats (five review rounds to a clean one)

### (2026-09-02) Soundfont looping

- root cause: the playhead never started at the start
- and the worklet dropped every sample's metadata on reassembly
- a loop is a loop, and the sample is the envelope

### (2026-08-31) Mini-notation tweaks + the test audit ... v0.3.6

- a name in braces finally means something ... the attribute block retires
- the MIDI note stops waiting for a timer, the command ring is drained on send
- audit F5-F20: tests that named a behaviour and checked nothing now check it (two bullets were simply wrong)
- every DSL doc example compiles now ... 46 of them did not

### (2026-08-28) Block-framing invariance audit ... v0.3.5

- a note sounds the same wherever it lands inside a render block ... proven node by node (O, D, W batches)
- no late voices, ever ... the startup race was one whole block
- FM depth per-sample, the delay drains, the phaser and tremolo LFOs are clocks, the fused distort retires
- the voice envelope can be switched off explicitly, never inferred

### (2026-08-29) MIDI

- MIDI MVP ... device panel and the full KeyLab Essential 49 mk3 map
- a held key releases into its own tail
- the bookkeeping moves to the playback, the scheduler gets its name

### (2026-08-25) Renames ... v0.3.4

- Klangmotör is Klangmotor now, plain o ... "too much ego" (Motörhead keeps its umlaut)
- a filter's frequency is freq on every surface; Ignitor.clip() is shape()
- sprudel's bitwise family says what it does

### (2026-08-23) Parameter unification (C series) ... v0.3.2 / v0.3.3

- one default q = 0.707 everywhere, unity-peak bandpass family, WetDryMix as THE wet/dry law
- compound colon-parameters became per-param functions on both doors; the compressor leaves the wire as a colon string
- envelope depth and pitch params say SEMITONES; ADSR curve default is EXP everywhere
- passes on lowpass/highpass ... a staggered Butterworth cascade
- klangblocks removed, strudel-heritage filter aliases deleted ... one surface fewer to keep consistent

### (2026-08-19) Unified EQ + graph optimizer ... v0.3.1.4 / v0.3.1.5

- EqCore: serial LP/HP/BP/NOTCH plus a peaking bell and a parallel raw tap, shape chosen by bake-off
- the optimizer fuses adjacent filter chains into one EqCore pass ... .eq() / .band() / .tap() on both doors
- constant-folding through the ignitor graph, 15 scalar overrides
- seeded per-voice RNG (the coreRandom derivation tree), .tag() semantic event tags
- swallowed runtime errors surfaced, and "no such method" made useful

### (2026-08-15) Tutorials + the motor face ... v0.3.1 to v0.3.1.3

- the generated tutorial corpus wiped ... the curriculum starts again, hand-written and certified
- A1-A7, B4-B10 written and certified; markdown prose blocks, section blocks, an ADSR visual
- MotorBackground reworked, look and feel pass
- highlights get cancelled on stop and on live update

### (2026-08-13) Klangmotör ... v0.3.0

- "Klang Audio Motör" is "Klangmotör" ... one word

### (2026-08-12) Unison phase pool ... v0.2.0

- the super-osc "fundamental lottery" is dead ... banded start-phase selection + per-orbit pools
- onset holes 16% -> 0, never-ring notes 6/120 -> 0, sustain texture untouched
- off by default (pooled onsets lift the low end ~2 dB) ... per-song opt-in via ONE combined .phasePool (...) call
- per-family k-bands, round-robin serving, amortized warm-up ... no audio-thread stalls
- wrote it up as a blog post (docs/blog/2026-08-12-the-fundamental-lottery)

### (2026-08-11) Master limiter lookahead

- the master limiter sees peaks coming now ... clean catches instead of cracked transients
- widened the voice timeline to Double along the way
- songs + by-ear engine tuning all week

### (2026-08-09) White paper

- the klang white paper ... the whole idea in one html (docs/whitepaper/)
- songs, songs, songs

### (2026-08-08) Sample onsets + block-size parity

- sample voices could start up to a block late ... onset quantization fixed
- block size pinned to 128 everywhere ... block size is a SOUND parameter, not a speed knob (offline = live now)

### (2026-08-03) Master-in-pattern + parameter parity

- the master stage rides the pattern now ... master (Master.of (MasterFx.gain (2.5), MasterFx.limiter ()))
- crossfades on swap, works offline, Master.default () switches it back off
- reverb/delay parameter parity ... same word = same meaning on orbit and master buses (the roomSize 10x bug)
- audio-backend audit planned ... the green audio_be suite is an unmeasured net (no mutation checks yet)

### (2026-08-01) Breadcrumbs

- a quiet summer week ... breadcrumbs in docs/tasks/_pick-up-here.md, priorities refreshed
- federated song sharing captured for post-launch ... klang.art:user/song@v1.0

### (2026-07-25) Songs

- songs, credits ... then a summer breather

### (2026-07-10) Releases + merges

- v0.1.3 / v0.1.4 ... songs + strategy notes
- ignitor DSL surface merged (PR #64)

### (2026-07-04) Benchmarks + pipeline perf ... v0.1.2

- pipeline optimizations merged (PR #63) ... benchmark-driven
- runSongBenchmark harness ... real songs, per-block timing
- adsr declick + expK knobs as oscParam slots
- live-update double-voice fix ... grace window + full-identity dedup
- "croon" body material

### (2026-06-30) Noise generators get knobs

- white noise spectral tilt (color) ... darken toward pink/brown, brighten toward blue/violet
- crackle is a *real* chaotic generator now (SuperCollider's Crackle map -> bipolar pops), no longer a dust alias
- perlin/berlin gained fBm (octaves + persistence) ... octaves=1 short-circuits, so the default stays perf-neutral
- brown depth + dust tail/bipolar ... heavy-tailed rare-loud pops = the vinyl-crackle signature
- every default is behavior-preserving AND perf-neutral ... a knob costs only when you turn it
- new Slots are oscParam-addressable; KlangScript named params + sprudel snd* compound strings; vinyl = a documented
  recipe, motör stays raw

### (2026-06-29) Pipeline DSL + typed oscillator subtypes

- EngineDsl -> PipelineDsl rename ... inline .pipeline(dsl) works end to end
- oscillators are typed IgnitorDsl subtypes now (the super-* unison family + saw/ramp/square/triangle)
- chained config methods (.spreadPower / .resetSamples / .sideAtten / ...) on narrowed return types
- a static supertype inferrer so base .lowpass()/.adsr() still resolve on a narrowed osc
- named arguments finally work in KlangScript ... Osc.supersaw(voices = 9, spread = 0.1)

### (2026-06-24) Multi-engine backend

- per-playback PlaybackEngine ... each playback owns its full render state
- no shared scratch -> multi-thread ready
- groundwork for a master/loudness stage + crossfade
- still in progress

### (2026-06-24) Licensing + version stamp

- AGPL v3 + per-file SPDX headers across the tree (tones/ stays MIT)
- build-time version.json -> corner stamp + Motör title tooltip

### (2026-06-23) Engine tuning, songs, optics

- more by-ear tuning
- sprudel pattern rewrites + doc updates
- optics cleanup, a few more songs

### (2026-06-20) Bugfixes

- TimeShiftPattern vs CycleTime ... boundary onset dup
- super-osc initial gain jitter ... exempt the center voice

### (2026-06-19) Housekeeping

- moved agent task notes into docs/tasks + docs/tasks-archive
- renamed History.md -> this dev diary, linked from the README

### (2026-06-17) Engine tuning

- lots of small tweaks, all by ear
- compressor improvements ... killed an un-steady gain jump

### (2026-06-11) Engine DSL

- configurable engines straight from KlangScript (Engine / Stage objects)
- data-driven voice pipeline
- wire-format update ... finally dropped kotlinx.serialization
- credits page, more songs

### (2026-06-09) Body resonator

- parallel band-pass "body" filter ... floor + peaks
- vowel() reworked the same way
- models a passive body, never adds energy

### (2026-06-07) Worklet transport

- custom KSP serializer for the JS audio worklet
- decode 67µs -> ~400ns ... no more kotlinx on the wire

### (2026-06-06) Mutable VoiceData

- sprudel query perf: SprudelVoiceData now mutable, single-owner
- kills the per-event copy overhead
- leaf clone ~17x faster

### (2026-06-03) SuperSaw + oscillator tuning

- supersaw rewrite ... analog flyback shape, no PolyBLEP
- oscillator sounds tuning & performance
- still chasing the "plastic pipe"

### (2026-05-29) CycleTime

- replaced Rational with fixed-point CycleTime
- sprudel query + JS Rational hot path optimizations
- runtime tuning

### (2026-05-27) Fighting the plastic pipe

- warmth hunt
- white noise for analogue drift
- lpf / hpf saturation + randomization
- filter envelope drift
- analog param in the Osc DSL

### (2026-05-22) Ignitor variants

- Osc.variants(osc1, osc2) ... combine ignitors
- Osc.pluck with all params
- klangscript pluck behaviour params

### (2026-05-19) KlangScript code-gen + engine cleanup

- annotation taxonomy redesign, better code-gen
- oscillator registration without binding the env to the player
- better analyzer + code completion
- more distort variants
- audio engine cleanup & performance

### (2026-05-05) Klangbuch

- klangbuch foundations (exportable song parts)

### (2026-05-01) Audio engine deep review

- long correctness + performance review pass
- jvm backend fix ... runs smoothly again

### (2026-04-29) Double arrays everywhere

- ported to Double arrays ... avoid float->double conversion cost on the jvm

### (2026-04-27) Ignitor DSL + click hunt

- more ignitor dsl work
- hunting clicks in the voices

### (2026-04-16) Named parameters

- KlangScript named params (many small steps)
- sprudel dsl named params
- ignitor dsl refactor ... memoization, freqMod at construction time

### (2026-04-13) Pedal engine mode

- "pedal" engine mode
- comm link: send voices in batches
- fixing playback startup hickups

### (2026-04-09) ADSR bugs + tutorials

- adsr bug fixes
- distort oversampling in ignitors
- seed() with control
- songs & tutorials

### (2026-04-06) Recording + tutorials

- music writing + music recording skills
- offline renderer -> wav files
- lexikon, audio pipeline order change, param docs

### (2026-04-01) Motör branding

- branding rename -> Motör
- FreqIgnitor
- KlangScript intellisense step 1
- sound library improvements

### (2026-03-31) Code quality pass

- code review, bug fixes, cleanup
- audio dsp benchmark

### (2026-03-28) Exciter DSL

- composable ExciterDsl
- integration with KlangScript
- klangscript intellisense ... first steps
- super oscis voice bug-fix

### (2026-03-24) KlangScript KSP

- ksp for easy registration of native functions
- audio pipeline code review

### (2026-03-21) sprudel rename

- strudel -> sprudel ... taking our own direction now
- credits page, spectrometer improvements

### (2026-03-20) Signal generators

- audio engine signal gen, 32bit floats
- porting the legacy oscillators
- a few rounds
- tutorial factory

### (2026-03-16) Rational performance

- rational impl performance
- removing Long everywhere (JS pain)
- klangscript AstIndex ... find nodes from code positions
- optics, base pattern class extraction

### (2026-03-15) KlangScript editor + docs

- extracted editor + common ui into klangscript-ui
- docs and repl
- improved error reporting
- new language feature

### (2026-03-13) Strudel UI tools

- per-effect ui tools: reverb, delay, filters, compressor, waveform
- audio be/fe time sync
- code completion

### (2026-03-11) Restructuring

- project restructuring + cleanup
- audio worklet production build
- webui dark theme

### (2026-03-06) Mini notation editor

- mini notation editor tool
- mini notation parser refactoring

### (2026-03-01) KlangScript + klangblocks

- klangscript language features
- klangblocks: drag & drop, css extraction, string editing, realtime highlighting
- project cleanup -> claude skills

### (2026-02-27) Klangblocks MVP

- visual block editor mvp
- editor commons, docs integration

### (2026-02-19) Strudel DSL documentation

- big documentation pass ... KDoc -> KSP doc extraction
- several refinement rounds across all the lang_*.kt files
- pattern mapper chaining audit
- continuous addons: bpm, timeOfDay

### (2026-02-15) Sample library

- sample library page + explorer
- shared sample preloader, KlangPlaybackContext
- one-shot / continuous playback
- klang parser rewrite
- extracted js worklet (safari attempt)

### (2026-02-11) Web UI + solo

- smooth immediate ui updates, real pages
- moved solo into the audio backend
- klangscript modulo / boolean logic

### (2026-02-10) Spectrometer

- browser spectrometer + gauge
- mini notation: allow // comments mid-line
- voices ring out on playback cleanup

### (2026-02-08) FE/BE sync

- fixing fe-be startup sync
- signals bus, playback latency feedback
- solo() / mute() as hush alias (now with control patterns)
- audio backend diagnostics

### (2026-02-06) Oscilloscope

- browser oscilloscope
- web dashboard
- notes from numbers

### (2026-02-04) Fighting rational numbers (again)

- ... fighting ... with rational numbers
- using Rational where it actually helps
- chunk functions (chunkBack, chunkInto)
- press() / pressBy()
- delay line + reverb improvements

### (2026-02-02) FM synthesis + filter unification

- fm synthesis
- audio be filter unification ... clearer pipeline
- limiter + ShortArray for performance
- part/whole refactoring wrapped up
- revv(), applyN(), brak(), invert(), pickF()

### (2026-01-31) Strudel - Part/Whole Refactoring & Verification

**Implementation Phase (Morning):**
- Implemented part/whole structure for StrudelPatternEvent to match JavaScript Strudel behavior
- Added TimeSpan data class with helper methods (shift, scale, clipTo)
- Updated all pattern operations to preserve whole through clipping operations
- Added hasOnset() filter in StrudelPlayback to only play onset events
- Continuous patterns (sine, saw) now use whole = null and always play
- Updated JavaScript interop to extract both part and whole from JS events
- Critical fix: prevents incorrect event playback for clipped patterns
- Build status: ✅ Compiles successfully
- Test status: ✅ 96% passing (2374/2476 tests, 102 failures in timing edge cases)

**Verification Phase (Afternoon):**

- Created comprehensive verification plan for 33 pattern classes and 14 helper methods
- **Phase 1 Verification Completed** - verified 16 pattern classes and 7 helper methods
- Critical fix: Removed default value `= true` from BindPattern clip parameter (user-reported bug)
- Verified patterns: ControlPattern, ChoicePattern, StackPattern, GapPattern, SometimesPattern, RandLPattern,
  MapPattern, ReinterpretPattern, ContextModifierPattern, ContextRangeMapPattern, PropertyOverridePattern, EmptyPattern,
  and 4 others
- Verified helper methods: _bind, _outerJoin, _applyControl, _lift, _liftData, _liftValue, _liftNumericField
- Result: Zero part/whole violations found - all patterns follow correct principles
- Documentation: Updated implementation and verification plan documents with detailed results
- Next: Address 102 failing tests and add comprehensive unit test coverage

### (2026-01-28) Strudel - Audio Effects

- phaser
- tremolo
- vowel()
- custom warmth() for synth voices
- custom morse()

### (2026-01-28) Strudel - Tonal Functions

- tonal functions integration

### (2026-01-28) Klang - Filter Envelopes

- filter envelopes implementation

### (2026-01-27) Strudel - Pattern Refactoring

- major pattern refactoring
- pattern picking functions

### (2026-01-26) Strudel - DSL & Cleanup

- new dsl functions
- pattern picking functions
- pattern code cleanup (createEventList, RandLPattern, FilterPattern, StepsOverridePattern, AlignedPattern, GapPattern,
  ContextRangeMapPattern)
- preps for gc free implementation

### (2026-01-25) Strudel - Pattern Cleanup

- pattern code cleanup (TimeShiftPattern, FastGapPattern, HurryPattern)

### (2026-01-23) Klang Studio

- started Klang Studio
- TempoModifierPattern refactoring

### (2026-01-21) Strudel - Control Patterns

- unifying Pattern and PatternWithControl architecture
- compress(), ply(), fastGap(), hurry(), focus() with control patterns
- fix pan() (left = 0.0, right = 1.0)

### (2026-01-21) Klang - Voice Data Separation

- separating StrudelVoiceData from VoiceData

### (2026-01-21) Klang Notebook

- prototype working

### (2026-01-20) KlangStudio - Live Coding

- live coding editor highlighting working
- KlangScript source location tracking

### (2026-01-19) KlangStudio - Editor

- CodeMirror integration
- KlangScript source location tracking

### (2026-01-18) Klang - Infrastructure

- infra refactor
- KlangPlayer refactoring
- KlangStudio first steps to frontend

### (2026-01-17) KlangPlayer - Performance

- jvm max priority threads for audio backend
- surface refactoring
- Strudel sample manipulation (wip)

### (2026-01-16) Strudel - DSL

- pattern creation functions (polyrhythm, stackBy, stackLeft, stackRight, stackCentre, sequenceP)
- fastcat(), polymeter(), pure(), slowcat()
- control pattern support for many functions (firstOf, lastOf, brandBy, irand, euclidean patterns, segment, bite, slow,
  fast, gap, rev)
- MiniNotationParser cache

### (2026-01-15) Strudel - Euclidean & Binary

- binary(), binaryN(), binaryL(), binaryNL(), euclidish(), eish(), run()
- euclidean rhythm functions (bjork, euclid, euclidRot, euclidLegato, euclidLegatoRot)
- n() now populates soundIndex

### (2026-01-15) Strudel - DSL

- random choice functions

### (2026-01-14) KlangScript

- comparison expressions
- array functions with { ... return x } bodies

### (2026-01-14) Strudel - DSL

- random functions and patterns
- continuous patterns
- filter functions

### (2026-01-13) Strudel - Fights with floating point numbers

- tried to use a full Rational implementation, but failed
- switched back to the fix-point approach with a small hack in SequencePattern

### (2026-01-12) Strudel - DSL

- more strudel dsl functions
- continuous patterns

### (2026-01-11) Strudel - DSL

- more strudel dsl functions

### (2026-01-10) Strudel - Cleanup and refactoring

- streamlining the api surface of all strudel functions
  - making them extension on StrudelPattern
  - making them extension on String
  - making all of this available to KlangScript as well
- Generalized StructurePattern so cover struct(), structAll(), mask(), maskAll(), etc...

### (2026-01-09) Strudel - New features

- compatibility tests
  - we compile patterns with GraalJS and compare the event sequence to the native KlangScript+Strudel result
  - very helpful!
- ensured compatibility of continuous patterns with strudel
- added struct()
- added mask()
- finally implemented n() ... "stranger things" now perfect
  - missing is lpenv, perlin, superimpose

### (2026-01-08) Strudel - New features

- cat() function
- rev() function
- palindrome() function

### (2026-01-08) Strudel - Tests

- tests for all implemented patterns
- fighting with floating point drift
- introduced fixed floating point math -> Rational.kt
- fixed euclidean pattern implementation

### (2026-01-07) Strudel - Using KlangScript as runtime

- porting strudel dsl to the new KlangScript runtime
- some refactoring on KlangScript kotlin interop and object / method registration
- enhancing MiniNotation parser with more features
  - euclidean patterns

### (2026-01-07) KlangScript

- working on KlangScript StdLib
- adding common math functions
- adding common string functions
- adding common list functions
- adding common object functions

### (2026-01-06) KlangScript

- creating a Javascript-ish interpreter language called KlangScript
- making heavy use of Claude-Agent
- parser based on better-parse
- kotlin interop with klang-script
- multiple refactoring and cleanup rounds

### (2026-01-05) Tones

- ported @tonaljs to kotlin into package "tones"
- heavy use of "Junie" for the port and gemini for cleanup and refactorings

### (2026-01-04) Strudel Code Parser

- starting to build the parser, now that many of the strudel functions are implemented

### (2026-01-03) Strudel DSL

- started implementing the Strudel DSL
- figuring out a generic way to "register" all functions
- first implementation of control patterns

### (2026-01-03) Soundfonts

- fixed the "start in the middle of the sound" issue by simplifying the adsr logic and ScheduledVoice
- rewrite of makeVoice() in the VoiceScheduler, by removing "lateFrames" logic
  - result: more straight scheduling math

### (2026-01-02) Soundfonts

- done with the rewrite on the SampleIndex and loading sounds on demand
- TODO: bring back the sound index to generic sounds
- sound index working for soundfonts
- working on understanding how loops and adsr work in soundfonts
- rework of the adsr logic when creating voices
- issues with playing soundfont voices ... "start in the middle of the sound" issue

### (2026-01-01) Soundfonts

- started to add soundfont support
- requires a rewrite of the SampleIndex and how the sample are loaded on demand

### (2025-12-29) Voice / Strudel

- implemented legato / clip

### (2025-12-28) Idea

When the strudel-parsr is ready build an llm tool that:

- you can talk to, to generate pattern
- you can saw things like "make it sound more 8-bit"
- the information is coming from the pattern-function definitions
- do we need a RAG model?

### (2025-12-28) Strudel / Sound engine

- added crush effect
- added coarse effect
- added accelerate / glisando modulation

### (2025-12-28) JS Audio Worklet - smoother audio samples upload to the backed

- Working on splitting the audio samples into smaller chunks
- ok, not the chunk size was the issue
- the issue was using kotlinx.serialization for FloatArray transfer between the
  main JS thread and the AudioWorklet thread
- wrote custom serialization code in class WorkletContract
- no more stutter when sending 32 kb blocks!

### (2025-12-27) JS Audio Worklet - stuttering when uploading samples

- it turns out that sending big chunks to the AudioWorklet leads to stuttering
- we have to send audio samples in smaller chunks
- So for JS the flow will be:
  - event fetcher sends Cmd.Sample.Complete command
  - JsKlangPlayerBackend splits these into multiple Cmd.Sample.Chunk commands
  - VoiceScheduler combines these

### (2025-12-27) General Architecture struggle

- after struggling with the general multiplatform architecture -> rewrite of audio backends
- resulted in much cleaner and easy to read code, fewer indirection
- we now have a platform specific klangPlayer() function that handles all the wiring
- strudelPlayer() is now defined in the commonMain and takes advantage of the platform specific klangPlayer()

### (2025-12-26) Project structure

- Organization into audio-frontend and audio-backend modules
- Strudel has its own module
  - leads to better separation in general
- Splitting StrudelPlayer into separate concerns:
  - StrudelPlayer
  - StrudelPlayerState
  - StrudelEventsFetcher
  - StrudelAudioLoop

### (2025-12-26) Browser backend

- figuring out how to implement the audio-backend in the browser
- Having trouble with KotlinJS compiler bugs getting the AudioWorkletProcessor
  - found hacky workarounds
- Also tried Wasm ... more problems... will try again later, once the JS backend works

### (2025-12-25) Setting up project for Kotlin Multiplatform

- Created a Kotlin Multiplatform project.
- separating thing into a :core and a very thing :dsp module
- Idea arises: Strudel is just one sound event provider
  - with the correct structure we could build e.g. a midi-player on top of the architecture

### (2025-12-25) Strudel: Distortion

- Implemented distortion and added it to the Strudel engine.

### (2025-12-25) Reverb

- Implemented reverb and added it to the Strudel engine.

### (2025-12-25) Stereo Output & Panning

- Implemented stereo output and panning.
- Before sound generation was only mono.

### (2025-12-24) Strudel: Orbits

- implemented orbits in the Strudel engine.
- allows for separate delay lines for multiple voices on different orbits.

### (2025-12-24) Strudel: Delay

- Added first version of a single mono delay line to the Strudel engine.

### (2025-12-24) Strudel: Envelope (Adsr)

- implemented ADSR envelope generation in the Strudel Voices.

### (2025-12-23) Sound sample banks for melodic sounds

- There are percussive and melodic sound banks
    - Percussive example: https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json
    - Melodic example: https://raw.githubusercontent.com/felixroos/dough-samples/main/piano.json

- Thank you @felixroos for the sample files!

- All samples can be played at a pitch / note... added this
- Melodic sound banks have multiple sound files for different pitches
    - add finding the best sound file for a given pitch

### (2025-12-23) Strudel: Added SampleVoice for percussive sounds

- added playback of sound samples in the Strudel engine

### (2025-12-23) Sound sample banks

- started working on sound sample banks
- implemented downloading percussive sound index files
- implemented downloading sound files
- added download caching
- settled on code organization for the sound banks

### (2025-12-23) Refactor and cleanup

- Short refactoring and cleanup session

### (2025-12-23) Did not understand n() vs note()

- fought many hours hunting a bug that does not exist
- n() and note() are different functions in Strudel.
- I used note() and expected it to behave like n()
- The good thing: the js bridge got way simpler in the process

### (2025-12-23) Modulation: Vibrato

- Implemented parameter arrays for frequency modulation.
- Refactored the oscillators to use the parameter arrays.
- Hooked up Strudel Vibrato to the SynthVoice and SampleVoice.

## (2025-12-23) First steps to the Strudel engine

- started working on the Strudel engine
- fetching events (haps) from strudel-js through the GraalVM bridge
- Voice generation
- timed Voice playback
- rendering audio from voices

## (2025-12-22) Oscillator

- more work on oscillators
- first working version for sine, square, saw, triangle, supersaw wave form
- bad performance though

## (2025-12-21) First step in audio synthesis

- First steps to synthesize audio by implementing a simple oscillator.
- Still fighting with the GraalVM bridge to the strudel-js engine.

## (2025-12-20) Started

- Started the project.
- Using GraalVM to run strudel-js (@strudel/core) on the jvm for sound event generation
- Figured out how to run javascript on the jvm
