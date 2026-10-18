// Webpack 5.108 (Kotlin 2.4) drops pixi.js's side-effect import `import './init.mjs'` in lib/app/Application.mjs,
// although pixi's package.json lists `./lib/app/init.*` under sideEffects (webpack 5.101 kept it). That import
// registers the Application plugins (ticker, resize), so without it `app.ticker` is undefined after `init()` and
// the code editor's playback highlight fails ("Cannot read properties of undefined (reading 'add')").
// Treat every pixi.js module as side-effectful: nothing of it is dropped for being "unused".
config.module = config.module || {};
config.module.rules = config.module.rules || [];
config.module.rules.push({
    test: /[\\/]node_modules[\\/]pixi\.js[\\/]/,
    sideEffects: true,
});
