package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.patterns.*
import io.peekandpoke.klang.tones.Tones
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@DslMarker
annotation class StrudelDsl

// Initialization Helper ///////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangInit = false

// Helpers
private fun List<Any?>.flattenToPatterns(): Array<StrudelPattern> {
    return this.flatMap { arg ->
        when (arg) {
            is StrudelPattern -> listOf(arg)
            is List<*> -> arg.filterIsInstance<StrudelPattern>()
            else -> emptyList()
        }
    }.toTypedArray()
}


// Structure Functions /////////////////////////////////////////////////////////////////////////////////////////////////

@StrudelDsl
val silence by dslFunction<Any> {
    object : StrudelPattern {
        override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> = emptyList()
    }
}

/**
 * A pattern that produces a single "rest" event (often represented as `~` in mini-notation).
 * Depending on implementation, this might just be silence or an explicit rest event.
 * For now, simple silence is usually enough.
 */
@StrudelDsl
val rest by dslFunction<Any> { silence() }

// Continuous patterns /////////////////////////////////////////////////////////////////////////////////////////////////

/** Sine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val sine by dslFunction<Any> {
    ContinuousPattern(
        getValue = { t -> sin(t * 2.0 * PI) },
    )
}

// Host patterns ///////////////////////////////////////////////////////////////////////////////////////////////////////

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction<StrudelPattern> { args ->
    val patterns = args.flattenToPatterns().toList()
    if (patterns.isEmpty()) silence() else SequencePattern(patterns)
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction<StrudelPattern> { args ->
    val patterns = args.flattenToPatterns().toList()
    if (patterns.isEmpty()) silence() else StackPattern(patterns)
}

// arrange([2, a], b) -> 2 cycles of a, 1 cycle of b.
@StrudelDsl
val arrange by dslFunction<Any> { args ->
    val segments = args.map { arg ->
        when (arg) {
            // Case: pattern (defaults to 1 cycle)
            is StrudelPattern -> 1.0 to arg
            // Case: [2, pattern]
            is List<*> if arg.size == 2 && arg[0] is Number && arg[1] is StrudelPattern -> {
                val dur = (arg[0] as Number).toDouble()
                val pat = arg[1] as StrudelPattern
                dur to pat
            }
            // Case: [pattern] (defaults to 1 cycle)
            is List<*> if arg.size == 1 && arg[0] is StrudelPattern -> 1.0 to (arg[0] as StrudelPattern)
            else -> 0.0 to silence()
        }
    }.filter { it.first > 0.0 }

    if (segments.isEmpty()) silence()
    else ArrangementPattern(segments)
}

// pickRestart([a, b, c]) -> picks patterns sequentially per cycle (slowcat)
@StrudelDsl
val pickRestart by dslFunction<Any> { args ->
    val patterns = args.flattenToPatterns()
    if (patterns.isEmpty()) {
        silence()
    } else {
        // seq plays all in 1 cycle. slow(n) makes each take 1 cycle.
        val s = seq(*patterns) // using our dslFunction 'seq' via kotlin invoke
        // We need to call .slow on the result. But 'slow' is a property delegate now!
        // In Kotlin code: s.slow(n). In internal code, we access the DslMethod 'slow'.
        s.slow(patterns.size)
    }
}

// Tempo modifiers /////////////////////////////////////////////////////////////////////////////////////////////////////

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.slow by dslMethod<Number> { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    TimeModifierPattern(p, max(1.0 / 128.0, factor))
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslMethod<Number> { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    TimeModifierPattern(p, 1.0 / max(1.0 / 128.0, factor))
}

// note() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val noteMutation = voiceModifier<String?> { copy(note = it, freqHz = Tones.noteToFreq(it ?: "")) }

/** Modifies the notes of a pattern */
@StrudelDsl
val StrudelPattern.note by dslPatternModifier(
    modify = noteMutation,
    combine = { source, control -> source.noteMutation(control.note) }
)

/** Creates a pattern with notes */
@StrudelDsl
val note by dslPatternCreator(noteMutation)

// n() /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val nMutation = voiceModifier<Number?> {
    // n can drive the note (e.g. for scales) or the sample index
    copy(
        note = it?.toString(),
        soundIndex = it?.toInt()
    )
}

/** Sets the note number or sample index */
@StrudelDsl
val StrudelPattern.n by dslPatternModifier(
    modify = nMutation,
    combine = { source, control -> source.nMutation(control.note?.toDoubleOrNull()) }
)

/** Sets the note number or sample index */
@StrudelDsl
val n: DslPatternCreator<Number> by dslPatternCreator(nMutation)

// sound() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val soundMutation = voiceModifier<String?> { copy(sound = it) }

private val soundModifier = dslPatternModifier(
    modify = soundMutation,
    combine = { source, control -> source.soundMutation(control.sound) }
)

private val soundCreator = dslPatternCreator(soundMutation)

/** Modifies the sounds of a pattern */
@StrudelDsl
val StrudelPattern.sound by soundModifier

/** Creates a pattern with sounds */
@StrudelDsl
val sound by soundCreator

/** Alias for [sound] */
@StrudelDsl
val StrudelPattern.s by soundModifier

/** Alias for [sound] */
@StrudelDsl
val s by soundCreator

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val bankMutation = voiceModifier<String?> { copy(bank = it) }

/** Modifies the banks of a pattern */
@StrudelDsl
val StrudelPattern.bank by dslPatternModifier(
    modify = bankMutation,
    combine = { source, control -> source.bankMutation(control.bank) }
)

/** Creates a pattern with banks */
@StrudelDsl
val bank: DslPatternCreator<String> by dslPatternCreator(bankMutation)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val gainMutation = voiceModifier<Number?> { copy(gain = it?.toDouble()) }

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternModifier(
    modify = gainMutation,
    combine = { source, control -> source.gainMutation(control.gain) }
)

/** Creates a pattern with gains */
@StrudelDsl
val gain: DslPatternCreator<Number> by dslPatternCreator(gainMutation)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val panMutation = voiceModifier<Number?> { copy(pan = it?.toDouble()) }

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternModifier(
    modify = panMutation,
    combine = { source, control -> source.panMutation(control.pan) }
)

/** Creates a pattern with pans */
@StrudelDsl
val pan: DslPatternCreator<Number> by dslPatternCreator(panMutation)

// legato() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val legatoMutation = voiceModifier<Number?> { copy(legato = it?.toDouble()) }

private val legatoModifier = dslPatternModifier(
    modify = legatoMutation,
    combine = { source, control -> source.legatoMutation(control.legato) }
)

private val legatoCreator = dslPatternCreator(legatoMutation)

/** Modifies the legatos of a pattern */
@StrudelDsl
val StrudelPattern.legato by legatoModifier

/** Creates a pattern with legatos */
@StrudelDsl
val legato by legatoCreator

/** Alias for [legato] */
@StrudelDsl
val StrudelPattern.clip by legatoModifier

/** Alias for [legato] */
@StrudelDsl
val clip by legatoCreator

// unison //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val unisonMutation = voiceModifier<Number?> { copy(voices = it?.toDouble()) }

private val unisonModifier = dslPatternModifier(
    modify = unisonMutation,
    combine = { source, control -> source.unisonMutation(control.voices) }
)

private val unisonCreator = dslPatternCreator(unisonMutation)

/** Modifies the voices of a pattern */
@StrudelDsl
val StrudelPattern.unison by unisonModifier

/** Creates a pattern with unison */
@StrudelDsl
val unison by unisonCreator

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni by unisonModifier

/** Alias for [unison] */
@StrudelDsl
val uni by unisonCreator

// detune //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val detuneMutation = voiceModifier<Number?> { copy(freqSpread = it?.toDouble()) }

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune by dslPatternModifier(
    modify = detuneMutation,
    combine = { source, control -> source.detuneMutation(control.freqSpread) }
)

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune by dslPatternCreator(detuneMutation)

// detune //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val spreadMutation = voiceModifier<Number?> { copy(panSpread = it?.toDouble()) }

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternModifier(
    modify = spreadMutation,
    combine = { source, control -> source.spreadMutation(control.panSpread) }
)

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread: DslPatternCreator<Number> by dslPatternCreator(spreadMutation)

// density /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val densityMutation = voiceModifier<Number?> { copy(density = it?.toDouble()) }

private val densityModifier = dslPatternModifier(
    modify = densityMutation,
    combine = { source, control -> source.densityMutation(control.density) }
)

private val densityCreator = dslPatternCreator(densityMutation)

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val StrudelPattern.density by densityModifier

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val density by densityCreator

/** Alias for [density] */
@StrudelDsl
val StrudelPattern.d by densityModifier

/** Alias for [density] */
@StrudelDsl
val d by densityCreator

// ADSR Attack /////////////////////////////////////////////////////////////////////////////////////////////////////////

private val attackMutation = voiceModifier<Number?> { copy(adsr = adsr.copy(attack = it?.toDouble())) }

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternModifier(
    modify = attackMutation,
    combine = { source, control -> source.attackMutation(control.adsr.attack) }
)

/** Sets the note envelope attack */
@StrudelDsl
val attack: DslPatternCreator<Number> by dslPatternCreator(attackMutation)

// ADSR Decay //////////////////////////////////////////////////////////////////////////////////////////////////////////

private val decayMutation = voiceModifier<Number?> { copy(adsr = adsr.copy(decay = it?.toDouble())) }

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternModifier(
    modify = decayMutation,
    combine = { source, control -> source.decayMutation(control.adsr.decay) }
)

/** Sets the note envelope decay */
@StrudelDsl
val decay: DslPatternCreator<Number> by dslPatternCreator(decayMutation)

// ADSR Sustain ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val sustainMutation = voiceModifier<Number?> { copy(adsr = adsr.copy(sustain = it?.toDouble())) }

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternModifier(
    modify = sustainMutation,
    combine = { source, control -> source.sustainMutation(control.adsr.sustain) }
)

/** Sets the note envelope sustain */
@StrudelDsl
val sustain: DslPatternCreator<Number> by dslPatternCreator(sustainMutation)

// ADSR Release ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val releaseMutation = voiceModifier<Number?> { copy(adsr = adsr.copy(release = it?.toDouble())) }

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternModifier(
    modify = releaseMutation,
    combine = { source, control -> source.releaseMutation(control.adsr.release) }
)

/** Sets the note envelope release */
@StrudelDsl
val release: DslPatternCreator<Number> by dslPatternCreator(releaseMutation)

// ADSR Combined ///////////////////////////////////////////////////////////////////////////////////////////////////////

val adsrMutation = voiceModifier<String> {
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
val StrudelPattern.adsr by dslPatternModifier(
    modify = adsrMutation,
    combine = { source, control -> source.copy(adsr = control.adsr.mergeWith(source.adsr)) }
)

/** Sets the note envelope release */
@StrudelDsl
val adsr: DslPatternCreator<String> by dslPatternCreator(adsrMutation)

// Filters - LowPass - lpf() ///////////////////////////////////////////////////////////////////////////////////////////

private val lpfMutation = voiceModifier<Number?> {
    val filter = FilterDef.LowPass(cutoffHz = it?.toDouble() ?: 1000.0, q = resonance ?: 1.0)

    copy(filters = filters.addOrReplace(filter))
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternModifier(
    modify = lpfMutation,
    combine = { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .lpfMutation(control.filters.getByType<FilterDef.LowPass>()?.cutoffHz)
    }
)

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val lpf by dslPatternCreator(lpfMutation)

// Filters - HighPass - hpf() //////////////////////////////////////////////////////////////////////////////////////////

private val hpfMutation = voiceModifier<Number?> {
    val filter = FilterDef.HighPass(cutoffHz = it?.toDouble() ?: 1000.0, q = resonance ?: 1.0)

    copy(filters = filters.addOrReplace(filter))
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternModifier(
    modify = hpfMutation,
    combine = { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .hpfMutation(control.filters.getByType<FilterDef.HighPass>()?.cutoffHz)
    }
)

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val hpf: DslPatternCreator<Number> by dslPatternCreator(hpfMutation)

// Filters - BandPass - bandf() ////////////////////////////////////////////////////////////////////////////////////////

private val bandfMutation = voiceModifier<Number?> {
    val filter = FilterDef.BandPass(cutoffHz = it?.toDouble() ?: 1000.0, q = resonance ?: 1.0)

    copy(filters = filters.addOrReplace(filter))
}

private val bandfModifier = dslPatternModifier(
    modify = bandfMutation,
    combine = { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .bandfMutation(control.filters.getByType<FilterDef.BandPass>()?.cutoffHz)
    }
)

private val bandfCreator = dslPatternCreator(bandfMutation)

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.bandf by bandfModifier

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val bandf by bandfCreator

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bpf by bandfModifier

/** Alias for [bandf] */
@StrudelDsl
val bpf by bandfCreator

// Filters - Notch | inverse BandPass - notchf() ///////////////////////////////////////////////////////////////////////

private val notchfMutation = voiceModifier<Number?> {
    val filter = FilterDef.Notch(cutoffHz = it?.toDouble() ?: 1000.0, q = resonance ?: 1.0)

    copy(filters = filters.addOrReplace(filter))
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternModifier(
    modify = notchfMutation,
    combine = { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .notchfMutation(control.filters.getByType<FilterDef.Notch>()?.cutoffHz)
    }
)

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val notchf: DslPatternCreator<Number> by dslPatternCreator(notchfMutation)

// Filters - resonance() ///////////////////////////////////////////////////////////////////////////////////////////////

private val resonanceMutation = voiceModifier<Number?> {
    val newQ = it?.toDouble() ?: 1.0

    val newFilters = filters.modifyAll { filter ->
        when (filter) {
            is FilterDef.LowPass -> filter.copy(q = newQ)
            is FilterDef.HighPass -> filter.copy(q = newQ)
            is FilterDef.BandPass -> filter.copy(q = newQ)
            is FilterDef.Notch -> filter.copy(q = newQ)
        }
    }

    copy(resonance = newQ, filters = newFilters)
}

private val resonanceModifier = dslPatternModifier(
    modify = resonanceMutation,
    combine = { source, control -> source.resonanceMutation(control.resonance) }
)

private val resonanceCreator = dslPatternCreator(resonanceMutation)

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.resonance by resonanceModifier

/** Sets the note envelope release */
@StrudelDsl
val resonance by resonanceCreator

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.res by resonanceModifier

/** Alias for [resonance] */
@StrudelDsl
val res by resonanceCreator

// distort() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val distortMutation = voiceModifier<Number?> { copy(distort = it?.toDouble()) }

/**
 * Applies distortion to the signal.
 * Range: usually 0.0 to 10.0 (or higher for extreme effects).
 * Useful for adding grit to synths or drums.
 */
@StrudelDsl
val StrudelPattern.distort by dslPatternModifier(
    modify = distortMutation,
    combine = { source, control -> source.distortMutation(control.distort) }
)

/**
 * Applies distortion to the signal.
 * Range: usually 0.0 to 10.0 (or higher for extreme effects).
 * Useful for adding grit to synths or drums.
 */
@StrudelDsl
val distort: DslPatternCreator<Number> by dslPatternCreator(distortMutation)

// crush() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val crushMutation = voiceModifier<Number?> { copy(crush = it?.toDouble()) }

/**
 * Reduces the bit depth (or similar degradation) of the signal.
 * Range: 1.0 to 16.0 (typically). Smaller values = more destruction.
 */
@StrudelDsl
val StrudelPattern.crush by dslPatternModifier(
    modify = crushMutation,
    combine = { source, control -> source.crushMutation(control.crush) }
)

/**
 * Reduces the bit depth (or similar degradation) of the signal.
 * Range: 1.0 to 16.0 (typically). Smaller values = more destruction.
 */
@StrudelDsl
val crush: DslPatternCreator<Number> by dslPatternCreator(crushMutation)

// coarse() ////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val coarseMutation = voiceModifier<Number?> { copy(coarse = it?.toDouble()) }

/**
 * Reduces the sample rate (downsampling) of the signal.
 * The value represents the step size (e.g. 1 = normal, 2 = half rate).
 */
@StrudelDsl
val StrudelPattern.coarse by dslPatternModifier(
    modify = coarseMutation,
    combine = { source, control -> source.coarseMutation(control.coarse) }
)

/**
 * Reduces the sample rate (downsampling) of the signal.
 * The value represents the step size (e.g. 1 = normal, 2 = half rate).
 */
@StrudelDsl
val coarse: DslPatternCreator<Number> by dslPatternCreator(coarseMutation)

// Reverb room() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val roomMutation = voiceModifier<Number?> { copy(room = it?.toDouble()) }

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.room by dslPatternModifier(
    modify = roomMutation,
    combine = { source, control -> source.roomMutation(control.room) }
)

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val room by dslPatternCreator(roomMutation)

// Reverb roomsize() ///////////////////////////////////////////////////////////////////////////////////////////////////

private val roomSizeMutation = voiceModifier<Number?> { copy(roomSize = it?.toDouble()) }

private val roomSizeModifier = dslPatternModifier(
    modify = roomSizeMutation,
    combine = { source, control -> source.roomSizeMutation(control.roomSize) }
)

private val roomSizeCreator = dslPatternCreator(roomSizeMutation)

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.roomsize by roomSizeModifier

/**
 * Sets the reverb mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val roomsize by roomSizeCreator

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.rsize by roomSizeModifier

/** Alias for [roomsize] */
@StrudelDsl
val rsize by roomSizeCreator

// Delay delay() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayMutation = voiceModifier<Number?> { copy(delay = it?.toDouble()) }

/**
 * Sets the delay mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val StrudelPattern.delay by dslPatternModifier(
    modify = delayMutation,
    combine = { source, control -> source.delayMutation(control.delay) }
)

/**
 * Sets the delay mix level.
 * Range: 0.0 (dry) to 1.0 (wet).
 */
@StrudelDsl
val delay by dslPatternCreator(delayMutation)

// Delay delaytime() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayTimeMutation = voiceModifier<Number?> { copy(delayTime = it?.toDouble()) }

/**
 * Sets the delay time in seconds (or cycles, depending on engine config).
 * Typically absolute seconds (e.g. 0.25 = 250ms).
 */
@StrudelDsl
val StrudelPattern.delaytime by dslPatternModifier(
    modify = delayTimeMutation,
    combine = { source, control -> source.delayTimeMutation(control.delayTime) }
)

/**
 * Sets the delay time in seconds (or cycles, depending on engine config).
 * Typically absolute seconds (e.g. 0.25 = 250ms).
 */
@StrudelDsl
val delaytime by dslPatternCreator(delayTimeMutation)

// Delay delayfeedback() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayFeedbackMutation = voiceModifier<Number?> { copy(delayFeedback = it?.toDouble()) }

/**
 * Sets the delay feedback amount.
 * Range: 0.0 (no repeats) to <1.0 (infinite repeats).
 * Higher values create longer echo tails.
 */
@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternModifier(
    modify = delayFeedbackMutation,
    combine = { source, control -> source.delayFeedbackMutation(control.delayFeedback) }
)

/**
 * Sets the delay feedback amount.
 * Range: 0.0 (no repeats) to <1.0 (infinite repeats).
 * Higher values create longer echo tails.
 */
@StrudelDsl
val delayfeedback by dslPatternCreator(delayFeedbackMutation)

// Routing orbit() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val orbitMutation = voiceModifier<Number?> { copy(orbit = it?.toInt()) }

/**
 * Routes the audio to a specific output bus or "orbit".
 * Used for multi-channel output or separating parts for different master effects.
 * Default is usually 0.
 */
@StrudelDsl
val StrudelPattern.orbit by dslPatternModifier(
    modify = orbitMutation,
    combine = { source, control -> source.orbitMutation(control.orbit) }
)

/**
 * Routes the audio to a specific output bus or "orbit".
 * Used for multi-channel output or separating parts for different master effects.
 * Default is usually 0.
 */
@StrudelDsl
val orbit by dslPatternCreator(orbitMutation)

// Context scale() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val scaleMutation = voiceModifier<String?> { copy(scale = it) }

/**
 * Sets the musical scale for interpreting note numbers.
 * Example: "C4:minor", "pentatonic", "chromatic".
 * Affects how `n()` values are mapped to frequencies.
 */
@StrudelDsl
val StrudelPattern.scale by dslPatternModifier(
    modify = scaleMutation,
    combine = { source, control -> source.scaleMutation(control.scale) }
)

/**
 * Sets the musical scale for interpreting note numbers.
 * Example: "C4:minor", "pentatonic", "chromatic".
 * Affects how `n()` values are mapped to frequencies.
 */
@StrudelDsl
val scale by dslPatternCreator(scaleMutation)

// vibrato() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoMutation = voiceModifier<Number?> { copy(vibrato = it?.toDouble()) }

private val vibratoModifier = dslPatternModifier(
    modify = vibratoMutation,
    combine = { source, control -> source.vibratoMutation(control.vibrato) }
)

private val vibratoCreator = dslPatternCreator(vibratoMutation)

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibrato by vibratoModifier

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val vibrato by vibratoCreator

/** Alias for [vibrato] */
@StrudelDsl
val StrudelPattern.vib by vibratoModifier

/** Alias for [vibrato] */
@StrudelDsl
val vib by vibratoCreator

// vibratoMod() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoModMutation = voiceModifier<Number?> { copy(vibratoMod = it?.toDouble()) }

private val vibratoModModifier = dslPatternModifier(
    modify = vibratoModMutation,
    combine = { source, control -> source.vibratoModMutation(control.vibratoMod) }
)

private val vibratoModCreator = dslPatternCreator(vibratoModMutation)

/** Sets the vibratoMod frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibratoMod by vibratoModModifier

/** Sets the vibratoMod frequency (speed) in Hz. */
@StrudelDsl
val vibratoMod: DslPatternCreator<Number> by vibratoModCreator

/** Alias for [vibratoMod] */
val StrudelPattern.vibmod by vibratoModModifier

/** Alias for [vibratoMod] */
val vibmod by vibratoModCreator

// accelerate() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val accelerateMutation = voiceModifier<Number?> { copy(accelerate = it?.toDouble()) }

/**
 * Applies a pitch glide (accelerate) effect.
 * Positive values glide pitch up, negative values glide down.
 * The amount is usually frequency change per cycle or similar unit.
 */
@StrudelDsl
val StrudelPattern.accelerate by dslPatternModifier(
    modify = accelerateMutation,
    combine = { source, control -> source.accelerateMutation(control.accelerate) }
)

/**
 * Applies a pitch glide (accelerate) effect.
 * Positive values glide pitch up, negative values glide down.
 * The amount is usually frequency change per cycle or similar unit.
 */
@StrudelDsl
val accelerate: DslPatternCreator<Number> by dslPatternCreator(accelerateMutation)
