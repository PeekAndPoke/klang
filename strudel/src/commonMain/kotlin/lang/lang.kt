package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.PerlinNoise
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.scale.Scale
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
private fun List<Any?>.flattenToPatterns(
    voiceFactory: VoiceData.(String) -> VoiceData = { copy(note = it, value = it.toDoubleOrNull()) },
): List<StrudelPattern> {
    val atomFactory = { it: String ->
        AtomicPattern(VoiceData.empty.voiceFactory(it))
    }

    return this.flatMap { arg ->
        when (arg) {
            is String -> listOf(parseMiniNotation(arg, atomFactory))
            is Number -> listOf(atomFactory(arg.toString()))
            is StrudelPattern -> listOf(arg)
            is List<*> -> arg.flattenToPatterns(voiceFactory)
            else -> emptyList()
        }
    }
}

/**
 * Cleans up the scale name
 */
private fun String.cleanScaleName() = replace(":", " ")

private fun Any.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}

private fun Any.asIntOrNull(): Int? = when (this) {
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    else -> null
}

// Continuous patterns /////////////////////////////////////////////////////////////////////////////////////////////////

/** Empty pattern that does not produce any events */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/** Empty pattern that does not produce any events */
@StrudelDsl
val rest by dslObject { silence }

/** Sine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val sine by dslObject {
    ContinuousPattern { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 }
}

/** Sawtooth oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val saw by dslObject {
    ContinuousPattern { t -> t % 1.0 }
}

/** Inverse Sawtooth oscillator: 1 to 0, period of 1 cycle */
@StrudelDsl
val isaw by dslObject {
    ContinuousPattern { t -> 1.0 - (t % 1.0) }
}

/** Triangle oscillator: 0 to 1 to 0, period of 1 cycle */
@StrudelDsl
val tri by dslObject {
    ContinuousPattern { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val square by dslObject {
    ContinuousPattern { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val perlin by dslObject {
    ContinuousPattern { t -> (PerlinNoise.noise(t) + 1.0) / 2.0 }
}

/**
 * Sets the range of a continuous pattern to a new minimum and maximum value.
 */
@StrudelDsl
val StrudelPattern.range by dslMethod { p, args ->
    val min = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.asDoubleOrNull() ?: 1.0

    p.withContext {
        setIfAbsent(ContinuousPattern.minKey, min)
        setIfAbsent(ContinuousPattern.maxKey, max)
    }
}

// Structural patterns /////////////////////////////////////////////////////////////////////////////////////////////////

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction { args ->
    val patterns = args.flattenToPatterns { copy(value = it.toDoubleOrNull()) }

    if (patterns.isEmpty()) silence else SequencePattern(patterns)
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction { args ->
    val patterns = args.flattenToPatterns().toList()
    if (patterns.isEmpty()) silence else StackPattern(patterns)
}

// arrange([2, a], b) -> 2 cycles of a, 1 cycle of b.
@StrudelDsl
val arrange by dslFunction { args ->
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
            // Unknown
            else -> 0.0 to silence
        }
    }.filter { it.first > 0.0 }

    if (segments.isEmpty()) silence
    else ArrangementPattern(segments)
}

// pickRestart([a, b, c]) -> picks patterns sequentially per cycle (slowcat)
@StrudelDsl
val pickRestart by dslFunction { args ->
    val patterns = args.flattenToPatterns()
    if (patterns.isEmpty()) {
        silence
    } else {
        // seq plays all in 1 cycle. slow(n) makes each take 1 cycle.
        val s = seq(patterns) // using our dslFunction 'seq' via kotlin invoke
        // We need to call .slow on the result. But 'slow' is a property delegate now!
        // In Kotlin code: s.slow(n). In internal code, we access the DslMethod 'slow'.
        s.slow(patterns.size)
    }
}

@StrudelDsl
val cat by dslFunction { args ->
    val patterns = args.flattenToPatterns()
    when {
        patterns.isEmpty() -> silence
        patterns.size == 1 -> patterns.first()
        else -> ArrangementPattern(patterns.map { 1.0 to it })
    }
}

// Tempo / Time modifiers //////////////////////////////////////////////////////////////////////////////////////////////

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.slow by dslMethod { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    TempoModifierPattern(p, max(1.0 / 128.0, factor))
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslMethod { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    TempoModifierPattern(p, 1.0 / max(1.0 / 128.0, factor))
}

@StrudelDsl
val StrudelPattern.rev: DslMethod by dslMethod { pattern, args ->
    val firstArgInt = args.firstOrNull()?.toString()?.toIntOrNull() ?: 1

    if (firstArgInt <= 1) {
        ReversePattern(pattern)
    } else {
        pattern.fast(firstArgInt).rev().slow(firstArgInt)
    }
}

@StrudelDsl
val StrudelPattern.palindrome by dslMethod { pattern, _ ->
    cat(pattern, pattern.rev())
}

/**
 * Structures the pattern according to another pattern (the mask).
 *
 * The structural pattern (usually mini-notation) uses 'x' to indicate where
 * the source pattern should be heard.
 *
 * Example: note("c e g").struct("x ~ x")
 */
@StrudelDsl
val StrudelPattern.struct by dslMethod { source, args ->
    val structure = when (val structArg = args.firstOrNull()) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    StructPattern(source, structure)
}

/**
 * Filters the pattern using a boolean mask.
 *
 * Events in the source pattern are only heard if the mask pattern
 * has a truthy value at that time.
 *
 * Example: note("c e g").mask("1 0 1")
 */
@StrudelDsl
val StrudelPattern.mask by dslMethod { source, args ->
    val maskPattern = when (val maskArg = args.firstOrNull()) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) {
            AtomicPattern(VoiceData.empty.copy(note = it))
        }

        else -> silence
    }

    MaskPattern(source, maskPattern)
}

/**
 * Layers a modified version of the pattern on top of itself.
 *
 * Example: s("bd sd").superimpose { it.fast(2) }
 */
@StrudelDsl
val StrudelPattern.superimpose by dslMethod { source, args ->
    @Suppress("UNCHECKED_CAST")
    val transform = args.firstOrNull() as? (StrudelPattern) -> StrudelPattern ?: { it }
    SuperimposePattern(source, transform)
}

/**
 * Applies multiple transformation functions to the pattern and stacks the results.
 *
 * Example: s("bd").layer({ it.fast(2) }, { it.rev() })
 */
@StrudelDsl
val StrudelPattern.layer by dslMethod { source, args ->
    val transforms = args.filterIsInstance<(StrudelPattern) -> StrudelPattern>()

    if (transforms.isEmpty()) {
        silence
    } else {
        val patterns = transforms.mapNotNull { transform ->
            try {
                transform(source)
            } catch (e: Exception) {
                println("Error applying layer transform: ${e.stackTraceToString()}")
                null
            }
        }

        if (patterns.size == 1) {
            patterns.first()
        } else {
            StackPattern(patterns)
        }
    }
}

/**
 * Alias for [layer].
 */
@StrudelDsl
val StrudelPattern.apply by dslMethod { source, args ->
    source.layer(args)
}


// note() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val noteMutation = voiceModifier { input ->
    // when the input is null, we re-interpret the current pattern as note
    val newNote = input?.toString() ?: value?.toString()

    copy(
        note = newNote,
        freqHz = Tones.noteToFreq(newNote ?: ""),
    )
}

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

private val nMutation = voiceModifier {
    // when the input is null, we re-interpret the current pattern as n
    val n = it?.asIntOrNull() ?: value?.toInt()
    val scaleName = scale?.cleanScaleName()

    if (n != null && !scaleName.isNullOrEmpty()) {
        // Use Scale.steps for 0-based indexing (standard in Strudel)
        // This returns the note name (e.g., "C4") based on the scale and the index n
        val noteName = Scale.steps(scaleName).invoke(n)

        copy(
            note = noteName,
            freqHz = Tones.noteToFreq(noteName),
            soundIndex = n,
        )
    } else {
        // Fallback: n drives the note string directly or the sample index
        copy(
            soundIndex = n,
        )
    }
}

/** Sets the note number or sample index */
@StrudelDsl
val StrudelPattern.n by dslPatternModifier(
    modify = nMutation,
    combine = { source, control -> source.nMutation(control.note) }
)

/** Sets the note number or sample index */
@StrudelDsl
val n: DslPatternCreator by dslPatternCreator(nMutation)

// sound() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val soundMutation = voiceModifier {
    val split = it?.toString()?.split(":") ?: emptyList()

    copy(
        sound = split.getOrNull(0),
        soundIndex = split.getOrNull(1)?.toIntOrNull(),
    )
}

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

private val bankMutation = voiceModifier {
    copy(bank = it?.toString())
}

/** Modifies the banks of a pattern */
@StrudelDsl
val StrudelPattern.bank by dslPatternModifier(
    modify = bankMutation,
    combine = { source, control -> source.bankMutation(control.bank) }
)

/** Creates a pattern with banks */
@StrudelDsl
val bank: DslPatternCreator by dslPatternCreator(bankMutation)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val gainMutation = voiceModifier {
    copy(gain = it?.toString()?.toDoubleOrNull())
}

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternModifier(
    modify = gainMutation,
    combine = { source, control -> source.gainMutation(control.gain) }
)

/** Creates a pattern with gains */
@StrudelDsl
val gain: DslPatternCreator by dslPatternCreator(gainMutation)

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val panMutation = voiceModifier {
    copy(pan = it?.toString()?.toDoubleOrNull())
}

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternModifier(
    modify = panMutation,
    combine = { source, control -> source.panMutation(control.pan) }
)

/** Creates a pattern with pans */
@StrudelDsl
val pan: DslPatternCreator by dslPatternCreator(panMutation)

// legato() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val legatoMutation = voiceModifier {
    copy(legato = it?.asDoubleOrNull())
}

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

private val unisonMutation = voiceModifier {
    copy(voices = it?.asDoubleOrNull())
}

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

private val detuneMutation = voiceModifier {
    copy(freqSpread = it?.asDoubleOrNull())
}

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

private val spreadMutation = voiceModifier {
    copy(panSpread = it?.asDoubleOrNull())
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternModifier(
    modify = spreadMutation,
    combine = { source, control -> source.spreadMutation(control.panSpread) }
)

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread: DslPatternCreator by dslPatternCreator(spreadMutation)

// density /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val densityMutation = voiceModifier {
    copy(density = it?.asDoubleOrNull())
}

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

private val attackMutation = voiceModifier {
    copy(adsr = adsr.copy(attack = it?.asDoubleOrNull()))
}

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternModifier(
    modify = attackMutation,
    combine = { source, control -> source.attackMutation(control.adsr.attack) }
)

/** Sets the note envelope attack */
@StrudelDsl
val attack: DslPatternCreator by dslPatternCreator(attackMutation)

// ADSR Decay //////////////////////////////////////////////////////////////////////////////////////////////////////////

private val decayMutation = voiceModifier {
    copy(adsr = adsr.copy(decay = it?.asDoubleOrNull()))
}

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternModifier(
    modify = decayMutation,
    combine = { source, control -> source.decayMutation(control.adsr.decay) }
)

/** Sets the note envelope decay */
@StrudelDsl
val decay: DslPatternCreator by dslPatternCreator(decayMutation)

// ADSR Sustain ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val sustainMutation = voiceModifier {
    copy(adsr = adsr.copy(sustain = it?.asDoubleOrNull()))
}

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternModifier(
    modify = sustainMutation,
    combine = { source, control -> source.sustainMutation(control.adsr.sustain) }
)

/** Sets the note envelope sustain */
@StrudelDsl
val sustain: DslPatternCreator by dslPatternCreator(sustainMutation)

// ADSR Release ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val releaseMutation = voiceModifier {
    copy(adsr = adsr.copy(release = it?.asDoubleOrNull()))
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternModifier(
    modify = releaseMutation,
    combine = { source, control -> source.releaseMutation(control.adsr.release) }
)

/** Sets the note envelope release */
@StrudelDsl
val release: DslPatternCreator by dslPatternCreator(releaseMutation)

// ADSR Combined ///////////////////////////////////////////////////////////////////////////////////////////////////////

val adsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.mapNotNull { d -> d.toDoubleOrNull() } ?: emptyList()

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
val adsr: DslPatternCreator by dslPatternCreator(adsrMutation)

// Filters - LowPass - lpf() ///////////////////////////////////////////////////////////////////////////////////////////

private val lpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.LowPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
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

private val hpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.HighPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        hcutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
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
val hpf: DslPatternCreator by dslPatternCreator(hpfMutation)

// Filters - BandPass - bandf() ////////////////////////////////////////////////////////////////////////////////////////

private val bandfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.BandPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        bandf = cutoff,
        filters = filters.addOrReplace(filter),
    )
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

private val notchfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.Notch(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
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
val notchf: DslPatternCreator by dslPatternCreator(notchfMutation)

// Filters - resonance() ///////////////////////////////////////////////////////////////////////////////////////////////

private val resonanceMutation = voiceModifier {
    val newQ = it?.asDoubleOrNull() ?: 1.0

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

private val distortMutation = voiceModifier {
    copy(distort = it?.asDoubleOrNull())
}

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
val distort: DslPatternCreator by dslPatternCreator(distortMutation)

// crush() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

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
val crush: DslPatternCreator by dslPatternCreator(crushMutation)

// coarse() ////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val coarseMutation = voiceModifier {
    copy(coarse = it?.asDoubleOrNull())
}

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
val coarse: DslPatternCreator by dslPatternCreator(coarseMutation)

// Reverb room() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val roomMutation = voiceModifier {
    copy(room = it?.asDoubleOrNull())
}

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

private val roomSizeMutation = voiceModifier {
    copy(roomSize = it?.asDoubleOrNull())
}

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

private val delayMutation = voiceModifier {
    copy(delay = it?.asDoubleOrNull())
}

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

private val delayTimeMutation = voiceModifier {
    copy(delayTime = it?.asDoubleOrNull())
}

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

private val delayFeedbackMutation = voiceModifier {
    copy(delayFeedback = it?.asDoubleOrNull())
}

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

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

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

private val scaleMutation = voiceModifier { scale ->
    val newScale = scale?.toString()?.cleanScaleName()
    val currentN = soundIndex

    if (currentN != null && !newScale.isNullOrEmpty()) {
        // If the current note is a number, interpret it using the new scale
        val noteName = Scale.steps(newScale).invoke(currentN)

        copy(
            scale = newScale,
            note = noteName,
            freqHz = Tones.noteToFreq(noteName),
            soundIndex = null, // clear the sound index
        )
    } else {
        copy(scale = newScale)
    }
}

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

private val vibratoMutation = voiceModifier {
    copy(vibrato = it?.asDoubleOrNull())
}

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

private val vibratoModMutation = voiceModifier {
    copy(vibratoMod = it?.asDoubleOrNull())
}

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
val vibratoMod: DslPatternCreator by vibratoModCreator

/** Alias for [vibratoMod] */
val StrudelPattern.vibmod by vibratoModModifier

/** Alias for [vibratoMod] */
val vibmod by vibratoModCreator

// add() ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Adds the given amount to the pattern's value.
 *
 * This is commonly used to transpose note numbers or modify continuous signals.
 *
 * Example: n("0 2").add("5") -> n("5 7")
 */
@StrudelDsl
val StrudelPattern.add: DslMethod by dslMethod { source, args ->
    val arg = args.firstOrNull() ?: return@dslMethod source

    // Create the control pattern from the argument
    val controlPattern = when (arg) {
        is StrudelPattern -> arg
        is Number -> AtomicPattern(VoiceData.empty.copy(value = arg.toDouble()))
        // Use parseMiniNotation with a factory that sets 'value'
        else -> parseMiniNotation(arg.toString()) {
            AtomicPattern(VoiceData.empty.copy(value = it.toDoubleOrNull() ?: 0.0))
        }
    }

    ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed for the control data itself
        combiner = { srcData, ctrlData ->
            val amount = (ctrlData.value as? Number)?.toDouble() ?: 0.0

            // Add to 'value' if present
            val newValue = if (srcData.value is Number) {
                (srcData.value as Number).toDouble() + amount
            } else {
                null
            }

            // Add to 'soundIndex' if present
            val newSoundIndex = if (srcData.soundIndex != null) {
                srcData.soundIndex!! + amount.toInt()
            } else {
                // If we didn't have soundIndex but have a value (e.g. from n()), use that?
                // Or if we only have 'value', maybe we shouldn't force soundIndex unless needed.
                // But for <0 2 ...>, we set both.
                null
            }

            // If we have a note string, we might want to transpose it?
            // (Leaving note transposition for a dedicated 'transpose' function or future improvement)

            srcData.copy(
                value = newValue ?: srcData.value,
                soundIndex = newSoundIndex ?: srcData.soundIndex
            )
        }
    )
}

// accelerate() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val accelerateMutation = voiceModifier {
    copy(accelerate = it?.asDoubleOrNull())
}

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
val accelerate: DslPatternCreator by dslPatternCreator(accelerateMutation)
