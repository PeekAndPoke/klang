/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.ultra.common.roundWithPrecision
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.window
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Motor's readings as the visuals show them (the plate's board, Klangamp), smoothed as the start page's needles
 * are: a fresh stream per call, so each visual subscribes to its own and lets go of it when it unmounts.
 */
object MotorReadings {

    /** The current player's latest diagnostics; null while there is none; not followed while the window has no attention. */
    private val online get() = Player.diagnostics.stream.cutoffWhen(UiWork.resting)

    /** The cylinders at work, 0 to 32; null while the Motor gives none. */
    fun cylinders(): Stream<Double?> = online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 32 * PlayerMiniStats.NEEDLE_NEAR)

    /** The Motor's heat, 0 (idle) to 1 (no headroom left); null while the Motor gives none. */
    fun heat(): Stream<Double?> = online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)

    /** The voices sounding; null while the Motor gives none. */
    fun voices(): Stream<Double?> = online.map { d -> d?.activeVoiceCount?.toDouble() }
        .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 100 * PlayerMiniStats.NEEDLE_NEAR)

    /**
     * The scope's signal now, 0 (silence) to 1 (full scale), as its glow shows it: mostly the low end, so the glow pumps
     * with the kick and the bass, jumping up with a beat and dying away after it (the maintainer, 2026-10-29: "can it
     * react faster to changes in energy... or maybe more in the lower octaves... so it looks a bit more pumpy with the
     * drums"). Read on every new frame of the waveform; values only when they change.
     */
    fun signal(): Stream<Double?> {
        val follower = GlowFollower()

        return Player.player.map { it?.getAnalyzer() }
            .switchMapNotNull { it.waveform }
            .cutoffWhen(UiWork.resting)
            .map<AnalyzerBufferHistory?, Double?> { history ->
                val target = if (history == null || history.size == 0) {
                    0.0
                } else {
                    glowEnergy(
                        frame = history[0],
                        size = history.bufferSize,
                        sampleRate = Player.get()?.options?.sampleRate ?: GlowFollower.SAMPLE_RATE,
                    )
                }

                follower.follow(target = target, nowMs = window.performance.now()).roundWithPrecision(3)
            }
            .distinct()
    }
}

/**
 * Follows the glow's energy: up quickly ([ATTACK_KEEP]), down slowly ([RELEASE_KEEP]), each as the share of the gap
 * kept per 10 ms, so it moves the same at any frame rate.
 */
internal class GlowFollower {

    companion object {
        /** How much of a rise is still to go after 10 ms: a beat lights it at once. */
        const val ATTACK_KEEP = 0.2

        /** How much of a fall is still to go after 10 ms: it dies away between the beats. */
        const val RELEASE_KEEP = 0.86

        /** The sample rate assumed while no player tells its own. */
        const val SAMPLE_RATE = 48_000
    }

    /** The glow now, 0 to 1. */
    var level = 0.0
        private set

    private var lastMs = Double.NaN

    /** The glow at [nowMs], moved towards [target]. */
    fun follow(target: Double, nowMs: Double): Double {
        val dt = nowMs - lastMs
        lastMs = nowMs

        // NaN-guard: the first frame, or a clock that went back, takes the target as it is
        level = if (dt.isNaN() || dt < 0.0) {
            target
        } else {
            val keep = (if (target > level) ATTACK_KEEP else RELEASE_KEEP).pow(dt / 10.0)

            target + (level - target) * keep
        }

        return level
    }
}

/** The low end the glow listens to most: the kick and the bass, below this. */
internal const val GLOW_LOW_HZ = 150.0

/** The low end's share of the glow; the rest is the whole signal's. */
internal const val GLOW_LOW_SHARE = 0.75

/** The low end lifted: low-passed, a mix peaks lower than it does whole. */
internal const val GLOW_LOW_GAIN = 1.6

/** The newest samples measured, about one 60 Hz frame at 48 kHz: the older ones were measured on an earlier frame. */
internal const val GLOW_NEWEST_SAMPLES = 800

/**
 * The glow's energy in a waveform [frame] of [size] samples, 0 to 1: the newest samples' peak below [GLOW_LOW_HZ]
 * (a one-pole low-pass over the whole frame, so it has settled when the newest samples come), lifted by
 * [GLOW_LOW_GAIN], as [GLOW_LOW_SHARE] of it, and the whole signal's peak as the rest.
 */
internal fun glowEnergy(frame: Float32Array, size: Int, sampleRate: Int): Double {
    // NaN-guard: no rate counts as the usual one
    val rate = if (sampleRate > 0) sampleRate else GlowFollower.SAMPLE_RATE
    val pole = exp(-2.0 * PI * GLOW_LOW_HZ / rate)
    val from = max(0, size - GLOW_NEWEST_SAMPLES)

    var low = 0.0
    var lowPeak = 0.0
    var wholePeak = 0.0

    for (i in 0 until size) {
        val x = frame[i].toDouble()
        low = x + (low - x) * pole

        if (i >= from) {
            lowPeak = max(lowPeak, abs(low))
            wholePeak = max(wholePeak, abs(x))
        }
    }

    val energy = GLOW_LOW_SHARE * min(1.0, lowPeak * GLOW_LOW_GAIN) + (1.0 - GLOW_LOW_SHARE) * min(1.0, wholePeak)

    // NaN-guard: a frame with a NaN in it glows not at all
    return if (energy.isNaN()) 0.0 else energy.coerceIn(0.0, 1.0)
}
