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
    val gain: Double,
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

    // Filters
    val filters: List<FilterDef>,

    // ADSR
    val adsr: AdsrEnvelope,

    // Pitch / Glisando
    val accelerate: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Effects
    val distort: Double?,
    val coarse: Double?,
    val crush: Double?,

    // HPF / LPF
    val cutoff: Double?,
    val hcutoff: Double?,
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
    val roomsize: Double?,

    // ???
    val bandf: Double?,
) {
    companion object {
        val empty = VoiceData(
            note = null,
            freqHz = null,
            scale = null,
            gain = 1.0,
            legato = null,
            bank = null,
            sound = null,
            soundIndex = null,
            density = null,
            panSpread = null,
            freqSpread = null,
            voices = null,
            filters = emptyList(),
            adsr = AdsrEnvelope.empty,
            accelerate = null,
            vibrato = null,
            vibratoMod = null,
            distort = null,
            coarse = null,
            crush = null,
            cutoff = null,
            hcutoff = null,
            resonance = null,
            orbit = null,
            pan = null,
            delay = null,
            delayTime = null,
            delayFeedback = null,
            room = null,
            roomsize = null,
            bandf = null
        )
    }

    fun asSampleRequest(): SampleRequest {
        return SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
    }

    /**
     * Merges this voice data with another.
     * Values from [other] take precedence if they are not null (or default/empty).
     *
     * Note on Gain: Gain is a double with default 1.0.
     * If 'other' has gain 1.0, is it "unset" or "explicitly 1.0"?
     * Usually in Strudel, we multiply gains or replace.
     * Replacing is safer for control patterns unless we define a "GainPattern".
     * Let's assume replace for now.
     */
    fun mergeWith(other: VoiceData): VoiceData {
        return copy(
            note = other.note ?: note,
            freqHz = other.freqHz ?: freqHz,
            scale = other.scale ?: scale,

            // Gain: If other has non-default gain (we assume default is 1.0), use it?
            // Or just always use other's gain?
            // In a control pattern `gain(0.5)`, the event has gain=0.5.
            // We likely want to use that.
            gain = if (other.gain != 1.0) other.gain else gain,

            legato = other.legato ?: legato,
            bank = other.bank ?: bank,
            sound = other.sound ?: sound,
            soundIndex = other.soundIndex ?: soundIndex,
            density = other.density ?: density,
            panSpread = other.panSpread ?: panSpread,
            freqSpread = other.freqSpread ?: freqSpread,
            voices = other.voices ?: voices,

            // TODO: what do we really need to do here?
            // Filters: Append? Replace?
            // Usually control patterns adding filters means adding MORE filters.
            filters = filters + other.filters,

            adsr = adsr.mergeWith(other.adsr),
            accelerate = other.accelerate ?: accelerate,
            vibrato = other.vibrato ?: vibrato,
            vibratoMod = other.vibratoMod ?: vibratoMod,
            distort = other.distort ?: distort,
            coarse = other.coarse ?: coarse,
            crush = other.crush ?: crush,

            // LPF/HPF legacy fields -> assume they are synced with filters list
            cutoff = other.cutoff ?: cutoff,
            hcutoff = other.hcutoff ?: hcutoff,
            resonance = other.resonance ?: resonance,

            orbit = other.orbit ?: orbit,
            pan = other.pan ?: pan,
            delay = other.delay ?: delay,
            delayTime = other.delayTime ?: delayTime,
            delayFeedback = other.delayFeedback ?: delayFeedback,
            room = other.room ?: room,
            roomsize = other.roomsize ?: roomsize,
            bandf = other.bandf ?: bandf,
        )
    }
}
