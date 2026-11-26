/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.FrameStats
import io.peekandpoke.klang.viz.liveact.PicturePreset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.js.Date

/**
 * The machine test's result as this browser keeps it ([STORAGE_KEY]), so a visitor who comes again on the same
 * machine sees it at once instead of waiting for the test: what the result screen shows and what the show is started
 * with. Kept in the browser only, never sent.
 *
 * [version]: the test's ([TEST_VERSION]); [at]: when it was measured, in milliseconds since 1970; [ratio], [width]
 * and [height]: what it was measured on (the device's pixels for each of the page's, and the screen's size in the
 * page's pixels); [voices]: the safe voices the sound test found; [fps] and [slowestMs]: the frame stats of the
 * picture test's first part (the score's picture half); [quality]: the picture's quality the test chose, a
 * [PicturePreset] by its word (`medium`; KLANG-00492, before that the oversampling and the fog).
 *
 * The names in the stored text are fixed ([SerialName]): a field renamed in the code keeps reading what was kept.
 */
@Serializable
@SerialName("notstrom.machine-test")
data class StoredTest(
    @SerialName("version") val version: Int,
    @SerialName("at") val at: Double,
    @SerialName("ratio") val ratio: Double,
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("voices") val voices: Int,
    @SerialName("fps") val fps: Double,
    @SerialName("slowestMs") val slowestMs: Double,
    @SerialName("quality") val quality: String,
) {
    /** The frame stats the result screen shows. */
    val frames: FrameStats get() = FrameStats(fps = fps, slowestMs = slowestMs)

    /** The picture's quality the test chose; null where the word names none. */
    val preset: PicturePreset? get() = PicturePreset.named(quality)

    /**
     * Whether this result counts for the machine as it is now: measured by this [TEST_VERSION], on a display of the
     * same [ratio] and screen size ([width] by [height]), and holding what the test may choose (a preset of
     * [QUALITY_CHOICES]). Otherwise the test runs again, and its result takes this one's place.
     */
    fun fits(ratio: Double, width: Int, height: Int): Boolean =
        version == TEST_VERSION && this.ratio == ratio && this.width == width && this.height == height &&
            preset in QUALITY_CHOICES

    companion object {
        /**
         * The machine test's version. Raise it when the test or the show's cost changes in a way that matters (what
         * is measured, how it is judged, a heavier stage): every kept result then counts no more, and each visitor's
         * machine is tested anew. Not the release's name: every deploy would test again. 3 since 2026-11-20: three
         * fog qualities, and a kept `high` meant twelve sheets, now eight. 4 since 2026-11-20: the mirror floor's
         * reflections no longer go with the fog (`ReflectionQuality`, on unless a visitor turns them off), so the
         * test's fog part, at low, now draws the new look's mirror as well, which it did not before; a kept result
         * chose its fog on a lighter stage than the show now draws there. 5 since 2026-11-26: half the fog's sheets
         * (KLANG-00484: low two, high four), so a kept choice was made on a heavier fog than the show now draws. 6
         * since 2026-11-26: the test chooses the picture's quality as a whole (KLANG-00492), kept as `quality` in
         * place of the oversampling and the fog.
         */
        const val TEST_VERSION = 6

        /** Where the browser keeps it. */
        const val STORAGE_KEY = "notstrom.machine-test"

        /**
         * The picture's qualities the machine test chooses from ([presetFor]): its first part draws [PicturePreset.Medium],
         * its second [PicturePreset.High]. [PicturePreset.Max] is the visitor's choice, never the test's.
         */
        val QUALITY_CHOICES: List<PicturePreset> = listOf(PicturePreset.Low, PicturePreset.Medium, PicturePreset.High)
    }
}

/**
 * When [at] (milliseconds since 1970) was, in plain words on the result screen: "Measured on this machine on 19 Nov
 * 2026 at 12:05." With the time of day: a test run again on the same day shows as a new one.
 */
fun measuredLine(at: Double): String {
    val date = Date(at)
    val month = MONTHS[date.getMonth().coerceIn(0, MONTHS.size - 1)]
    val minutes = date.getMinutes().toString().padStart(length = 2, padChar = '0')

    return "Measured on this machine on ${date.getDate()} $month ${date.getFullYear()} at ${date.getHours()}:$minutes."
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
