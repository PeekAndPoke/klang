package io.peekandpoke.klang.ui.feel

import de.peekandpoke.kraft.addons.styling.StyleSheets
import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.klang.ui.feel.KlangTheme.initialize
import io.peekandpoke.klang.ui.feel.KlangTheme.update
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

    /** Reads CSS custom properties from `:root`, applies the resulting theme, and emits it. */
    fun initialize() {
        update(KlangLookAndFeel.fromCss())
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
    val gold: Color get() = Color(invoke().gold)

    val excellent: Color get() = Color(invoke().excellent)
    val good: Color get() = Color(invoke().good)
    val moderate: Color get() = Color(invoke().moderate)
    val warning: Color get() = Color(invoke().warning)
    val critical: Color get() = Color(invoke().critical)

    val statusColors: List<Color> get() = invoke().statusColors

    // ── Gauge helpers ─────────────────────────────────────────────────────────

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
        val gold: String get() = KlangTheme().gold

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
        style.setProperty("--klang-gold", laf.gold)
        style.setProperty("--klang-excellent", laf.excellent)
        style.setProperty("--klang-good", laf.good)
        style.setProperty("--klang-moderate", laf.moderate)
        style.setProperty("--klang-warning", laf.warning)
        style.setProperty("--klang-critical", laf.critical)
    }
}
