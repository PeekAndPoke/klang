package io.peekandpoke.klang

import de.peekandpoke.kraft.routing.RootRouterBuilder
import de.peekandpoke.kraft.routing.Route1
import de.peekandpoke.kraft.routing.Static
import io.peekandpoke.klang.layouts.FullscreenLayout
import io.peekandpoke.klang.layouts.MenuLayout
import io.peekandpoke.klang.pages.DashboardPage
import io.peekandpoke.klang.pages.MakeSongPage
import io.peekandpoke.klang.pages.NotFoundPage
import io.peekandpoke.klang.pages.TourPage

object Nav {
    val dashboard = Static("")
    val dashboardSlash = Static("/")

    val newSong = Static("/song")
    val editSong = Route1("/song/{id}")

    val tour = Static("/tour")
}

fun RootRouterBuilder.mountNav() {

    layout({ MenuLayout { it() } }) {
        mount(Nav.dashboard) { DashboardPage() }
        mount(Nav.dashboardSlash) { DashboardPage() }

        mount(Nav.newSong) { MakeSongPage(id = null) }
        mount(Nav.editSong) { MakeSongPage(id = it["id"]) }
    }

    mount(Nav.tour) { TourPage() }

    catchAll {
        layout({ FullscreenLayout { it() } }) {
            NotFoundPage()
        }
    }
}
