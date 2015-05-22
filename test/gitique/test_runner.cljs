(ns gitique.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [gitique.core-test :as core-test]
            [gitique.pure-test :as pure-test]))

(run-tests 'gitique.core-test 'gitique.pure-test)
