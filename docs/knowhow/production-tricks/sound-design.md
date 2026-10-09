<!-- Raw research catalogue, collected 2026-10-08 by a research agent (Claude Sonnet) for
docs/plans/aaa-production-tricks.md, which maps every entry onto the engine. Kept as written, with its own
honesty notes: only the two CCRMA pages were read in full; numbers marked [MEMORY] or (?) must be verified
before they are coded. Parameter ranges are starting points for the ear, not facts. -->

# Source-level sound design catalogue for Klang

## Honesty note on sources (read first)
Web access was limited. Pages I actually fetched and read:
- CCRMA Karplus-Strong page https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithm.html (confirmed: one-zero loop filter, lowpassed noise excitation whose cutoff is a dynamics control; the fetched text did NOT cover allpass tuning/stretching).
- CCRMA commuted synthesis https://ccrma.stanford.edu/~jos/pasp/Commuted_Synthesis.html (confirmed: string and body are LTI so body IR folds into the excitation table; minimum-phase + exponential window to shorten).
- Search results (snippets, not full pages): Attack Magazine Diva kick https://www.attackmagazine.com/technique/synth-secrets/how-to-make-your-own-kick-drums-in-diva/ ; Attack clap https://www.attackmagazine.com/technique/tutorials/synthesizing-a-clap-with-white-noise/ ; Noise Engineering clap https://noiseengineering.us/blogs/loquelic-literitas-the-blog/percussion-synthesis-part-3-the-sound-of-one-synth-clapping/ ; Muzines Synclap https://www.muzines.co.uk/articles/the-synclap/6177 (4 bursts spaced 10-30 ms + long-decay tail path).
- Szabo supersaw thesis: PDF host (nada.kth.se) is dead; I could NOT read the equations. Search confirmed only the qualitative shape (detune stays small up to mid, then rises sharply; hardware detune never exactly 0; free-running phases). Numbers marked [MEMORY] below are from my recollection of the thesis and implementations and MUST be verified (KTH DiVA mirror, or Eric Skogen's SuperCollider port, or https://schollz.com/til/221103).
- Sound On Sound Synth Secrets index returned HTTP 410; I cite SOS series by title from memory only, unverified URLs not given.

Everything else below is from my own domain knowledge. Where a number is a typical working range rather than a measured fact it is a "typical" value, tune by ear. Items marked (?) are ones I am unsure of. Source lines listing "knowledge" mean no page was consulted for that item.

Klang notes: your rules say Motor stays raw, no DSP in frontends, seconds on the wire, pitch in semitones. Ranges are given in ms / Hz / cents / semitones accordingly.

---
# A. BASS

### 1. Three-layer bass (sub / mid / top)
- Purpose: a bass that is felt, audible on every speaker and has articulation.
- Mechanism: split role by band. Sub: sine (or triangle) at f, below ~120 Hz, mono, clean. Mid: saw/square/FM at f (or f+12 st) lowpassed, 100-800 Hz, carries pitch identity and holds up on small speakers. Top: bright, narrow, buzzy/noisy layer or a pluck/click, highpassed above ~800 Hz-2 kHz, gives attack and cut in a dense mix. Crossovers: Linkwitz-Riley 4th order at ~100-150 Hz and ~600-1000 Hz so the sum is phase-flat; or just per-layer HP/LP with 12-24 dB/oct and check the sum. Levels: sub 0 dB reference, mid -3..-8 dB, top -10..-20 dB.
- Genres: EDM, trap, dnb, pop, hip-hop.
- Pitfalls: phase cancellation at the crossover (use LR or test polarity flip); layers fighting 100-250 Hz mud; top layer stereo-widened must not touch the sub.
- Sources: knowledge; Attack kick layering analog above.

### 2. Reese bass
- Purpose: thick, slowly phasing, menacing bass.
- Mechanism: two (or 3-4) saws detuned +/- 5..25 cents (up to ~40 for aggressive), same note, LP filter 200-1200 Hz, optional slow filter LFO 0.1-0.5 Hz. Beating rate = f * (2^(c/1200)-1)*2 roughly: at 55 Hz and 15 cents detune each side the beat is ~0.95 Hz, giving the movement. Add chorus/phaser (motion), mild saturation, and high-pass the stereo difference so lows are mono (see #4).
- Genres: dnb, jungle, dubstep, techno.
- Pitfalls: beating on low notes becomes too slow (<0.3 Hz) = sounds static; on high notes too fast = harsh. Scale detune in Hz not cents at very low notes if you want constant beat. Stereo spread kills mono compatibility.
- Sources: knowledge.

### 3. Mono sub (low-end mono)
- Mechanism: everything below ~100-150 Hz is identical in L and R. At the source: sub layer is a mono sine; mid layers stereo only above the cutoff (M/S with S highpassed at 120-200 Hz). Reason: bass wavelengths > 2 m, no localisation, out-of-phase bass wastes headroom and cancels on mono systems/club.
- Pitfalls: detuned unison reaching below 120 Hz in stereo; reverb sends on the bass.

### 4. Sub sine with phase reset on note-on
- Purpose: consistent punch; every hit has the same waveform start so repeated notes do not randomly cancel against the previous release tail or the kick.
- Mechanism: on note-on set sine phase to 0 (or to a fixed phase that starts at a zero crossing, or sin starting at peak for a "thump" cosine start). Random phase makes the first half-cycle amplitude vary hit to hit (a note starting at a peak vs a zero crossing differs by up to ~6 dB in the first 5-10 ms for short notes). Kick+bass: pick phase relation so first peaks add.
- Pitfalls: starting at non-zero value = click (apply 1-3 ms attack or start at zero crossing); legato notes should NOT reset.
- Genres: all electronic, 808.

### 5. Missing fundamental / harmonics for small speakers (what you already built, with variants)
- Mechanism: the ear infers pitch f0 from harmonic series 2f..nf even if f is absent (virtual pitch; the harmonics must be at least 2-3 resolved or share a common periodicity ~ temporal envelope at f). Variants:
  a) additive partials 2f..8f at 1/n (yours);
  b) waveshaped copy of the sub (tanh / full-wave rectifier x^2 style, giving 2f, 3f) mixed under, high-passed at ~1.5f so the sub is not doubled;
  c) tape/tube style soft saturation in parallel with sub (see #6);
  d) octave-up layer (a sine/triangle at 2f) at -6..-12 dB: cheapest, most robust;
  e) psychoacoustic bass processors (Waves MaxxBass style): isolate band 40-120 Hz, apply a non-linearity (rectifier/ multiplier), band-pass the harmonics 2f..4f, add back. Odd-harmonic dominant (3f, 5f) reads as "buzzy/solid", even (2f, 4f) as "warm". Phone speakers roll off below ~150-300 Hz, so partials should sit at 150-800 Hz.
- Typical ratio: harmonic energy -6..-15 dB relative to the fundamental.
- Pitfalls: partials at exact integer ratios produce a very static, organ-like sound; add 1-3 cents drift or a slight envelope decay so higher partials die faster (the bass gets "darker" as it decays, more natural). Too much = nasal/honk. Note velocity: raise harmonic amount with velocity so quiet notes are clean.
- Sources: knowledge (virtual pitch literature; Waves MaxxBass patent description).

### 6. Multiband distortion (distort the mid layer only)
- Mechanism: split bass (LR crossover ~100-200 Hz); keep the lows clean; drive only the band >150 Hz through tanh/foldback/bitcrush; optionally HP the distorted band after. Drive 6-24 dB. Result: harmonics without intermodulation mush in the sub (distorting a 50 Hz sine plus a 70 Hz sine creates 20/120/190 Hz IM products).
- Pitfall: oversample 2-4x for the waveshaper to avoid aliasing of saw layers; match level pre/post.
- Genres: dubstep, hard techno, metal-ish bass, trap 808s.

### 7. FM bass growl
- Mechanism: 2-op FM, carrier sine at f, modulator ratio 1:1, 1:2, 2:1 or non-integer (e.g. 1.5, 3.01). Modulation index I = peak deviation / fm; sidebands at fc +/- k fm with amplitude J_k(I). Envelope the index (I from ~6 down to ~1 over 50-300 ms) for the "wow". Growl: modulate I (or modulator ratio, small amount) with an LFO at 2-8 Hz or sync to tempo (1/8, 1/16, triplet), feedback FM on the modulator for noisy edges. Follow with a filter and distortion.
- Genres: dubstep, DnB, neurofunk.
- Pitfalls: aliasing at high index (oversample); ratios non-integer make the pitch ambiguous = good growl, bad bassline clarity on low notes.
- Sources: Chowning FM (knowledge); SOS Synth Secrets FM articles (title only, unverified).

### 8. Formant / vowel bass (neuro, "yoi")
- Mechanism: take a saw-rich bass (often two detuned saw + distortion), pass through 2-3 parallel bandpass/peaking filters with vowel formants (e.g. /a/ F1 700, F2 1100, F3 2500 Hz; /i/ F1 270, F2 2300, F3 3000; /u/ 300, 870, 2240; /o/ 450, 800, 2830; male values, typical Peterson-Barney). Morph between vowel presets with an LFO / envelope (3-10 Hz) or tempo-synced; Q 4-12. Often followed by more distortion then another filter ("sandwich": distort, filter, distort, filter). Since your engine has vowel filters, this is a direct mapping.
- Genres: neurofunk, dubstep, dnb.
- Pitfalls: formants fixed in Hz while pitch changes = fine (that is how voice works); distortion after formants smears them; keep the sub out of formants.

### 9. 808 bass: tuning
- Mechanism: 808 kick/sub is a decaying sine with a long tail (0.4-2 s); its fundamental must be a note in the key. Tune the sample to the song (root typically 30-60 Hz: F1=43.65, G1=49, A1=55, E1=41.2). A detuned 808 clashes with bass/chords even when masked. Pitched via sample rate pitch shifting or via synth.
- Pitfalls: very low notes (<35 Hz) vanish on most speakers, so pair with harmonics (#5) or play an octave up; slides between notes need legato and glide (#10).

### 10. 808 pitch glide and drive
- Mechanism: 808s glide between notes with portamento 30-250 ms (exp curve), typically in triplet/trap slides where only the note-on triggers the amp env but the pitch moves (legato). Initial pitch click: start the sine +12..+24 st above target and decay over 5-30 ms (tiny kick-like transient). Drive: tanh/asymmetric saturation after, 3-15 dB, adds 2nd/3rd harmonic (#5). Also: gentle low-shelf compress/limit to keep tail level, and set amp release to avoid a click at the end (cut at zero crossing or 5-10 ms release).
- Genres: trap, drill, hip-hop, modern pop.
- Pitfalls: DC offset from asymmetric clipping (use DC blocker, ~10-20 Hz one-pole HPF); long tails overlapping next note's pitch (monophonic with choke).

### 11. Bass / kick key matching and relationship
- Mechanism: two ways. (a) Tune kick fundamental to the key (usually root or fifth), so kick and bass reinforce; (b) separate: kick sub at 45-60 Hz, bass fundamental either above (>70 Hz) or ducked (sidechain, which is bus-level, the sibling's area). At source: the bass note envelope shortened so it releases before the kick, or kick-bass shared time slots (bass 'rests' on the kick). Check the sum of kick fundamental and bass note interval (a minor 2nd apart = beating).
- Pitfall: tuning a kick sample requires pitch-shift without time-stretch; a kick with a pitch sweep tuned by its end frequency (the sweep's tail).

### 12. Bass portamento / mono legato voice
- Mechanism: monophonic voice; overlapped note retains the envelope (no retrigger), pitch glides with time constant 20-120 ms. Exp glide in semitone domain: p += (target-p)*(1-exp(-dt/tau)); constant-time glide (time independent of interval) feels more musical for bass than constant-rate.
- Genres: acid, funk, hip-hop, synthwave.

### 13. Acid bass (303-style)
- Mechanism: single saw or square, 4-pole diode-ish ladder LP with high resonance, a decaying filter envelope 100-300 ms with accent (louder note: higher cutoff mod, shorter env), slide (legato glide ~60-100 ms), distortion after. Envelope amount rises with accent.
- Pitfall: resonance causes level jumps; compensate gain. The "squelch" depends on cutoff envelope decay being faster than amp decay.

---
# B. DRUMS

### 14. Kick layering: click + body + sub
- Mechanism: three layers. Click/transient: 1-10 ms burst, 2-8 kHz (noise burst, high-pitched short sine ~1 kHz decaying 2-5 ms, or a snappy sample), -6..-12 dB. Body: sine/triangle with pitch sweep, 60-200 Hz, decay 80-250 ms, carries "thump". Sub tail: sine ~40-55 Hz, decay 200-500 ms (EDM; skip for tight kicks), tuned to key. Align phase at the start (all components begin at zero or consistent polarity), high-pass the body below the sub to avoid doubling. Attack stage on the sub amp env can suppress its own click.
- Genres: house, techno, trap, pop, rock (acoustic sample + sub).
- Pitfalls: phase cancellation between sample layer and synth (polarity flip test); too much click = papery.
- Sources: Attack https://www.attackmagazine.com/technique/synth-secrets/how-to-make-your-own-kick-drums-in-diva/ (sweep, click via filter envelope, 909 vs 808 character).

### 15. Kick pitch-envelope design
- Mechanism: f(t) = f_end + (f_start - f_end) * exp(-t/tau). Typical: f_end 40-60 Hz (the "note"), f_start 150-400 Hz (tight house/ pop: ~150-250; techno/hardstyle: up to 400+ plus distortion), tau 15-60 ms (909: ~30-40 ms; 808 kick: f_start ~ 120-150 Hz, tau 20-40ms, long amp decay). Phase must be integrated from instantaneous frequency (phase += 2pi f(t)/sr), never compute sin(2pi f(t) t). Hardstyle/gabber: drive hard, then shape pitch tail to be the "melodic" part, second distortion stage.
- Pitfalls: very fast sweep (<5 ms) = click not sweep; sweeps ending above 70 Hz sound thin; end frequency drifts from key.

### 16. Sample tuning to key
- Mechanism: rather than altering all drums, tune tonal elements (kick, toms, 808, claps with body, snare body) so their dominant partial hits a scale degree. Find the fundamental via FFT/autocorrelation; shift by semitone+cents offset to the nearest key tone. Typical effect: less mud, drums stop beating against bass/chords (5 Hz beating of a 55 vs 50 Hz mismatch is audible).
- Pitfall: resampling pitch-shift changes the decay/length too (also sweep); formant-preserving shift for snare changes character.

### 17. Snare layering: crack + body + noise tail
- Mechanism: crack/transient (1-5 ms, 2-6 kHz: a short noise burst or rimshot/clap sample), body (tonal sine/triangle at 180-250 Hz, with small pitch drop, 50-100 ms decay), noise (white noise band-passed 3-8 kHz, decay 100-250 ms, snappiness = noise level), plus a ring/shell (two detuned sines ~330 and 180 Hz in 909). 909 snare: two triangle-ish oscillators ~ 180 and 330 Hz plus noise through a HP filter.
- Pitfalls: noise layer has random phase, so each hit differs (feature; for exact repeat seed it the same). Layers' phase relation.

### 18. Clap construction
- Mechanism: noise (white/pink) through a bandpass ~900-1500 Hz (Q 1.5-3) then highpass for brightness. Envelope: 3-4 short bursts each with a very fast attack and ~5-10 ms decay, spaced ~10-30 ms (Synclap per Muzines), followed by a longer envelope (decay ~120-300 ms) that forms the tail (stand-in for a room). Slightly different amplitudes (0 dB, -1, -2, 0) and random spacing jitter +/- 2-3 ms to sound like several hands. Add a short reverb/room.
- Genres: house, trap, pop.
- Pitfalls: identical burst spacing = machine buzz (12 ms spacing is a 83 Hz comb); filters resonance gives pitched artefacts.
- Sources: https://www.muzines.co.uk/articles/the-synclap/6177 , https://www.attackmagazine.com/technique/tutorials/synthesizing-a-clap-with-white-noise/ , Noise Engineering clap blog above.

### 19. Transient enhancement at the source
- Mechanism: (a) layer a click; (b) shape the amplitude envelope with a faster attack/decay in the first 5-20 ms (two-stage decay: fast 5-15 ms drop of 3-8 dB then slow body decay); (c) differential-envelope designer: gain = f(fast_env - slow_env), boosting only the attack portion; (d) a short high-frequency burst filter-envelope (cutoff opens from 8 kHz to 500 Hz over 10 ms). Beware: attack time < 0.5 ms creates clicks; start at zero crossing.
- Genres: all drums, plucks, bass attack.

### 20. Drum parallel "smash" (source-level notes)
- The bus compression itself belongs to the sibling topic. Source relevant point: designing drums with extra noise tail and room layers so that heavy compression (parallel, 8:1+, fast attack) has something to lift; build the dry sound to survive it (clean transient, not clipped). Mention only.

### 21. Room mic simulation (synth drums)
- Mechanism: a send of the drums to a short, dark, early-reflection-rich reverb (RT60 0.2-0.6 s, predelay 5-15 ms, HP 200 Hz, LP 6-8 kHz) then crushed/compressed and mixed -12..-20 dB. Cheaper: comb of 3-6 taps at 7-40 ms with decaying gains plus LP. A synth kick should have almost no room (stays tight), snare and clap benefit most. Gate or short-envelope room for 80s gated-reverb style (RT 0.3-0.5 s then hard cut).
- Pitfalls: the same room on all drums makes a smear; pan/level different per element; keep room mono-compatible.

### 22. Ghost notes
- Mechanism: very low velocity hits (velocity 15-40 % of main snare), placed on 16th positions around the backbeat (e.g. "a" of 2 and "e" of 3 etc.), with shorter decay and a lowered filter cutoff or pitched slightly down so they read as texture, not accents. Velocity-to-timbre mapping (#23).
- Genres: funk, hip-hop, DnB, neo-soul, lofi.

### 23. Humanization: timing and velocity micro-variation
- Mechanism: timing jitter Gaussian sigma ~ 2-8 ms (tight electronic 0-3 ms; lazy backbeat snare behind the beat by 5-20 ms; hi-hats slightly ahead). Velocity jitter +/- 5-12 %. Use correlated (low-pass) noise rather than white so it drifts like a player. Velocity-to-timbre: velocity maps also to filter cutoff (+/- 1-2 kHz), decay, and attack (sample layers), not only to gain. Keep the kick/bass relation tight. Do not jitter all instruments independently: bass/kick pair moves together.
- Pitfalls: more than ~15 ms random jitter = sloppy; keep deterministic seeded randomness to meet "fixed learnable target" principle if desired.

### 24. Groove / swing templates
- Mechanism: delay every second 16th (or 8th) by swing s: 50 % = straight, 54-58 % light (house, hip-hop), 62-66 % heavy (MPC "66 %" = triplet). Delay amount = (s-0.5)*2*step. MPC-style swing swings 16ths only; J Dilla style: kick/snare off-grid by 10-40 ms with hats quantised. Groove templates also include per-step velocity accents (accent pattern e.g. 100/60/80/55) and per-step timing offsets (per instrument).
- Genres: hip-hop, house, garage (2-step swing ~ 58 %), funk.

### 25. Round-robin and anti machine-gun
- Mechanism: pick one of N (3-8) sample variants each hit with "no immediate repeat" rule (random but not same as last); for synths, add per-hit random: +/- 3-10 cents pitch, +/- 2 dB gain, +/- 5-10 % filter cutoff, noise seeds different, 0.5-2 ms start offset. Velocity layering (3-5 layers crossfaded). Repeated identical 16th hats trigger obvious phasing; modulate the sample start offset by small amounts.
- Pitfalls: randomising too much loses "signature"; for the identity of a track keep the random range inside perceptual JND (about 1 dB, 5 cents).

### 26. Hi-hat choke groups and hat articulation
- Mechanism: closed, pedal and open hats share a group; triggering closed (or pedal) cuts open hat with a 3-10 ms release (not instant, avoids click), exponential. Hat tone: 6 square oscillators at inharmonic ratios (808: 205.3, 304.4, 369.6, 522.7, 540, 800 Hz) summed, band-passed (~7-10 kHz) and high-passed; closed decay 30-80 ms, open 250-600 ms. Add velocity-to-decay (soft = shorter) and slight tone change.
- Pitfall: choke needs to be per-voice, not by MIDI note alone; a metallic noise with sharp 1/f is harsh >10 kHz (LP 14-16 kHz).

### 27. Shaker / percussion movement
- Mechanism: noise through bandpass 4-9 kHz, envelope with soft attack 5-15 ms (the 'shh' rise) and decay 40-100 ms; alternate accents (down-stroke louder; "shaker swing" 8ths with 16th ghost); pan slowly or alternate L/R ±20 %, cutoff modulated by a slow random (#44), tempo-synced offbeat. Use a rule of rhythmic density: one percussion element per missing 16th gap, keep the rhythm interlocking with hats.
- Genres: house, afro-house, latin, pop.

### 28. Modal synthesis for percussion (toms, bells, 808 cowbell, metal)
- Mechanism: bank of N exponentially decaying sinusoids (or 2-pole resonators): y = sum a_k * exp(-t/t_k) * sin(2 pi f_k t). Mode frequency ratios: ideal membrane (circular drum) 1, 1.59, 2.14, 2.30, 2.65, 2.92 (Bessel zeros); free bar 1, 2.76, 5.40, 8.93; bell ~ 1, 2, 2.4, 3, 4.2, 5.4 plus minor third (0.5, 1.2). Decay for higher modes shorter (t_k ~ t_1/k^0.5..1). Excite with a short noise or impulse; strike position determines a_k (nodal lines). Tom: add pitch drop on strike (tension modulation), 5-15 % at start.
- Genres: electronic percussion, cinematic, ambient.
- Source: DAFX/Smith modal literature (knowledge, not fetched). Ratios from Bessel zeros: confident; bell ratios approximate (?).

### 29. Tom and conga synthesis; tuned toms in key
- Sine with small pitch envelope (+30..50 %, 20-60 ms), plus transient noise; decay 150-400 ms. Tune toms to chord tones to make fills "melodic".

### 30. Double-hit/flam and burst fills
- Flam: second hit 10-30 ms after first, first at -6..-10 dB. Drag/ruff: 3 hits at 8-15 ms. Rolls: accelerate rate with velocity ramp. Nice for snare builds (see #55).

---
# C. SYNTHS AND PADS

### 31. Supersaw / unison (JP-8000 analysis, Szabo)
- Mechanism (from thesis; only the structure verified in search, numbers from memory): 7 saws: 1 centre and 6 sides at fixed relative offsets (approximately 0, +-0.11002313, +-0.06288439, +-0.01952356 times the detune amount [MEMORY: verify]). Oscillators free-running (random phase), no phase reset. Detune knob mapped through a non-linear polynomial (11th order in the thesis, detune curve small up to ~50 %, steep above; hardware never at exactly 0 detune). Mix knob: centre gain approx -0.55366*x + 0.99785, sides approx -0.73764*x^2 + 1.2841*x + 0.044372 [MEMORY: verify]. A highpass filter (cutoff following the pitch, ~ at the fundamental) is applied to remove low-frequency beating between the detuned oscillators; this is key to the "airy" feel and mono sub safety.
- Generalised version: N voices spread linearly in cents ±5..±40 (pads ±5-15, trance leads ±15-30, hard EDM ±30-50), equal power per voice 1/sqrt(N) normalisation.
- Genres: trance, EDM, pop, synthwave.
- Pitfalls: alias of naive saw (use PolyBLEP/BLEP or wavetables); unison on bass notes becomes mush (HP + fewer voices); 256 voices cost.
- Sources: https://schollz.com/til/221103 (search hit, not read in full), KVR/Reaktor ports (search hits). Thesis PDF not retrievable.

### 32. Stereo unison spread
- Mechanism: assign unison voice i pan p_i spread across [-w, +w] (w = 0..1), either linear, or alternate L/R by detune order (lowest-detune pairs closest to centre) so centre stays focused; constant-power pan: gL = cos((p+1)pi/4), gR = sin(...). Ensure sum is mono-compatible: voice with pitch +d at left, -d at right gives decorrelated L/R (a good thing for width, but the mono sum is the beating comb).
- Pitfall: width via large detune+pan only works with free-running random phases.

### 33. Analog drift
- Mechanism: each voice (and each unison sub-voice) gets slowly varying pitch offset: smoothed noise at 0.1-1 Hz, amplitude 2-8 cents (analog polys: ~±3-8 cents; subtle: 1-2). Also drift filter cutoff (±1-3 %), amp level (±0.2 dB), PWM. Use independent random per voice, generated by a one-pole LP of white noise or 1/f-ish (sum of 2-3 octaves of LFNoise). Plus per-note tolerance: static random offset ±2-5 cents at note-on (voice-to-voice differences).
- Genres: synthwave, ambient, pop, neo-soul, any "warmth".
- Pitfall: with 1-2 cents of drift on a lead you get detuned feel; leave basses mostly stable.

### 34. Phase randomisation vs phase reset
- Mechanism: random start phase = smoothest chorus-like pad, no repeat identity; phase reset (all unison voices at 0 or fixed offsets) = the same transient every key press: punchy plucks, EDM leads, keeps repeated chord hits phase-coherent but yields a comb "stacked" attack. Common compromise: reset the first oscillator, randomise the others (partial coherence) or 'phase spread' knob.
- Pitfall: with phase reset and identical detunes the unison begins as one thick spike then diverges (can be a feature: "supersaw punch").

### 35. Wavetable motion
- Mechanism: morph position as LFO/envelope/random target; table scanning with linear interpolation between mip-mapped frames (band-limited per octave to avoid alias); cross-fade frames, optionally spectral morph (interpolate partial amplitudes and phases). Typical LFO 0.05-1 Hz for pads, envelope fast sweep for plucks. Add subtle noise in the position (±1-3 %) to make it alive. Modern (2020-26) tools add time-domain warps (bend, sync, mirror, FM) per frame.
- Pitfall: frames with different phase alignment create zipper/phasing; align phases, interpolate carefully.

### 36. PWM
- Mechanism: pulse wave with duty d(t) = 0.5 + m*LFO, m 0.1-0.4, LFO 0.2-3 Hz; two pulse oscillators with inverted LFO = thick "string machine" sound; PWM equals a sum of two detuned saws (saw - saw delayed by d cycles). Duty near 0/1 thins to nothing; clamp to 0.05-0.95. Band-limit pulse as difference of two PolyBLEP saws.
- Genres: synthwave, 80s pads, chiptune, string-machine.

### 37. Oscillator sync sweeps
- Mechanism: slave osc resets phase whenever master completes a cycle. Slave frequency f_s = ratio*f_m, ratio swept 1-8 with envelope/LFO: the pitch of slave defines the formant-like timbre, spectral peak sweeps through harmonics (the classic "sync lead" scream). Hard sync is aliased; use BLEP corrected sync or oversample. Soft sync (reverse direction) gives smoother tones.
- Genres: lead, 80s, prog, bass.

### 38. Ring modulation
- Mechanism: y = a(t) * b(t); sum and difference frequencies f1±f2. With harmonic ratio (1:2, 1:3) sounds metallic but tonal; non-harmonic ratios give bells/robotics, clangs. Use carrier 100-4000 Hz or follow-pitch with an offset. Use mix 10-40 % for metallic edge on leads and as noise-percussion tuner (cowbell, ride).
- Pitfall: if the carrier is audio-rate not tracking pitch, notes become atonal.

### 39. FM for pads/e-pianos/bells
- Mechanism: ratios 1:1 (warm), 1:14 (DX7 e-piano "tine": carrier 1, modulator 14 with fast decay index), 3.5:1 or 1:3.5 etc. for bells. Index envelope with velocity scaling; operator output level key-scaled (so high notes are not too bright); detune carriers by ±1-3 cents for chorus. Feedback on modulator 0-0.3 radian for a saw-like spectrum.
- Genres: 80s pop, R&B, lofi, ambient.

### 40. Filter envelopes, key tracking, velocity-to-brightness
- Mechanism: cutoff_Hz = base * 2^(keytrack*(note-60)/12 + env*envAmt_oct + vel*velAmt_oct + lfo*...), working in octaves (log). Key tracking 50-100 % keeps brightness consistent across range (100 = filter follows pitch exactly; many patches use 40-70 %). Velocity -> cutoff of +1..+3 octaves and also louder = "reads brighter" (as real instruments do). Env amount 2-5 oct for plucks, decay 80-400 ms; for pads attack 0.3-2 s.
- Pitfall: highpass key-tracking in pads; resonance gain changes with keytrack.

### 41. Layering with an octave or fifth
- Mechanism: second voice at +12 st at -6..-12 dB gives brightness and size without EQ; +7 st (or +19) adds a pseudo-power-chord ("organ-like"), keep it at -12..-18 dB so it reads as timbre not as harmony. Octave-down layer for weight (-12 st, low-passed). Pitfall: fifth layers conflict with minor/major thirds in chords? (no, but a fifth with a b5 chord gives clash); mono-fy low octave.

### 42. "Air" and noise layers
- Mechanism: very quiet filtered noise (HP > 6-10 kHz, or bandpass 8-14 kHz) at -30..-45 dB relative with slow tremolo or tracking the note envelope; makes pads/vocals feel breathy and expensive; for "breath" on leads/flute adds random breath noise ~1-3 kHz band at -20 dB. In the 2020s noise "bed" is also used as tonal glue (pink noise at -45 dB behind the entire track).
- Pitfall: noise accumulates across many voices; use a shared one instead of per-voice (cheap and avoids phase-coherent noise addition).

### 43. Granular textures
- Mechanism: grains of 20-200 ms, Hann window, density 10-100 grains/s, position jitter ±5-20 %, pitch jitter ±0-12 cents (or ±octave for shimmering), pan jitter; freeze = position fixed; stretches a sample indefinitely. For pads: loop a vocal/ piano note with 50-100 ms grains, 4x overlap, reverse 20 % of grains. Output normalise for overlap (sum of windows).
- Genres: ambient, cinematic, hyperpop, modern pop textures.
- Pitfall: grain rate regularity gives a buzz (pitch = grain rate if >20 Hz); jitter the grain timing.

### 44. Slow random modulation (S&H, smoothed random, drift)
- Mechanism: sample&hold at rate r, output a staircase; slew it (one-pole with time constant ~0.5/r) → smoothed random; "wander" uses interpolated cubic random at 0.1-2 Hz. Apply to pan (±10-30 %), cutoff (±0.2-1 oct), pitch (±2-5 cents), pulse width, wavetable position, delay time (±1-3 ms). Always bipolar-around-zero, always separate seed per destination.
- Genres: ambient, IDM, techno, anything that should "breathe".

### 45. Chorus on pads / ensemble
- Mechanism: 2-6 delay taps modulated: base 10-25 ms, depth 1-6 ms, rates 0.1-1.2 Hz (0.3, 0.5, 0.7 Hz offset phases), L/R opposite phases. Ensemble (Solina): three taps at 120° phase offsets, delay ~ 3-8 ms. Wet 30-60 %. Mono sums create a comb, so keep high-pass on the wet chorus above 200 Hz (see pitfall).

### 46. Sidechain-pumping pads (source-level)
- Mechanism: instead of a bus compressor, trigger an envelope (a ducker) per note-on of the kick event inside the synth: gain = 1 - depth*(exp(-t/tau)), depth 0.5-0.9, tau 80-200 ms, attack 1-5 ms, tempo-synced; or a LFO shaped 'volume shaper'. Per-voice applied to pads and bass; additive frequency-dependent: duck only 100-500 Hz of pads (multiband). Also gives rhythmic movement to a static pad.
- Genres: house, trance, pop, future bass.

### 47. Chord voicing: open / spread
- Mechanism: instead of close-position triads in 200-600 Hz (mud, major 3rd at ~200 Hz muddy), spread: bass root at its register (below 200 Hz), 5th/7th next, 3rd above 300 Hz; keep intervals between adjacent chord tones below the register of 250 Hz at ≥ a fifth; drop-2 voicing; omit the root in upper structures when bass has it; avoid minor 2nd/9th clusters below ~C3. Doubling the root an octave up. Shell voicings (R-3-7) for jazz. Limit the pad to 3-4 notes in mid register; 'voice leading': smooth top note motion by step.
- Genres: all.
- Pitfall: very open voicings lose cohesion at fast tempo; width is not loudness.

### 48. Velocity/random "vintage polyphonic" behaviours
- Mechanism: per-voice component tolerance (cutoff ±3 %, env times ±5 %, tuning ±3 cents); each note differs; plus the "round robin" principle. Keeps chord notes from sounding clinically identical (Juno/Prophet-like).

---
# D. LEADS AND PLUCKS

### 49. Pluck envelope design
- Mechanism: amp env attack 0-2 ms, decay 80-300 ms to sustain 0 (or low), filter env decay ~ 0.5 of the amp decay, amount 2-4 octaves, fast initial pitch drop of 5-20 cents over 5-10 ms to mimic string tension; add a short noise/click layer 2-8 ms. Pair with delay (dotted 1/8) for "expensive" pluck sound; decay time proportional to pitch^-1 for realism (higher notes decay faster: t60 ∝ 1/f^0.7..1).

### 50. Portamento / legato lead
- Mechanism: monophonic with last-note priority, glide 30-150 ms, retrigger envelopes only when not overlapped; vibrato delay (see 51); "legato slides" with tempo-synced glide times.

### 51. Delayed vibrato
- Mechanism: vibrato LFO 4.5-6.5 Hz (singer's ~5.5-6 Hz), depth ±10-40 cents (singing ±50-100), fade-in after 200-600 ms of the note (amplitude ramps over 300-800 ms), tiny random rate variation ±5 %, and amplitude variance. Also tremolo/ brightness vibrato for strings/winds. Vibrato from pitch + filter + amp together is more convincing. Pitfall: immediate full vibrato = sounds like a cheap synth.

### 52. Pitch scoop / note-start bend
- Mechanism: note starts 0.5-3 semitones (voice-like scoop: -1..-2 st, brass fall -1..-12 st) below and glides to target in 30-120 ms with exp curve, often only for long notes or accents; also upward "doits" at note end (+2..+12 st over 100-300 ms, with amp fade). Velocity scales scoop depth. Genres: vocal-like lead, brass, guitar-like, R&B, trap melody.

### 53. Unison lead and octave doubling
- Mechanism: 3-7 voices detuned ±8-25 cents, plus one voice at +12 st at -9..-12 dB (or -12 st); stereo-spread; HP the side voices above ~ 200 Hz; short slow-attack on the doubling for "bloom".

### 54. Harmonizer
- Mechanism: parallel pitch-shifted copy(ies) at diatonic intervals (3rd, 5th) following scale degree map, with ±5-10 cents detune, 5-20 ms delay and -6..-12 dB; formant shift if vocal (#58). Modern: scale-aware, with intelligent voicing from the chord track.

### 55. Formant shifting / formant preservation
- Mechanism: formants are the spectral envelope; pitch shift moves envelope with pitch ("chipmunk"). Preserve: estimate envelope (LPC order 16-32 or cepstral lifter) / use PSOLA or phase-vocoder with envelope correction. Shift deliberately for gender/character: ratio 0.8-1.3 (±3-4 st). In a synth, a fixed formant filter bank behind an oscillator already preserves it (source-filter model).

### 56. Transient layering with a noise click on leads/plucks
- Mechanism: a 1-10 ms bandpassed noise burst (1.5-6 kHz) at note-on, -18..-30 dB, plus slight filter env snap, to give pick/hammer realism and cut through. Velocity scaled.

---
# E. VOCALS AND VOCAL-LIKE

### 57. Doubling and ADT
- Mechanism: ADT (Abbey Road 1966, Ken Townsend): a delayed copy of the vocal (modulated delay 20-40 ms with random wow ±1-3 ms, 0.2-1 Hz) mixed at -6..-10 dB, panned opposite. Real double-tracking: independent takes differ in timing (±10-30 ms), pitch (±5-15 cents), tone. Synthesis: two copies pitch-shifted ±8-12 cents, delayed 12-25 ms, pan L/R, slight different filter. Haas region (see #75).
- Pitfall: comb filtering in mono when delay <10 ms; static detune = chorus not double.

### 58. Harmonies / stacks
- Mechanism: 3rd/5th above/below following chords; keep harmonic voices lower in level (-6..-12 dB), darker (LP 6-8 kHz), more reverb, the lead dry-ish. Large stacks (4-8 voices) with random timing ±15 ms and detune ±10 cents = "choir" for hooks (2020s pop).

### 59. De-essing (source-level)
- Mechanism: sibilance band 4-9 kHz; the detector is HP/BP at ~6 kHz; gain reduction applied only to that band (split-band) with a 0-10 ms attack / 20-60 ms release, 3-8 dB reduction. At source for synth vowel/vocal: generate /s/ as separate, level-controlled noise burst. Pitfall: lisping by over-reducing. Bus processing is the sibling topic.

### 60. Vocal chops
- Mechanism: slice sample into syllables (10-300 ms), pitch to notes in key via resample or formant-preserving shift, amp env with short attack 0-5 ms, release 30-100 ms, rhythmic pattern in 8th/16th with ghost reverb tails / reverse; process with formant shift (±2-5 st), light saturation, bitcrush; layering octave. Typical in future bass, house, hip-hop.

### 61. Vocoder
- Mechanism: analysis bank of N band-pass filters (12-32 bands, Q ~ 5-10, ERB or log spaced 100-8000 Hz) on modulator (voice); envelope followers per band (attack 1-5 ms, release 10-50 ms); carrier (saw/pulse chord) goes through the same bank; multiply by envelope. Add a sibilance path: highpass noise (> 5 kHz) mixed under for consonants (essential for intelligibility), and a pass of unvoiced detection. 
- Genres: electro, funk, Daft Punk-style pop.
- Pitfall: carrier needs high-frequency richness; saw + noise; set bands at same frequency as analysis for clarity.

### 62. Talkbox
- Mechanism: instrument sound is fed into a tube into the mouth and picked up with a mic; modelling: instrument saw → time-varying formant filter (mouth shape = vowel envelope/ LPC) controlled by speech. In synth: vowel filters morphed per syllable (5 formants), with voiced saw + breath noise.

### 63. Pitch correction as an effect (hard-tune, Auto-Tune effect)
- Mechanism: pitch detect (YIN/autocorrelation), target = nearest scale note, retune speed 0-5 ms = robotic "T-Pain" step (glitchy), 20-100 ms natural; formants preserved; apply with a light vibrato removal (1 Hz LP of pitch contour). Ranges: retune time 0 ms to 400 ms; humanise parameter keeps the vibrato. Used also for synths: quantize pitch bend and glide into steps.
- Genres: trap, pop, hyperpop.

### 64. Vocal "air" and breath
- Mechanism: HF shelf from saturation (parallel exciter, 8-12 kHz), plus breath noise layer; mention as source-level noise layer (#42).

---
# F. REALISM / MODELLING

### 65. Karplus-Strong refinements
- Mechanism: baseline: delay line length N = fs/f - 0.5 (due to the one-zero filter half-sample delay), loop filter H(z) = (1+z^-1)/2 times g (g 0.995-0.9999). Refinements: (a) fractional delay tuning with 1st-order allpass (coefficient (1-d)/(1+d)) or Lagrange/Thiran interpolation; (b) frequency-dependent decay via 1-pole LP with coefficient depending on pitch so high notes do not die too fast, brightness parameter; (c) noise excitation LPF'd with cutoff dependent on velocity (dynamics, confirmed by Smith); (d) pick position: excitation comb (1 - z^-round(beta*N)), beta 0.1-0.5 (0.5 = hollow; near bridge 0.1 = bright); (e) pick direction/ pluck shape: use a triangle or half-sine window instead of white noise for "cleaner" tone; (f) dispersion (next); (g) tension modulation: very slight pitch rise at note start; (h) sympathetic: see #70; (i) string coupling (two polarisations slightly detuned = beating/two-stage decay), mix 2 delay lines tuned ±1-3 cents → two-stage decay characteristic of piano and guitar.
- Source: https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithm.html (fetched).

### 66. Commuted synthesis & body resonance
- Mechanism: because string and body are LTI, convolve the excitation (pluck) with the body IR once and play the result as the pluck table into the waveguide; cost per voice stays low. Body IR from a bridge hammer test or from a sample of the instrument; shorten via minimum phase + exponential window (confirmed). Guitar body: main Helmholtz resonance ~100 Hz and top plate ~200 Hz modes; a cheap alternative is a resonator bank of 8-30 biquad modes with Q 10-50 and gains following a measured profile, mixed in parallel at 20-50 %.
- Source: https://ccrma.stanford.edu/~jos/pasp/Commuted_Synthesis.html (fetched).

### 67. Pick position comb filter
- Mechanism: excitation at fraction beta of the string length attenuates harmonics at k = n/beta: for beta=0.5 even harmonics vanish (hollow/clarinet-like), 1/5 kills 5th, 10th... Implement: x_exc = x - z^{-round(beta N)} x. Beta 0.12-0.2 normal electric, 0.25-0.4 mellow. A 'brighter at the bridge' sweep can be automated for expression.

### 68. String stiffness / dispersion allpass
- Mechanism: stiff strings: partial f_n = n f0 sqrt(1 + B n^2), inharmonicity coefficient B ~ 1e-5..1e-4 (guitar) up to 1e-3 (piano bass). Implement by cascade of 1st-order or 2nd-order allpass filters in the loop with frequency dependent delay (group delay increases at low freq); Van Duyne & Smith, Rocchesso & Scalise. Detailed design; (?) coefficient mapping to B I do not recall exactly: tune by measuring partials.
- Pitfall: allpass changes the fundamental, retune with delay.

### 69. Release samples / key-off noise
- Mechanism: on note-off trigger a short 'release' event: piano damper thump (low thud 100-300 Hz, 30-80 ms), harpsichord jack click, guitar string squeak/finger-release noise, organ key click; level -20..-30 dB, velocity depends on how long the note was held (shorter = more). For synth: a tiny noise burst or brief filter-resonance ping at note-off. Also key-on mechanical noise (piano hammers, organ chiff 20-60 ms at the 1-3 kHz).

### 70. Sympathetic resonance
- Mechanism: undamped strings (piano with pedal, sitar, harp) resonate with partials: feed the signal (a fraction -30..-40 dB) into a bank of lightly damped resonators tuned to scale tones/pitches of open strings; resonator t60 1-5 s. Cheap: a "resonating comb" bank at note frequencies of the key with feedback 0.9-0.98. Adds depth to piano and pad.

### 71. Breath and bow noise
- Mechanism: wind: band-passed noise (1-4 kHz, envelope proportional to pressure) added before the bore (waveguide) to play flute-like jet; amplitude ~ 2-10 % of the carrier. Bowed string: friction noise 2-8 kHz, plus stick-slip nonlinearity (bow velocity vs friction curve, McIntyre-Schumacher-Woodhouse). Pre-onset noise 10-40 ms before pitch settles (the "chiff"). Add slow random pressure fluctuations 0.5-4 Hz.

### 72. Instrument-level analog of "physical" expressiveness: velocity layers & timbre morph
- Mechanism: map velocity to at least 3 parameters (gain, cutoff/brightness, attack time/transient level); and "key position" (higher keys decay faster, shorter transient).

---
# G. MOVEMENT AND EAR CANDY

### 73. LFO to everything
- Mechanism: LFO shapes: sine/tri (smooth), S&H (random steps), saw (ramps), exponential decay. Destinations: pitch (vibrato), cutoff, resonance, pan, amp (tremolo), wavetable position, unison detune, delay time, fx send, FM index. Tempo-sync rates 1/4 .. 1/32 and triplet. Modulation depth rule of thumb: perceived movement is relative: log-scaled for freq (octaves), dB for amp. Use 2-3 slightly non-integer-related rates (e.g. 0.13, 0.31, 0.47 Hz) to avoid obvious looping.

### 74. Envelope followers as modulators
- Mechanism: one-pole rectified/RMS follower attack 1-10 ms, release 20-300 ms; used to make a pad's filter react to the kick/vocals, for "auto-wah", for ducking at source, for transient shaping. Pitfall: attack too fast follows waveform ripple on bass.

### 75. Risers
- Mechanism: white noise (HP sweeping up 200 Hz → 10-16 kHz over 4-16 bars), amplitude ramp 0 → 1 (exp), plus a pitch-rising tone (sine/saw sweeping +12..+36 st up), plus a rising LFO rate (tremolo accelerating 1 Hz → 16 Hz), with stereo widening growing toward the end, and resonance rising. Stop 1 beat early or cut at the downbeat to leave silence/air (see #92).
- Genres: EDM, pop, cinematic.

### 76. Downlifters and reverse downsweeps
- Mechanism: noise or tonal sweep descending (HP/LP closing or pitch falling) over 1-4 bars after the drop's start to make a release, or reversed crash.

### 77. Impacts / booms / sub drops
- Mechanism: sine starting at 60-100 Hz falling to 25-35 Hz over 0.5-2 s (exp), decay 1.5-4 s, plus noise burst 20-100 ms for transient, plus a reversed-reverb tail into the hit (anticipation), plus big reverb (3-6 s) with HP 150 Hz. Place on the downbeat 1; may be preceded by 1-4 beats of silence (pre-drop gap).

### 78. Reverse reverb / reverse cymbal
- Mechanism: take the sound (e.g. a snare or vocal word), apply a long reverb, render, reverse the wet only; place it so that reverb peak lands at the next downbeat (reverb length = pre-delay arrangement); reverse cymbal = crash reversed, length aligned to the beat grid so peak is at the target beat.

### 79. Tape stop / vinyl stop
- Mechanism: playback speed ramp from 1 → 0 over 0.3-1.5 s with curve (exp or linear speed → pitch falls ~ logarithmic), simultaneous LP closing a bit and level slightly rising; implement as playback rate r(t) applied to a delay-line read pointer. Reverse: tape start.

### 80. Stutter / glitch / beat repeat
- Mechanism: capture the last 1/4-1/16 note slice (loop of 62-250 ms) at a trigger, repeat with shortening divisions 1/8 → 1/16 → 1/32 over a bar (accelerando), gate-envelope each repeat with 1-3 ms fade to avoid clicks; pitch may rise (increasing playback rate); crossfade 1-5 ms at slice boundaries. Variants: bitcrush, reverse, random slice reorder.

### 81. Filter sweeps
- Mechanism: HP or LP swept exponentially (cutoff in Hz ∝ 2^(8*x)) over 1-16 bars, resonance 0.2-0.6; on a whole drum/synth group builds energy. Use 12 dB for transparent, 24 dB + resonance for dramatic. Automate with ease-in curves for tension.

### 82. Shepard / Risset tone risers
- Mechanism: N sine partials spaced one octave apart (e.g. 8-10 octaves), each gliding up at a constant rate in log frequency, amplitude given by a fixed bell-shaped (Gaussian/ raised-cosine) window over log-frequency: A(f) = exp(-((log2 f - c)^2)/(2 s^2)); when a partial reaches the top it fades to zero while a new one fades in at the bottom: perceived endless rise. Rate: one octave per 4-8 s. Used in Hans Zimmer/ Nolan, EDM builds, game tension. Risset rhythm (accelerating tempo) is the time analog.
- Pitfall: the window must be wide and smooth; too narrow = visible fade.

### 83. Vinyl / tape / noise beds
- Mechanism: vinyl: crackle (Poisson impulses 1-10/s on top of dust, amplitude lognormal) + surface noise (pink noise, band-limited 1-8 kHz at -45..-35 dB) + rumble (LP noise <40 Hz) + RIAA-ish dulling; tape: wow (0.5-2 Hz, ±0.05-0.2 % speed), flutter (6-12 Hz, ±0.02 %), hiss (-50 dB), HF roll-off 10-14 kHz, saturation soft. Mix low and start the bed before the music (anticipation of the reveal).
- Genres: lofi, boom-bap, ambient.

### 84. Granular freeze
- Mechanism: continuously record into a ring buffer 0.5-3 s; at trigger, freeze the write pointer, play grains (50-150 ms) from a fixed position with jitter 5-20 ms, 8x overlap; fade between live and frozen. Used for transitions: freeze the last word/chord of a phrase into the gap; also "infinite reverb" feel (freeze with spectral smear).

---
# H. PSYCHOACOUSTICS AT THE SOURCE

### 85. Missing fundamental (see #5): ear detects pitch of f0 from harmonics spacing; works best for harmonics 3-7 in the 200-1000 Hz region (dominance region), when partials are unresolved the periodicity of the envelope matters.

### 86. Haas / precedence doubling
- Mechanism: copy of a sound delayed 5-30 ms, panned opposite, level -3..-10 dB: perceived as widening, location stays with the first arrival (precedence effect) up to ~30-40 ms; echo threshold beyond. Better with the delay modulated slightly and the copy EQ'd darker. Pitfall: mono sum comb with notches at f = (2k+1)/(2 d); d=10 ms → 50, 150 Hz... Avoid on bass.
- Sources: knowledge (Haas 1951).

### 87. Harmonic distortion for audibility on phones (see #5, #6)
- Phone/laptop speakers cut below 100-250 Hz. Use 2nd/3rd harmonic generation with a waveshaper after the sub (tanh drive 3-12 dB in parallel). Check the sound with a 150 Hz HPF on the monitoring path.

### 88. Masking avoidance by arrangement (frequency slots, register separation)
- Mechanism: critical bands ~ 100 Hz wide below 500 Hz, so sounds closer than ~1/3 octave in the same band mask each other (esp. low-level under high-level ones). Assign each element a primary slot: sub 30-80, bass 80-250, kick body/pad 60-120 vs 200-500 low-mid, chords/guitars 250-1.5k, vocals 1-4k, hats/air 6-16k; avoid more than one dense thing in one slot; if two share the register, make them rhythmically alternate (time-slot masking), or differ in timbre (noise vs tone) or pan. Lead instruments should sit in the 1-3 kHz range where ear is most sensitive.
- Also temporal masking: post-masking ~50-200 ms after loud sounds (kick followed by bass at the same moment).

### 89. Loudness perception (Fletcher-Munson / ISO 226) and brightness
- Mechanism: equal-loudness contours: at moderate level the ear is ~10-20 dB more sensitive at 2-5 kHz than at 1 kHz, and less sensitive below 200 Hz (40 phon contour needs +40 dB at 40 Hz relative to 1 kHz). So sounds with more energy at 2-5 kHz read louder at the same RMS. Design: place presence into the lead/snare; make bass audible with harmonics (#5); a brighter version of the same patch will be judged louder & "better" in A/B (beware: always level-match when comparing).

### 90. Pre-echo / anticipation
- Mechanism: a sound that begins a short moment before the beat (anticipation in the arrangement: reverse swell 1-2 beats ahead; kick pre-hit 1/16 early at low level, pickup notes) primes the ear: the downbeat feels bigger. Also, avoid unintended pre-echo from linear-phase filters/limiters in synthesis (the Klang master lookahead is a constant, ignore). Tape "print-through" pre-echo is used as an effect (quiet repeat before the sound).

### 91. Spectral tilt / pink-ish balance at the source
- Mechanism: design patches with ≈ -3 dB/oct (pink) average tilt; a patch with ≥ -6 dB/oct (saw LP) sounds dull next to polished references; use gentle HF shelf in the patch (or a saw + noise) to match. (Practical rule, not strictly a literature fact.)

---
# I. ARRANGEMENT-LEVEL

### 92. Silence before the drop
- Mechanism: 1/8 - 1 beat of total (or near total: leave only reverb tail/ noise) silence before the downbeat of the drop; the contrast and the ear's recovery (temporal release of adaptation) makes the drop feel louder by several dB. Cut risers + drums ~1 beat early; keep a reversed sound or a vocal tag in the gap.

### 93. Contrast and the energy curve
- Mechanism: plan an energy per section on a 1-10 scale; adjacent sections differ by ≥ 2 steps for impact; verticals: layer count, filter brightness, density (note-rate), register width, stereo width, level, noise. Typical pop: verse 4, prechorus 6, chorus 8, bridge 3, final chorus 9-10; EDM: intro 3, build 5→8, drop 10, breakdown 3-4, drop 2 variation 10. Chorus is wider (stereo) and brighter than verse.

### 94. "Less is more" layer count
- Mechanism: limit simultaneous prominent elements to ~3-5 (per frequency slot 1 hero); each added layer must serve a different slot/role; mute test: remove each layer, if the sound doesn't change, delete it. Fewer elements = more headroom, as dynamics and clarity are preserved.

### 95. Dynamics via arrangement rather than compression
- Mechanism: use the number of voices/layers and register to change perceived loudness ( +1 layer ≈ +1..3 dB), sparse verses; automating the velocity & filter in builds; avoid crushing everything with compression so peaks remain meaningful. The master limiter then gets a cleaner input.

### 96. Call-and-response
- Mechanism: phrase A (call, 1-2 bars) answered by phrase B (response) by a different instrument/register/timbre/pan, fills the gaps of the lead; vocals ↔ synth stab; kick ↔ clap; sections in 2-bar alternation. Rule: leave ≥ 20-30 % empty time per layer; overlapping calls and responses masks each other.

### 97. Tension builds: noise, snare rolls, rhythmic acceleration
- Mechanism: snare roll subdivisions 1/4 → 1/8 → 1/16 → 1/32 over 4-8 bars with velocity ramp (30 → 127), pitch rise of the snare (+0..+5 st) or the filter opening; plus riser (#75), HP filter on the other elements removing the low end (low cut rising 100 → 600 Hz) so the drop's low end is a relief; plus a decreasing note length; a final 1-beat gap (#92). Also "bar-length pause".

### 98. Section transitions: fills, crashes, reverse swells
- Mechanism: every 4/8/16-bar boundary has a marker (crash + reverse swell + fill) so structure is clearly audible; vary the marker (don't copy-paste) to avoid fatigue; the final bar before change is removed (dropout).

### 99. Register/octave arrangement ("sparkle and weight")
- Mechanism: for a big chorus: double the main riff in octaves; add a sub octave; open the chord voicing a bit upward; widen the stereo spread; these combined raise perceived size without increasing the level.

---
# J. 2020-2026 STANDARDS (some are my recollection of the trends; flag as trend, not hard fact)

### 100. Hyperpop / maximalist saturation with pitch-shifted vocals and hard-tuned formant-shifted leads (+pitch correction effect #63, formant shift #55).
### 101. Detuned/"wonky" lofi: tape wobble, vinyl bed, drifting chords, rounded drums (#33, #83): "imperfection as character".
### 102. Dolby Atmos/immersive-aware stereo: because mono-compat and headphone listening dominate, sources are built in mid/side from the start: mono sub and centre-heavy bass, wide upper layers (#3, #32); stem-separation-resistant arrangement. (trend)
### 103. Drill/trap sliding 808 with distorted, gliding bass (#10), hi-hat rolls with velocity/pitch rolls, triplet rolls (1/24 divisions).
### 104. Neural/ML tools: stem splitters, timbre transfer, generative one-shots (I am unsure of specifics: skip for DSP).
### 105. "Gated/dynamic" synth pads with sidechain-LFO shapers (Kickstart / LFOTool style) (#46).
### 106. Mono-compat "wide mono": stereo width by short decorrelation (all-pass chains, 5-30 ms velvet noise) on sources rather than Haas; velvet-noise decorrelator: sparse ±1 impulses, ~1000-2000/s over 10-30 ms; maintains mono sum with little combing.
### 107. Dynamic, velocity-sensitive MPE and per-note modulation: expressive sources (per-note pitch/timbre) rather than static layers. (trend)
### 108. Reese/"wavetable bass" with spectral formant morph; also "tape-saturation + clip" of the whole kick+bass in one stage: the latter belongs in the bus group.

---
## Priority suggestions for Klang (opinion; sound-first)
1. Bass: layer split + multiband distortion (#1, #6) + harmonic variants (#5-b/e, with decay-darkening partials); phase-reset sub (#4); mono below 120 Hz (#3).
2. Drums: kick pitch-env with integrated phase, click layer (#14, #15); clap multi-burst (#18); per-hit micro-random + velocity->timbre (#23, #25); choke groups (#26).
3. Synths: analog drift (#33), unison with HP'd side voices (#31), partial phase reset (#34), noise beds (#42, #83).
4. Arrangement hooks: pre-drop gap, risers, Shepard (#82), ducker per voice (#46).
5. Verify before coding: Szabo coefficients (#31), vowel formant table (#8), modal ratios for bells (#28), dispersion allpass design (#68).
