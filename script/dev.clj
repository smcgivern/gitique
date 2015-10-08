(require 'cljs.closure)

(cljs.closure/watch "src" {:optimizations :whitespace
                           :output-to "gitique.js"
                           :output-dir "out"
                           :source-map "gitique.js.map"})
