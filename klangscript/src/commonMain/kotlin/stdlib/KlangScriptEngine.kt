/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Engine` object for KlangScript — builds [EngineDsl] voice-pipeline configs.
 *
 * Use a built-in (`Engine.modern()` / `Engine.pedal()`) and tweak its character, or author a
 * custom pipeline with `Engine.of(Stage.…)`. Pass the result to a pattern's `.engine(…)`:
 *
 * ```
 * let warm  = Engine.modern().expK(2.5).declick(0.0008)
 * let dirty = Engine.of(Stage.vca().expK(2.0), Stage.distort(), Stage.filter().drift(8.0))
 * note("c e g").engine(dirty)
 * ```
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Engine")
object KlangScriptEngine {

    override fun toString(): String = "[Engine object]"

    /** The default subtractive engine: `osc → waveshaper → VCF → VCA` (ADSR last). */
    @KlangScript.Method
    fun modern(): EngineDsl = EngineDsl.modern

    /** Guitar-pedal engine: VCA first, so the waveshapers respond to dynamics. */
    @KlangScript.Method
    fun pedal(): EngineDsl = EngineDsl.pedal

    /**
     * Builds a custom engine from an ordered list of stages. Stages may be omitted freely —
     * a slot only renders if the note's matching amount (distort/crush/cutoff…) is active.
     *
     * @param stages the pipeline, in order (e.g. `Stage.filterMod(), Stage.vca(), Stage.filter()`)
     */
    @KlangScript.Method
    fun of(vararg stages: StageDsl): EngineDsl = EngineDsl(stages.toList())
}

/**
 * `Stage` object for KlangScript — builds the [StageDsl] slots of an [EngineDsl] pipeline.
 *
 * Marker stages (`filterMod`/`crush`/`coarse`/`distort`/`tremolo`/`phaser`) carry no config.
 * `filter()` and `vca()` return *configurable* stages — chain their tuning right after, before
 * adding the next stage: `Stage.vca().expK(2.0).declick(0.5)`, `Stage.filter().drive(1.0)`.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Stage")
object KlangScriptStage {

    override fun toString(): String = "[Stage object]"

    /** Control-rate filter-cutoff modulation (belongs first in the pipeline). */
    @KlangScript.Method
    fun filterMod(): StageDsl = StageDsl.FilterMod

    /** Bit-crusher waveshaper. */
    @KlangScript.Method
    fun crush(): StageDsl = StageDsl.Crush

    /** Sample-rate reducer ("coarse") waveshaper. */
    @KlangScript.Method
    fun coarse(): StageDsl = StageDsl.Coarse

    /** Distortion waveshaper. */
    @KlangScript.Method
    fun distort(): StageDsl = StageDsl.Distort

    /** Tremolo (post-filter amplitude LFO). */
    @KlangScript.Method
    fun tremolo(): StageDsl = StageDsl.Tremolo

    /** Phaser (post-filter all-pass sweep). */
    @KlangScript.Method
    fun phaser(): StageDsl = StageDsl.Phaser

    /** Main filter + its per-voice "feel" (`cutoffOffset` / `drive` / `drift` — chain to tune). */
    @KlangScript.Method
    fun filter(): StageDsl.Filter = StageDsl.Filter()

    /** Amplitude VCA (ADSR) + its envelope character (`expK` / `declick` — chain to tune). */
    @KlangScript.Method
    fun vca(): StageDsl.Vca = StageDsl.Vca()
}
