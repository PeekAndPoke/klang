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

    val newSong = Static("/song")
    val editSong = Route1("/song/{id}")

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

        mount(Nav.newSong) { MakeSongPage(id = null) }
        mount(Nav.editSong) { MakeSongPage(id = it["id"]) }
    }


    layout({ FullscreenLayout { it() } }) {
        catchAll {
            NotFoundPage()
        }
    }
}
