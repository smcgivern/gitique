(require 'cljs.build.api 'clojure.java.shell)

(cljs.build.api/watch
 (cljs.build.api/inputs "src" "test")
 {:optimizations :whitespace
  :output-to "out/gitique-test.js"
  :watch-fn #(do (print (:out (clojure.java.shell/sh "phantomjs" "test/runner.js")))
                 (flush))})
