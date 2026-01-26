package io.peekandpoke.klang

import de.peekandpoke.kraft.routing.RootRouterBuilder
import de.peekandpoke.kraft.routing.Static
import io.peekandpoke.klang.pages.DashboardPage
import io.peekandpoke.klang.pages.NotFoundPage
import io.peekandpoke.klang.pages.TourPage

object Nav {
    val dashboard = Static("")
    val dashboardSlash = Static("/")

    val tour = Static("/tour")
}

fun RootRouterBuilder.mountNav() {
    mount(Nav.dashboard) { DashboardPage() }
    mount(Nav.dashboardSlash) { DashboardPage() }

    mount(Nav.tour) { TourPage() }

    catchAll {
        NotFoundPage()
    }
}
