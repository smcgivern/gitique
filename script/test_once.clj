(require 'cljs.build.api 'clojure.java.shell)

(cljs.build.api/build (cljs.build.api/inputs "src" "test") {:optimizations :whitespace :output-to "out/gitique-test.js"})
