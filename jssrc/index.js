const _ = require('lodash');

console.log("Hello from JS!");
console.log("Lodash test:", _.kebabCase("Hello World From Graal"));

module.exports = {
    test: () => "JS is ready"
};
