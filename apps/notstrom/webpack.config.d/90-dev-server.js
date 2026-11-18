// Notstrom's dev server: a port of its own, beside the viz demo's (18751), and no browser opened by itself.
if (config.devServer) {
    config.devServer.port = 18752
    config.devServer.open = false
    config.devServer.hot = false
    config.devServer.client = { logging: 'none' }
}
