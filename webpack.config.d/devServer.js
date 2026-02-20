let devServer = config.devServer

if (devServer) {
    config.mode = "development"
    // config.devtool = false
    config.devtool = "source-map"

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

console.log(config)
