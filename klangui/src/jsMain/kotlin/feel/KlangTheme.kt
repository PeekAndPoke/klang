/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.peekandpoke.klang.ui.feel.KlangTheme.initialize
import io.peekandpoke.klang.ui.feel.KlangTheme.update
import io.peekandpoke.kraft.addons.styling.StyleSheets
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.document
import kotlinx.css.Color
import org.w3c.dom.HTMLElement

/**
 * Single entry-point for the active Klang look-and-feel.
 *
 * **In Kraft components** — subscribe to re-render on theme change:
 * ```kotlin
 * private val laf by subscribingTo(KlangTheme)
 * // in render:
 * css { backgroundColor = Color(laf.appBackground) }
 * ```
 *
 * **In non-subscribing contexts** (e.g. CodeMirrorTheme) — read the current snapshot:
 * ```kotlin
 * val bg = KlangTheme.Hex.appBackground
 * val color = KlangTheme.appBackground
 * ```
 *
 * Call [initialize] once in `main()` before mounting the app.
 * Call [update] to apply a new look-and-feel at runtime (future user theming).
 */
object KlangTheme : Stream<KlangLookAndFeel> {

    private val source: StreamSource<KlangLookAndFeel> = StreamSource(KlangLookAndFeel())

    private var mountedStyles: KlangLookAndFeel.Styles? = null

    override fun invoke(): KlangLookAndFeel = source()

    override fun subscribeToStream(sub: (KlangLookAndFeel) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Reads CSS custom properties from `:root`, applies the resulting theme, and emits it; writes the look's roles. */
    fun initialize() {
        update(KlangLookAndFeel.fromCss())
        applyLayers(lookLayers)
    }

    // ── The look: roles and layers (KLANG-00337) ──────────────────────────────

    private var lookLayers: KlangLookLayers = KlangLookLayers()

    private val lookSource: StreamSource<KlangLook> = StreamSource(KlangLook.defaults)

    /** The resolved look; subscribe where a value is needed outside CSS (a canvas, a JS object). */
    val look: Stream<KlangLook> get() = lookSource

    /** The stack the look is resolved from. */
    val layers: KlangLookLayers get() = lookLayers

    /** Puts [values] on [layer] (values a role does not accept are dropped) and repaints through the CSS variables. */
    fun setLayer(layer: KlangLookLayers.Layer, values: Map<KlangRole, String>) {
        applyLayers(lookLayers.with(layer, values))
    }

    /** Empties [layer]; the layers below show again. */
    fun clearLayer(layer: KlangLookLayers.Layer) {
        applyLayers(lookLayers.cleared(layer))
    }

    private var wornLook: KlangLookPreset = KlangLooks.Default

    /** The look worn: the one chosen last, built-in or the user's own. */
    val worn: KlangLookPreset get() = wornLook

    /** Puts [preset] on the user's layer (KLANG-00339) and wears it; the default look clears the layer. */
    fun choose(preset: KlangLookPreset) {
        wornLook = preset
        putOnUserLayer(preset.values)
    }

    /**
     * Puts [values] on the user's layer without wearing them (KLANG-00382): the look editor shows the look being edited
     * on the whole app; [endPreview] goes back to the look worn.
     */
    fun preview(values: Map<KlangRole, String>) {
        putOnUserLayer(values)
    }

    /** The look worn again, after a [preview]. */
    fun endPreview() {
        putOnUserLayer(wornLook.values)
    }

    /** The look worn while the user's layer holds it; null while it holds something else (a preview, say). */
    val chosen: KlangLookPreset?
        get() = wornLook.takeIf { it.values == lookLayers[KlangLookLayers.Layer.User] }

    private fun putOnUserLayer(values: Map<KlangRole, String>) {
        if (values.isEmpty()) {
            clearLayer(KlangLookLayers.Layer.User)
        } else {
            setLayer(KlangLookLayers.Layer.User, values)
        }
    }

    private fun applyLayers(next: KlangLookLayers) {
        lookLayers = next

        val resolved = next.resolve()
        val style = (document.documentElement as? HTMLElement)?.style

        if (style != null) {
            for ((name, value) in resolved.cssVars()) {
                style.setProperty(name, value)
            }
        }

        lookSource(resolved)
    }

    /** Replaces the active theme: syncs CSS vars, swaps the stylesheet, and emits the new value. */
    fun update(laf: KlangLookAndFeel) {
        swapStyles(laf)
        syncCssVars(laf)
        source(laf)
    }

    // ── Snapshot color accessors (Color) ──────────────────────────────────────

    val appBackground: Color get() = Color(invoke().appBackground)
    val menuBackground: Color get() = Color(invoke().menuBackground)
    val panelBackground: Color get() = Color(invoke().panelBackground)
    val cardBackground: Color get() = Color(invoke().cardBackground)
    val overlayBackground: Color get() = Color(invoke().overlayBackground)

    val textPrimary: Color get() = Color(invoke().textPrimary)
    val textSecondary: Color get() = Color(invoke().textSecondary)
    val textTertiary: Color get() = Color(invoke().textTertiary)

    val accent: Color get() = Color(invoke().accent)
    val accentMuted: Color get() = Color(invoke().accentMuted)
    val gold: Color get() = Color(invoke().gold)
    val bronze: Color get() = Color(invoke().bronze)
    val popupBackground: Color get() = Color(invoke().popupBackground)

    val excellent: Color get() = Color(invoke().excellent)
    val good: Color get() = Color(invoke().good)
    val moderate: Color get() = Color(invoke().moderate)
    val warning: Color get() = Color(invoke().warning)
    val critical: Color get() = Color(invoke().critical)

    val statusColors: List<Color> get() = invoke().statusColors

    // ── Gauge helpers ─────────────────────────────────────────────────────────

    /** The glow's blur radius around round gauges, as a share of their size. */
    val gaugeGlowBlur: Double get() = invoke().gaugeGlowBlur

    fun rangedMixer(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = statusColors,
    )

    fun rangedMixerReversed(from: Number, to: Number) = RangeColorMixer(
        range = from.toDouble()..to.toDouble(),
        colors = statusColors.asReversed(),
    )

    // ── Hex strings (for CodeMirror / canvas / JS object contexts) ────────────

    object Hex {
        val appBackground: String get() = KlangTheme().appBackground
        val menuBackground: String get() = KlangTheme().menuBackground
        val panelBackground: String get() = KlangTheme().panelBackground
        val cardBackground: String get() = KlangTheme().cardBackground
        val overlayBackground: String get() = KlangTheme().overlayBackground

        val textPrimary: String get() = KlangTheme().textPrimary
        val textSecondary: String get() = KlangTheme().textSecondary
        val textTertiary: String get() = KlangTheme().textTertiary

        val accent: String get() = KlangTheme().accent
        val accentMuted: String get() = KlangTheme().accentMuted
        val gold: String get() = KlangTheme().gold
        val bronze: String get() = KlangTheme().bronze
        val popupBackground: String get() = KlangTheme().popupBackground

        val excellent: String get() = KlangTheme().excellent
        val good: String get() = KlangTheme().good
        val moderate: String get() = KlangTheme().moderate
        val warning: String get() = KlangTheme().warning
        val critical: String get() = KlangTheme().critical
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun swapStyles(laf: KlangLookAndFeel) {
        mountedStyles?.let { StyleSheets.unmount(it) }
        mountedStyles = laf.styles
        StyleSheets.mount(laf.styles)
    }

    private fun syncCssVars(laf: KlangLookAndFeel) {
        val style = (document.documentElement as? HTMLElement)?.style ?: return
        style.setProperty("--klang-bg-app", laf.appBackground)
        style.setProperty("--klang-bg-menu", laf.menuBackground)
        style.setProperty("--klang-bg-panel", laf.panelBackground)
        style.setProperty("--klang-bg-card", laf.cardBackground)
        style.setProperty("--klang-bg-overlay", laf.overlayBackground)
        style.setProperty("--klang-text-primary", laf.textPrimary)
        style.setProperty("--klang-text-secondary", laf.textSecondary)
        style.setProperty("--klang-text-tertiary", laf.textTertiary)
        style.setProperty("--klang-accent", laf.accent)
        style.setProperty("--klang-accent-muted", laf.accentMuted)
        style.setProperty("--klang-gold", laf.gold)
        style.setProperty("--klang-bronze", laf.bronze)
        style.setProperty("--klang-bg-popup", laf.popupBackground)
        style.setProperty("--klang-excellent", laf.excellent)
        style.setProperty("--klang-good", laf.good)
        style.setProperty("--klang-moderate", laf.moderate)
        style.setProperty("--klang-warning", laf.warning)
        style.setProperty("--klang-critical", laf.critical)
        style.setProperty("--klang-gauge-glow-blur", laf.gaugeGlowBlur.toString())
    }
}
