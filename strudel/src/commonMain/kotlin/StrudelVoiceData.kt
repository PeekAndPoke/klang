package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.*
import kotlinx.serialization.Serializable

/**
 * Strudel-specific voice data with flat fields.
 *
 * This is the intermediate representation used within the Strudel pattern system.
 * It uses flat fields (no complex objects like AdsrEnvelope or FilterDefs) to match
 * the original JavaScript Strudel implementation.
 *
 * Gets converted to [io.peekandpoke.klang.audio_bridge.VoiceData] when passed to the audio engine.
 */
@Serializable
data class StrudelVoiceData(
    // note, scale, freq
    val note: String?,
    val freqHz: Double?,
    val scale: String?,
    /** Chord name (e.g., "Cmaj7", "Dm7", "F/A") for harmonic context */
    val chord: String?,

    // Gain / Dynamics
    val gain: Double?,
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

    // ADSR (flattened)
    val attack: Double?,
    val decay: Double?,
    val sustain: Double?,
    val release: Double?,

    // Pitch / Glisando
    val accelerate: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Effects
    val distort: Double?,
    val coarse: Double?,
    val crush: Double?,

    // Filters (flattened) - each filter has its own cutoff and resonance
    /** Low pass filter cutoff frequency */
    val cutoff: Double?,
    /** Low pass filter resonance/Q */
    val resonance: Double?,
    /** High pass filter cutoff frequency */
    val hcutoff: Double?,
    /** High pass filter resonance/Q */
    val hresonance: Double?,
    /** Band pass filter cutoff frequency */
    val bandf: Double?,
    /** Band pass filter resonance/Q */
    val bandq: Double?,
    /** Notch filter cutoff frequency */
    val notchf: Double?,
    /** Notch filter resonance/Q */
    val nresonance: Double?,

    // Lowpass filter envelope
    /** Low pass filter envelope attack time */
    val lpattack: Double?,
    /** Low pass filter envelope decay time */
    val lpdecay: Double?,
    /** Low pass filter envelope sustain level */
    val lpsustain: Double?,
    /** Low pass filter envelope release time */
    val lprelease: Double?,
    /** Low pass filter envelope depth/amount */
    val lpenv: Double?,

    // Highpass filter envelope
    /** High pass filter envelope attack time */
    val hpattack: Double?,
    /** High pass filter envelope decay time */
    val hpdecay: Double?,
    /** High pass filter envelope sustain level */
    val hpsustain: Double?,
    /** High pass filter envelope release time */
    val hprelease: Double?,
    /** High pass filter envelope depth/amount */
    val hpenv: Double?,

    // Bandpass filter envelope
    /** Band pass filter envelope attack time */
    val bpattack: Double?,
    /** Band pass filter envelope decay time */
    val bpdecay: Double?,
    /** Band pass filter envelope sustain level */
    val bpsustain: Double?,
    /** Band pass filter envelope release time */
    val bprelease: Double?,
    /** Band pass filter envelope depth/amount */
    val bpenv: Double?,

    // Notch filter envelope
    /** Notch filter envelope attack time */
    val nfattack: Double?,
    /** Notch filter envelope decay time */
    val nfdecay: Double?,
    /** Notch filter envelope sustain level */
    val nfsustain: Double?,
    /** Notch filter envelope release time */
    val nfrelease: Double?,
    /** Notch filter envelope depth/amount */
    val nfenv: Double?,

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

    // Sample manipulation
    val begin: Double?,
    val end: Double?,
    val speed: Double?,
    val loop: Boolean?,
    val cut: Int?,

    // Custom value
    val value: StrudelVoiceValue? = null,
) {
    companion object {
        val empty = StrudelVoiceData(
            note = null,
            freqHz = null,
            scale = null,
            chord = null,
            gain = null,
            legato = null,
            bank = null,
            sound = null,
            soundIndex = null,
            density = null,
            panSpread = null,
            freqSpread = null,
            voices = null,
            warmth = null,
            attack = null,
            decay = null,
            sustain = null,
            release = null,
            accelerate = null,
            vibrato = null,
            vibratoMod = null,
            distort = null,
            coarse = null,
            crush = null,
            cutoff = null,
            resonance = null,
            hcutoff = null,
            hresonance = null,
            bandf = null,
            bandq = null,
            notchf = null,
            nresonance = null,
            lpattack = null,
            lpdecay = null,
            lpsustain = null,
            lprelease = null,
            lpenv = null,
            hpattack = null,
            hpdecay = null,
            hpsustain = null,
            hprelease = null,
            hpenv = null,
            bpattack = null,
            bpdecay = null,
            bpsustain = null,
            bprelease = null,
            bpenv = null,
            nfattack = null,
            nfdecay = null,
            nfsustain = null,
            nfrelease = null,
            nfenv = null,
            orbit = null,
            pan = null,
            delay = null,
            delayTime = null,
            delayFeedback = null,
            room = null,
            roomSize = null,
            begin = null,
            end = null,
            speed = null,
            loop = null,
            cut = null,
            value = null,
        )
    }

    fun merge(other: StrudelVoiceData): StrudelVoiceData {
        return StrudelVoiceData(
            note = other.note ?: note,
            freqHz = other.freqHz ?: freqHz,
            scale = other.scale ?: scale,
            chord = other.chord ?: chord,
            gain = other.gain ?: gain,
            legato = other.legato ?: legato,
            bank = other.bank ?: bank,
            sound = other.sound ?: sound,
            soundIndex = other.soundIndex ?: soundIndex,
            density = other.density ?: density,
            panSpread = other.panSpread ?: panSpread,
            freqSpread = other.freqSpread ?: freqSpread,
            voices = other.voices ?: voices,
            warmth = other.warmth ?: warmth,
            attack = other.attack ?: attack,
            decay = other.decay ?: decay,
            sustain = other.sustain ?: sustain,
            release = other.release ?: release,
            accelerate = other.accelerate ?: accelerate,
            vibrato = other.vibrato ?: vibrato,
            vibratoMod = other.vibratoMod ?: vibratoMod,
            distort = other.distort ?: distort,
            coarse = other.coarse ?: coarse,
            crush = other.crush ?: crush,
            cutoff = other.cutoff ?: cutoff,
            resonance = other.resonance ?: resonance,
            hcutoff = other.hcutoff ?: hcutoff,
            hresonance = other.hresonance ?: hresonance,
            bandf = other.bandf ?: bandf,
            bandq = other.bandq ?: bandq,
            notchf = other.notchf ?: notchf,
            nresonance = other.nresonance ?: nresonance,
            lpattack = other.lpattack ?: lpattack,
            lpdecay = other.lpdecay ?: lpdecay,
            lpsustain = other.lpsustain ?: lpsustain,
            lprelease = other.lprelease ?: lprelease,
            lpenv = other.lpenv ?: lpenv,
            hpattack = other.hpattack ?: hpattack,
            hpdecay = other.hpdecay ?: hpdecay,
            hpsustain = other.hpsustain ?: hpsustain,
            hprelease = other.hprelease ?: hprelease,
            hpenv = other.hpenv ?: hpenv,
            bpattack = other.bpattack ?: bpattack,
            bpdecay = other.bpdecay ?: bpdecay,
            bpsustain = other.bpsustain ?: bpsustain,
            bprelease = other.bprelease ?: bprelease,
            bpenv = other.bpenv ?: bpenv,
            nfattack = other.nfattack ?: nfattack,
            nfdecay = other.nfdecay ?: nfdecay,
            nfsustain = other.nfsustain ?: nfsustain,
            nfrelease = other.nfrelease ?: nfrelease,
            nfenv = other.nfenv ?: nfenv,
            orbit = other.orbit ?: orbit,
            pan = other.pan ?: pan,
            delay = other.delay ?: delay,
            delayTime = other.delayTime ?: delayTime,
            delayFeedback = other.delayFeedback ?: delayFeedback,
            room = other.room ?: room,
            roomSize = other.roomSize ?: roomSize,
            begin = other.begin ?: begin,
            end = other.end ?: end,
            speed = other.speed ?: speed,
            loop = other.loop ?: loop,
            cut = other.cut ?: cut,
            value = other.value ?: value
        )
    }

    /**
     * Converts this Strudel-specific voice data to audio engine [VoiceData].
     *
     * Maps flat fields to complex objects:
     * - attack, decay, sustain, release → AdsrEnvelope
     * - cutoff/resonance, hcutoff/hresonance, bandf/bandq, notchf/nresonance → FilterDefs
     */
    fun toVoiceData(): VoiceData {
        // Build filter list from flat fields, each with its own resonance
        val filters = buildList {
            cutoff?.let { cutoffValue ->
                // Build envelope if any lpattack/lpdecay/lpsustain/lprelease/lpenv fields are present
                val envelope =
                    if (lpattack != null || lpdecay != null || lpsustain != null || lprelease != null || lpenv != null) {
                        FilterEnvelope(
                            attack = lpattack,
                            decay = lpdecay,
                            sustain = lpsustain,
                            release = lprelease,
                            depth = lpenv,
                        )
                    } else null

                add(
                    FilterDef.LowPass(
                        cutoffHz = cutoffValue,
                        q = resonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            hcutoff?.let { hcutoffValue ->
                // Build envelope if any hpattack/hpdecay/hpsustain/hprelease/hpenv fields are present
                val envelope =
                    if (hpattack != null || hpdecay != null || hpsustain != null || hprelease != null || hpenv != null) {
                        FilterEnvelope(
                            attack = hpattack,
                            decay = hpdecay,
                            sustain = hpsustain,
                            release = hprelease,
                            depth = hpenv,
                        )
                    } else null

                add(
                    FilterDef.HighPass(
                        cutoffHz = hcutoffValue,
                        q = hresonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            bandf?.let { bandfValue ->
                // Build envelope if any bpattack/bpdecay/bpsustain/bprelease/bpenv fields are present
                val envelope =
                    if (bpattack != null || bpdecay != null || bpsustain != null || bprelease != null || bpenv != null) {
                        FilterEnvelope(
                            attack = bpattack,
                            decay = bpdecay,
                            sustain = bpsustain,
                            release = bprelease,
                            depth = bpenv,
                        )
                    } else null

                add(
                    FilterDef.BandPass(
                        cutoffHz = bandfValue,
                        q = bandq ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            notchf?.let { notchfValue ->
                // Build envelope if any nfattack/nfdecay/nfsustain/nfrelease/nfenv fields are present
                val envelope =
                    if (nfattack != null || nfdecay != null || nfsustain != null || nfrelease != null || nfenv != null) {
                        FilterEnvelope(
                            attack = nfattack,
                            decay = nfdecay,
                            sustain = nfsustain,
                            release = nfrelease,
                            depth = nfenv,
                        )
                    } else null

                add(
                    FilterDef.Notch(
                        cutoffHz = notchfValue,
                        q = nresonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }
        }

        return VoiceData(
            note = note,
            freqHz = freqHz,
            scale = scale,
            gain = gain,
            legato = legato,
            bank = bank,
            sound = sound,
            soundIndex = soundIndex,
            density = density,
            panSpread = panSpread,
            freqSpread = freqSpread,
            voices = voices,
            warmth = warmth,
            filters = FilterDefs(filters),
            adsr = AdsrEnvelope(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
            ),
            accelerate = accelerate,
            vibrato = vibrato,
            vibratoMod = vibratoMod,
            distort = distort,
            coarse = coarse,
            crush = crush,
            cutoff = cutoff,
            hcutoff = hcutoff,
            bandf = bandf,
            resonance = resonance, // For backward compatibility, use LPF resonance as default

            orbit = orbit,
            pan = pan,
            delay = delay,
            delayTime = delayTime,
            delayFeedback = delayFeedback,
            room = room,
            roomSize = roomSize,
            begin = begin,
            end = end,
            speed = speed,
            loop = loop,
            cut = cut,
        )
    }
}
