// Ktor 3.6 loads Node's `net` and `dgram` through `import(<expression>)` in ktor-network, behind a
// `process.release.name === 'node'` check, so the browser never takes that path. Webpack still warns
// ("Critical dependency: the request of a dependency is an expression") and the dev server shows it as
// "Compiled with problems". Ignore exactly that warning from exactly that module; every other one still shows.
// (As the studio's file of the same name; the module is a `.js` file here and a `.mjs` file there.)
config.ignoreWarnings = config.ignoreWarnings || [];
config.ignoreWarnings.push({
    module: /ktor-ktor-network\.m?js$/,
    message: /Critical dependency: the request of a dependency is an expression/,
});
