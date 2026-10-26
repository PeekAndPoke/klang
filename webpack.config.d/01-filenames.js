// Content-hash async chunks so CDNs serve the correct version after deploys. Only for the app's bundle: Karma's test
// bundle has no output section (KLANG-00343)
if (config.output) {
    config.output.chunkFilename = "[id].[contenthash].js"
}
