<!-- Raw research catalogue, collected 2026-10-08 by a research agent (Claude Sonnet) for
docs/plans/aaa-production-tricks.md, which maps every entry onto the engine. Kept as written, with its own
honesty notes: [C] = a source was consulted (search summaries only), [K] = the agent's own knowledge, not
verified online, [U] = unsure. Parameter ranges are starting points for the ear, not facts. -->

# Mixing and Mastering Techniques Catalogue (bus / master side)

Honesty notes
- "Consulted" URLs: I ran web searches and read the result summaries (not full page fetches). They are listed per section as [C]. Items marked [K] are from my own DSP/engineering knowledge and were NOT verified online in this session; treat parameter ranges as typical starting points, not facts.
- [U] = I am unsure. Vendor internals (Pultec, MaxxBass, Aphex, SPL, Soothe) are proprietary; descriptions are reconstructions.
- Notation: x[n] input, fs sample rate, one-pole smoother coefficient a = exp(-1/(tau*fs)), dB<->lin: g = 10^(dB/20).

Consulted sources (all via search, summaries only):
- Pultec trick: https://musicradar.com/how-to/pultec-low-end-trick , https://www.adesignsaudio.com/the-pultec-low-end-trick , https://www.musicguymixing.com/pultec-trick/
- Loudness / true peak / dither: https://www.roexaudio.com/blog/what-lufs-should-i-master-at , https://www.masteringbox.com/learn/mastering-for-streaming , https://www.softube.com/limiter-features-when-mastering-understanding-true-peak-metering-and-dithering , https://musictech.com/tutorials/10mm-no211-inter-sample-peaks/ (vendor blogs, not platform docs; Apple/Amazon figures conflicted between sources)
- Trackspacer / Soothe: https://musictech.com/reviews/wavesfactory-trackspacer-review/ , https://tapeop.com/reviews/gear/101/trackspacer-plug-in , https://www.oeksound.com/manuals/soothe2 , https://www.pro-tools-expert.com/production-expert-1/2020/6/24/taming-low-end-resonances-with-soothe2-from
- Abbey Road trick: https://flypaper.soundfly.com/tips/the-abbey-road-trick-how-to-eq-reverb-sends-to-free-up-space-in-a-mix/ , https://mixdownmag.com.au/features/abbey-road-reverb-emulating-with-stock-plugins/
- Exciter / MaxxBass: https://www.soundonsound.com/techniques/how-enhancers-work , https://www.muzines.co.uk/articles/behind-the-aphex-aural-exciter/10682 , https://www.soundonsound.com/reviews/waves-maxxbass-101-102 , https://en.wikipedia.org/wiki/Exciter_(effect)
- Transient designer: https://barryrudolph.com/mix/transient.html , https://www.kvraudio.com/forum/viewtopic.php?p=5067014
- Stereo width: https://www.soundonsound.com/sound-advice/q-can-haas-delays-be-mono-compatible , https://forum.juce.com/t/making-a-mono-input-wide-adding-stereo-info/60357

Further reading I know of but did NOT open this session [K]: Julius O. Smith, "Physical Audio Signal Processing" and "Introduction to Digital Filters" (ccrma.stanford.edu/~jos); Zolzer ed., "DAFX"; Giannoulis/Massberg/Reiss, "Digital Dynamic Range Compressor Design" (JAES 2012); Parker/Esqueda/Bergner on antiderivative anti-aliasing (DAFx 2016) and Esqueda "Aliasing reduction in clipped signals" (2016); Kleimola et al.; Gerzon "Optimum reproduction matrices"/Blumlein M/S; Zavalishin "The Art of VA Filter Design"; ITU-R BS.1770-4 / EBU R128 (loudness, true peak); Bob Katz "Mastering Audio"; Lipshitz/Vanderkooy on dither; FabFilter learn pages (fabfilter.com/learn); iZotope learn pages.

---------------------------------------------------------------------------------

## A. LOW-END MANAGEMENT

### 1. Missing-fundamental harmonic bass enhancement (MaxxBass, Waves RBass, "psychoacoustic bass")  [C: SOS MaxxBass review, Waves manual summary]
- Purpose: bass is perceived on speakers that cannot reproduce it, and gets weight/audibility on any system.
- DSP: split input with a crossover at fc ~ 80-150 Hz. Low band LB = LPF(x). Generate harmonics of LB with a nonlinearity: full-wave rectifier gives even harmonics (2f, 4f), a soft clipper/tanh gives odd (3f, 5f); MaxxBass-like designs use a controlled mix with envelope-following so harmonic level tracks the bass level. Then bandpass/HPF the harmonics (keep roughly 2f..8f, i.e. 100-500 Hz), scale by `amount`, and sum with the (optionally attenuated) original: y = HP(x) + k_orig*LB + k_h*BP(NL(LB)). Typical k_h -10..-3 dB rel. to LB; optionally LPF the original fundamental below 40-60 Hz to protect small systems. Your approach (sine partials at 1/n) is the additive-synthesis variant; the effect-based variant works on arbitrary material (also on samples with unknown pitch).
- Waves RBass (K, U exact algo): similar harmonic generator + dynamic. Ratio of harmonics matters: strong 2f and 3f give the cue; relative phase of partials affects waveform crest but not pitch cue.
- Chain: per bass track or bass bus; also master "bass shelf" in lite amounts.
- Genres: hip-hop, EDM, pop, trap 808s, anything consumed on phones/laptops.
- Pitfalls: boxy/muddy 200-400 Hz if overdone (SOS warns); rectifier on polyphonic low content produces intermod; added harmonics raise peak/RMS - recheck headroom and limiter drive; aliasing for odd-order NL at high drive (oversample 2x or use band-limited NL); phase relation between harmonics and fundamental changes with crossover phase - use LR4 or linear-phase split.

### 2. Sub-harmonic synthesis (dbx 120A/Boom Box, Aphex Big Bottom, "octave-down sub")  [K]
- Purpose: add content one octave (or two) below the input for physical sub weight.
- DSP: LPF input at 100-150 Hz -> Schmitt-trigger zero-crossing detector -> flip-flop (divide-by-2) gives a square at f/2 -> LPF 2nd/4th order at ~50-80 Hz to a sine-ish -> amplitude from envelope follower of the input (so sub follows dynamics) -> mix. Digital alternative: pitch-track f0, then oscillate sine at f0/2 gated by the input envelope; or use a short 2x phase-vocoder pitch shift down.
- Chain: send/aux on kick or bass; or per-voice ("sub layer").
- Genres: hip-hop, dubstep, film/trailer, reggae/dub.
- Pitfalls: mono sources only; polyphonic input confuses the divider; sub below 30-35 Hz wastes headroom and is inaudible on most systems; clip it/HPF at ~25-30 Hz; keep mono and phase-aligned with the kick (see #5).

### 3. Mono-below-X Hz / elliptical EQ / bass-mono-ing  [K]
- Purpose: tight, centred, vinyl-safe, club-safe low end; frees headroom.
- DSP (M/S method): M=(L+R)/2, S=(L-R)/2. HPF S at X (typical 100-150 Hz, 12-24 dB/oct, Linkwitz-Riley or linear-phase), L = M+S', R = M-S'. Equivalent: crossover L,R into low/high with LR4; low = (Llow+Rlow)/2 sent to both. Elliptical EQ (Vinyl-type): same thing with a gentle slope (6-12 dB/oct) and X 100-300 Hz so it is a "soft" mono-ing.
- Chain: master (last stages before limiter, or in the limiter's M/S), also per bass bus.
- Genres: all electronic music, club-targeted, vinyl.
- Pitfalls: min-phase HPF on S adds phase shift around X (use LR/linear-phase if you care); anything panned hard in the lows (a wide sub pad) loses width; do it before the limiter so the limiter's L/R link is stable.

### 4. Kick/bass sidechain, volume ducking  [C: Trackspacer reviews describe the contrast]
- Purpose: kick punches through, bass breathes, avoids low-frequency masking; also pumping groove.
- DSP: compressor on bass with key = kick (or a trigger MIDI/envelope). Gain computer: g_dB = -(ratio-based reduction); typical ratio 4:1..inf, attack 0.1-5 ms, release 40-200 ms (tempo-synced; releases to finish before next kick), gain reduction 3-12 dB. A "fake" version without a kick audio: ADSR/LFO-driven gain curve (volume shaper) synchronised to the beat; zero latency, perfectly stable. Key can be HPF'd/LPF'd (e.g. kick 40-120 Hz) to avoid hi-hat triggers.
- Chain: bass bus (or all non-kick bus), key from kick.
- Genres: house, EDM, trance, pop, trap.
- Pitfalls: audible pump on full-band ducking (ducks mids/highs of bass too -> use #5/#6); too-fast release gives low-frequency distortion (gain modulation at audio rate creates sidebands); zero attack clicks - ramp over >= 1 ms.

### 5. Dynamic-EQ / spectral ducking (Trackspacer, Soothe sidechain, dynamic EQ)  [C: Trackspacer reviews]
- Purpose: duck only the colliding frequencies of the bass/pad/guitar when the kick/vocal plays.
- DSP: Trackspacer-style: FFT/filterbank (32 bands described) on both signals; per band, band_gain = 1 - depth*f(key_band_energy / ref), smoothed with attack/release; apply as reverse EQ to the target (STFT multiply or minimum-phase filterbank). Dynamic EQ equivalent: 1-4 bell/shelf bands whose gain is driven by a detector on the (filtered) key: gain_dB = -max(0, key_dB - thresh)*(1-1/ratio) capped at range; attack 1-20 ms, release 50-300 ms.
- Chain: target tracks/buses; key from kick or lead vocal.
- Genres: electronic, pop, modern hip-hop; vocal-vs-guitar carving anywhere.
- Pitfalls: STFT latency & pre-echo; per-bin gain jitter -> "scratchy"/chirpy artifacts (smooth in freq & time, one forum report cited artifacts [U, unconfirmed]); wide kick spectrum filters the target heavily unless sidechain LP-limited.

### 6. Multiband (split-band) sidechain  [K]
- As #5 but with 2-4 bands: ducks e.g. only 30-150 Hz of the bass bus using key=kick. Crossover must be LR4 (flat sum) or linear-phase. Cheaper than FFT; the "Nicky Romero Kickstart"-type shapers on a band-limited basis.

### 7. Kick/bass phase and polarity alignment  [K]
- Purpose: low-frequency energy adds rather than cancels; punchier sum.
- DSP: compare polarity (flip) and time offset (sample-delay up to ~1-3 ms) of kick vs bass at the overlap frequency; maximise low-passed sum RMS. All-pass (first/second-order around 60-100 Hz) can rotate phase without delay. Verified by the sum of LPF(kick+bass) at 150 Hz.
- Chain: per track (kick and bass), before bus.
- Genres: all with live bass/kick; EDM too (sample-start offsets).
- Pitfalls: aligning at one f misaligns another (phase differs with frequency); rotating the bass phase changes the crest factor; a single-ended polarity flip is not always louder, it is sometimes thinner.

### 8. Low-end resonance taming (room modes, 808 tail control, sub notches)  [C: Production Expert Soothe, K]
- DSP: static narrow notch/bell cuts (Q 4-12, -3..-8 dB) at the dominant mode frequencies found by sweeping, or dynamic cut engaged only above a threshold (dynamic EQ with an LPF'd detector). 808s: one-pole sustain shortening via a gate or an envelope; tune the 808 root to the key, ducking / dynamic cut on the note that rings. For synthesized bass: high-pass at 25-30 Hz and cut the DC.
- Genres: all; essential in trap/808.
- Pitfalls: notch changes phase near its frequency; mix at low monitor volume hides issues.

### 9. DC blocker / subsonic filter  [K]
- y[n] = x[n] - x[n-1] + R*y[n-1], R=0.995..0.9995 (fc ~ 1-5 Hz); or HPF 20-30 Hz at 12-24 dB/oct on every non-bass track and at the master. Stop DC added by asymmetric waveshapers (even harmonics, #19-#22) before they reach a limiter.
- Pitfalls: after a nonlinearity, not before; time constant leaves a DC step response click on mutes.

---------------------------------------------------------------------------------

## B. DYNAMICS

### 10. Feed-forward/feed-back compression basics (reference)  [K]
- Level detector (peak or RMS, optionally HPF'd), gain computer with threshold T, ratio R, knee W (soft knee: quadratic region from T-W/2 to T+W/2), attack/release smoothing in the dB domain (log-domain smoothing is more musical; "branching" or "smooth decoupled" detector per Giannoulis 2012), make-up gain. Gain reduction y_dB = x_dB for x<T-W/2; T + (x-T)/R above T+W/2; quadratic between. Stereo link: max or sum of channels, or M/S for balance.

### 11. Parallel ("New York") compression  [K]
- Purpose: density and sustain while keeping transients and dynamics of the dry signal.
- DSP: send copy to a hard compressor (ratio 8:1..20:1, 10-20 dB reduction, attack 0.1-10 ms, release 50-200 ms; often with an HPF/saturation), blend with dry: y = dry + w*comp(dry), w typically -12..-3 dB relative to dry. Equivalently an upward compressor; both lift low-level detail.
- Chain: drum bus (classic), vocal bus, master bus (in light form).
- Genres: rock, pop, hip-hop drums, vocals.
- Pitfalls: latency mismatch between dry and wet (PDC); comb filtering from lookahead differences; phase flips; the wet path raising noise floor.

### 12. Bus "glue" compression (SSL-style stereo bus)  [K]
- Purpose: sticks elements together, adds coherence.
- DSP: VCA feed-forward compressor, ratio 1.5:1-4:1, attack 10-30 ms (lets transients through), auto or ~100-300 ms release, 1-3 dB gain reduction, HPF the key 60-150 Hz (so bass does not drive the pumping). Often with a soft knee and a program-dependent release (two-stage release: fast + slow).
- Chain: drum bus, mix bus, sometimes master.
- Genres: universal; rock/pop/hip-hop.
- Pitfalls: bass pumping if the key is not filtered; level-matched A/B (compression always sounds "better" when louder).

### 13. Serial (multi-stage) compression  [K]
- Two or three gentle compressors (e.g. 2 dB each, different attack/release/character) instead of one hard one: less artifact, same total dynamics control. E.g. fast peak-control (1 ms, 4:1) followed by slow leveller (opto-like, 3:1, 30 ms attack, 500 ms release). Vocals: FET -> opto -> de-esser.

### 14. Opto-style leveling (LA-2A) / program-dependent release  [K]
- Gain control element (cell) with attack ~10 ms and two-stage release (fast part ~60 ms, slow tail 1-5 s dependent on the amount of prior reduction). DSP: state variable g_r (reduction memory) that increases the release time constant with the time/level spent in reduction. Gentle ratio ~3:1, soft knee.

### 15. Multiband compression  [K]
- Purpose: control one frequency region (low-end consistency, harsh upper mids) independently.
- DSP: crossover into 3-5 bands (LR4 at 120 / 1.2k / 6k typical), independent compressor per band, sum. Band-dependent timing: attack 30 ms/release 150 ms for lows, 5 ms/50 ms highs. Linear-phase for mastering, min-phase LR4 for zero latency.
- Chain: master/mix bus, drum or vocal bus.
- Pitfalls: crossover regions create magnitude ripple when bands are compressed differently; "sounds like EQ with automation"; level-match.

### 16. Upward compression / OTT-style (Ableton "Multiband Dynamics", Xfer OTT)  [K]
- Purpose: loudness, density, in-your-face brightness.
- DSP: three bands (e.g. 88 Hz / 2.5 kHz); per band: downward compression above T_high and upward expansion below T_low (gain increases quiet signal toward the threshold). Gain law in dB: if x<Tlow: g = (Tlow - x)*(1 - 1/Ru); if x>Thigh: g = -(x - Thigh)*(1 - 1/Rd). Very fast timing (attack 1-10 ms, release 50-150 ms), then wet/dry (depth) 10-50 %.
- Chain: parallel on synth buses; light on masters in EDM; leads/pads.
- Genres: EDM, dubstep, future bass, hyperpop.
- Pitfalls: noise floor and reverb tails rise; makes everything sound the same; pumping at low-band; watch peaks (post-compression crest).

### 17. Transient shaping (SPL Transient Designer: differential envelope)  [C: Barry Rudolph, KVR thread]
- Purpose: more or less attack/punch or sustain/tail independent of level.
- DSP: two envelope followers on rectified signal, one fast (attack ~0.5-1 ms, release ~ 5-20 ms) and one slow (attack 15-50 ms, release 100-300 ms). Attack gain: gA = k_a*(env_fast - env_slow)/(env_slow + eps), applied as dB (SPL's attack range up to +-15 dB, sustain up to +-24 dB). Sustain gain: from inverse differential (env_slow vs fast with long release). Normalising by env makes the process level-independent (no threshold). Implementation: compute in dB, smooth the gain with ~1 ms to avoid zipper, apply with lookahead if needed.
- Chain: per drum, drum bus, bass (reducing sustain), acoustic guitar; master rarely.
- Genres: drums everywhere, rock, EDM.
- Pitfalls: naive followers alias/ripple on LF (use lowpass or oversample - KVR); sustain boost lifts the noise and reverb; HF/LF band-split versions (multiband transient shaper) needed for kicks.

### 18. Expanders, gates and ducking gates  [K]
- Gate: g=0 (or floor -10..-80 dB) below threshold, hysteresis (open at T, close at T-3..6 dB), attack 0.05-1 ms, hold 5-100 ms, release 50-500 ms, lookahead 0.5-5 ms to avoid click-chopping the transient; sidechain HPF/LPF filtered key. Downward expander ratio 1:2..1:4 is the softer version.
- Chain: per track on drums (tom, snare), vocals (breath), guitars; reverb gating (#46).
- Pitfalls: chatter (use hysteresis), cutting natural decay, bleed triggers.

### 19. De-essing  [K]
- Purpose: tame sibilance 4-10 kHz.
- DSP: split-band: BP/HP detector at 5-9 kHz; compare its envelope with a threshold (or relative to broad-band level for a level-independent mode); apply gain reduction only to the sibilant band (split-band) or to the entire signal (wideband). Fast attack 0.1-1 ms, release 10-50 ms, 3-8 dB reduction. Adaptive: listen-mode.
- Chain: vocal chain after compression, on mix bus for harsh cymbals/hi-hat (spectral).
- Pitfalls: lisping when wideband; level-dependent triggering; do before heavy HF boosts (exciters).

### 20. Dynamic EQ  [C: Trackspacer/dynamic EQ comparison]
- Per band: EQ filter with gain G (static) + dynamic gain Gd(t) controlled by a detector (own band or external key). Modes: compress above T (downward) or expand/boost below T (upward). Range limit 0..12 dB. Used for: boomy-note taming, vocal harshness, masking.
- Chain: track, bus, master (e.g. 250 Hz buildup when drums loud).
- Pitfalls: phase shift of EQ filter modulates over time; zipper at fast speeds; band loops (detector in the filtered path).

### 21. Spectral resonance suppression (Soothe2, Gullfoss-style)  [C: oeksound manual, Production Expert]
- Purpose: remove harsh/boomy resonances, and (Gullfoss) adaptive EQ across the spectrum, automatically.
- DSP (reconstruction [U exact algo]): STFT (window 2048-8192 at 48 k, hop = N/4 or N/8). Per bin: compute magnitude |X_k|, form a smoothed spectral envelope E_k (e.g. median or Gaussian smoothing over ~1/3 octave, ERB-warped), the "resonance" ratio r_k = |X_k|/E_k; gain g_k = 1 / (1 + depth*(max(0, r_k - 1))^sharpness) i.e. cut where the bin is a peak over its neighbourhood; smooth g_k in freq (width = sharpness) and time (attack/release 5-100 ms); also selectivity split into delta mode; mix wet/dry; M/S processing; HP/LP/bell "nodes" limit where it acts. Gullfoss additionally boosts where the spectrum has a "dip" relative to target (Recover/Tame).
- Chain: vocals, bus, mix bus, master (light).
- Genres: universal for cleanup; vocal, EDM leads, acoustic.
- Pitfalls: latency (FFT window), pre-ringing, transient smearing -> use transient handling; "phasey/swirly" at high depth; CPU.

### 22. Clippers, soft and hard, before a limiter (StandardClip, KClip)  [K]
- Purpose: shave short transient peaks so the limiter works less => louder, more punch, less limiter pumping.
- DSP: hard clip y=clamp(x, -c, c) (output ceiling c, typically -0.1..-1 dBFS before the limiter); soft clip: y = tanh(x), cubic x - x^3/3 for |x|<1, atan, or piecewise "knee" sinusoid; clip at 2x-8x oversampling (or antiderivative anti-aliasing: ADAA1: y = (F(x_n)-F(x_{n-1}))/(x_n-x_{n-1}) where F is the antiderivative of the clipper) with a linear-phase or half-band filter pair. Low drive amounts: 1-3 dB over the clipper threshold on transients only.
- Chain: drum bus, master bus before limiter, in front of the limiter (clip-to-limit, #55).
- Genres: hip-hop, EDM, pop, rock mastering.
- Pitfalls: harsh odd-harmonics when overdriven; aliasing (must oversample); intersample overs after the SRC filter; stereo-linked vs unlinked clipping.

### 23. Lookahead brickwall / true-peak limiter  [C: Softube true-peak article; K for internals]
- DSP: delay audio by L ms (1-5 ms; mastering 2-10 ms with soft attack), compute the gain needed to bring peak under the ceiling: g_req[n] = min(1, ceiling/|x_pk|), where peak detection uses an oversampled (4x-8x) path for true-peak; smooth the minimal gain over a window of the lookahead length (moving minimum then smooth with a Hann/triangle/two-stage) so the gain reduction ramps in before the peak; release 20-500 ms, often program-dependent (dual/triple-stage: fast release for transients, slow for sustained). Ceiling -1 dBTP (streaming), -0.3 dBFS (CD).
- Chain: last in the master chain (before dither).
- Pitfalls: release too fast -> LF distortion (gain ripples at audio rate); over-limiting loses crest/punch; inter-sample peaks after encode (codec adds up to ~+3 dB overs per one vendor blog [U]); latency.

### 24. Sidechain from filtered key / external triggers  [K]
- A key signal with HPF/BPF/LPF applied before the detector: compressors (HPF 100 Hz), de-essers (BPF 6k), ducking (kick LPF'd 100 Hz), ducking compressors with sidechain listen. "Key follows pitch" patterns such as vocal ducking the guitar with BP 1-4 kHz.

### 25. Ducking (voice-over, vocal-over-music)  [K]
- Gain rides on the music bus controlled by vocal envelope: attack 5-30 ms, hold 100-300 ms, release 200-800 ms, 1.5-4 dB depth; narrow-band (dynamic EQ at 1-4 kHz) preferred over full-band to preserve energy.

### 26. Auto-gain / gain staging / loudness matching  [K]
- Compensate post-processing gain: make-up = -(measured LUFS change) over a window, or estimate analytically for compressors (e.g. 0.5 * |reduction|); keep nominal bus levels -18 dBFS RMS (analog reference 0 VU) so nonlinear stages (tape, tube) work in the intended region. Fair A/B at equal loudness (+-0.2 dB).

### 27. Envelope followers (shared infrastructure)  [K]
- Peak: e[n] = max(|x|, a_r*e[n-1]) or attack/release one-pole: e = x>e ? a_a*e+(1-a_a)x : a_r*e+(1-a_r)x. RMS: 1-pole on x^2 with tau 5-300 ms then sqrt. Log domain smoothing for compressors. True-peak: 4x oversample first. Always NaN-guard and denormal-guard.

---------------------------------------------------------------------------------

## C. SATURATION AND HARMONICS

### 28. Waveshaping saturation (tube / transformer / generic)  [K]
- Memoryless NL: tanh (odd harmonics, symmetrical "transistor/transformer"), asymmetrical forms y = tanh(x + b) - tanh(b) (even harmonics, "tube"), arctan, x/(1+|x|), polynomial/cubic. Pre-gain (drive) 3-20 dB, post-trim to match loudness. Add DC blocker after asymmetric shapers. Transformer: also an LF HPF with LF-saturation increasing with level (hysteresis, #29) and mild HF roll-off.
- Chain: track, bus, master (very mild).
- Pitfalls: aliasing (oversample 2-8x or ADAA), DC, loudness-bias (louder sounds better), harsh high-frequency intermodulation on chords.

### 29. Tape emulation (hysteresis, head bump, HF loss, wow/flutter)  [K]
- Purpose: glue, soft compression, warmth, gentle HF smoothing.
- DSP: pre-emphasis (HF shelf) -> hysteresis model (Jiles-Atherton: dM/dH with anhysteretic Langevin curve, differential eq solved per sample with Runge-Kutta/ Newton; real-time versions by Chowdhury "Real-time physical modelling for analog tape machines" DAFx 2019 [K, not fetched]) -> de-emphasis. Head bump: bell +1..3 dB at 40-100 Hz (depends on speed: 15 ips ~ 50-70 Hz, 7.5 ips ~ 100 Hz), HF loss: LPF 12-18 kHz (speed dependent) with spacing loss, gap-loss comb dips; wow (0.5-2 Hz) and flutter (6-12 Hz, up to 100 Hz scrape) as an interpolated delay-line modulation, depth 0.01-0.2 % pitch; tape noise (pink-ish hiss) -60 dB; stereo azimuth/dual-mono differences.
- Chain: drum bus, mix bus, master; or per-track in "tape stack".
- Genres: rock, lo-fi hip-hop, indie, pop (subtle).
- Pitfalls: wow/flutter on sustained pads gives pitch seasickness; processing at 1x sampling aliasing; level-dependent so gain staging matters.

### 30. Console / summing nonlinearity (SSL, Neve, API channel & bus)  [K]
- Per channel, a very light NL (tanh with small drive, ~0.1-1 % THD), per-channel slight random/different gain/phase and a tiny crosstalk between channels (-60..-80 dB), pushing mix bus summing nonlinearity (soft saturation of the sum). Neve-style transformer: LF-saturation and 3rd harmonic; SSL: VCA clean + subtle grit; API: asymmetric saturation (odd harmonic) with proportional-Q EQ.
- Pitfalls: placebo-prone; implement mild or leave for the tone; crosstalk reduces stereo width slightly.

### 31. Exciters (Aphex Aural Exciter / Orange-style) & enhancers  [C: SOS, Muzines, Wikipedia]
- Purpose: clarity, presence, "air" without raising the EQ.
- DSP: HPF side chain (fc 2-5 kHz, often with a frequency-dependent phase shift = 1st/2nd-order all-pass or HPF) -> dynamic harmonic generator (level-dependent: more distortion at low level; compress one half-wave and expand the other => even+odd harmonics) -> level control -> mix back in small (-20..-10 dB). Output y = x + k*G(HPF(x)).
- BBE Sonic Maximizer: three-band split with frequency-dependent time delay (lows delayed ~ 2-3 ms relative to mids, highs earlier, i.e. compensating speaker driver time smear [U exact values]), plus dynamic HF/LF gain "process" and "lo contour".
- Chain: vocals, mix bus, master (mild).
- Genres: pop vocals, acoustic, 80s-style sound.
- Pitfalls: noise and sibilance enhancement; mono-incompatible when phase-shifted differently per channel; aliasing of the high-frequency generator (oversample or HF-limit the generated band).

### 32. Parallel saturation / multiband saturation  [K]
- Parallel: split signal; saturate with high drive (10-20 dB), HPF the wet to stay away from lows, blend -15..-6 dB. Multiband: split into 3-4 bands (LR4), independent drive per band (e.g. lows: gentle saturation for harmonics, highs: tape-like, mids: tube), sum; used for "dirt" in the mids without harshing the highs.
- Pitfalls: unmatched crossover phase between wet and dry; wet/dry latency mismatch; intermodulation within band.

### 33. "Air" band: high-shelf and harmonic-extended airband (Maag EQ4 "Air Band", 40 kHz band)  [K]
- Shelf with the corner at 10-40 kHz (analog) with a gentle slope, +2..+6 dB; in a 48 kHz digital chain emulate using a Baxandall high shelf at 10-12 kHz with a wide slope plus a bit of excitation. Maag's trick: very high corner means the filter's skirt boosts 10+ kHz smoothly with very little phase shift in the mid range. For 44.1/48 k use oversampling (96 k) to place a real high-shelf corner above Nyquist/2.
- Chain: vocals, master.
- Pitfalls: sibilance, noise boost, mp3/AAC encoder treats air above 16 kHz inconsistently.

### 34. Anti-aliasing for nonlinear stages (oversampling, ADAA)  [K]
- 2x-8x oversampling with half-band polyphase FIR/IIR filters before/after the NL (latency 0.2-1.5 ms for linear-phase; zero for min-phase IIR with some phase shift). ADAA (antiderivative anti-aliasing) replaces the NL f(x) by an average between successive samples through its antiderivative F: y = (F(x_n) - F(x_{n-1}))/(x_n - x_{n-1}), fallback f((x_n+x_{n-1})/2) when denominators tiny. Higher-order ADAA available (2nd). Cheaper than 8x oversampling; good for clip/tanh.
- Pitfalls: oversampling filter ringing & latency in sidechain-lookahead chains; ADAA adds half-sample delay and a mild lowpass.

---------------------------------------------------------------------------------

## D. EQ

### 35. Subtractive EQ / high-pass-everything-except-kick-and-bass  [K]
- HPF at 20-40 Hz on everything but sub elements; HPF at 80-120 Hz on guitars/synth/vocals; cuts at 200-400 Hz (mud), 500 Hz (box), 2-4 kHz (harsh), at Q 1-4, -2..-6 dB. Prefer cut over boost; "cut narrow, boost wide". 12 dB/oct or 18-24 for steep.
- Pitfalls: min-phase HPF phase rotation on a multi-mic source; thinness when the sum of HPFs removes more than expected.

### 36. Pultec low-end trick (boost and cut at the same frequency)  [C: MusicRadar, A-Designs, Music Guy]
- Purpose: tight, fat low end: a bump in the sub-lows with a dip above.
- Mechanism: the passive EQP-1A "boost" is a low shelf, "atten" a shelf at slightly different curve, so same-frequency settings do not cancel: net = bump at f and dip at ~100-400 Hz above it. Sources disagree on how much is tube saturation vs filter shape.
- DSP: low-shelf boost (fc = 60 Hz [30/60/100], +4..+8 dB, shelf Q low) and a second low-shelf (cut) at a slightly higher corner (fc*~1.6-2 [K, U]) of -3..-6 dB with a broader slope: net H(s)=Hboost*Hcut. Add a mild tanh for tube. Also the high-end Pultec "boost 10 kHz, atten 10 kHz" (HF boost with 12 kHz attenuation) gives smooth air.
- Chain: kick, bass, drum bus, master (+-1-2 dB).
- Pitfalls: boost raises the sub energy: check headroom; phase shift of shelving.

### 37. Baxandall tilt / shelves (and tilt EQ)  [K]
- Baxandall: two shelves from a single circuit, tone knobs: ~+-12 dB at 100 Hz and 10 kHz. Tilt EQ: pivot ~ 650 Hz-1 kHz, +x dB highs /-x dB lows with a gently sloped line ("BWDTilt"). Implementation: low shelf and high shelf with same corner and complementary gains: Hshelf(s) = A*(s^2 + sqrt(A)/Q s + A)/(A s^2 + sqrt(A)/Q s + 1) (RBJ cookbook). Use +-0.5-2 dB for master tonal balance.
- Chain: master, mix bus, vocal.

### 38. Linear-phase vs minimum-phase EQ  [K]
- Min-phase: IIR biquad/bell, zero latency, natural phase shift (acts like analog). Linear-phase: symmetric FIR via FFT convolution, pre-ringing, latency N/2 (10-50 ms), preserves phase relation; use on parallel-processed paths (so processed and dry align), on mastering M/S filtering, and in crossovers. Pre-ringing audible in LF steep filters on transients (kick) -> use medium-phase / "natural" hybrid.

### 39. Mid/Side EQ  [K]
- Encode M=(L+R)/2, S=(L-R)/2; EQ separately; decode L=M+S, R=M-S. Typical: HPF S at 100-150 Hz (mono bass), +1-2 dB shelf at 10 kHz on S (wider air), -1.5 dB 3 kHz on S, boost M 2-4 kHz for vocals. The M/S matrix is orthonormal if using scaled 1/sqrt(2): M=(L+R)/sqrt2.
- Pitfalls: errors of level balance; M/S in non-linear (compressor) stages creates different distortions on the sides.

### 40. Carving / complementary EQ between instruments  [K]
- Boost-this/cut-the-other at the same band: e.g. bass +2 dB 80 Hz, kick -2 dB 80 Hz, kick +2 dB 3 kHz click, bass -2 dB 3 kHz; vocal +2 dB 3 kHz, guitars -2 dB 3 kHz; "frequency slotting". Automate with dynamic EQ keyed from the competing element (#20). Measure with spectrum and masking meters (e.g. Bark-band masking overlays).

### 41. Notching resonances / ringing  [K]
- Sweep a narrow boost (Q 8-15, +10 dB) to find the ring, then cut -3..-8 dB with Q 6-12; use the dynamic variant when the resonance is note-dependent. Linear-phase for the narrow notch to avoid ringing smear.

### 42. Matching / reference EQ (spectral matching)  [K]
- Compute average spectra (1/3 octave, long-term) of mix and reference, derive a smoothed difference curve (max +-3 dB, wide smoothing), implement as linear-phase FIR/minimum-phase EQ with 10-30 bands. Pink-noise tilt target -4.5 dB/oct as a sanity curve for the mix spectrum.

---------------------------------------------------------------------------------

## E. STEREO AND SPACE

### 43. Mid/side processing in general  [C: SOS answers]
- Encoding & decoding as #39. Uses: widen (S gain +1..+4 dB, only above 150 Hz), narrow (S gain -x), mid-only compression for vocal/kick/bass punch, side-only reverb or saturation, side-only EQ. Width control w: S' = w*S; w=0 mono, w=1 unchanged, w=2 double. Mono-safe by construction (S cancels in mono). Pitfall: raising S raises peak levels, so apply before limiter.

### 44. Haas effect / precedence widening  [C: SOS "Can Haas delays be mono compatible"]
- Delay one channel 5-35 ms (precedence effect: image stays on the early side while the delayed side adds width). R = delay(mono, 8-25 ms), L = mono (or panned image). Wet-only HPF 150 Hz recommended by SOS to keep bass centred.
- Pitfalls: comb filtering in mono (notches at f = (2k+1)/(2*delay)); the guitar nearly vanishes in mono (per SOS forum case); instruments on hard-pan with short delays leaves the centre hollow.

### 45. Stereo widening via decorrelation (all-pass decorrelators, noise-burst/velvet-noise decorrelation)  [C: JUCE forum, SOS]
- Purpose: make a mono source wide, mono-compatible.
- DSP: artificial side = decorrelated copy of mid: S' = D(M) where D is a chain of cascaded all-pass sections (random/Schroeder all-pass with delays 1-30 ms and g ~ 0.3-0.7), or a velvet-noise FIR (sparse +-1 impulses, ~1000-2000 per sec, 20-40 ms length), or STFT random phase with smoothed phase curves; then L=M+w*S', R=M-w*S'. Because L+R = 2M, the artificial side cancels in mono, so the downmix is unchanged in magnitude spectrum. Apply HPF to S' (~150 Hz).
- A single delay is not enough (fully correlated at some frequencies => comb) [JUCE].
- Pitfalls: all-pass decorrelators smear transients (ringing 10-30 ms) => keep kick/snare mono; too much sounds roomy and phasey; time-varying all-pass (slow LFO) for dynamic width creates chorus-like motion.

### 46. Stereo chorus / ensemble width  [K]
- 2-4 modulated delay lines per side (base 7-25 ms, LFO 0.1-1.5 Hz depth 1-6 ms, LFO phases offset 90-180° between L and R), mixed 20-50 %; Juno/Roland-style 2 phase-locked delays with triangle LFOs at 0.5 and 0.8 Hz; ensemble (Solina) 3 phases 120° apart. Mono: LFO-modulated comb notches (swirl) but typically stable if wet <= 50 %. Chain: pads, guitars, synth, vocal doubles.

### 47. Double-tracking simulation (ADT, "automatic double tracking")  [K]
- Delay 15-60 ms with slow random modulation (0.1-1 Hz, depth 1-3 ms), pitch detune +-5-15 cents (via short granular pitch shifter or modulated delay), slight EQ/tilt difference, panned opposite; mix -3..-8 dB. Two-voice doubler: L = x + 0.7 * d1(x) ; R = x + 0.7 * d2(x) with d1, d2 independently modulated. Mono-compatible-ish when modulation is incoherent and delay > 20 ms. Genres: vocals, guitars, pop, rock.

### 48. Panning laws and LCR panning  [K]
- Pan law for pan position p in [0,1]: constant-power gL=cos(p*pi/2), gR=sin(p*pi/2) (-3 dB center); -4.5 dB compromise: sqrt of the product of linear and constant-power; linear (-6 dB center). Mono sum of constant-power pan is not constant (sum gets +3 dB louder at center on mono). LCR mixing: only hard L, C, R; creates a wide image, preserves the centre punch; with panning narrow images via two overlaps.

### 49. Mono compatibility checks  [K]
- Correlation meter: r = sum(L*R)/sqrt(sum(L^2)sum(R^2)) over 300 ms windows (aim for > 0 mostly); goniometer/Lissajous; mono-sum null test (M vs original perceived loudness); compare LUFS of L+R mono to stereo: a drop > 3 dB indicates phase problems.

### 50. Pre-delay (reverb) and early reflections  [K]
- Pre-delay 10-40 ms (vocals, sets distance and clarity), tempo-synced to 1/32 - 1/16 note for rhythm. Early reflections: 4-24 taps from 5 to 80 ms, panned, with decaying amplitude and slight HF roll-off, giving a "room" cue; plus a separate ER-only patch to place a dry source in a space without tail. Use ER-only for double-tracking-like width.

### 51. Reverb sends with EQ and Abbey Road trick  [C: Soundfly, Mixdown]
- Pre-reverb EQ: HPF 600 Hz (12-18 dB/oct), LPF 10 kHz (12 dB/oct) before the reverb; variants LPF 6 kHz (darker), 2 kHz on drums. Optionally saturator in front of the EQ (Mixdown). Result: cleaner, can raise the return level.
- Return post-EQ: HPF 200-300 Hz, LPF 8-10 kHz, optionally de-ess.
- Sources are mix tutorials, not the studio itself. Chain: reverb bus on aux.

### 52. Reverb types: plate, spring, room, hall, convolution, shimmer  [K]
- Algorithmic FDN (feedback delay network, 8-16 lines, Hadamard/Householder feedback matrix, damping filters per line, 1-pole LPF + low shelf in-loop for frequency-dependent RT60, all-pass input diffusion, LFO modulation of delays 0.5-2 ms, to hide metallic ringing; Valhalla / Lexicon style). Plate: dense, bright, fast build up (EMT 140: RT 1-3 s, no early reflections), implement as FDN with strong diffusion & HF emphasis. Spring: dispersion chain of allpasses (chirp "boing") + LPF + delay ~30-60 ms + LF saturation. Convolution: FFT partitioned convolution with IRs (uniform/nonuniform partitioning for latency). Shimmer: reverb with pitch-shifter (+12 st) inside feedback loop and gain < 1 => rising octave harmonic tails; (granular or phase-vocoder shifter, mix feedback 0.4-0.7).
- Pitfalls: metallic modes in short FDNs; CPU of convolution; shimmer can run away if loop gain >= 1; LFO modulation in reverb tails can detune.

### 53. Reverb/delay ducking (sidechained returns)  [K]
- Compressor on the return with key = dry source (vocal): wet ducks 3-10 dB while the source sounds, comes up in pauses (attack 1-10 ms, release 100-400 ms ~ phrase gap). Also gated "dry-in" variants. Result: intelligible vocals with big space. Genres: pop, ballads, EDM vocals.
- Pitfalls: audible pumping on the reverb if release too short.

### 54. Gated reverb (Phil Collins/AMS RMX16 'nonlin')  [K]
- Dense reverb (RT 1-2 s) -> gate with threshold from the *dry* snare, hold 80-250 ms, release 5-50 ms (fast, non-linear sudden cut), so the tail suddenly cuts. Alternative: reverse of Envelope shape. Also use: send snare to reverb that is gated by its own level. Genres: 80s drums, synth-pop, rock ballads (retro).

### 55. Delay types: ping-pong, tempo-synced, delay throws, tape echo, filtered feedback, modulated  [K]
- Delay time t = (60/BPM)*note factor (1/4 = 60/BPM; dotted 1/8 = 0.75*60/BPM; triplet 1/8 = 1/3*60/BPM). Feedback 0.2-0.6 with feedback filter HPF 150-300 Hz & LPF 3-6 kHz (each repeat gets darker; tape echo). Ping-pong: L tap feeds R delay and vice versa (cross feedback). Throw: automate send on one word/last word of phrase; send level up for ~1 beat. Tape echo: modulated delay (wow/flutter 0.2-5 Hz depth 0.1-1 ms), soft clip in loop (tanh), HF loss. Modulated delay: chorus-ier repeats. Ducked delay: key=dry signal (#53). Stereo offset delays (L 1/8, R 1/8 dotted) for "Haas-like" width without comb.
- Pitfalls: unity-gain loop = runaway (clip/limiter in loop); delay-time change zipper => crossfade or interpolate; feedback + LFO modulated detuning; stability (|g|<1).

### 56. Depth placement: front/back  [K]
- Far: HF rolloff (air absorption: LPF 4-8 kHz, gentle -6 dB), lower level, higher reverb:dry ratio (wet 30-60 %), longer pre-delay absent (pre-delay short for distance, since direct/ER ratio shrinks), transient softening (reduce attack with transient shaper -3..-6 dB, or slower attack on compressor), mono narrower. Near: dry, bright (+HF), low reverb, transient emphasised, pre-delay 20-40 ms, wide ER absent. Also the "distance" macro: gain -6 dB per doubling + HF LPF cutoff dropping with distance.

---------------------------------------------------------------------------------

## F. MASTER CHAIN

### 57. Typical modern mastering chain order  [K; general consensus, no single source]
1. Utility: DC / subsonic HPF 20-30 Hz, gain staging to ~-18 LUFS-ish or -6 dB peak headroom. 2. Corrective EQ (linear-phase/M-S), dynamic EQ and resonance suppressor. 3. Tone EQ (tilt, Pultec shape, airband). 4. Saturation/tape (subtle, oversampled). 5. Stereo shaping (M/S width, mono-bass). 6. Glue compressor (1-2 dB, 2:1, slow attack). 7. Multiband compressor (optional). 8. Soft clipper (0.5-3 dB shaving). 9. Limiter (true-peak, 2-6 dB reduction, ceiling -1 dBTP). 10. Dither (only for bit-depth reduction) and meters (LUFS, TP, correlation, spectrum). Order variations: EQ after compressor for tonal corrections; M/S compression early.
- Pitfalls: stacked latencies; clipping the ISPs; gain chain level matching.

### 58. Loudness targets (LUFS) and true peak  [C: Roex, MasteringBox, Softube]
- LUFS = BS.1770: K-weighting (high-shelf +4 dB at ~1.5 kHz, then RLB HPF at ~38 Hz), mean square over 400 ms blocks (momentary), 3 s (short-term), gated integrated (absolute -70 LUFS, relative -10 LU gate). True peak = 4x oversampled peak (dBTP).
- Targets (sources vary and are vendor blogs): Spotify ~-14 LUFS (also "Loud" -11), YouTube ~-14, Apple Music ~-16 (Sound Check; whether it boosts quiet tracks is disputed between sources), Tidal ~-14, Deezer ~-15, Amazon ~-14 with -1 or -2 dBTP; peak ceiling ~-1 dBTP (-2 for safer lossy-codec margin). Broadcast EBU R128 = -23 LUFS +-1, TP -1; US ATSC A/85 -24 LKFS.
- Practical: EDM/hip-hop master around -8 to -11 LUFS accepts normalisation down; dynamic genres -14 to -18. Verify platform specs yourself.

### 59. Stem mastering  [K]
- Master 4-8 stems (drums, bass, music, vocals, FX) instead of the stereo mix: allows balancing and processing per group (e.g. compress drums, de-ess vocals) then summing through the master chain. Needs headroom stems at -6 dB, processing latency compensated, bounce at same length; final limiter on sum.

### 60. Clip-to-limit / multi-stage limiting  [K]
- Clip (#22) shaves 2-4 dB peaks, then a limiter (#23) with 1-3 dB reduction. Often 2 limiters in series: first transparent 1-2 dB (fast), then final ceiling. Intersample safe: clip at -1 dBFS before limiter with TP limiter ceiling -1 dBTP.

### 61. Multiband limiting  [K]
- Split LR4/linear-phase into 3-4 bands; limiter per band with a shared or different ceiling, sum; then final broadband limiter to catch sum peaks. Allows heavy low-end loudness without the highs pumping, or the highs held while low is released slowly. Pitfalls: band sum overshoot (sum of ceilings > ceiling) and crossover ripple, so a final true-peak limiter is mandatory.

### 62. Mid/side limiting & dynamics  [K]
- Limit M and S separately with different thresholds: S threshold lower than M to prevent the side from dominating or to protect mono compat; linking options to avoid image shifts. Clip M at -0.5 dB, S at -2 dB. Pitfall: decode L/R peak can exceed sum of M and S ceilings by up to the sum (M peak + S peak) so a final L/R limiter is still needed.

### 63. Dither and noise shaping  [K; C for true-peak interplay with dither]
- When reducing to 16 bits, add TPDF noise (difference of two uniform random numbers, +-1 LSB, triangular PDF) before rounding: y = round(x*32767 + r1 - r2)/32767. It decorrelates quantisation error from the signal (removes distortion at low levels in tails and fades), turning it into a flat -98 dBFS-ish noise floor (16 bit). Noise shaping: filter error with e.g. 5-9 tap highpass-heavy filter so noise lies at 15-22 kHz where hearing is less sensitive, reduces perceived noise by ~10 dB in sensitive region (POW-r, MBIT+). No dither at 24 bit (below -140 dB) except when going through many steps. Dither is the very last stage; do it once.
- Pitfalls: dither after a TP limiter can push peaks over (re-check TP; per forum); double dither; shaped dither under lossy encoding is useless.

### 64. Oversampling for nonlinear stages in the master  [C: forum summaries; K]
- 4x for limiter detection and clippers, 2-4x for saturators; use half-band minimum-phase filters (low latency) in production, linear-phase in the final. Sample rate conversion before limiting (not after) avoids SRC-generated overs.

### 65. Inter-sample peak (ISP) handling  [C: MusicTech, Softube, forum]
- ISP: reconstruction lowpass of the samples can exceed the sample values; up to +3 dB when the signal is heavily clipped around fs/4 (sine at fs/4 with phase 45° gives all samples at 0.707 and true peak 1.0). Detect with 4x oversample (sinc/poly) max; limiter ceiling -1 dBTP; post-lossy-encode (MP3/AAC) overs possible up to ~+1-3 dB [U].

### 66. Reference tracks and loudness-match A/B  [K]
- Check against references at matched LUFS; compare 1/3-octave spectrum, stereo width, crest, LRA. Not DSP processing but the most-used QC.

---------------------------------------------------------------------------------

## G. AUTOMATION AND MOVEMENT

### 67. Filter rises / sweeps / risers  [K]
- Automate LPF or HPF cutoff on 4-16 bars (exponential map: fc = f0*(f1/f0)^t), resonance 0.2-0.6 for emphasis, optionally with a noise riser (white noise HPF-swept, amplitude and pitch rising), reverse-reverb swell (reverse of reverb tail pre-trigger), downlifter with the opposite motion; add an impact (sub + noise burst) at the drop. HPF sweep on the master bus in the build with HPF Q 0.7 (drop of bass), then bypass on the drop for contrast.
- Pitfalls: zipper noise from stepped cutoff (smooth 5-20 ms); resonant peaks clip the limiter; the sweep amplitude jumps.

### 68. Volume rides and macro dynamics  [K]
- Automate fader gain +-1-4 dB per phrase (vocals); section-level changes (chorus +1 dB, verse -1 dB), "fader riding" pre-compressor to even loudness for compressors; the "stage" approach by section to create arrangement impact beyond limiter. Alternative: algorithmic leveler (slow RMS following, +-6 dB, 0.5-2 s time constant), aka "vocal rider".

### 69. Pumping as a groove (rhythmic gain modulation)  [K]
- Intentional: sidechain/LFO volume shaper tied to the tempo: curve per beat g(t)=1 - d*(1 - (t/T)^p), with release shaped exponential (p=2..4) to land back at unity before the next beat; depth 6-20 dB, applied on pads/bass/whole mix. Multiple LFO shapes on a "gate" (trance gate): 16-step pattern of gains with smoothing 3-10 ms. Also, subtle swing: nudge shaper step times by 5-20 %.

### 70. Parameter modulation (LFO/envelope-controlled filters & stereo)  [K]
- Autopan (LFO to pan; 0.1-4 Hz), tremolo (amplitude LFO 4-8 Hz), auto-filter (envelope follower -> cutoff), phaser/flanger (all-pass chain 4-12 stages, LFO 0.05-1 Hz, feedback 0-0.7) on busses to give life, subtle in master: slow pan movement of S (width LFO).

---------------------------------------------------------------------------------

## H. RECENT / 2020-2026 STANDARDS (DSP only)  [K, nothing verified online]

### 71. Smart sidechain/spectral ducking with gain-smoothing (Trackspacer, Gullfoss, Soothe2)  -> see #5, #21. Standard in 2020s mixing.
### 72. Soft-clipper-before-limiter loudness chain (StandardClip, KClip, Minimal Audio) -> #22; essentially universal in hip-hop/EDM mastering since ~2018.
### 73. Transparent oversampled true-peak limiting with loudness-target presets (Ozone, Pro-L 2, Limitless) -> #23.
### 74. Dynamic/phase-aware stereo imagers, band-wise width, mono-below in the multiband imager -> #3, #43, #45.
### 75. Spectral shaping "tilt" and "reference-matching" (Gullfoss, Ozone Match, Tonal Balance Control) -> #37, #42 (analysing target curves).
### 76. Mastering-grade ADAA / polynomial oversampled saturators (low CPU) -> #34.
### 77. Loudness-normalised streaming practice: aim ~-9 to -14 LUFS-I with TP -1; peak-to-loudness ratio (PLR) 8-10 dB; short-term LUFS 'S' maximum used for drops.
### 78. Neural / ML-assisted processing: DSP-wise it is usually a learned EQ/compressor parameter predictor. Out of scope here.

---------------------------------------------------------------------------------

## Ideas that fit Klang quickly (suggested, my judgment, [U] on benefit)
1. Master bus: DC/subsonic HPF -> (optional) tilt/Pultec shelf -> mono-below-120 Hz (M/S HPF on S) -> glue compressor (key HPF 100 Hz) -> soft clipper (oversampled 2x/ADAA) -> look-ahead true-peak limiter -> (dither only at 16-bit export).
2. Per-orbit: "send EQ" HPF 600/LPF 10k before reverb and delay; ducked reverb/delay returns keyed from the dry orbit signal.
3. Bass: harmonic enhancer on the effect side (rectifier + tanh on the <120 Hz band) in addition to the additive partials.
4. Kick/bass: the voice-level sidechain to already-existing duck effect + a dynamic-EQ-style band ducking (30-150 Hz only).
5. Width: M/S width with S-HPF 150 Hz, optional velvet-noise decorrelator for mono sources.
6. Transient shaper (differential envelope) and parallel compression on the drum orbit.
Everything with a nonlinearity: oversample or ADAA and guard NaN/denormals; keep block size 128 as per project guardrails.
