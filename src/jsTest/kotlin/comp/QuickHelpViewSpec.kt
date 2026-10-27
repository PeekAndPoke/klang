/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.peekandpoke.klang.script.intel.LocalUsage
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType
import io.peekandpoke.kraft.KraftApp
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.window
import kotlinx.html.Tag
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/** `lowpass` in two kinds: on a string first, as a library may list it, and on a pattern. */
private val lowpass = KlangSymbol(
    name = "lowpass",
    category = "filters",
    origin = KlangSymbol.Origin.Library("sprudel"),
    variants = listOf(
        KlangCallable(
            name = "lowpass",
            receiver = KlangType(simpleName = "String"),
            params = listOf(KlangParam(name = "freq", type = KlangType(simpleName = "PatternLike"))),
            description = "On a string: STRING KIND",
        ),
        KlangCallable(
            name = "lowpass",
            receiver = KlangType(simpleName = "SprudelPattern"),
            params = listOf(
                KlangParam(name = "freq", type = KlangType(simpleName = "PatternLike"), description = "The cutoff in Hz."),
                KlangParam(name = "q", type = KlangType(simpleName = "PatternLike"), isOptional = true, defaultDoc = "0.707"),
            ),
            returnType = KlangType(simpleName = "SprudelPattern"),
            description = "On a pattern: PATTERN KIND",
        ),
    ),
)

/** `lowpass` where the receiver is known to be a pattern: one kind only. */
private val lowpassOnPattern = lowpass.copy(variants = lowpass.variants.drop(1))

/** `lowpass` with other kinds: in an Ignitor, and on a pattern. */
private val lowpassInIgnitor = lowpass.copy(
    variants = listOf(
        KlangCallable(
            name = "lowpass",
            receiver = KlangType(simpleName = "IgnitorDsl"),
            params = emptyList(),
            description = "In an Ignitor: IGNITOR KIND",
        ),
        lowpass.variants[1],
    ),
)

/** A panel whose name changes under it, as the song page's does from hover to hover. */
private class SwappingHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: SwappingHost? = null
    }

    var symbol: KlangSymbol by value(lowpass)

    init {
        current = this
    }

    override fun VDom.render() {
        QuickHelpView(symbol = symbol, declarationCode = null, onNavigate = { _, _ -> })
    }
}

@Suppress("FunctionName")
private fun Tag.SwappingHost() = comp { SwappingHost(it) }

/** The Quick help panel's view of a name (KLANG-00349): a pattern's kind first, its call, its parameters, its kinds. */
class QuickHelpViewSpec : StringSpec({

    fun Element.text() = textContent ?: ""

    /** The descriptions are markdown: the app's markdown addon renders them. */
    val withMarkdown: KraftApp.Builder.() -> Unit = { addons { marked() } }

    val noNav: (KlangSymbol, dynamic) -> Unit = { _, _ -> }

    "a pattern's kind shows first: the call as one writes it, its description, its parameters with type and default" {
        TestBed.preact(
            appSetup = withMarkdown,
            view = { QuickHelpView(symbol = lowpass, declarationCode = null, onNavigate = noNav) },
        ) { root ->
            val view = root.single()

            eventually {
                view.text() shouldContain ".lowpass(freq, q)"
                view.text() shouldContain "gives SprudelPattern"
                view.text() shouldContain "PATTERN KIND"
                view.text() shouldNotContain "STRING KIND"
                view.text() shouldContain "PatternLike = 0.707"
                view.text() shouldContain "The cutoff in Hz."
            }
        }
    }

    "the kinds are keys, the pattern's pressed; another one, pressed, shows its own facts" {
        TestBed.preact(
            appSetup = withMarkdown,
            view = { QuickHelpView(symbol = lowpass, declarationCode = null, onNavigate = noNav) },
        ) { root ->
            val view = root.single()
            val kinds = view.querySelectorAll("button[aria-pressed]")
            val keys = (0 until kinds.length).map { kinds.item(it) as HTMLElement }

            keys.map { it.textContent } shouldContainExactly listOf("Pattern", "String")
            keys.map { it.getAttribute("aria-pressed") } shouldContainExactly listOf("true", "false")

            keys[1].click()

            eventually {
                view.text() shouldContain "STRING KIND"
                view.text() shouldNotContain "PATTERN KIND"
            }
        }
    }

    "the same name with other kinds starts over at its first: never the old place, never a blank panel" {
        TestBed.preact(appSetup = withMarkdown, view = { SwappingHost() }) { root ->
            val view = root.single()

            eventually { (view.querySelectorAll("button[aria-pressed]").item(1) as HTMLElement).click() }
            eventually { view.text() shouldContain "STRING KIND" }

            SwappingHost.current!!.symbol = lowpassInIgnitor

            eventually {
                view.text() shouldContain "PATTERN KIND"
                view.text() shouldNotContain "IGNITOR KIND"
            }

            eventually { (view.querySelectorAll("button[aria-pressed]").item(1) as HTMLElement).click() }
            eventually { view.text() shouldContain "IGNITOR KIND" }

            SwappingHost.current!!.symbol = lowpassOnPattern

            eventually {
                view.text() shouldContain "PATTERN KIND"
                view.text() shouldContain ".lowpass(freq, q)"
            }
        }
    }

    "a plain click on a Lexikon link goes to the host, not the page; with Ctrl it keeps its own way" {
        val withLink = lowpass.copy(
            variants = listOf(
                KlangCallable(
                    name = "lowpass",
                    receiver = KlangType(simpleName = "SprudelPattern"),
                    params = emptyList(),
                    description = "One filter per [the *orbit* bus](/manuals/lexikon/orbit-bus), or [elsewhere](/manuals/other).",
                ),
            ),
        )
        val opened = mutableListOf<String>()

        TestBed.preact(
            appSetup = withMarkdown,
            view = { QuickHelpView(symbol = withLink, declarationCode = null, onNavigate = noNav, onLexikon = { opened.add(it) }) },
        ) { root ->
            val view = root.single()

            // Whether the view stopped the link's own way; then stopped here anyway, so the test page never leaves
            val stoppedByView = mutableListOf<Boolean>()
            val stopper: (Event) -> Unit = { event ->
                stoppedByView.add(event.defaultPrevented)
                event.preventDefault()
            }

            fun click(selector: String, ctrl: Boolean = false) {
                view.querySelector(selector)!!.dispatchEvent(
                    MouseEvent("click", MouseEventInit(bubbles = true, cancelable = true, ctrlKey = ctrl)),
                )
            }

            eventually { (view.querySelector("a[href='/manuals/lexikon/orbit-bus']") != null) shouldBe true }

            window.addEventListener("click", stopper)

            try {
                // On the link's emphasised word: the link is what counts, not the word under the mouse
                click("a[href='/manuals/lexikon/orbit-bus'] em")
                click("a[href='/manuals/lexikon/orbit-bus'] em", ctrl = true)
                click("a[href='/manuals/other']")
            } finally {
                window.removeEventListener("click", stopper)
            }

            opened shouldContainExactly listOf("orbit-bus")
            withClue("plain: stays on the page; with Ctrl: its own way; another link: its own way") {
                stoppedByView shouldContainExactly listOf(true, false, false)
            }
        }
    }

    "its aliases are named and each links to its own docs page" {
        val withAliases = lowpass.copy(aliases = listOf("lpf", "cutoff"))

        TestBed.preact(
            appSetup = { addons { marked() }; routing { } },
            view = { QuickHelpView(symbol = withAliases, declarationCode = null, onNavigate = noNav) },
        ) { root ->
            val view = root.single()

            eventually { view.text() shouldContain "Aliases: lpf, cutoff" }

            val links = view.querySelectorAll("a[data-alias]")
            fun href(index: Int): String = decodeURIComponent((links.item(index) as Element).getAttribute("href")!!)

            links.length shouldBe 2
            href(0) shouldContain "/manuals/library/sprudel"
            href(0) shouldContain "function:lpf"
            href(1) shouldContain "function:cutoff"
        }
    }

    "a help text links the Lexikon's terms by itself, once each, beside a hand-written link" {
        helpMarkdown("Set [per voice](/manuals/lexikon/voice): the pitch, its pitch again, and the [semitones].") shouldBe
            "Set [per voice](/manuals/lexikon/voice): the [pitch](/manuals/lexikon/pitch), its pitch again, and the `semitones`."
    }

    "a KDoc reference shows as code; a link, a reference definition and code stay as they are" {
        kdocRefsAsCode("[semitones] is the shift on the [PitchEnvelopeBuilder].") shouldBe
            "`semitones` is the shift on the `PitchEnvelopeBuilder`."
        kdocRefsAsCode("per [orbit bus](/manuals/lexikon/orbit-bus)") shouldBe "per [orbit bus](/manuals/lexikon/orbit-bus)"
        kdocRefsAsCode("see [voice](/manuals/lexikon/voice)") shouldBe "see [voice](/manuals/lexikon/voice)"
        kdocRefsAsCode("[ref]: https://example.org") shouldBe "[ref]: https://example.org"
        kdocRefsAsCode("as `x[i]` reads") shouldBe "as `x[i]` reads"
    }

    "a Lexikon entry's link gives its slug; another link none" {
        lexikonSlugOf("/manuals/lexikon/orbit-bus") shouldBe "orbit-bus"
        lexikonSlugOf("#/manuals/lexikon/perlin-noise") shouldBe "perlin-noise"
        lexikonSlugOf("https://klang.example/manuals/lexikon/voice/") shouldBe "voice"
        lexikonSlugOf("/manuals/lexikon") shouldBe null
        lexikonSlugOf("/manuals/other") shouldBe null
        lexikonSlugOf("/manuals/lexikon/orbit-bus/more") shouldBe null
    }

    "a local name shows its declaration as the song writes it, and no docs key" {
        val local = KlangSymbol(
            name = "riff",
            category = "local",
            origin = KlangSymbol.Origin.Local(KlangSymbol.LocalKind.entries.first()),
            variants = emptyList(),
        )

        TestBed.preact(
            appSetup = withMarkdown,
            view = { QuickHelpView(symbol = local, declarationCode = "let riff = 1", onNavigate = noNav) },
        ) { root ->
            val view = root.single()

            eventually { view.text() shouldContain "let riff = 1" }

            val buttons = view.querySelectorAll("button")

            (0 until buttons.length).any { (buttons.item(it) as HTMLElement).textContent == "Docs" } shouldBe false
        }
    }

    "a local lists where the song uses it, each line with its code; a click jumps there" {
        val local = KlangSymbol(
            name = "riff",
            category = "local",
            origin = KlangSymbol.Origin.Local(KlangSymbol.LocalKind.entries.first()),
            variants = emptyList(),
        )
        val usages = listOf(
            LocalUsage(start = 20, end = 24, line = 3, code = "run(riff)"),
            LocalUsage(start = 41, end = 45, line = 7, code = "stack(riff, beat)"),
        )
        val jumped = mutableListOf<Int>()

        TestBed.preact(
            appSetup = withMarkdown,
            view = {
                QuickHelpView(
                    symbol = local,
                    declarationCode = "let riff = 1",
                    onNavigate = noNav,
                    usages = usages,
                    onJump = { jumped.add(it) },
                )
            },
        ) { root ->
            val view = root.single()

            eventually { view.text() shouldContain "Used on" }

            val uses = view.querySelectorAll("[data-use]")
            val rows = (0 until uses.length).map { uses.item(it) as HTMLElement }

            rows.map { it.textContent } shouldContainExactly listOf("3run(riff)", "7stack(riff, beat)")

            rows[1].click()

            jumped shouldContainExactly listOf(41)
        }
    }
})

private external fun decodeURIComponent(encoded: String): String
