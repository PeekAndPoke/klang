const HtmlWebpackPlugin = require('html-webpack-plugin');
const path = require('path');

if (config.plugins) {
    config.plugins.push(new HtmlWebpackPlugin({
        // Point directly to your source index.html as the template
        template: path.resolve(__dirname, '../../../../src/jsMain/resources/index_template.html'),
        // Automatically inject the <script> tags at the bottom of the body
        inject: 'body',
        filename: 'index.html',
        // Force the injected script tag to have an absolute path starting with '/'
        publicPath: '/'
    }));
}
