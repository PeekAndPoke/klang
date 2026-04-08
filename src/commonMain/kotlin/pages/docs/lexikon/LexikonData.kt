package io.peekandpoke.klang.pages.docs.lexikon

// -- Domain ------------------------------------------------------------------------------------------------------ //

enum class LexikonDomain(val label: String) {
    Audio("Audio"),
    Pattern("Pattern"),
    Technical("Technical"),
}

// -- Category ---------------------------------------------------------------------------------------------------- //

enum class LexikonCategory(val label: String, val domain: LexikonDomain) {
    // Audio signal chain (in pipeline order)
    Tuning("Tuning", LexikonDomain.Audio),
    Degrading("Degrading", LexikonDomain.Audio),
    Carving("Carving", LexikonDomain.Audio),
    Contouring("Contouring", LexikonDomain.Audio),
    Saturating("Saturating", LexikonDomain.Audio),
    Animating("Animating", LexikonDomain.Audio),
    Mixing("Mixing", LexikonDomain.Audio),
    Spatializing("Spatializing", LexikonDomain.Audio),

    // Pattern event operations
    Sequencing("Sequencing", LexikonDomain.Pattern),
    Cycling("Cycling", LexikonDomain.Pattern),
    Arranging("Arranging", LexikonDomain.Pattern),
    Pacing("Pacing", LexikonDomain.Pattern),
    Gating("Gating", LexikonDomain.Pattern),
    Mapping("Mapping", LexikonDomain.Pattern),
    Layering("Layering", LexikonDomain.Pattern),
    Spawning("Spawning", LexikonDomain.Pattern),

    // Technical reference
    Abbreviations("Abbreviations", LexikonDomain.Technical),
    MotorTerms("Motör Terms", LexikonDomain.Technical),
}

// -- Tag --------------------------------------------------------------------------------------------------------- //

enum class LexikonTag(val label: String) {
    Fundamental("Fundamental"),
    Effect("Effect"),
    Filter("Filter"),
    Envelope("Envelope"),
    Synthesis("Synthesis"),
    Abbreviation("Abbreviation"),
    Motor("Motör"),
}

// -- Entry ------------------------------------------------------------------------------------------------------- //

data class LexikonTrivia(
    val text: String,
    val year: String? = null,
    val person: String? = null,
    val device: String? = null,
    val url: String? = null,
)

data class LexikonEntry(
    val term: String,
    val category: LexikonCategory,
    val tags: Set<LexikonTag>,
    val summary: String,
    val detail: String,
    val conventional: String? = null,
    val trivia: LexikonTrivia? = null,
) {
    val domain: LexikonDomain get() = category.domain
}

// -- All entries ------------------------------------------------------------------------------------------------- //

val allLexikonEntries: List<LexikonEntry> = listOf(

    // =============================================================================================================
    // AUDIO — Tuning
    // =============================================================================================================

    LexikonEntry(
        term = "Tuning",
        category = LexikonCategory.Tuning,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Filter),
        summary = "Priming filter cutoffs before the signal chain runs.",
        detail = "At the start of each audio block, the engine updates control parameters — filter cutoffs, " +
                "modulation depths — so that downstream stages respond to modulation sources. " +
                "This is a control-rate operation: it sets the stage, but doesn't process audio itself.",
        conventional = "Control-rate modulation, parameter automation",
    ),

    // =============================================================================================================
    // AUDIO — Degrading
    // =============================================================================================================

    LexikonEntry(
        term = "Degrading",
        category = LexikonCategory.Degrading,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Effect),
        summary = "Irreversibly reducing resolution to create lo-fi, digital artifacts.",
        detail = "Degrading throws away information. The result is grittier, harsher, more \"digital\". " +
                "Because it's destructive, it sits early in the signal chain — before filtering, " +
                "so you can tame the new harmonics it introduces.",
        conventional = "Bit-crushing, sample-rate reduction, decimation",
    ),

    LexikonEntry(
        term = "Crush",
        category = LexikonCategory.Degrading,
        tags = setOf(LexikonTag.Effect),
        summary = "Reduces bit depth — fewer amplitude steps means a grittier, more stepped sound.",
        detail = "Like hearing audio through a cheap walkie-talkie. Higher crush values = fewer bits = " +
                "more extreme quantization. Adds harsh harmonics and a characteristic \"crunchy\" texture.",
        conventional = "Bit-crush, bit-depth reduction",
        trivia = LexikonTrivia(
            text = "Bit-crushing was originally an unintentional artifact of early low-resolution samplers. " +
                    "The E-mu SP-1200 (1987) had 12-bit converters and a 26 kHz sample rate that gave it a " +
                    "warm, crunchy character. Producers like DJ Premier and Pete Rock turned that limitation " +
                    "into the defining sound of golden-age hip-hop. Bit-crushing as an intentional effect " +
                    "only emerged in the late 1990s with software plugins recreating this degradation on purpose.",
            year = "1987",
            person = "E-mu Systems",
            device = "E-mu SP-1200",
            url = "https://en.wikipedia.org/wiki/Bitcrusher",
        ),
    ),

    LexikonEntry(
        term = "Coarse",
        category = LexikonCategory.Degrading,
        tags = setOf(LexikonTag.Effect),
        summary = "Reduces sample rate — fewer time steps means an aliased, metallic quality.",
        detail = "Like audio from a 1980s sampler. The sound loses high-frequency detail and gains " +
                "staircase-like artifacts. Higher coarse values = more aggressive downsampling.",
        conventional = "Sample-rate reduction, decimation, downsampling",
    ),

    // =============================================================================================================
    // AUDIO — Carving
    // =============================================================================================================

    LexikonEntry(
        term = "Carving",
        category = LexikonCategory.Carving,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Filter),
        summary = "Removing frequencies to shape the sound's character.",
        detail = "A carved sound has less in it — sharper, more focused, less muddy. " +
                "You carve with filters: LP to remove highs, HP to remove lows, BP to keep only a band. " +
                "Sits after degrading in the chain, so it can clean up the harmonics that crushing introduced.",
        conventional = "Subtractive filtering, EQ",
    ),

    LexikonEntry(
        term = "LP (Low-Pass)",
        category = LexikonCategory.Carving,
        tags = setOf(LexikonTag.Filter, LexikonTag.Abbreviation),
        summary = "Lets low frequencies through, dulls the highs.",
        detail = "Sound gets darker, warmer — like hearing music through a wall. " +
                "The cutoff knob controls where \"bright\" ends and \"dark\" begins. " +
                "The most common filter in synthesis. Turn it down for muffled pads, turn it up for bright leads.",
        conventional = "Low-pass filter, LPF",
        trivia = LexikonTrivia(
            text = "Robert Moog's transistor ladder filter (patented 1966) was the breakthrough that " +
                    "made subtractive synthesis viable as a musical technique. The resonant low-pass filter " +
                    "became THE defining sound-shaping tool — first in the Moog Modular, then in the " +
                    "Minimoog (1970), which put it in the hands of every studio musician.",
            year = "1966",
            person = "Robert Moog",
            device = "Moog Modular / Minimoog",
            url = "https://en.wikipedia.org/wiki/Moog_synthesizer#Transistor_ladder_filter",
        ),
    ),

    LexikonEntry(
        term = "HP (High-Pass)",
        category = LexikonCategory.Carving,
        tags = setOf(LexikonTag.Filter, LexikonTag.Abbreviation),
        summary = "Lets high frequencies through, removes the bottom.",
        detail = "Sound gets thinner, airier — like turning down the bass knob. " +
                "Use it to clean up muddy low-end, create telephone effects, or thin out a pad " +
                "so it doesn't clash with the bass line.",
        conventional = "High-pass filter, HPF",
    ),

    LexikonEntry(
        term = "BP (Band-Pass)",
        category = LexikonCategory.Carving,
        tags = setOf(LexikonTag.Filter, LexikonTag.Abbreviation),
        summary = "Keeps only a frequency band, removes everything above and below.",
        detail = "Sounds nasal, telephone-like — only a slice of the spectrum gets through. " +
                "You control the center frequency and bandwidth. " +
                "Great for radio effects, vocal formants, or isolating a specific frequency range.",
        conventional = "Band-pass filter, BPF",
    ),

    LexikonEntry(
        term = "Notch",
        category = LexikonCategory.Carving,
        tags = setOf(LexikonTag.Filter),
        summary = "Removes one frequency band, keeps everything else.",
        detail = "The opposite of band-pass — it cuts a narrow slot out of the spectrum. " +
                "Useful for removing a specific resonance or unwanted frequency without affecting the rest.",
        conventional = "Notch filter, band-reject filter",
    ),

    // =============================================================================================================
    // AUDIO — Contouring
    // =============================================================================================================

    LexikonEntry(
        term = "Contouring",
        category = LexikonCategory.Contouring,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Envelope),
        summary = "Shaping the sound's amplitude over time — how it begins, lives, and dies.",
        detail = "Every sound has a life arc: it starts, it sustains, it fades. " +
                "Contouring defines that arc using an ADSR envelope. " +
                "A piano has fast attack and no sustain. A violin has slow attack and full sustain. " +
                "This is what makes a sound feel percussive, sustained, swelling, or abrupt.",
        conventional = "Amplitude envelope, VCA envelope",
    ),

    LexikonEntry(
        term = "ADSR",
        category = LexikonCategory.Contouring,
        tags = setOf(LexikonTag.Envelope, LexikonTag.Abbreviation, LexikonTag.Fundamental),
        summary = "Attack, Decay, Sustain, Release — the four stages of a volume envelope.",
        detail = "Attack: how quickly the sound reaches full volume (fast = percussive, slow = swelling). " +
                "Decay: how quickly it falls from peak to sustain level. " +
                "Sustain: the steady-state volume while a note is held. " +
                "Release: how quickly it fades after the note ends.",
        conventional = "ADSR envelope",
        trivia = LexikonTrivia(
            text = "The four-stage ADSR envelope was formalized by Robert Moog in the mid-1960s. " +
                    "The Moog Modular synthesizer (~1965) was the first commercial instrument with a " +
                    "dedicated ADSR envelope generator as a standard module. The concept drew on earlier " +
                    "acoustics research describing how sounds evolve over time, but Moog's engineering " +
                    "turned it into a practical, voltage-controlled building block that became universal.",
            year = "~1965",
            person = "Robert Moog",
            device = "Moog Modular Synthesizer",
            url = "https://en.wikipedia.org/wiki/Envelope_(music)#ADSR",
        ),
    ),

    // =============================================================================================================
    // AUDIO — Saturating
    // =============================================================================================================

    LexikonEntry(
        term = "Saturating",
        category = LexikonCategory.Saturating,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Effect),
        summary = "Adding harmonics through nonlinear waveshaping.",
        detail = "Pushes the signal into a transfer curve that clips or folds it, generating new overtones. " +
                "Gentle saturation = warm, analog-like character. Heavy saturation = aggressive, fuzzy. " +
                "Runs before the filters so that LP/HP/BP have the final say over the frequency spectrum.",
        conventional = "Distortion, overdrive, waveshaping, fuzz",
    ),

    LexikonEntry(
        term = "Distort",
        category = LexikonCategory.Saturating,
        tags = setOf(LexikonTag.Effect),
        summary = "Applies waveshaping distortion with controllable amount and shape.",
        detail = "The amount controls how hard the signal is driven into the transfer curve. " +
                "The shape selects the type of nonlinearity — soft clip for warmth, hard clip for aggression, " +
                "fold for metallic complexity.",
        conventional = "Distortion, overdrive, clipping",
        trivia = LexikonTrivia(
            text = "Distortion was discovered accidentally through damaged equipment. A key early moment: " +
                    "Ike Turner's \"Rocket 88\" (1951), where a punctured amp speaker produced fuzzy tones. " +
                    "The first intentional fuzz device was the Maestro FZ-1 Fuzz-Tone (1962), built by " +
                    "Glenn Snoddy after investigating a faulty mixing console. It became a sensation when " +
                    "Keith Richards used it on \"(I Can't Get No) Satisfaction\" (1965).",
            year = "1962",
            person = "Glenn Snoddy / Maestro",
            device = "Maestro FZ-1 Fuzz-Tone",
            url = "https://en.wikipedia.org/wiki/Distortion_(music)#History",
        ),
    ),

    // =============================================================================================================
    // AUDIO — Animating
    // =============================================================================================================

    LexikonEntry(
        term = "Animating",
        category = LexikonCategory.Animating,
        tags = setOf(LexikonTag.Fundamental, LexikonTag.Effect),
        summary = "Periodic cycling of a parameter, driven by an LFO. Makes the sound move and breathe.",
        detail = "An LFO (Low Frequency Oscillator) generates a slow wave — usually below 20 Hz — " +
                "that modulates a parameter over time. The parameter goes up and down rhythmically, " +
                "creating motion. Different targets produce different animations: " +
                "volume = tremolo, pitch = vibrato, spectrum = phaser.",
        conventional = "LFO modulation",
    ),

    LexikonEntry(
        term = "Tremolo",
        category = LexikonCategory.Animating,
        tags = setOf(LexikonTag.Effect),
        summary = "Amplitude animation — volume goes up and down rhythmically.",
        detail = "Like a guitarist's amp tremolo. Creates a pulsing, breathing quality. " +
                "Rate controls how fast it pulses, depth controls how extreme the volume swings are. " +
                "Subtle tremolo adds warmth; extreme tremolo creates rhythmic chopping.",
        conventional = "Tremolo, amplitude modulation (AM)",
        trivia = LexikonTrivia(
            text = "One of the oldest electronic effects. Don Leslie built the Leslie speaker (1941), " +
                    "producing both tremolo and Doppler pitch modulation. The Fender Tremolux (1955) was " +
                    "one of the first guitar amps with built-in electronic tremolo. Fun fact: Fender " +
                    "famously mislabeled everything — the \"vibrato\" bar on the Stratocaster actually " +
                    "produces pitch changes (true vibrato), while the \"vibrato\" channel on Fender amps " +
                    "actually produces tremolo (volume changes).",
            year = "1941",
            person = "Don Leslie / Leo Fender",
            device = "Leslie Speaker, Fender Tremolux",
            url = "https://en.wikipedia.org/wiki/Tremolo",
        ),
    ),

    LexikonEntry(
        term = "Phaser",
        category = LexikonCategory.Animating,
        tags = setOf(LexikonTag.Effect),
        summary = "Spectral animation — sweeping notches move through the frequency spectrum.",
        detail = "Creates a \"whooshing\" or \"jet engine\" quality by sweeping a series of notch filters " +
                "up and down the spectrum. Rate, depth, center, and sweep control the motion. " +
                "Can be applied per-voice or per-cylinder.",
        conventional = "Phaser, phase shifting",
        trivia = LexikonTrivia(
            text = "The phaser effect was first achieved using tape manipulation at Abbey Road Studios — " +
                    "Ken Townsend developed Automatic Double Tracking for The Beatles in 1966. The first " +
                    "electronic phaser pedal was the Maestro PS-1 Phase Shifter (~1971), but it was the " +
                    "MXR Phase 90 (1974) that made the effect a staple. Eddie Van Halen's use of the " +
                    "Phase 90 on early Van Halen records cemented it as a rock guitar essential.",
            year = "1974",
            person = "MXR / Ken Townsend",
            device = "MXR Phase 90",
            url = "https://en.wikipedia.org/wiki/Phaser_(effect)",
        ),
    ),

    // =============================================================================================================
    // AUDIO — Mixing
    // =============================================================================================================

    LexikonEntry(
        term = "Mixing",
        category = LexikonCategory.Mixing,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Combining multiple sound sources by summing their signals.",
        detail = "When orbits and channels play simultaneously, their audio signals are added together. " +
                "Each channel's gain (volume level) controls how loud it is in the mix. " +
                "Good mixing means giving each sound its own space — in volume, frequency, and stereo position.",
        conventional = "Mixing, gain staging, summing",
    ),

    // =============================================================================================================
    // AUDIO — Spatializing
    // =============================================================================================================

    LexikonEntry(
        term = "Spatializing",
        category = LexikonCategory.Spatializing,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Placing the sound in the stereo field — left, center, or right.",
        detail = "Controls where you hear the sound between your speakers or headphones. " +
                "Pan 0.0 = hard left, 0.5 = center, 1.0 = hard right. " +
                "Spreading sounds across the stereo field creates width and clarity in the mix.",
        conventional = "Panning, stereo imaging",
    ),

    // =============================================================================================================
    // PATTERN — Sequencing
    // =============================================================================================================

    LexikonEntry(
        term = "Sequencing",
        category = LexikonCategory.Sequencing,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Events happen in an order, one after another, within a cycle.",
        detail = "A pattern is a sequence of musical instructions — notes, rests, chords — arranged in time. " +
                "Sequencing is the fundamental concept: things happen in order. " +
                "Everything in the pattern domain builds on this.",
    ),

    // =============================================================================================================
    // PATTERN — Cycling
    // =============================================================================================================

    LexikonEntry(
        term = "Cycling",
        category = LexikonCategory.Cycling,
        tags = setOf(LexikonTag.Fundamental),
        summary = "The pattern repeats — one cycle is one complete pass through the sequence.",
        detail = "Every pattern in Klang loops. The cycle length is tied to tempo (BPM). " +
                "This is the heartbeat of everything. One cycle = one complete repetition of the pattern. " +
                "Most transformations operate within or across cycles.",
    ),

    // =============================================================================================================
    // PATTERN — Arranging
    // =============================================================================================================

    LexikonEntry(
        term = "Arranging",
        category = LexikonCategory.Arranging,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Reordering events within a cycle without adding or removing them.",
        detail = "The sequence stays the same size, just reorganized. " +
                "rev plays the pattern backwards. palindrome plays forward then backward. " +
                "rotate shifts events in time within the cycle. " +
                "The raw material stays the same — only the order changes.",
    ),

    // =============================================================================================================
    // PATTERN — Pacing
    // =============================================================================================================

    LexikonEntry(
        term = "Pacing",
        category = LexikonCategory.Pacing,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Changing the speed or density of events.",
        detail = "fast compresses events into less time — double speed means twice as dense. " +
                "slow stretches events over more time — half speed means twice as sparse. " +
                "Changes how many events fit in one cycle without changing the events themselves.",
    ),

    // =============================================================================================================
    // PATTERN — Gating
    // =============================================================================================================

    LexikonEntry(
        term = "Gating",
        category = LexikonCategory.Gating,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Conditionally selecting which events play.",
        detail = "Introduces variation over time by applying rules about when something happens. " +
                "every applies a transformation every Nth cycle. " +
                "when applies only when a condition is true. " +
                "sometimesBy applies with a probability. " +
                "Events are allowed through or blocked — like a gate opening and closing.",
    ),

    // =============================================================================================================
    // PATTERN — Mapping
    // =============================================================================================================

    LexikonEntry(
        term = "Mapping",
        category = LexikonCategory.Mapping,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Transforming values per event — the pattern structure stays the same, the values change.",
        detail = "+12 transposes up an octave. scale maps values into a musical scale. " +
                "transpose shifts pitch by a fixed amount. " +
                "The pattern's timing and structure are preserved; only the note/parameter values are recomputed.",
    ),

    // =============================================================================================================
    // PATTERN — Layering
    // =============================================================================================================

    LexikonEntry(
        term = "Layering",
        category = LexikonCategory.Layering,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Combining multiple patterns into one — more events, same time span.",
        detail = "stack plays multiple patterns at the same time. " +
                "layer overlays patterns on top of each other. " +
                "superimpose adds a transformed copy on top of the original. " +
                "The result is denser: more notes, more voices, more texture.",
    ),

    // =============================================================================================================
    // PATTERN — Spawning
    // =============================================================================================================

    LexikonEntry(
        term = "Spawning",
        category = LexikonCategory.Spawning,
        tags = setOf(LexikonTag.Fundamental),
        summary = "Generating new events that didn't exist in the original pattern.",
        detail = "random picks a random value. choose picks from a set. shuffle randomizes order. " +
                "Creates new material from rules, not from existing events. " +
                "Adds unpredictability and variation — the pattern is different every time it cycles.",
    ),

    // =============================================================================================================
    // TECHNICAL — Abbreviations
    // =============================================================================================================

    LexikonEntry(
        term = "LFO",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation, LexikonTag.Fundamental),
        summary = "Low Frequency Oscillator — a slow wave used to animate parameters.",
        detail = "Usually below 20 Hz, so you don't hear it as a tone — you feel it as movement. " +
                "An LFO can modulate volume (tremolo), pitch (vibrato), filter cutoff (wah), " +
                "or almost any other parameter. It's the engine behind all animation effects.",
        trivia = LexikonTrivia(
            text = "Dedicated LFO modules were formalized independently by Robert Moog and Don Buchla " +
                    "in the mid-1960s. Both the Buchla 100 Series (1963-66) and the Moog Modular had " +
                    "oscillators that could run at sub-audio rates for modulation. The ARP 2600 (1971) " +
                    "made LFO routing especially accessible. The term \"LFO\" became standard synth " +
                    "terminology through the 1970s as dedicated modules (0.1-20 Hz) became distinct from " +
                    "audio-rate VCOs.",
            year = "~1965",
            person = "Robert Moog / Don Buchla",
            device = "Moog Modular, Buchla 100 Series",
            url = "https://en.wikipedia.org/wiki/Low-frequency_oscillation",
        ),
    ),

    LexikonEntry(
        term = "VCA",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation, LexikonTag.Envelope),
        summary = "Voltage-Controlled Amplifier — a volume knob controlled by a signal.",
        detail = "In analog synthesis, the VCA is controlled by an envelope voltage. " +
                "In Klang, this is the contouring stage — the ADSR envelope controls the VCA " +
                "to shape the sound's amplitude over time.",
        trivia = LexikonTrivia(
            text = "The VCA was a core building block of Moog's voltage-controlled synthesis paradigm. " +
                    "The classic signal path VCO -> VCF -> VCA — each controlled by voltage — was Moog's " +
                    "transformative insight: by making every parameter controllable by the same voltage " +
                    "standard, modules became freely interconnectable. This modular philosophy still " +
                    "defines how we think about synthesis today.",
            year = "~1964",
            person = "Robert Moog",
            device = "Moog Modular",
            url = "https://en.wikipedia.org/wiki/Voltage-controlled_amplifier",
        ),
    ),

    LexikonEntry(
        term = "DSP",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation),
        summary = "Digital Signal Processing — processing audio as numbers.",
        detail = "The entire field of manipulating audio digitally. Everything the audio engine does — " +
                "filtering, enveloping, mixing, effects — is DSP. Sound is represented as streams of numbers " +
                "(samples) processed in blocks.",
    ),

    LexikonEntry(
        term = "BPM",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation, LexikonTag.Fundamental),
        summary = "Beats Per Minute — how fast the music plays.",
        detail = "120 BPM = 2 beats per second. Tempo controls how fast patterns cycle. " +
                "Higher BPM = faster music. Most electronic music lives between 90 BPM (hip hop) " +
                "and 180 BPM (drum & bass).",
        trivia = LexikonTrivia(
            text = "Measuring tempo in beats per minute dates back to Johann Nepomuk Maelzel, who " +
                    "patented the metronome in 1815 (though Dietrich Nikolaus Winkel invented the " +
                    "mechanism). Beethoven was an early enthusiast, adding metronome markings to his " +
                    "symphonies starting in 1817. In electronic and dance music culture from the 1970s " +
                    "onward, BPM became the primary way to classify tracks for DJ mixing.",
            year = "1815",
            person = "Johann Nepomuk Maelzel",
            device = "Maelzel's Metronome",
            url = "https://en.wikipedia.org/wiki/Tempo#Beats_per_minute",
        ),
    ),

    LexikonEntry(
        term = "dB",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation),
        summary = "Decibel — unit for measuring loudness.",
        detail = "A logarithmic scale. 0 dB is the maximum digital level. " +
                "Negative values are quieter: -6 dB is roughly half as loud, -12 dB is a quarter. " +
                "You'll see dB on gain controls, thresholds, and meters.",
    ),

    LexikonEntry(
        term = "Hz / kHz",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation, LexikonTag.Fundamental),
        summary = "Hertz / Kilohertz — frequency units.",
        detail = "Hz measures cycles per second. 440 Hz = the note A4. " +
                "Higher Hz = higher pitch. kHz = 1000 Hz. " +
                "Filter cutoffs are specified in Hz (e.g. LP at 2000 Hz). " +
                "Human hearing: roughly 20 Hz to 20 kHz.",
        trivia = LexikonTrivia(
            text = "Named after Heinrich Hertz (1857-1894), the German physicist who first proved the " +
                    "existence of electromagnetic waves in 1887. The unit was officially adopted in 1930 " +
                    "by the International Electrotechnical Commission, replacing the older term " +
                    "\"cycles per second\" (cps). The standard concert pitch A4 = 440 Hz was adopted " +
                    "internationally in 1955, though the debate about \"correct\" tuning continues to this day.",
            year = "1930",
            person = "Heinrich Hertz",
        ),
    ),

    LexikonEntry(
        term = "RPM",
        category = LexikonCategory.Abbreviations,
        tags = setOf(LexikonTag.Abbreviation, LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "Revolutions Per Minute — the Motör's word for tempo.",
        detail = "In a combustion engine, RPM measures how fast the crankshaft spins. " +
                "In the Motör, RPM maps to CPS (cycles per second) — how fast patterns cycle. " +
                "Higher RPM = faster music. Crank it up and the engine roars.",
        conventional = "BPM (Beats Per Minute), CPS (Cycles Per Second)",
    ),

    // =============================================================================================================
    // TECHNICAL — Motör Terms
    //
    // The Motör metaphor maps a combustion engine to an audio engine.
    // Signal chain: Fuel → Injection → Ignitor → Katalyst → Fusion
    // Narrative: "Fuel is Injected into each Cylinder. The Ignitor transforms it into sound.
    //             The Katalyst refines it. Fusion combines all Cylinders into the final output."
    // =============================================================================================================

    LexikonEntry(
        term = "Motör",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "The whole audio engine — everything that turns patterns into sound.",
        detail = "The Klang Audio Motör is the runtime that processes your music. " +
                "It contains Cylinders, each running their own signal chain. " +
                "The engine metaphor runs deep: Fuel is Injected, Ignitors spark the sound, " +
                "Katalysts refine it, and Fusion produces the final output. " +
                "\"Sound First!\" is its promise. \"Closer to the machine\" is its SDK.",
    ),

    LexikonEntry(
        term = "Fuel",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "The raw material — the pattern data that feeds the engine.",
        detail = "In a combustion engine, fuel is what enters the cylinders and gets transformed into power. " +
                "In the Motör, Fuel is your music: the note events, timing, parameter values — " +
                "everything that Sprudel or KlangScript generates. " +
                "Fuel without an Ignitor is just data. An Ignitor without Fuel has nothing to burn.",
    ),

    LexikonEntry(
        term = "Cylinder",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "An independent effect bus — one engine running its own signal chain.",
        detail = "In a combustion engine, each cylinder fires independently with its own fuel-air mixture. " +
                "In the Motör, up to 16 Cylinders run in parallel, each with its own delay, reverb, " +
                "phaser, and compressor. Voices are routed to a Cylinder by their Orbit. " +
                "Self-contained, powerful, running in parallel — like the real thing.",
    ),

    LexikonEntry(
        term = "Injection",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor),
        summary = "Getting the right Fuel to the right Cylinder at the right time.",
        detail = "In a combustion engine, fuel injection delivers a precisely metered fuel-air mixture " +
                "to each cylinder just before the spark. In the Motör, Injection is the event scheduler: " +
                "it takes pattern events (Fuel) and delivers them to the correct Cylinder with all " +
                "their parameters — pitch, gain, filter settings, modulation — ready for ignition.",
    ),

    LexikonEntry(
        term = "Ignitor",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Synthesis),
        summary = "The spark that sets the music on fire — the sound source.",
        detail = "In a combustion engine, the spark plug ignites the fuel-air mixture. " +
                "In the Motör, the Ignitor transforms Fuel (data) into actual sound. " +
                "An Ignitor can be an oscillator (sine, saw, square, noise), a sample, " +
                "or a custom exciter built from combinators. Without the Ignitor, " +
                "there is no combustion — no sound. It's where silence becomes music.",
    ),

    LexikonEntry(
        term = "Katalyst",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Effect),
        summary = "Refines the raw sound after ignition — per-cylinder effects processing.",
        detail = "In a combustion engine, the catalytic converter refines the exhaust, " +
                "turning harsh byproducts into cleaner output. In the Motör, the Katalyst " +
                "applies per-cylinder effects — delay, reverb, phaser, compression — " +
                "that shape the raw ignited sound into something polished. " +
                "The K-spelling is a nod to German/Greek roots. \"Prepare, Ignite, Refine, Fuse.\"",
    ),

    LexikonEntry(
        term = "Fusion",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor),
        summary = "Where all Cylinders converge into the final output.",
        detail = "In a multi-cylinder engine, all cylinders feed into a common output. " +
                "In the Motör, Fusion is the master mixing stage: all active Cylinders are summed, " +
                "sidechain ducking is applied across Cylinders, and the result passes through " +
                "the master limiter to produce the final audio. " +
                "Fusion is the last step — what the audience hears.",
    ),

    LexikonEntry(
        term = "RPM (Motör)",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "How fast the engine cycles — tempo in Motör language.",
        detail = "In a combustion engine, RPM measures crankshaft rotations per minute. " +
                "In the Motör, RPM maps to CPS (cycles per second) — how fast your patterns repeat. " +
                "Higher RPM = faster music. \"Crank up the RPM\" = speed up the tempo. " +
                "The direct mapping: RPM is a rate, CPS is a rate. Intuitive.",
    ),

    LexikonEntry(
        term = "Orbit",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "Where a pattern meets a signal chain — the bridge between events and audio.",
        detail = "An Orbit connects the pattern domain (when things happen) to the audio domain " +
                "(what they sound like). Each Orbit routes to a Cylinder. " +
                "Different Orbits = different effect buses = independent sound channels. " +
                "The Orbit is where Fuel meets the engine.",
    ),

    LexikonEntry(
        term = "Sprudel",
        category = LexikonCategory.MotorTerms,
        tags = setOf(LexikonTag.Motor, LexikonTag.Fundamental),
        summary = "Klang's pattern language — the Fuel source.",
        detail = "A sibling of strudel.cc that's taking its own direction. " +
                "Write patterns as code: notes, rhythms, transformations, and effects — " +
                "all expressed as composable functions. Sprudel is how you compose your Fuel: " +
                "the musical data that feeds into the Motör.",
    ),
)
