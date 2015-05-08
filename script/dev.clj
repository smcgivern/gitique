(require 'cljs.closure)

(cljs.closure/watch "src" {:optimizations :whitespace :output-to "gitique.js"})
