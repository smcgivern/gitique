(ns gitique.core
  (:require [clojure.string :as string]
            [goog.net.XhrIo :as xhr]
            [goog.dom :as dom]))

(enable-console-print!)

(extend-type js/NodeList ISeqable (-seq [array] (array-seq array 0)))
(extend-type js/DOMTokenList ISeqable (-seq [array] (array-seq array 0)))

(def token-key "gitique.github_access_token")
(def state (atom {:selected-commits '()
                  :current-pr nil}))

(declare add-icons! get-new-commits! update-icons!)
(declare log maybe-show-new)

(add-watch state
           :pr-change
           (fn [_ _ old new]
             (let [new-pr (:current-pr new)]
               (when (not= (:current-pr old) new-pr)
                 (add-icons!)
                 (maybe-show-new (:repo new-pr) (:pr new-pr))))))

(add-watch state
           :commits-change
           (fn [_ _ old new]
             (let [repo (get-in new [:current-pr :repo])
                   new-commits (:selected-commits new)]
               (when (not= (:selected-commits old) new-commits)
                 (update-icons! new-commits)
                 (get-new-commits! repo new-commits)))))


(defn- log [s] (println s) s)
(defn- is? [type] (fn [item] (= (:type item) type)))
(defn- child-text [parent selector] (.-textContent (.querySelector parent selector)))

(defn- set-text! [selector text]
  (set! (.-textContent (js/document.querySelector selector)) text))

(defn- commit-sha [element]
  (last (string/split (.getAttribute (.querySelector element ".commit-id") "href") "/")))

(defn- commit-shas [item]
  (if-let [element (:element item)]
    (map commit-sha (.querySelectorAll (:element item) ".commit"))
    []))

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
        [reviewed-commits new-commits] (split-with #(< (:index %) (:index last-reviewer-comment)) commits)]
    {:last-reviewed-commit (-> reviewed-commits last commit-shas last)
     :new-commits (mapcat commit-shas new-commits)}))

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
        group (dom/createDom "div" #js["btn-group" "right" "gitique-header-wrapper"] all new)]
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

(defn- update-token! [url event]
  (let [element (.-target event)
        new-token (.-value (.querySelector element "input"))]
    (js/localStorage.setItem token-key new-token)
    (get-new-commits! url)
    (.removeChild (.-parentElement element) element)
    (.preventDefault event)
    (.stopPropagation event)))

(defn- request-token! [url]
  (let [parent (js/document.querySelector "#toc")
        sibling (js/document.querySelector "#toc .toc-diff-stats")
        current-token (js/localStorage.getItem token-key)
        input (dom/createDom "input" #js{:type "text"
                                         :length 50
                                         :value current-token
                                         :placeholder "Access token"
                                         :class (when (> (count current-token) 1) "error")})
        needs-repo? (js/document.querySelector ".repo-private-label")
        token-link (dom/createDom
                    "a"
                    #js{:href "https://help.github.com/articles/creating-an-access-token-for-command-line-use/"}
                    "access token")
        explanation (dom/createDom
                     "span"
                     nil
                     "Please enter an " token-link (when needs-repo? " with repo scope") ": ")
        wrapper (dom/createDom
                 "form"
                 #js{:class "right gitique-header-wrapper"}
                 explanation input)]
    (.addEventListener input "input" #(.remove (.-classList (.-target %)) "error"))
    (.addEventListener wrapper "submit" (partial update-token! url))
    (.insertBefore parent wrapper sibling)))

(defn- xhr-handler [event]
  (if-let [error (not= 200 (.getStatus (.-target event)))]
    (request-token! (.getLastUri (.-target event)))
    (let [body (js->clj (.getResponseJson (.-target event)) :keywordize-keys true)]
      (annotate-files! (:files body))
      (add-button!)
      (update-overall!))))

(defn- get-new-commits!
  ([url]
   (let [auth-token (js/localStorage.getItem token-key)
         headers (if auth-token {"Authorization" (str "token " auth-token)} {})]
     (xhr/send url xhr-handler "GET" nil headers)))
  ([repo [from & to]]
   (when (and from to)
     (get-new-commits! (str "https://api.github.com/repos/" repo "/compare/" from "..." (last to))))))

(defn- add-icon! [element]
  (let [parent (.-parentElement (.-parentElement element))]
    (when-not (.querySelector parent ".gitique-icon")
      (.appendChild parent (dom/createDom "span" #js["octicon octicon-diff-added gitique-icon"])))))

(defn- add-icons! []
  (doseq [element (js/document.querySelectorAll ".commit-id")] (add-icon! element)))

(defn- find-commit [commit-id]
  (.-parentElement (.-parentElement (js/document.querySelector (str ".commit-id[href$='" commit-id "']")))))

(defn- update-icon! [commit-id new-class new-title]
  (let [element (if (string? commit-id)
                  (.querySelector (find-commit commit-id) ".gitique-icon")
                  commit-id)
        element-classes (.-classList element)]
    (doseq [class ["gitique-disabled" "gitique-enabled" "gitique-first"]]
      (.remove element-classes class))
    (.add element-classes new-class)
    (.setAttribute element "title" new-title)))

(defn- update-icons! [[from & new]]
  (when from
    (when new
      (update-icon! from "gitique-first" "Last reviewed commit"))
    (doseq [new-commit new]
      (update-icon! new-commit "gitique-enabled" "New commit")))
  (doseq [disabled-commit (js/document.querySelectorAll ".gitique-icon:not(.gitique-enabled):not(.gitique-first)")]
    (update-icon! disabled-commit "gitique-disabled" "Reviewed commit")))

(defn- maybe-show-new [repo pr]
  (swap! state assoc :selected-commits
         (when repo
           (let [{:keys [last-reviewed-commit new-commits]} (commit-info)]
             (cons last-reviewed-commit new-commits)))))

(defn- main []
  (let [components (string/split js/window.location.pathname "/")
        repo (str (get components 1) "/" (get components 2))
        pr (get components 4)
        current-pr (when (= (get components 3) "pull") {:repo repo :pr pr})]
    (swap! state assoc :current-pr current-pr)))

(defn ^:export watch []
  (let [target (js/document.querySelector "#js-repo-pjax-container")
        is-valid-mutation? #(and (= (.-type %) "childList") (not-empty (.-addedNodes %)))
        observer (js/MutationObserver. #(when (some is-valid-mutation? %) (main)))]
    (.observe observer target #js{:childList true :attributes false :characterData false})
    (main)))
