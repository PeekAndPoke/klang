// Notstrom's page (KLANG-00465): index.html made from src/jsMain/resources/index_template.html with the bundle's script
// tag injected, so the production bundle can carry its content hash in its name (the engine takes that hash for the
// worklet's address too), and the release's fields baked in (window.__APP_VERSION__). Content-hashed async chunks as
// well. Only for the app's bundle: Karma's test bundle has no output section.
const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');
const fs = require('fs');

// The repository: the nearest folder upward with a settings.gradle.kts. Not a fixed number of '../': the webpack
// working folder sits one level deeper when user claude builds into .build-claude/ (MC-00031).
function repositoryRoot() {
    let dir = __dirname;
    while (!fs.existsSync(path.join(dir, 'settings.gradle.kts'))) {
        const parent = path.dirname(dir);
        if (parent === dir) {
            throw new Error('01-page.js: no settings.gradle.kts above ' + __dirname);
        }
        dir = parent;
    }
    return dir;
}

// The release's fields (project, version, gitRev, gitDesc, date), as this build's versionFile task wrote them into this
// project's build directory. No guess between users (MC-00031): this config runs inside this build's root, so where
// that lies under <repo>/.build-<name>/ the file is <repo>/.build-<name>/apps/notstrom/generated/version/version.json,
// else apps/notstrom/build/generated/version/version.json. Baked into the page, so it needs no request of its own.
function appVersion() {
    const root = repositoryRoot();
    const inside = path.relative(root, __dirname).split(path.sep)[0];
    const file = inside.startsWith('.build-')
        ? path.join(root, inside, 'apps/notstrom/generated/version/version.json')
        : path.join(root, 'apps/notstrom/build/generated/version/version.json');

    if (!fs.existsSync(file)) {
        throw new Error('01-page.js: no ' + file + ' (the versionFile task writes it before webpack runs)');
    }

    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

if (config.plugins && config.output) {
    config.output.chunkFilename = "[id].[contenthash].js";
    config.plugins.push(new HtmlWebpackPlugin({
        template: path.join(repositoryRoot(), 'apps/notstrom/src/jsMain/resources/index_template.html'),
        inject: 'body',
        filename: 'index.html',
        // beside the page: it is served at a domain's root
        publicPath: '',
        templateParameters: {appVersion: appVersion()},
    }));
}
