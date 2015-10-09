CLJS_COMPILER=java -cp cljs.jar:src clojure.main

all: dev

cljs.jar:
	wget https://github.com/clojure/clojurescript/releases/download/r1.7.28/cljs.jar

jpm-install:
	npm install jpm
	touch jpm-install

dev: cljs.jar
	$(CLJS_COMPILER) script/dev.clj

test: cljs.jar
	$(CLJS_COMPILER) script/test.clj

ci: cljs.jar
	$(CLJS_COMPILER) script/test_once.clj
	phantomjs test/runner.js

prod: cljs.jar
	$(CLJS_COMPILER) script/prod.clj

firefox: prod jpm-install
	cd dist/firefox && ../../node_modules/.bin/jpm xpi

chrome: prod
	cd dist/chrome && zip -r ../chrome.zip .

release: firefox chrome

selenium: firefox
	./selenium.clj
