// The studio's page (KLANG-00465): index.html made from src/jsMain/resources/index_template.html with the bundle's
// script tag injected (the production bundle carries its content hash in its name) and the release's fields baked in
// (window.__APP_VERSION__). Only for the app's bundle: Karma's test bundle (no output section) has no page
// (KLANG-00343).
const HtmlWebpackPlugin = require('html-webpack-plugin');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');

// Per-build hash, used to cache-bust static assets (e.g. /css/klang.css) that
// are not part of the webpack asset graph.
const buildHash = crypto.randomBytes(6).toString('hex');

// The repository: the nearest folder upward with a settings.gradle.kts. Not a fixed number of '../': the webpack
// working folder sits one level deeper when user claude builds into .build-claude/ (MC-00031).
function repositoryRoot() {
    let dir = __dirname;
    while (!fs.existsSync(path.join(dir, 'settings.gradle.kts'))) {
        const parent = path.dirname(dir);
        if (parent === dir) {
            throw new Error('02-html-config.js: no settings.gradle.kts above ' + __dirname);
        }
        dir = parent;
    }
    return dir;
}

// The release's fields (project, version, gitBranch, gitRev, gitDesc, date), as this build's versionFile task wrote
// them into this project's build directory. No guess between users (MC-00031): this config runs inside this build's
// root, so where that lies under <repo>/.build-<name>/ the file is
// <repo>/.build-<name>/apps/studio/generated/version/version.json, else apps/studio/build/generated/version/version.json.
// Baked into index.html, so it needs no request of its own. An empty object if the file is not there (the frontend
// then fetches /version.json at runtime instead).
function appVersion() {
    const root = repositoryRoot();
    const inside = path.relative(root, __dirname).split(path.sep)[0];
    const file = inside.startsWith('.build-')
        ? path.join(root, inside, 'apps/studio/generated/version/version.json')
        : path.join(root, 'apps/studio/build/generated/version/version.json');

    try {
        return JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (e) {
        // said aloud: without it the figures are asked for as `?v=dev`, an address a browser may keep for a year
        console.warn('version.json not found at ' + file + ': the page is built without its release fields');

        return {};
    }
}

if (config.plugins && config.output) {
    config.plugins.push(new HtmlWebpackPlugin({
        template: path.join(repositoryRoot(), 'apps/studio/src/jsMain/resources/index_template.html'),
        // Automatically inject the <script> tags at the bottom of the body
        inject: 'body',
        filename: 'index.html',
        // Force the injected script tag to have an absolute path starting with '/'
        publicPath: '/',
        templateParameters: {buildHash, appVersion: appVersion()}
    }));
}
