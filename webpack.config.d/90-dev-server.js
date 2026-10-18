let devServer = config.devServer

if (devServer) {
    config.mode = "development"
    // Since Kotlin 2.4 the generated config writes the source maps itself (a SourceMapDevToolPlugin, devtool false);
    // a devtool on top writes every map twice. Set one only when Kotlin did not.
    if (!(config.plugins || []).some(p => p && p.constructor && p.constructor.name === "SourceMapDevToolPlugin")) {
        config.devtool = "source-map"
    }

    // devServer.host = "127.0.0.1"
    devServer.port = 55264
    devServer.open = true
    devServer.hot = false

    // History fallback so that reloads work properly
    devServer.historyApiFallback = true

    // Disable logging options
    devServer.client = {
        logging: 'none'  // Disables client-side logging
    }
    devServer.devMiddleware = {
        stats: 'none'    // Disables webpack build stats
    }
}
