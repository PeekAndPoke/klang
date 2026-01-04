package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
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

private val noteModifier = voiceModifier<String?> { copy(note = it, freqHz = Tones.noteToFreq(it ?: "")) }

@StrudelDsl
val StrudelPattern.note
    get() = dslPatternModifier(
        modify = noteModifier,
        combine = { source, control -> source.noteModifier(control.note) }
    )

@StrudelDsl
val note = dslPatternCreator(noteModifier)

// n() /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// TODO: n()

// sound() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val soundModifier = voiceModifier<String?> { copy(sound = it) }

@StrudelDsl
val StrudelPattern.sound: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = soundModifier,
        combine = { source, control -> source.soundModifier(control.sound) }
    )

@StrudelDsl
val sound: DslPatternCreator<String> = dslPatternCreator(soundModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val bankModifier = voiceModifier<String?> { copy(bank = it) }

@StrudelDsl
val StrudelPattern.bank: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = bankModifier,
        combine = { source, control -> source.bankModifier(control.bank) }
    )

@StrudelDsl
val bank: DslPatternCreator<String> = dslPatternCreator(bankModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val gainModifier = voiceModifier<Number?> { copy(gain = it?.toDouble()) }

@StrudelDsl
val StrudelPattern.gain: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = gainModifier,
        combine = { source, control -> source.gainModifier(control.gain) }
    )

@StrudelDsl
val gain: DslPatternCreator<Number> = dslPatternCreator(gainModifier)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val panModifier = voiceModifier<Number?> { copy(pan = it?.toDouble()) }

@StrudelDsl
val StrudelPattern.pan: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = panModifier,
        combine = { source, control -> source.panModifier(control.pan) }
    )

@StrudelDsl
val pan: DslPatternCreator<Number> = dslPatternCreator(panModifier)

// legato() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val legatoModifier = voiceModifier<Number?> { copy(legato = it?.toDouble()) }

@StrudelDsl
val StrudelPattern.legato: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = legatoModifier,
        combine = { source, control -> source.legatoModifier(control.legato) }
    )

@StrudelDsl
val legato: DslPatternCreator<Number> = dslPatternCreator(legatoModifier)

/** Alias for [legato] */
@StrudelDsl
val StrudelPattern.clip get() = this.legato

/** Alias for [legato] */
@StrudelDsl
val clip = legato

// unison //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val unisonModifier = voiceModifier<Number?> { copy(voices = it?.toDouble()) }

@StrudelDsl
val StrudelPattern.unison: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = unisonModifier,
        combine = { source, control -> source.unisonModifier(control.voices) }
    )

@StrudelDsl
val unison: DslPatternCreator<Number> = dslPatternCreator(unisonModifier)

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni get() = unison

/** Alias for [unison] */
@StrudelDsl
val uni = unison

// detune //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val detuneModifier = voiceModifier<Number?> { copy(freqSpread = it?.toDouble()) }

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = detuneModifier,
        combine = { source, control -> source.detuneModifier(control.freqSpread) }
    )

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune: DslPatternCreator<Number> = dslPatternCreator(detuneModifier)

// detune //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val spreadModifier = voiceModifier<Number?> { copy(panSpread = it?.toDouble()) }

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = spreadModifier,
        combine = { source, control -> source.spreadModifier(control.panSpread) }
    )

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread: DslPatternCreator<Number> = dslPatternCreator(spreadModifier)

// density /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val densityModifier = voiceModifier<Number?> { copy(density = it?.toDouble()) }

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val StrudelPattern.density: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = densityModifier,
        combine = { source, control -> source.densityModifier(control.density) }
    )

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val density: DslPatternCreator<Number> = dslPatternCreator(densityModifier)

/** Alias for [density] */
val StrudelPattern.d get() = density

/** Alias for [density] */
val d = density

// ADSR Attack /////////////////////////////////////////////////////////////////////////////////////////////////////////

private val attackModifier = voiceModifier<Number?> { copy(adsr = adsr.copy(attack = it?.toDouble())) }

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = attackModifier,
        combine = { source, control -> source.attackModifier(control.adsr.attack) }
    )

/** Sets the note envelope attack */
@StrudelDsl
val attack: DslPatternCreator<Number> = dslPatternCreator(attackModifier)

// ADSR Decay //////////////////////////////////////////////////////////////////////////////////////////////////////////

private val decayModifier = voiceModifier<Number?> { copy(adsr = adsr.copy(decay = it?.toDouble())) }

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = decayModifier,
        combine = { source, control -> source.decayModifier(control.adsr.decay) }
    )

/** Sets the note envelope decay */
@StrudelDsl
val decay: DslPatternCreator<Number> = dslPatternCreator(decayModifier)

// ADSR Sustain ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val sustainModifier = voiceModifier<Number?> { copy(adsr = adsr.copy(sustain = it?.toDouble())) }

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = sustainModifier,
        combine = { source, control -> source.sustainModifier(control.adsr.sustain) }
    )

/** Sets the note envelope sustain */
@StrudelDsl
val sustain: DslPatternCreator<Number> = dslPatternCreator(sustainModifier)

// ADSR Release ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val releaseModifier = voiceModifier<Number?> { copy(adsr = adsr.copy(release = it?.toDouble())) }

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = releaseModifier,
        combine = { source, control -> source.releaseModifier(control.adsr.release) }
    )

/** Sets the note envelope release */
@StrudelDsl
val release: DslPatternCreator<Number> = dslPatternCreator(releaseModifier)

// ADSR Combined ///////////////////////////////////////////////////////////////////////////////////////////////////////

val adsrModifier = voiceModifier<String> {
    val parts = it.split(":").mapNotNull { d -> d.toDoubleOrNull() }
    val newAdsr = AdsrEnvelope(
        attack = parts.getOrNull(0),
        decay = parts.getOrNull(1),
        sustain = parts.getOrNull(2),
        release = parts.getOrNull(3),
    )

    copy(adsr = newAdsr.mergeWith(adsr))
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.adsr: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = adsrModifier,
        combine = { source, control -> source.copy(adsr = control.adsr.mergeWith(source.adsr)) }
    )

/** Sets the note envelope release */
@StrudelDsl
val adsr: DslPatternCreator<String> = dslPatternCreator(adsrModifier)

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

// distort() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val distortModifier = voiceModifier<Number?> { copy(distort = it?.toDouble()) }

/**
 * Applies distortion to the signal.
 * Range: usually 0.0 to 10.0 (or higher for extreme effects).
 * Useful for adding grit to synths or drums.
 */
@StrudelDsl
val StrudelPattern.distort: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = distortModifier,
        combine = { source, control -> source.distortModifier(control.distort) }
    )

/**
 * Applies distortion to the signal.
 * Range: usually 0.0 to 10.0 (or higher for extreme effects).
 * Useful for adding grit to synths or drums.
 */
@StrudelDsl
val distort: DslPatternCreator<Number> = dslPatternCreator(distortModifier)

// crush() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val crushModifier = voiceModifier<Number?> { copy(crush = it?.toDouble()) }

/**
 * Reduces the bit depth (or similar degradation) of the signal.
 * Range: 1.0 to 16.0 (typically). Smaller values = more destruction.
 */
@StrudelDsl
val StrudelPattern.crush: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = crushModifier,
        combine = { source, control -> source.crushModifier(control.crush) }
    )

/**
 * Reduces the bit depth (or similar degradation) of the signal.
 * Range: 1.0 to 16.0 (typically). Smaller values = more destruction.
 */
@StrudelDsl
val crush: DslPatternCreator<Number> = dslPatternCreator(crushModifier)

// coarse() ////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val coarseModifier = voiceModifier<Number?> { copy(coarse = it?.toDouble()) }

/**
 * Reduces the sample rate (downsampling) of the signal.
 * The value represents the step size (e.g. 1 = normal, 2 = half rate).
 */
@StrudelDsl
val StrudelPattern.coarse: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = coarseModifier,
        combine = { source, control -> source.coarseModifier(control.coarse) }
    )

/**
 * Reduces the sample rate (downsampling) of the signal.
 * The value represents the step size (e.g. 1 = normal, 2 = half rate).
 */
@StrudelDsl
val coarse: DslPatternCreator<Number> = dslPatternCreator(coarseModifier)

// Reverb room() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val roomModifier = voiceModifier<Number?> { copy(room = it?.toDouble()) }

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.room: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = roomModifier,
        combine = { source, control -> source.roomModifier(control.room) }
    )

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val room: DslPatternCreator<Number> = dslPatternCreator(roomModifier)

// Reverb roomsize() ///////////////////////////////////////////////////////////////////////////////////////////////////

private val roomsizeModifier = voiceModifier<Number?> { copy(roomSize = it?.toDouble()) }

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.roomsize: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = roomsizeModifier,
        combine = { source, control -> source.roomsizeModifier(control.roomSize) }
    )

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val roomsize: DslPatternCreator<Number> = dslPatternCreator(roomsizeModifier)

/** Alias for [roomsize] */
val StrudelPattern.rsize get() = roomsize

/** Alias for [roomsize] */
val rsize = roomsize

// Delay delay() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayModifier = voiceModifier<Number?> { copy(delay = it?.toDouble()) }

/**
 * Sets the delay mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.delay: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = delayModifier,
        combine = { source, control -> source.delayModifier(control.delay) }
    )

/**
 * Sets the delay mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val delay: DslPatternCreator<Number> = dslPatternCreator(delayModifier)

// Delay delaytime() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delaytimeModifier = voiceModifier<Number?> { copy(delayTime = it?.toDouble()) }

/**
 * Sets the delay time in seconds (or cycles, depending on engine config).
 * Typically absolute seconds (e.g. 0.25 = 250ms).
 */
@StrudelDsl
val StrudelPattern.delaytime: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = delaytimeModifier,
        combine = { source, control -> source.delaytimeModifier(control.delayTime) }
    )

/**
 * Sets the delay time in seconds (or cycles, depending on engine config).
 * Typically absolute seconds (e.g. 0.25 = 250ms).
 */
@StrudelDsl
val delaytime: DslPatternCreator<Number> = dslPatternCreator(delaytimeModifier)

// Delay delayfeedback() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayfeedbackModifier = voiceModifier<Number?> { copy(delayFeedback = it?.toDouble()) }

/**
 * Sets the delay feedback amount.
 * Range: 0.0 (no repeats) to <1.0 (infinite repeats).
 * Higher values create longer echo tails.
 */
@StrudelDsl
val StrudelPattern.delayfeedback: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = delayfeedbackModifier,
        combine = { source, control -> source.delayfeedbackModifier(control.delayFeedback) }
    )

/**
 * Sets the delay feedback amount.
 * Range: 0.0 (no repeats) to <1.0 (infinite repeats).
 * Higher values create longer echo tails.
 */
@StrudelDsl
val delayfeedback: DslPatternCreator<Number> = dslPatternCreator(delayfeedbackModifier)

// Routing orbit() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val orbitModifier = voiceModifier<Number?> { copy(orbit = it?.toInt()) }

/**
 * Routes the audio to a specific output bus or "orbit".
 * Used for multi-channel output or separating parts for different master effects.
 * Default is usually 0.
 */
@StrudelDsl
val StrudelPattern.orbit: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = orbitModifier,
        combine = { source, control -> source.orbitModifier(control.orbit) }
    )

/**
 * Routes the audio to a specific output bus or "orbit".
 * Used for multi-channel output or separating parts for different master effects.
 * Default is usually 0.
 */
@StrudelDsl
val orbit: DslPatternCreator<Number> = dslPatternCreator(orbitModifier)

// Context scale() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val scaleModifier = voiceModifier<String?> { copy(scale = it) }

/**
 * Sets the musical scale for interpreting note numbers.
 * Example: "C4:minor", "pentatonic", "chromatic".
 * Affects how `n()` values are mapped to frequencies.
 */
@StrudelDsl
val StrudelPattern.scale: DslPatternModifier<String>
    get() = dslPatternModifier(
        modify = scaleModifier,
        combine = { source, control -> source.scaleModifier(control.scale) }
    )

/**
 * Sets the musical scale for interpreting note numbers.
 * Example: "C4:minor", "pentatonic", "chromatic".
 * Affects how `n()` values are mapped to frequencies.
 */
@StrudelDsl
val scale: DslPatternCreator<String> = dslPatternCreator(scaleModifier)

// vibrato() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoModifier = voiceModifier<Number?> { copy(vibrato = it?.toDouble()) }

/**
 * Sets the vibrato frequency (speed) in Hz.
 */
@StrudelDsl
val StrudelPattern.vibrato: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = vibratoModifier,
        combine = { source, control -> source.vibratoModifier(control.vibrato) }
    )

/**
 * Sets the vibrato frequency (speed) in Hz.
 */
@StrudelDsl
val vibrato: DslPatternCreator<Number> = dslPatternCreator(vibratoModifier)

/** Alias for [vibrato] */
val StrudelPattern.vib get() = vibrato

/** Alias for [vibrato] */
val vib = vibrato

// vibratoMod() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoModModifier = voiceModifier<Number?> { copy(vibratoMod = it?.toDouble()) }

/**
 * Sets the vibratoMod frequency (speed) in Hz.
 */
@StrudelDsl
val StrudelPattern.vibratoMod: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = vibratoModModifier,
        combine = { source, control -> source.vibratoModModifier(control.vibratoMod) }
    )

/**
 * Sets the vibratoMod frequency (speed) in Hz.
 */
@StrudelDsl
val vibratoMod: DslPatternCreator<Number> = dslPatternCreator(vibratoModModifier)

/** Alias for [vibratoMod] */
val StrudelPattern.vibmod get() = vibratoMod

/** Alias for [vibratoMod] */
val vibmod = vibratoMod

// accelerate() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val accelerateModifier = voiceModifier<Number?> { copy(accelerate = it?.toDouble()) }

/**
 * Applies a pitch glide (accelerate) effect.
 * Positive values glide pitch up, negative values glide down.
 * The amount is usually frequency change per cycle or similar unit.
 */
@StrudelDsl
val StrudelPattern.accelerate: DslPatternModifier<Number>
    get() = dslPatternModifier(
        modify = accelerateModifier,
        combine = { source, control -> source.accelerateModifier(control.accelerate) }
    )

/**
 * Applies a pitch glide (accelerate) effect.
 * Positive values glide pitch up, negative values glide down.
 * The amount is usually frequency change per cycle or similar unit.
 */
@StrudelDsl
val accelerate: DslPatternCreator<Number> = dslPatternCreator(accelerateModifier)
