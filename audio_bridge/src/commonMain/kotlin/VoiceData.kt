package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Defines a voice
 */
@Serializable
data class VoiceData(
    // note, scale, freq
    // TODO: note can also be numbers -> Midi and detune, f.e. 50.3
    val note: String?,
    val freqHz: Double?,
    val scale: String?,

    // Gain / Dynamics
    val gain: Double?,
    val velocity: Double?,
    val postGain: Double?,
    val legato: Double?,

    // Sound, bank, sound index
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    val bank: String?,
    /** Parsed from osc if it looks like "bd:2". sound="bd", soundIndex=2 */
    val sound: String?,
    /** Sound index */
    val soundIndex: Int?,

    // Oscillator parameters
    /** Density */
    val density: Double?,
    /** Panorama spread */
    val panSpread: Double?,
    /** Frequency spread */
    val freqSpread: Double?,
    /** Number of voices */
    val voices: Double?,

    // Oscillator warmth (custom addon)
    /** Controls oscillator warmth (low-pass filtering amount). 0.0 = bright, 1.0 = muffled */
    val warmth: Double?,

    // Filters
    val filters: FilterDefs = FilterDefs.empty,

    // ADSR
    val adsr: AdsrEnvelope,

    // Pitch / Glisando
    val accelerate: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Pitch envelope
    val pAttack: Double?,
    val pDecay: Double?,
    val pRelease: Double?,
    val pEnv: Double?,
    val pCurve: Double?,
    val pAnchor: Double?,

    // FM Synthesis
    val fmh: Double?,
    val fmAttack: Double?,
    val fmDecay: Double?,
    val fmSustain: Double?,
    val fmEnv: Double?,

    // Effects
    val distort: Double?,
    val coarse: Double?,
    val crush: Double?,

    // Phaser
    val phaser: Double?,
    val phaserDepth: Double?,
    val phaserCenter: Double?,
    val phaserSweep: Double?,

    // Tremolo
    val tremoloSync: Double?,
    val tremoloDepth: Double?,
    val tremoloSkew: Double?,
    val tremoloPhase: Double?,
    val tremoloShape: String?,

    // Ducking / Sidechain
    val duckOrbit: Int?,
    val duckAttack: Double?,
    val duckDepth: Double?,

    // HPF / LPF
    /** Low pass filter cutoff frequency */
    val cutoff: Double?,
    /** High pass filter cutoff frequency */
    val hcutoff: Double?,
    /** Band pass filter cutoff frequency */
    val bandf: Double?,
    /** Resonance amount for filters */
    val resonance: Double?,

    // Routing
    val orbit: Int?,

    // Panning (-1.0 = Left, 0.0 = Center, 1.0 = Right)
    val pan: Double?,

    // Delay
    val delay: Double?, // Mix amount (0.0 to 1.0)
    val delayTime: Double?, // Time in seconds
    val delayFeedback: Double?, // Feedback amount (0.0 to <1.0)

    // Reverb
    val room: Double?,
    val roomSize: Double?,
    val roomFade: Double?,
    val roomLp: Double?,
    val roomDim: Double?,
    val iResponse: String?,

    // Sample manipulation
    val begin: Double?,
    val end: Double?,
    val speed: Double?,
    val loop: Boolean?,
    val cut: Int?,
    val loopBegin: Double?,
    val loopEnd: Double?,

    // Dynamics / Compression
    val compressor: String?,

    // Solo
    /** Solo amount: 1.0 = full solo (mute others), 0.0 = no solo. Frontend defaults to 0.95. */
    val solo: Double?,

    /** Unique source ID for tracking which audio source this voice came from (e.g., pattern, track, instrument) */
    val sourceId: String?,
) {
    companion object {
        val empty = VoiceData(
            note = null,
            freqHz = null,
            scale = null,
            gain = null,
            velocity = null,
            postGain = null,
            legato = null,
            bank = null,
            sound = null,
            soundIndex = null,
            density = null,
            panSpread = null,
            freqSpread = null,
            voices = null,
            warmth = null,
            filters = FilterDefs.empty,
            adsr = AdsrEnvelope.empty,
            accelerate = null,
            vibrato = null,
            vibratoMod = null,
            pAttack = null,
            pDecay = null,
            pRelease = null,
            pEnv = null,
            pCurve = null,
            pAnchor = null,
            fmh = null,
            fmAttack = null,
            fmDecay = null,
            fmSustain = null,
            fmEnv = null,
            distort = null,
            coarse = null,
            crush = null,
            phaser = null,
            phaserDepth = null,
            phaserCenter = null,
            phaserSweep = null,
            tremoloSync = null,
            tremoloDepth = null,
            tremoloSkew = null,
            tremoloPhase = null,
            tremoloShape = null,
            duckOrbit = null,
            duckAttack = null,
            duckDepth = null,
            cutoff = null,
            hcutoff = null,
            bandf = null,
            resonance = null,
            orbit = null,
            pan = null,
            delay = null,
            delayTime = null,
            delayFeedback = null,
            room = null,
            roomSize = null,
            roomFade = null,
            roomLp = null,
            roomDim = null,
            iResponse = null,
            begin = null,
            end = null,
            speed = null,
            loop = null,
            cut = null,
            loopBegin = null,
            loopEnd = null,
            compressor = null,
            solo = null,
            sourceId = null,
        )
    }

    fun asSampleRequest(): SampleRequest {
        return SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
    }
}
