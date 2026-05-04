const HtmlWebpackPlugin = require('html-webpack-plugin');
const crypto = require('crypto');
const path = require('path');

// Per-build hash, used to cache-bust static assets (e.g. /css/klang.css) that
// are not part of the webpack asset graph.
const buildHash = crypto.randomBytes(6).toString('hex');

if (config.plugins) {
    config.plugins.push(new HtmlWebpackPlugin({
        // Point directly to your source index.html as the template
        template: path.resolve(__dirname, '../../../../src/jsMain/resources/index_template.html'),
        // Automatically inject the <script> tags at the bottom of the body
        inject: 'body',
        filename: 'index.html',
        // Force the injected script tag to have an absolute path starting with '/'
        publicPath: '/',
        templateParameters: {buildHash}
    }));
}
