package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.VoiceModifierPattern.Companion.modifyVoice
import io.peekandpoke.klang.tones.Tones
import kotlin.math.max

@DslMarker
annotation class StrudelDsl

// Control Pattern Helpers /////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Applies a control pattern to the current pattern.
 * The structure comes from [this] pattern.
 * The values are taken from [control] pattern sampled at each event.
 */
@StrudelDsl
fun StrudelPattern.applyControl(
    control: StrudelPattern,
    combiner: VoiceDataMerger,
): StrudelPattern = ControlPattern(this, control, combiner)

// Root Pattern generation /////////////////////////////////////////////////////////////////////////////////////////////

/**
 * A pattern that produces no events.
 * Useful for gaps or placeholders.
 */
@StrudelDsl
val silence: StrudelPattern = object : StrudelPattern {
    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> = emptyList()
}

/**
 * A pattern that produces a single "rest" event (often represented as `~` in mini-notation).
 * Depending on implementation, this might just be silence or an explicit rest event.
 * For now, simple silence is usually enough.
 */
@StrudelDsl
val rest: StrudelPattern = silence

/** Creates a sequence pattern, dividing the cycle equally among the [patterns]. */
@StrudelDsl
fun seq(vararg patterns: StrudelPattern): StrudelPattern =
    SequencePattern(patterns.toList())

/** Plays multiple patterns at the same time. */
@StrudelDsl
fun stack(vararg patterns: StrudelPattern): StrudelPattern =
    StackPattern(patterns.toList())

// Tempo modifiers /////////////////////////////////////////////////////////////////////////////////////////////////////

/** Slows down all inner patterns by the given [factor] */
fun StrudelPattern.slow(factor: Number): StrudelPattern =
    TimeModifierPattern(this, max(1 / 128.0, factor.toDouble()))

/** Speeds up all inner patterns by the given [factor] */
fun StrudelPattern.fast(factor: Number): StrudelPattern =
    TimeModifierPattern(this, 1.0 / max(1 / 128.0, factor.toDouble()))

// note() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val noteModifier = voiceModifier<String> { copy(note = it, freqHz = Tones.noteToFreq(it)) }

@StrudelDsl
val StrudelPattern.note
    get() = dslPatternModifier(
        modify = noteModifier,
        combine = { source, control -> source.copy(note = control.note, freqHz = control.freqHz) }
    )

@StrudelDsl
val note = dslPatternCreator(noteModifier)

// n() /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// TODO: n()

// sound() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val soundModifier = voiceModifier<String> { copy(sound = it) }

@StrudelDsl
val StrudelPattern.sound: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = soundModifier,
        combine = { source, control -> source.copy(sound = control.sound) }
    )

@StrudelDsl
val sound: DslPatternCreator<String> = dslPatternCreator(soundModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val bankModifier = voiceModifier<String> { copy(bank = it) }

@StrudelDsl
val StrudelPattern.bank: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = bankModifier,
        combine = { source, control -> source.copy(bank = control.bank) }
    )

@StrudelDsl
val bank: DslPatternCreator<String> = dslPatternCreator(bankModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val gainModifier = voiceModifier<Number> { copy(gain = it.toDouble()) }

@StrudelDsl
val StrudelPattern.gain: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = gainModifier,
        combine = { source, control -> source.copy(gain = control.gain) }
    )

@StrudelDsl
val gain: DslPatternCreator<Number> = dslPatternCreator(gainModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val panModifier = voiceModifier<Number> { copy(pan = it.toDouble()) }

@StrudelDsl
val StrudelPattern.pan: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = panModifier,
        combine = { source, control -> source.copy(pan = control.pan) }
    )

@StrudelDsl
val pan: DslPatternCreator<Number> = dslPatternCreator(panModifier)

// legato() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Sets the legato (duration relative to step size).
 * Maps to `legato` in VoiceData.
 */
fun StrudelPattern.legato(amount: Number): StrudelPattern =
    modifyVoice { it.copy(legato = amount.toDouble()) }

/** Alias for [legato] */
fun StrudelPattern.clip(amount: Number): StrudelPattern = legato(amount)

// Oscillator parameters ///////////////////////////////////////////////////////////////////////////////////////////////

/** Sets the oscillator voices (for supersaw) */
fun StrudelPattern.unison(voices: Number): StrudelPattern =
    modifyVoice { it.copy(voices = voices.toDouble()) }

/** Alias for [unison] */
fun StrudelPattern.uni(voices: Number): StrudelPattern = unison(voices)

/** Sets the oscillator frequency spread (for supersaw) */
fun StrudelPattern.detune(amount: Number): StrudelPattern =
    modifyVoice { it.copy(freqSpread = amount.toDouble()) }

/** Sets the oscillator pan spread (for supersaw) */
fun StrudelPattern.spread(amount: Number): StrudelPattern =
    modifyVoice { it.copy(panSpread = amount.toDouble()) }

/** Sets the oscillator density (for supersaw) */
fun StrudelPattern.density(amount: Number): StrudelPattern =
    modifyVoice { it.copy(density = amount.toDouble()) }

/** Alias for [density] */
fun StrudelPattern.d(amount: Number): StrudelPattern = density(amount)

// ADSR Envelope ///////////////////////////////////////////////////////////////////////////////////////////////////////

/** Sets the ADSR attack */
fun StrudelPattern.attack(amount: Number): StrudelPattern =
    modifyVoice { it.copy(adsr = it.adsr.copy(attack = amount.toDouble())) }

/** Sets the ADSR decay */
fun StrudelPattern.decay(amount: Number): StrudelPattern =
    modifyVoice { it.copy(adsr = it.adsr.copy(decay = amount.toDouble())) }

/** Sets the ADSR sustain level */
fun StrudelPattern.sustain(amount: Number): StrudelPattern =
    modifyVoice { it.copy(adsr = it.adsr.copy(sustain = amount.toDouble())) }

/** Sets the ADSR release */
fun StrudelPattern.release(amount: Number): StrudelPattern =
    modifyVoice { it.copy(adsr = it.adsr.copy(release = amount.toDouble())) }

/**
 * Sets the ADSR envelope using a string format "attack:decay:sustain:release".
 * Example: adsr("0.1:0.2:0.8:1.0")
 */
fun StrudelPattern.adsr(config: String): StrudelPattern {
    val parts = config.split(":").mapNotNull { it.toDoubleOrNull() }
    val a = parts.getOrNull(0)
    val d = parts.getOrNull(1)
    val s = parts.getOrNull(2)
    val r = parts.getOrNull(3)

    return modifyVoice {
        it.copy(
            adsr = it.adsr.copy(
                attack = a ?: it.adsr.attack,
                decay = d ?: it.adsr.decay,
                sustain = s ?: it.adsr.sustain,
                release = r ?: it.adsr.release
            )
        )
    }
}

// Filters /////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Adds a Low Pass Filter with the given cutoff frequency.
 */
fun StrudelPattern.lpf(cutoff: Number): StrudelPattern = modifyVoice {
    val q = it.resonance ?: 1.0
    it.copy(
        filters = it.filters + FilterDef.LowPass(cutoffHz = cutoff.toDouble(), q = q),
        cutoff = cutoff.toDouble() // Also set the legacy/global field if needed by backend
    )
}

/**
 * Adds a High Pass Filter with the given cutoff frequency.
 */
fun StrudelPattern.hpf(cutoff: Number): StrudelPattern = modifyVoice {
    val q = it.resonance ?: 1.0
    it.copy(
        filters = it.filters + FilterDef.HighPass(cutoffHz = cutoff.toDouble(), q = q),
        hcutoff = cutoff.toDouble()
    )
}

/**
 * Adds a Band Pass Filter.
 */
fun StrudelPattern.bandf(cutoff: Number): StrudelPattern = modifyVoice {
    val q = it.resonance ?: 1.0
    it.copy(
        filters = it.filters + FilterDef.BandPass(cutoffHz = cutoff.toDouble(), q = q),
        bandf = cutoff.toDouble()
    )
}

/** Alias for [bandf] */
fun StrudelPattern.bpf(cutoff: Number): StrudelPattern = bandf(cutoff)

/**
 * Sets the resonance (Q-factor) for filters.
 * updates the global resonance AND updates all existing filters to use this new Q.
 */
fun StrudelPattern.resonance(amount: Number): StrudelPattern = modifyVoice { voice ->
    val newQ = amount.toDouble()
    voice.copy(
        resonance = newQ,
        // Update all existing filters with the new Q
        filters = voice.filters.map { filter ->
            when (filter) {
                is FilterDef.LowPass -> filter.copy(q = newQ)
                is FilterDef.HighPass -> filter.copy(q = newQ)
                is FilterDef.BandPass -> filter.copy(q = newQ)
                is FilterDef.Notch -> filter.copy(q = newQ)
            }
        }
    )
}

/** Alias for [resonance] */
fun StrudelPattern.res(amount: Number): StrudelPattern = resonance(amount)

// Effects /////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Applies distortion to the signal.
 * Range: usually 0.0 to 10.0 (or higher for extreme effects).
 * Useful for adding grit to synths or drums.
 */
fun StrudelPattern.distort(amount: Number): StrudelPattern =
    modifyVoice { it.copy(distort = amount.toDouble()) }

/**
 * Reduces the bit depth (or similar degradation) of the signal.
 * Range: 1.0 to 16.0 (typically). Smaller values = more destruction.
 */
fun StrudelPattern.crush(amount: Number): StrudelPattern =
    modifyVoice { it.copy(crush = amount.toDouble()) }

/**
 * Reduces the sample rate (downsampling) of the signal.
 * The value represents the step size (e.g. 1 = normal, 2 = half rate).
 */
fun StrudelPattern.coarse(amount: Number): StrudelPattern =
    modifyVoice { it.copy(coarse = amount.toDouble()) }

// --- Reverb ---

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
fun StrudelPattern.room(amount: Number): StrudelPattern =
    modifyVoice { it.copy(room = amount.toDouble()) }

/**
 * Sets the simulated room size for the reverb.
 * Range: typically 0.0 (small room) to 10.0 or more (huge hall).
 */
fun StrudelPattern.roomsize(amount: Number): StrudelPattern =
    modifyVoice { it.copy(roomsize = amount.toDouble()) }

/** Alias for [roomsize] */
fun StrudelPattern.rsize(amount: Number): StrudelPattern = roomsize(amount)

// --- Delay ---

/**
 * Sets the delay mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
fun StrudelPattern.delay(amount: Number): StrudelPattern =
    modifyVoice { it.copy(delay = amount.toDouble()) }

/**
 * Sets the delay time in seconds (or cycles, depending on engine config).
 * Typically absolute seconds (e.g. 0.25 = 250ms).
 */
fun StrudelPattern.delaytime(amount: Number): StrudelPattern =
    modifyVoice { it.copy(delayTime = amount.toDouble()) }

/**
 * Sets the delay feedback amount.
 * Range: 0.0 (no repeats) to <1.0 (infinite repeats).
 * Higher values create longer echo tails.
 */
fun StrudelPattern.delayfeedback(amount: Number): StrudelPattern =
    modifyVoice { it.copy(delayFeedback = amount.toDouble()) }

// --- Routing & Scales ---

/**
 * Routes the audio to a specific output bus or "orbit".
 * Used for multi-channel output or separating parts for different master effects.
 * Default is usually 0.
 */
fun StrudelPattern.orbit(index: Int): StrudelPattern =
    modifyVoice { it.copy(orbit = index) }

/**
 * Sets the musical scale for interpreting note numbers.
 * Example: "C4:minor", "pentatonic", "chromatic".
 * Affects how `n()` values are mapped to frequencies.
 */
fun StrudelPattern.scale(scale: String): StrudelPattern =
    modifyVoice { it.copy(scale = scale) }

// --- Vibrato & Glissando ---

/**
 * Sets the vibrato frequency (speed) in Hz.
 */
fun StrudelPattern.vibrato(frequency: Number): StrudelPattern =
    modifyVoice { it.copy(vibrato = frequency.toDouble()) }

/** Alias for [vibrato] */
fun StrudelPattern.vib(frequency: Number): StrudelPattern = vibrato(frequency)

/**
 * Sets the vibrato modulation depth (intensity).
 */
fun StrudelPattern.vibratoMod(amount: Number): StrudelPattern =
    modifyVoice { it.copy(vibratoMod = amount.toDouble()) }

/** Alias for [vibratoMod] */
fun StrudelPattern.vibmod(amount: Number): StrudelPattern = vibratoMod(amount)

/**
 * Applies a pitch glide (accelerate) effect.
 * Positive values glide pitch up, negative values glide down.
 * The amount is usually frequency change per cycle or similar unit.
 */
fun StrudelPattern.accelerate(amount: Number): StrudelPattern =
    modifyVoice { it.copy(accelerate = amount.toDouble()) }
