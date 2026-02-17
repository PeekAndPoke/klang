// Fix for AudioWorklet: Disable automatic publicPath detection
// AudioWorklets run in a separate thread where scriptUrl detection fails
config.output.publicPath = '';