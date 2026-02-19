package io.peekandpoke.klang

import de.peekandpoke.kraft.routing.RootRouterBuilder
import de.peekandpoke.kraft.routing.Route1
import de.peekandpoke.kraft.routing.Static
import io.peekandpoke.klang.layouts.FullscreenLayout
import io.peekandpoke.klang.layouts.MenuLayout
import io.peekandpoke.klang.pages.*

object Nav {
    val start = Static("")
    val startSlash = Static("/")

    val dashboard = Static("/dashboard")

    val newSongCode = Static("/song/code")
    val editSongCode = Route1("/song/code/{id}")

    val samplesLibrary = Static("/samples/library")

    val strudelDocs = Static("/docs/strudel")

    val tour = Static("/tour")
}

fun RootRouterBuilder.mountNav() {

    layout({ FullscreenLayout { it() } }) {
        mount(Nav.start) { StartPage() }
        mount(Nav.startSlash) { StartPage() }

        mount(Nav.tour) { TourPage() }
    }

    layout({ MenuLayout { it() } }) {

        mount(Nav.dashboard) { DashboardPage() }

        mount(Nav.newSongCode) { CodeSongPage(id = null) }
        mount(Nav.editSongCode) { CodeSongPage(id = it["id"]) }

        mount(Nav.samplesLibrary) { SamplesLibraryPage() }

        mount(Nav.strudelDocs) { StrudelDocsPage() }
    }


    layout({ FullscreenLayout { it() } }) {
        catchAll {
            NotFoundPage()
        }
    }
}
