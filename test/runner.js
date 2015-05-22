var page = require('webpage').create();

page.onConsoleMessage = console.log.bind(console);

page.injectJs('out/gitique-test.js');

phantom.exit();
