// The demo's dev server: a port of its own, beside the band prototype's (18750), and no browser opened by itself.
if (config.devServer) {
    config.devServer.port = 18751
    config.devServer.open = false
    config.devServer.hot = false
    config.devServer.client = { logging: 'none' }
}
