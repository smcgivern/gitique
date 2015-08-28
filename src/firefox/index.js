var pageMod = require('sdk/page-mod');

pageMod.PageMod({
  include: 'https://github.com/*',
  contentScriptFile: './gitique.js',
  contentStyleFile: './gitique.css',
  contentScript: 'gitique.core.watch();'
});
