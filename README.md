# Gitique

[![Build Status](https://travis-ci.org/smcgivern/gitique.svg?branch=master)](https://travis-ci.org/smcgivern/gitique)

A Chrome extension to make GitHub code review better. Get it in the
[Chrome Web Store][webstore].

## Developing

1. Clone this repo.
2. From the root of the cloned repo, run `./build`. This will download the ClojureScript
   compiler and run it on the source, producing the file `gitique.js`. This script will
   watch for changes in the source.
3. Go to [chrome://extensions/](chrome://extensions/) and choose to load an unpacked
   extension, then point to the cloned repo.
4. To run the tests, run `./build test`. This requires [PhantomJS](http://phantomjs.org/)
   version 2 to be on the path as `phantomjs`.

### Production builds

1. Run `./build prod`. This will use advanced optimizations and no watch the source files.
2. Update [`dist/manifest.json`](dist/manifest.json) for the new version.

[webstore]: https://chrome.google.com/webstore/detail/gitique/mmjofndmajimmdkeejmmlfljclmghomk
