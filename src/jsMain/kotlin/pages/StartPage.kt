package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.JoinedPageTitle
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.datetime.Kronos
import de.peekandpoke.ultra.common.maths.Ease
import de.peekandpoke.ultra.common.maths.Ease.timed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.comp.PlayerMiniStats
import io.peekandpoke.klang.comp.RoundButton
import io.peekandpoke.klang.comp.Spectrumeter
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.StartPage() = comp {
    StartPage(it)
}

class StartPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private sealed interface State {
        fun update()

        fun getOpacity(): Double

        fun gotoBooting()

    }

    private inner class StateOffline : State {
        override fun update() {
            // noop
        }

        override fun getOpacity(): Double = 0.07

        override fun gotoBooting() {
            console.log("going to booting state")
            state = StateBooting(this)
        }
    }

    private inner class StateBooting(previous: State) : State {
        val durationMs = 2500.toDouble()
        val start = Kronos.systemUtc.millisNow()
        val opacityEase = Ease.In.quad.timed(previous.getOpacity(), 1.0, durationMs.milliseconds)

        init {
            launch {
                Player.ensure().await()
            }
        }

        private fun elapsedMs() = Kronos.systemUtc.millisNow() - start

        override fun update() {
            if (elapsedMs() >= durationMs) {
                state = StateOnline()
            }
        }

        override fun getOpacity(): Double = opacityEase((elapsedMs()) / durationMs)

        override fun gotoBooting() {
            // noop
        }

    }

    private inner class StateOnline : State {
        override fun update() {
            // noop
        }

        override fun getOpacity(): Double {
            return 1.0
        }

        override fun gotoBooting() {
            // noop
        }
    }

    private var state: State by value(StateOffline())

    private val currentOpacity get() = state.getOpacity()

    private val ticker by subscribingTo(ticker(16.milliseconds))

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        JoinedPageTitle { listOf("KLANG", "AUDIO", "MOTÖR") }

        router.navToUri(Nav.newSongCode())

        div {
            key = "start-page"
            css {
                height = 100.vh
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                backgroundColor = Color.black
                color = Color.white
                textAlign = TextAlign.center
            }


            renderMotoer()
        }
    }

    private fun DIV.renderMotoer() {
        div {
            key = "motör"

            css {
                textAlign = TextAlign.center
                position = Position.relative
                // Flex column ensures the container height includes children's margins
                display = Display.flex
                flexDirection = FlexDirection.column
                width = 100.pct
            }

            div {
                RoundButton(
                    icon = { power_off },
                    color = Color.red,
                    onClick = {
                        state.gotoBooting()
                    }
                )
            }

            div {
                key = "stats"
                css {
                    marginBottom = 6.px
                    opacity = currentOpacity
                }
                PlayerMiniStats()
            }

            div {
                key = "oscilloscope"
                css {
                    height = 60.px
                    opacity = currentOpacity
                }
                Oscilloscope { Player.get() }
            }

            div {
                key = "title"

                css {
                    marginBottom = 16.px
                }

                div {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        opacity = currentOpacity
                    }

                    icon.music { css { marginRight = 8.px } }

                    ui.big.text {
                        css {
                            fontFamily = "monospace"
                            lineHeight = LineHeight("2.0em")
                            color = Color.white
                            display = Display.inlineBlock
                            fontWeight = FontWeight.bold
                        }
                        +"KLANG AUDIO MOTÖR"
                    }

                    icon.music { css { transform { scaleX(-1.0) }; marginLeft = 8.px } }
                }
            }

            div {
                key = "spectrum-visualizer"

                val spectHeight = 150
                css {
                    position = Position.absolute
                    pointerEvents = PointerEvents.none
                    // Anchor to bottom
                    bottom = 0.px
                    left = 0.px
                    right = 0.px
                    // Dimensions
                    height = spectHeight.px
                    width = 100.pct

                    opacity = 0.5
                }

                Spectrumeter { Player.get() }
            }
        }
    }
}
