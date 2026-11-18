// KLANG-00407: the Logger's files (every `logging/` directory of a Kotlin source set) on the source maps' ignore list,
// beside what Kotlin lists there itself. The browser's developer tools then show a logged message at the line that
// logged it, the first frame outside the list, not at the Logger's own call to `console`.
//
// Kotlin's generated config adds the SourceMapDevToolPlugin itself. Its options are read in the constructor (a cache
// salt among them), so the plugin is built again with the list added, not changed in place.
const LOGGING_FILES = /\/src\/\w+\/kotlin\/logging\//;

config.plugins = (config.plugins || []).map(plugin => {
    if (!(plugin && plugin.constructor && plugin.constructor.name === "SourceMapDevToolPlugin")) {
        return plugin;
    }

    const options = plugin.options || {};
    const ignoreList = options.ignoreList ? [].concat(options.ignoreList, LOGGING_FILES) : LOGGING_FILES;

    return new plugin.constructor(Object.assign({}, options, {ignoreList: ignoreList}));
});
