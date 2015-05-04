(ns gitique.core
  (:require [clojure.string :as string]
            [goog.net.XhrIo :as xhr]
            [goog.dom :as dom]))

(enable-console-print!)

(extend-type js/NodeList ISeqable (-seq [array] (array-seq array 0)))
(extend-type js/DOMTokenList ISeqable (-seq [array] (array-seq array 0)))

(defn- log [s] (println s) s)
(defn- is? [type] (fn [item] (= (:type item) type)))
(defn- child-text [parent selector] (.-textContent (.querySelector parent selector)))

(defn- set-text! [selector text]
  (set! (.-textContent (js/document.querySelector selector)) text))

(defn- commit-sha [element]
  (last (string/split (.getAttribute (.querySelector element ".commit-id") "href") "/")))

(defn- commit-shas [item]
  (map commit-sha (.querySelectorAll (:element item) ".commit")))

(defn- item-type [creator item]
  (let [classes (.-classList item)]
    (cond (.contains classes "discussion-commits") "commit-block"
          (.contains classes "discussion-item-assigned") "assigned"
          (.contains classes "discussion-item-labeled") "labeled"
          (.contains classes "outdated-diff-comment-container") "outdated-diff-comment"
          :else (if (= (child-text item ".author") creator) "owner-comment" "reviewer-comment"))))

(defn- annotated-element [creator]
  (fn [index element]
    {:type (item-type creator element) :element element :index index}))

(defn- commit-info []
  (let [elements (js/document.querySelectorAll ".js-discussion .timeline-comment-wrapper, .js-discussion .discussion-item")
        creator (child-text (.item elements 0) ".author")
        items (map-indexed (annotated-element creator) elements)
        last-reviewer-comment (last (filter (is? "reviewer-comment") items))
        commits (filter (is? "commit-block") items)
        partitioned-commits (split-with #(< (:index %) (:index last-reviewer-comment)) commits)]
    {:last-reviewed-commit (-> partitioned-commits first last)
     :new-commits (last partitioned-commits)}))

(defn- diffstat-count [gitique-enabled direction]
  (let [selector (str "#toc ol>li" (when gitique-enabled ":not(.gitique-hidden)") " .diffstat>.text-diff-" direction)
        elements (js/document.querySelectorAll selector)
        contents (map #(string/replace (.-textContent %) "−" "-") elements)
        numbers (map #(js/parseInt % 10) contents)]
    (reduce + numbers)))

(defn- update-overall! []
  (let [gitique-enabled (js/document.querySelector ".gitique-enabled")
        selector (str "#files .file" (when gitique-enabled ":not(.gitique-hidden)"))
        file-count (count (js/document.querySelectorAll selector))
        added (diffstat-count gitique-enabled "added")
        deleted (- (diffstat-count gitique-enabled "deleted"))]
    (set-text! "#files_tab_counter" file-count)
    (set-text! "#diffstat>.text-diff-added" (str "+" added))
    (set-text! "#diffstat>.text-diff-deleted" (str "−" deleted))
    (set-text! ".toc-diff-stats button" (str file-count " changed files"))
    (set-text! ".toc-diff-stats strong:first-of-type" (str added " additions"))
    (set-text! ".toc-diff-stats strong:last-of-type" (str deleted " deletions"))))

(defn- set-state! [state event]
  (let [enabled? (= state "new")
        other-state (if enabled? "all" "new")
        to-enable (dom/getElement (str "gitique-show-" state))
        to-disable (dom/getElement (str "gitique-show-" other-state))
        wrapper (dom/getElement "js-repo-pjax-container")]
    (.add (.-classList to-enable) "selected")
    (.remove (.-classList to-disable) "selected")
    (if (= state "new")
      (.add (.-classList wrapper) "gitique-enabled")
      (.remove (.-classList wrapper) "gitique-enabled"))
    (update-overall!)))

(defn- add-button! []
  (let [parent (js/document.querySelector "#toc")
        sibling (js/document.querySelector "#toc .toc-diff-stats")
        all (dom/createDom "a" #js{:className "btn btn-sm selected" :id "gitique-show-all"}
                           "All files")
        new (dom/createDom "a" #js{:className "btn btn-sm" :id "gitique-show-new"}
                           "Since last CR comment")
        group (dom/createDom "div" (clj->js ["btn-group" "right" "gitique-buttons"]) all new)]
    (.addEventListener all "click" (partial set-state! "all") true)
    (.addEventListener new "click" (partial set-state! "new") true)
    (.insertBefore parent group sibling)))

(defn- annotate-files! [files]
  (let [include-filenames (set (map :filename files))]
    (doseq [element (js/document.querySelectorAll "#toc ol>li, #files .file")]
      (let [toc-link (.querySelector element "li>a")
            file-contents (.querySelector element "[data-path]")
            filename (if toc-link (.-textContent toc-link) (.getAttribute file-contents "data-path"))]
        (if-not (include-filenames filename)
          (.add (.-classList element) "gitique-hidden"))))))

(defn- xhr-handler [event]
  (let [body (js->clj (.getResponseJson (.-target event)) :keywordize-keys true)]
    (annotate-files! (:files body))
    (add-button!)
    (update-overall!)))

(defn- get-new-commits! [repo from to]
  (let [url (str "https://api.github.com/repos/" repo "/compare/" from "..." to)]
    (xhr/send url xhr-handler "GET" nil headers)))

(defn- add-icon! [element]
  (let [parent (.-parentElement (.-parentElement element))
        plusIcon (js/document.createElement "span")]
    (.setAttribute plusIcon "class" "octicon octicon-diff-added")
    (.insertBefore parent plusIcon (.-firstChild parent))))

(defn- add-icons! [commit-block]
  (dorun (map add-icon! (.querySelectorAll (:element commit-block) ".commit-id"))))

(defn- maybe-show-new [repo]
  (let [{:keys [last-reviewed-commit new-commits]} (commit-info)]
    (if last-reviewed-commit
      (do
        (get-new-commits! repo (-> last-reviewed-commit commit-shas last) (-> new-commits last commit-shas last))
        (dorun (map add-icons! new-commits)))
      (log "No unreviewed commits"))))

(defn ^:export main []
  (let [components (string/split (aget js/window "location" "pathname") "/")
        repo (str (get components 1) "/" (get components 2))]
    (if (= (get components 3) "pull")
      (maybe-show-new repo)
      (log (str "Not a pull request: " components)))))

(main)
