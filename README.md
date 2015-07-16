# Gitique

[![Build status](https://travis-ci.org/smcgivern/gitique.svg?branch=master)][travis]

A Chrome extension to make GitHub code review better. Get it in the
[Chrome Web Store][webstore].

## Usage

Once installed, Gitique runs on every GitHub page. When it detects that you're on a pull
request, it looks at the discussion. Any commits made after the last comment by someone
who is not the author will have a green icon next to them.

### Icon key

* `#bdbdbd` - very light grey - a commit that is not the HEAD, when there are no review
  comments.
* `#999` - light grey - a reviewed commit. If there are comments by reviewers (people not
  the author of the pull request), all but the last commit before the last review comment
  will have this colour. Diffs from these commits are not shown when the Gitique view is
  enabled.
* `#000` - black - the base commit. This takes the place of the HEAD of the target
  branch when the Gitique view is enabled, so its diff is not shown. By default, it is the
  last commit before the last review comment.
* `#6cc644` - light green - a reviewed commit. All but the last commit after the last
  reviewer comment are shown in this colour by default, and their diffs are maintained
  when the Gitique view is enabled.
* `#48a220` - dark green - the HEAD of the selected branch in this PR. This diff will
  always be shown when the Gitique view is enabled, and this is the only commit which
  cannot be selected as the base commit.

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

[travis]: https://travis-ci.org/smcgivern/gitique
[webstore]: https://chrome.google.com/webstore/detail/gitique/mmjofndmajimmdkeejmmlfljclmghomk
