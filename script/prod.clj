(require 'cljs.closure)

(cljs.closure/build "src" {:optimizations :advanced :output-to "dist/gitique.js"})
