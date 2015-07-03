(ns gitique.core
  (:require [clojure.string :as string]
            [goog.dom :as dom]
            [gitique.api :as api]
            [gitique.pure :as pure]
            [gitique.util :as util]))

(enable-console-print!)

(def state (atom {:selected-commits '()
                  :current-pr nil}))

(def pjax-wrapper (util/qs "#js-repo-pjax-container"))

(declare add-icons! maybe-show-new update-icons! update-dom!)

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
                 (api/get-new-commits! repo new-commits update-dom!)))))

(defn- is? [type] (fn [item] (= (:type item) type)))

(defn- set-text!
  "Set the element matching the selector's `textContent` property"
   [selector text]
  (set! (.-textContent (util/qs selector)) text))

(defn- commit-sha
  "Given an element with a link to a commit, with the class `commit-id`, get the commit SHA"
  [element]
  (last (string/split (.getAttribute (util/qs ".commit-id" element) "href") "/")))

(defn- commit-shas
  "Seq of the commit SHAs contained within the item's element"
  [item]
  (if-let [element (:element item)]
    (map commit-sha (util/qsa ".commit" (:element item)))
    []))

(defn- item-type
  "Categorise an item based on its class and the creator of the PR"
  [creator item]
  (let [classes (.-classList item)]
    (cond (.contains classes "discussion-commits") "commit-block"
          (.contains classes "discussion-item-assigned") "assigned"
          (.contains classes "discussion-item-labeled") "labeled"
          :else (if (= (util/child-text item ".author") creator) "owner-comment" "reviewer-comment"))))

(defn- annotated-element [creator]
  (fn [index element]
    {:type (item-type creator element) :element element :index index}))

(defn- commit-info
  "On a pull request, get all of the discussion items and their types, returning a map of
  the last reviewed commit and the commits since"
  []
  (let [elements (util/qsa ".js-discussion .timeline-comment-wrapper, .js-discussion .discussion-item")
        creator (util/child-text (.item elements 0) ".author")
        items (map-indexed (annotated-element creator) elements)
        last-reviewer-comment (last (filter (is? "reviewer-comment") items))
        commits (filter (is? "commit-block") items)
        [reviewed-commits new-commits] (split-with #(< (:index %) (:index last-reviewer-comment)) commits)]
    {:last-reviewed-commit (-> reviewed-commits last commit-shas last)
     :new-commits (mapcat commit-shas new-commits)}))

(defn- diffstat-count
  "Sum the lines in `direction` (added or removed) based on the visible files"
  [gitique-enabled direction]
  (let [selector (str "#diff .file" (when gitique-enabled ":not(.gitique-hidden)") " .blob-num-" direction)
        elements (util/qsa selector)]
    (count elements)))

(defn- update-overall!
  "Update the diff stats and file counts above the file list, given the files shown"
  []
  (let [gitique-enabled (.contains (.-classList pjax-wrapper) "gitique-enabled")
        selector (str "#files .file" (when gitique-enabled ":not(.gitique-hidden)"))
        file-count (count (util/qsa selector))
        added (diffstat-count gitique-enabled "addition")
        deleted (diffstat-count gitique-enabled "deletion")]
    (set-text! "#files_tab_counter" file-count)
    (set-text! "#diffstat>.text-diff-added" (str "+" added))
    (set-text! "#diffstat>.text-diff-deleted" (str "−" deleted))
    (set-text! ".toc-diff-stats button" (str file-count " changed files"))
    (set-text! ".toc-diff-stats strong:first-of-type" (str added " additions"))
    (set-text! ".toc-diff-stats strong:last-of-type" (str deleted " deletions"))))

(defn- set-state! [state event]
  (let [enabled? (= state "new")
        other-state (if enabled? "all" "new")
        to-enable (util/qs (str "#gitique-show-" state))
        to-disable (util/qs (str "#gitique-show-" other-state))]
    (.add (.-classList to-enable) "selected")
    (.remove (.-classList to-disable) "selected")
    (if (= state "new")
      (.add (.-classList pjax-wrapper) "gitique-enabled")
      (.remove (.-classList pjax-wrapper) "gitique-enabled"))
    (update-overall!)))

(defn- add-button! []
  (when-let [existing-buttons (util/qs "#toc .gitique-header-wrapper")]
    (.remove existing-buttons))
  (let [parent (util/qs "#toc")
        sibling (util/qs "#toc .toc-diff-stats")
        all (dom/createDom "a" #js{:className "btn btn-sm selected" :id "gitique-show-all"}
                           "All files")
        new (dom/createDom "a" #js{:className "btn btn-sm" :id "gitique-show-new"}
                           "Since last CR comment")
        group (dom/createDom "div" #js["btn-group" "right" "gitique-header-wrapper"] all new)]
    (.addEventListener all "click" (partial set-state! "all") true)
    (.addEventListener new "click" (partial set-state! "new") true)
    (.insertBefore parent group sibling)))

(defn- annotate-lines! [element file]
  (let [new-lines-list (flatten (:new (-> file :patch pure/parse-diff)))
        new-lines (zipmap (map :index new-lines-list) new-lines-list)]
    (doseq [line (util/qsa ".diff-table tr" element)]
      (let [line-number-element (util/qs "[data-line-number]" line)
            line-number (if line-number-element (.getAttribute line-number-element "data-line-number") "0")]
        (if-let [new-line (new-lines (js/parseInt line-number 10))]
          (when (= :context (:type new-line)) (.add (.-classList line) "gitique-context"))
          (.add (.-classList line) "gitique-hidden"))))))

(defn- annotate-files! [files]
  (let [include-filenames (zipmap (map :filename files) files)]
    (doseq [element (util/qsa "#toc ol>li, #files .file")]
      (let [toc-link (util/qs "li>a" element)
            file-contents (util/qs "[data-path]" element)
            filename (if toc-link (.-textContent toc-link) (.getAttribute file-contents "data-path"))]
        (if-let [file (include-filenames filename)]
          (annotate-lines! element file)
          (.add (.-classList element) "gitique-hidden"))))))

(defn- add-icon! [element]
  (let [parent (.-parentElement (.-parentElement element))]
    (when-not (util/qs ".gitique-icon" parent)
      (.appendChild parent (dom/createDom "span" #js["octicon octicon-diff-added gitique-icon"])))))

(defn- add-icons! []
  (doseq [element (util/qsa ".commit-id")] (add-icon! element)))

(defn- find-commit
  "Find the link to a commit on the page by its SHA"
  [commit-id]
  (.-parentElement (.-parentElement (util/qs (str ".commit-id[href$='" commit-id "']")))))

(defn- update-icon! [commit-id new-class new-title]
  (let [element (if (string? commit-id)
                  (util/qs ".gitique-icon" (find-commit commit-id))
                  commit-id)
        element-classes (.-classList element)]
    (doseq [class ["gitique-disabled" "gitique-enabled" "gitique-first"]]
      (.remove element-classes class))
    (.add element-classes new-class)
    (.setAttribute element "title" new-title)))

(defn- update-icons! [[from & new]]
  (when (and from new)
    (update-icon! from "gitique-first" "Last reviewed commit")
    (doseq [new-commit new]
      (update-icon! new-commit "gitique-enabled" "New commit")))
  (doseq [disabled-commit (util/qsa ".gitique-icon:not(.gitique-enabled):not(.gitique-first)")]
    (update-icon! disabled-commit "gitique-disabled" "Reviewed commit")))

(defn- update-dom! [body]
  (annotate-files! (:files body))
  (add-button!)
  (update-overall!))

(defn- maybe-show-new [repo]
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

(defn ^:export watch
  "Run `main` once, then watch for DOM mutations in the PJAX container and run `main` when
  it changes"
  []
  (when pjax-wrapper
    (let [is-valid-mutation? #(and (= (.-type %) "childList") (not-empty (.-addedNodes %)))
          observer (js/MutationObserver. #(when (some is-valid-mutation? %) (main)))]
      (.observe observer pjax-wrapper #js{:childList true :attributes false :characterData false})))
  (main))
