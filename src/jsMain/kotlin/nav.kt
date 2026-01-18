package io.peekandpoke.klang

import de.peekandpoke.kraft.routing.RootRouterBuilder
import de.peekandpoke.kraft.routing.Static
import io.peekandpoke.klang.pages.DashboardPage
import io.peekandpoke.klang.pages.NotFoundPage

object Nav {
    val dashboard = Static("")
    val dashboardSlash = Static("/")
}

fun RootRouterBuilder.mountNav() {
    mount(Nav.dashboard) { DashboardPage() }
    mount(Nav.dashboardSlash) { DashboardPage() }

    catchAll {
        NotFoundPage()
    }
}
