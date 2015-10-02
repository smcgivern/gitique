#!/usr/bin/env boot

(set-env! :dependencies '[[clj-webdriver "0.7.2"]
                          [org.seleniumhq.selenium/selenium-java "2.47.1"]])

(use 'clojure.test 'clj-webdriver.taxi)
(require '[clj-webdriver.driver :as driver]
         '[clojure.java.io :as io])

(deftest load-pr
  (testing "Pull request"
    (to "https://github.com/smcgivern/gitique-examples/pull/1/files")
    (wait-until #(exists? ".gitique-header-wrapper"))
    (testing "Shows Gitique buttons"
      (is (visible? ".gitique-header-wrapper")))))

(import '(org.openqa.selenium.firefox FirefoxDriver FirefoxProfile)
        '(org.openqa.selenium.chrome ChromeDriver ChromeOptions))

(defn firefox []
  (let [dir "dist/firefox"
        last-file (->> (.list (io/file dir)) (filter #(re-matches #"^.*\.xpi$" %)) sort last)
        extension-file (io/as-file (str dir "/" last-file))
        profile (doto (FirefoxProfile.) (.addExtension extension-file))]
    (set-driver! (driver/init-driver (FirefoxDriver. profile)))))

(defn chrome []
  (let [options (doto (ChromeOptions.) (.addArguments ["load-extension=dist/chrome/"]))]
    (when-let [chrome-path (System/getenv "CHROME_PATH")]
      (.setBinary options chrome-path))
    (set-driver! (driver/init-driver (ChromeDriver. options)))))

(defn test-results []
  (map
   (fn [[name browser-setup]]
     (println (str "\n" name " tests:"))
     (browser-setup)
     (let [results (run-tests)]
       (quit)
       results))
   {"Chrome" chrome "Firefox" firefox}))

(defn success? [results]
  (every? (fn [result] (= 0 (:fail result) (:error result))) results))

(defn -main []
  (System/exit (if (success? (test-results)) 0 1)))
