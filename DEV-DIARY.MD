# Project history

Condensed per-quarter summaries live in [docs/history](docs/history/). This file is the running diary, newest first.

## Diary

### (2026-10-25) Keys with hints ... v0.7.5

- Play / Update and Stop on the song page have keys: Ctrl+Shift+Enter and Ctrl+Shift+. (Cmd on a Mac), and they work while typing in the editor
- hold Ctrl (Cmd) for 300 ms and each key shows next to the button it belongs to, every key in its own small key cap, the signs drawn as sharp icons ... no hint on a disabled button, and the hints sit on a layer of their own that places them so they do not overlap or run off the page
- the editor's keys show in a panel in its corner while Ctrl is held, the bookmarks 0 to 9 as one row ... the editor still handles those keys itself, they move onto the key manager next
- the keys are a table of named actions with a preset per platform, and a user's own keys can sit on top of it, kept in the browser for now ... the page to change them is still to come
- the key manager lives in a new module, workshop, that is meant to move into Kraft (Apache-2.0 like Ultra) ... it depends on Kraft and Ultra only, so the move is a copy, and Klang's own look of the hints stays in Klang
- hover popups and context menus get their dark blue background at last ... a colour was missing from klang.css, so they showed Semantic UI's own
- Ultra and Kraft 0.115.0: a modal answers with a result ... Klang's yes/no question uses Kraft's answer (the first answer stands, a dismissal is a no) and keeps its own dialog for the look (maintainer)
- the stream cleanups (KLANG-00331): the Motor stats, the oscilloscope and the code playback controller follow their inner streams with Ultra's switchMapNotNull, no hand resubscribe is left in Klang ... the warehouse panel drops its 250 ms ticker and redraws only when its snapshot changed
- rough: the page keys still act behind a modal (Ctrl+Shift+Enter plays behind the bookmark question), and the Mac keys are not tried on a Mac yet

### (2026-10-25) A volume knob, and tidier imports ... v0.7.4

- a master volume knob sits left of the three gauges in the editor's Motor ... drag it up or down; it runs on a loudness scale from silence up to the song's own level, a small cyan LED shows where it points, and the browser remembers it
- the gain sits between the worklet and the scope, so the scope and the spectrum follow the volume, and a change glides, so turning the knob does not click ... the one exception (maintainer) to our rule that a frontend gets no DSP of its own: a single gain on the browser's output, the engine and the wire do not learn about it
- the step back to the last edit scrolls its place to the middle of the editor, and any cursor move, by key or by mouse, ends the walk back (KLANG-00322, maintainer, after the hand tests)
- the offline render counts the master chain's latency: a delaying master chain (a lookahead, an oversampled distort) used to lose its last frames at tailSec = 0 ... Kokon and Der Schmetterling get two more blocks of run-out each, peaks unchanged
- an unclosed block comment is a syntax error now ("Unterminated block comment") ... the lexer used to swallow the rest of the text
- imports follow one layout from a shared .editorconfig, the stdlib last and no wildcard import left ... one reorder over 86 files, import lines only, about ten unused imports gone; IntelliJ's Optimize Imports is allowed as the first IDE write action (maintainer)
- the git hooks are installed: they block dashes in Markdown prose and KDoc, control bytes, and a Claude Code commit without its Co-Authored-By trailer ... a check over the last 300 commits flagged 11, all real; a build run by Claude writes to .build-claude/ and leaves the IDE's build/ alone
- the rules register is slimmed from 47 rows to 21, each area's rules moved to that area, and incidents get a file of their own ... the top review rung is one Opus reviewer at max effort (maintainer)
- the engine loses six entry points that only tests called, the specs run through the production ones instead ... the last full check (2026-10-23, before the import reorder and the knob): 18,379 tests green, corpus 3 of 3 frozen and 15 of 15 live songs identical, allocation 80 of 80

### (2026-10-21) Go to definition, back to the last edit, bookmarks ... v0.7.3

- Ctrl+click on a local goes to its declaration and the hover shows its code (KLANG-00321) ... lets, consts, exports and arrow parameters, and the context menu says "Go to definition"
- Ctrl+Shift+Backspace goes back to the last edit (KLANG-00322) ... edits are grouped into places by distance, not by time: an edit within two lines of the newest place replaces it, up to 30 places, and a new edit ends the walk
- bookmarks per file (KLANG-00323): Ctrl+Shift+digit sets one on the cursor's line and asks before it replaces one, Ctrl+digit goes there ... the line number turns cyan with the digit in a small box beside it, bookmarks follow their text through edits, line moves, undo and redo, and the browser keeps them per song
- the digit is read from the key's position, so every layout works ... a German Ctrl+Shift+7 no longer comments the line out
- the bookmark question is a themed yes/no modal: Enter answers yes, Esc or a click beside answers no ... klang.css themes Semantic modals now, which came out white
- the superpluck caches each string's base delay and recomputes it only when its inputs change, the duck and gain take their knobs through holders (KLANG-00290, items 4 and 45; item 13 checked, nothing to change) ... spluck-a reads 0 high rounds in 100 where it read 6, bit for bit on 40 of 40 allocation cases
- every task and plan is a numbered ticket now, KLANG-00001 to KLANG-00320, and the console tools (test-digest, mutate, docs-lint, review-pack, stack-merge, ticket) live in console/tools ... 1,748 references swept, docs/plans/ gone; the first epic is keyboard shortcuts (KLANG-00325)
- the ideas from the editors and IDEs we have worked in are credited, without naming a product ... CREDITS.MD and the Credits page in step
- the build moves to Ultra 0.114.0 (from 0.112.0) ... the full check: 18,359 tests green, corpus 3 of 3 frozen identical, allocation 80 of 80

### (2026-10-20) The editor completes your names ... v0.7.2

- completion offers the song's own names: lets, consts, exports and lambda parameters, the innermost scope first, a shadowed name once ... while the text does not parse, the editor completes from its last good analysis
- inside a call's parentheses completion offers the parameters no argument has filled yet, as `name = `, with the type and the default in the detail ... not after a positional argument (names and positions do not mix), and not inside strings, comments, brackets or braces
- both completion features passed the maintainer's hand tests
- the gliding div ran 1.6 to 1.9 us per block on the JVM in suite order and about 0.3 to 0.5 now (one method per arithmetic operator, each with its own JIT profile) ... E-arith's second V8 mode is gone (it read high in 12 of 30 rounds, in 0 of 38 after), the duck skips an unchanged setting and its budget line goes from 152 to 112, all bit for bit
- two fm boxes on V8 are gone and two stay: the modulator's frequency and the pinned carrier box one number per block each at middle C ... they wait for the FM round with the maintainer
- an SVF coefficient cache was built, measured and not kept: "A Synth Worth Lying For" computes 13 percent fewer static coefficients, but its timing moved 1.9 percent against 3.3 percent for an unaffected control, inside a run-to-run spread of 14 to 50 percent ... the code stays as it was
- the delay docs said a feedback at or above 1.0 self-oscillates ... at exactly 1.0 the echoes keep their level, only above 1.0 do they grow until the cap holds them
- every module's browser tests get a fixed Karma port, 10031 to 10046 ... a project's ChromeHeadless could connect to another project's server, and one run reported two klang suites as its own; the build lock now finds the main checkout's lock from a git worktree
- the full check: 18,284 tests green, corpus 3 of 3 frozen identical, allocation 80 of 80

### (2026-10-19) The playback highlight is back ... v0.7.1

- the code editor's playback highlight works again: webpack 5.108 (with Kotlin 2.4) dropped an import in pixi.js that registers the ticker, so the highlight failed and its queue grew until the page froze ... every pixi.js module counts as side-effectful now, and the CodeMirror bump checklist knows the pitfall
- Ultra 0.112.0 registers pixi's extensions itself, so the webpack rule is removed
- the dev server no longer shows Ktor's dynamic Node import as "Compiled with problems"
- Der Schmetterling retuned (maintainer): feel from 10 to 25, a 4x12 cab with less low-mid boost, a deeper snare cut, fewer unison voices on the guitars, a changed drum pattern
- the full check: 18,252 tests green, corpus 3 of 3 frozen identical, allocation 79 of 80 ... E-arith on V8 read red twice with a round under the line each time, a known tail; alone, it read 4 of 8 rounds high on Ultra 0.112.0 and 3 of 8 on 0.111.0

### (2026-10-18) A newer Kotlin ... v0.7.0

- Kotlin 2.4.21 and the library bumps: Ultra 0.111.0, KSP 2.3.12, coroutines and serialization 1.11.0, Ktor 3.6.0, Kotest 6.2.6, logback 1.6.5, GraalVM 25.0.4, the CodeMirror minors ... Kotlin 2.4 writes the webpack source maps itself, and the yarn lock holds one copy of each CodeMirror and Lezer package
- the new compiler costs about 110 B more per note on V8 ... the allocation check first read red on E-C-n1 (13,146 B against a budget of 13,068), and the budget is 13,210 B per block now (maintainer: "increase the allocation budget slightly"); the JVM is unchanged
- a browser checklist for trying a CodeMirror bump is written down in docs/tasks/in-progress
- Der Schmetterling retuned again (maintainer): pans, levels and a few patterns
- the full check: 18,252 tests green, corpus 3 of 3 frozen identical, allocation 80 of 80

### (2026-10-18) Heard by ear ... v0.6.9

- an edit that arrives while the old chain is still draining cuts the drain: the leaving tail fades out over 0.15 s and the new chain lands in about 0.2 s (maintainer, by ear: "the change should happen more or less immediate") ... a swap drain now ends near the room's audible ring-out, measured every 0.5 s against a floor about 90 dB below full scale, and ordinary playback stays as it was
- the body and vowel bank swap crossfades over 50 ms instead of 20 (maintainer, by ear: no difference to hear, "to be on the safe side") ... the price is that a material can change about once every 52 ms, so 16ths pass and 32nds park above about 143 BPM; Seltsamere Dinge, Piper's Last Rave and the frozen Stranger Things of July moved, and we accepted the change (maintainer)
- a compressor knob move that relaxes the reduction follows the compressor's release instead of gliding over 50 ms (maintainer, by ear: the hard swell was "not really acceptable") ... a move that adds reduction still glides, a release time under 50 ms still takes the glide's 50 ms, and each move is judged once, at a fixed loud input of 0 dBFS
- s("sgpad").fm(...) keeps its pitch: the fm stage sat above the pad's detune fork, so one modulator served two pitches and jumped a block of phase per pitch (95 percent of the energy off the harmonic grid, depending on the block size) ... without fm the pad is bit for bit the same
- the listening round is done: the pitch pipeline's pairs are heard and accepted, and so are the delay and reverb fade ("both after versions ok") and the frozen songs' orbits ("all well")
- Der Schmetterling and Kokon retuned (maintainer): the cab's eq, the distort stages, gains and a few patterns

### (2026-10-17) Typed doors, and a solo that reacts at once ... v0.6.8

- Kotlin gets the same doors as the script: typed Ign and Kat objects that mirror the script's words (1.5.ign and 0.5.kat make the constants), one set of builders for both doors, the script door calls the typed one and only the accepted types differ ... sprudel's doors still take any pattern-like value until their own typed door is designed, open with the maintainer
- rolled out in seven steps: the 15 oscillators, filters and eq, adsr, fm, phaser, shimmer and tremolo, the Katalyst stages, then bands, serial, parallel and blend ... the arithmetic words lose their 15 Double twins, the corpus reads 0 edited and the 19 generated KSP outputs come out byte for byte the same
- found on the way: the KSP did not record a top-level extension function's own file as a source, so an incremental build could drop registrations, fixed and pinned by a test ... and KlangScript's operators do not work on signals yet (saw.mul(0.5) is the way, saw * 0.5 is not), recorded with a designed task (KLANG-00056)
- solo reacts at once and the newest solo() value wins (maintainer): the 2 s hold and the protection window are gone, an amount of 0, NaN or Inf ends it on the spot, a voice that falls away fades over at least 4 ms, the 1.5 s ramp stays ... an op after .solo() still leaves gaps, a ticket of its own
- a delay or reverb that is switched off fades its feed over the wet glide instead of cutting it (maintainer): 50 ms, one block if modulated ... accepted: the new owner's first 50 ms bleed into the old ring or room at a falling level
- duty(amount) is duty(width) on every surface, no alias (maintainer): 0 to 1, the share of the cycle that is high, 0.5 a square
- the frozen songs' shared orbits are fixed (maintainer): each conflicting pattern gets a free orbit of its own, so Stranger Things (July) and two Der Schmetterling versions keep the sound each pattern meant ... found, not changed: the live Der Schmetterling's guitar 3 shares orbit 2 with guitar 2, the maintainer's call
- the code song page has a minimap beside the editor (@replit/codemirror-minimap 0.5.2, MIT, credited) ... the embeddable editors stay without one
- Der Schmetterling's 4x12 cab gets a highpass at 110 Hz (maintainer) ... the full check is green after the decided changes (17,742 tests, corpus 3 of 3 frozen and 15 live songs identical, allocation 80 of 80) and after the typed doors

### (2026-10-16) The V8 batch, and songs move freely ... v0.6.7

- engine follow-ups 31, 32, 34, 39, 40 and 41 (the corpus reads 0 edited through all of them): a full classic() voice allocates what a plain saw does (about 47 to 15 B per block), the oversampled paths stop allocating (E-distort about 155 to 16 B, E-parallel 197 to 15, E-bands 232 to 16), the Katalyst knob noises too (E-kat-perlin about 114 to 22 B, E-kat-all7 119 to 15)
- two causes found on the way: Kotlin/JS emits Double.NaN as the global NaN, so `if (c) value else NaN` boxed its double arm on V8 (E-kat-gates about 55 to 16 B) ... and the delay and reverb now skip an unchanged per-block configure, which takes the 64 B (delay) and 48 B (reverb) per block out of mixed V8 profiles
- the modulated orbit compressor writes its five knobs once per block, 3 exp instead of 15 (E-kat-dyn about 97 to 45 B on V8)
- songs move freely (maintainer): the corpus reference holds a code hash per row next to the sound hash, so a song edit is information and an engine change that moves a song's sound is red ... corpus-accept-engine accepts a deliberate engine change and names the rows
- randL and sprudel's filter doors are cut (maintainer) ... within keeps its inside/outside split, KlangScript's examples show filterWhen
- rules are guard-rails, not commandments (maintainer): a reasonable exception gets short documentation where it lives, and "two kinds of check" (for the future, or scaffolding that goes with its work) starts provisional
- decided with the maintainer, still to build: typed Kotlin doors, a solo that reacts at once, a switch-off that fades, duty(width), free orbits for the frozen songs ... and a non-finite signal sample is the author's own, a note sounds the same wherever in a block it starts (the sweep that checks it waits for stable nodes)
- the full check: 17,688 tests green, corpus 3 of 3 frozen and 15 live songs identical, allocation 80 of 80

### (2026-10-13) Knobs that listen ... v0.6.6

- every Katalyst stage knob has its own wire type, KatalystKnobDsl (constant or param) ... an Ignitor node on a Katalyst knob is a type error at the door, and Kat.param returns the wire node
- a knob may be an expression over slots: plus, minus, times, div, pow, min, max, neg, abs and range(from, to), with the Ignitor's words and laws ... compiled once per knob and evaluated only when the orbit's params change, one non-finite slot makes the whole knob unset, katp and the eq knobs refuse an expression
- modulators for knobs: Kat.sine, saw, ramp, tri and square (freq, configure), Kat.perlin and Kat.berlin (rate, octaves, persistence) ... a modulated knob moves every block and glides over one block, every other knob keeps its 50 ms, and the n-th noise in a chain gets seed + n (maintainer)
- body and vowel can move wet and floor (maintainer's choice): only a change of material, vowel or on installs a bank through the 20 ms swap, a wet or floor change glides the coefficients in place, over one block when modulated, else 50 ms
- a modulated delay time or reverb size that crosses its gate fades the feed out over the wet glide, and every return glides up from 0 ... every other switch-off still cuts hard, which waits for a listening round
- ten allocation cases, and timing rows that run each modulated chain against its constant twin ... on V8 E-kat-mix goes from 521 to 196 B per block and E-kat-res from 160 to 16
- sprudel's doors have an allocation case of their own (S-doors), which closes engine follow-up 2 ... Berlin noise and fractal Brownian motion are credited in CREDITS.MD and on the Credits page
- the corpus renders 18 of 18 the same through all the steps, and Kokon, edited by the maintainer, is the one accepted change (peak 0.7614 to 0.7602) ... the full check of the finished branch reads 17,684 tests green and allocation 78 of 78

### (2026-10-12) One map of our own ... v0.6.5

- on V8 every Kotlin/JS HashMap operation with a non-null key allocates a 40 B context, and a new orbit param map made 27 lookups, about 1,100 B ... KlangMap is a read-only map with its own implementation per platform, a native JS Map in the browser and a LinkedHashMap on the JVM
- the wire decoder and the engine's two producers build it ... the new case C-remap takes 76 to 93 B where the old decoder took 1,128 B and more, and the wire format is unchanged
- sprudel's param bags sit on KlangMap: per event (toVoiceData plus the wire encode) three bus doors go from 17.1 to 12.4 KB and 7.8 to 4.3 us, many doors from 45.3 to 31.7 KB and 24.5 to 12.5 us; the JVM stays within its spread
- the sound registry and the dispatcher stop hashing and lowercasing per note: a registry lookup 128 B to about 1, a sample note per block 1,033 to 737 B, a synth note about 450 B less
- KlangMap is not a Map (maintainer) ... no caller can walk it or reach the stdlib's hashing by accident; the wire bytes are identical, and the schema hash is unchanged and pinned by a test
- the parallel stage copies the bus with copyRangeInto instead of a typed-array view per branch: E-parallel about 600 to 220 B per block on V8, E-bands about 820 to 250
- the Katalyst stages are in the measurement harness, ten allocation cases and six timing rows ... and the first two steps of the Katalyst knob expressions are in: the range law and the perlin constants in one home each, the compressor's and the duck's settings in holders that are refilled in place
- the rules get one more: write a lesson down when it is learned, prune in a separate pass (provisional) ... code-style goes from 463 to 194 lines with every rule, date and reason kept
- every step rendered the corpus bit for bit the same, 18 of 18 ... the full check before the release: 17,483 tests green, allocation 58 of 58

### (2026-10-11) The bus learns arithmetic, and the harness counts bytes ... v0.6.4

- the allocation harness is in the repo: 14 fixed cases at middle C, on the JVM in-process and on V8 through node on the production bundle, checked against budgets, two rows per run written to a scoreboard ... a measurement that saw nothing, a silent case, a stale bundle or a timeout reads red, not a quiet green
- console/full-check.sh runs tests, corpus, timing and allocations under the build lock in about 7 to 12 minutes ... the corpus check is tracked too, 18 of 18 against v0.6.3's sound
- the Katalyst gets arithmetic stages on the bus: plus, minus, times, div, pow, min, max, neg and abs, per sample with the Ignitor's laws ... div glides its reciprocal, so a move through or into a zero divisor passes through silence instead of a +300 dB gain
- no optimizer for them (maintainer): one cheap stage costs about 50 ns per block, and a fold of three would save about 0.004 % of the budget
- the bus distort oversamples at 2x by default (maintainer) ... a bus bends the whole mix, so its aliasing lands on every note; a bare bus distort delays its orbit by 3 frames; Kokon's and Der Schmetterling's bus distorts already asked for 4x, so no song moves
- more V8 boxes gone, bit for bit: the delay, reverb and phaser stages (the phaser skips an unchanged config), the plain waves' dt (about 16 B per block and oscillator to about 0, and 0.83 to 0.93 of the time), the SVF setup (a full voice about 190 to 30 B per block), the superpluck's drift ramp (about 300 to 550 down to 32 to 48 B)
- the PWM box did not show on the production bundle ... on the development bundle the loop boxed about 2,096 B per block and boxes 64 now, after the V8 seed rule reached every loop-carried seed in the oscillators
- the per-voice frequency box stays (maintainer): about 12 B per voice block in Chrome, and the ready fix is written down in case the phone needs it
- the full check before the release: 17,407 tests green, corpus 18 of 18, allocation 34 of 34

### (2026-10-10) Lighter still, and a cleaner top ... v0.6.3

- every moving envelope boxed each sample on V8 (the chain adsr, classic()'s envelope, the pitch and FM envelopes, about 2.1 KB per block each) ... now about nothing, bit for bit
- every knob read boxed one number per voice and block on V8, through one shared helper the browser never inlined ... inline now: a classic saw from about 80 to 21 bytes per block, a unison stack from about 90 to 0
- with a new note every 16 blocks the engine allocated 26.3 KB per block at v0.6.2 ... about 1 KB now, most of it the voice build
- the JVM boxes nothing per block anymore, the vibrato included: a knob's control-rate value is a plain number now, and the "constant, but no value" fallback is gone (maintainer)
- the oversampler is a polyphase IIR half-band: flat to 20 kHz where the old one lost 4 dB, aliases about 100 dB down instead of 20 ... a dry path beside an oversampled one runs through its phase twin, so a clean plus no longer notches the top; Kokon renders about 25 % slower, accepted, a shared oversampling DSL will win it back
- next: the allocation harness moves into the repo, scores on a scoreboard and one full check before a release ... the design waits for the maintainer
- the corpus rendered bit for bit the same through every engine step ... the oversampler's sound change is the one we meant

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
