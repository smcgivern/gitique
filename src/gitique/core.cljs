(ns gitique.core
  (:require [clojure.string :as string]
            [goog.dom :as dom]
            [gitique.api :as api]
            [gitique.pure :as pure]
            [gitique.util :as util]))

(enable-console-print!)

(def state (atom {:current-pr {} ;; {:repo "smcgivern/gitique" :pr "1"}
                  :commit-count 0 ;; the number of commit SHAs on the page
                  :base-commit nil ;; the start point for the diff
                  :all-commits '() ;; all of the commits found on the page
                  :files '() ;; the files property of the GitHub API response
                  :view nil ;; :unified / :split
                  }))

(def pjax-wrapper (util/qs "#js-repo-pjax-container"))

(declare add-icons! maybe-show-new update-icons! update-dom!)

(add-watch state :pr-change (fn [_ _ old new]
                              (let [new-pr (:current-pr new)
                                    new-count (:commit-count new)]
                                (when (and new-pr
                                           (or (not= (:current-pr old) new-pr)
                                               (and (pos? new-count) (not= (:commit-count old) new-count))))
                                  (util/remove-class pjax-wrapper "gitique-enabled")
                                  (add-icons!)
                                  (maybe-show-new (:repo new-pr) (:pr new-pr))))))

(add-watch state
           :commits-change
           (fn [_ _ old new]
             (let [repo (get-in new [:current-pr :repo])
                   new-commit (:base-commit new)]
               (when (and (not= (:base-commit old) new-commit) new-commit)
                 (let [all-commits (:all-commits new)
                       new-commits (drop-while #(not= new-commit %) all-commits)]
                   (update-icons! new-commits)
                   (api/get-new-commits! repo new-commit (last all-commits)
                                         #(swap! state assoc :files (:files %))))))))

(add-watch state :files-change (fn [_ _ old new]
                                 (let [new-files (:files new)]
                                   (when (and (not= (:files old) new-files) (not-empty new-files))
                                     (update-dom! new-files)))))

(add-watch state :view-change (fn [_ _ old new]
                                (when (and (not= (:view old) (:view new)) (not-empty (:files new)))
                                  (update-dom! (:files new)))))

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
  (let [classes-contain #(.contains (.-classList item) %)]
    (cond (classes-contain "discussion-commits") "commit-block"
          (classes-contain "discussion-item-assigned") "assigned"
          (classes-contain "discussion-item-unassigned") "unassigned"
          (classes-contain "discussion-item-labeled") "labeled"
          (classes-contain "discussion-item-unlabeled") "unlabeled"
          (classes-contain "discussion-item-ref") "cross-reference"
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
        last-reviewed-commit-block (take-while #(< (:index %) (:index last-reviewer-comment)) commits)]
    {:last-reviewed-commit (-> last-reviewed-commit-block last commit-shas last)
     :all-commits (mapcat commit-shas commits)}))

(defn- diffstat-count
  "Sum the lines in `direction` (added or removed) based on the visible files"
  [gitique-enabled direction]
  (let [selector (str "#diff .file" (when gitique-enabled ":not(.gitique-hidden)")
                      " tr" (when gitique-enabled ":not(.gitique-hidden):not(.gitique-context)")
                      " .blob-num-" direction ":not(.empty-cell)")
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

(defn- set-gitique-state! [state event]
  (let [enabled? (= state "new")
        other-state (if enabled? "all" "new")
        to-enable (util/qs (str "#gitique-show-" state))
        to-disable (util/qs (str "#gitique-show-" other-state))]
    (util/add-class to-enable "selected")
    (util/remove-class to-disable "selected")
    ((if enabled? util/add-class util/remove-class) pjax-wrapper "gitique-enabled")
    (update-overall!)))

(defn- add-button! []
  (when-let [existing-buttons (util/qs "#toc .gitique-header-wrapper")]
    (.remove existing-buttons))
  (let [enabled? (.contains (.-classList pjax-wrapper) "gitique-enabled")
        parent (util/qs "#toc")
        sibling (util/qs "#toc .toc-diff-stats")
        all (dom/createDom "a" #js{:className "btn btn-sm" :id "gitique-show-all"}
                           "All commits")
        new (dom/createDom "a" #js{:className "btn btn-sm" :id "gitique-show-new"}
                           "Selected commits")
        group (dom/createDom "div" #js["btn-group" "right" "gitique-header-wrapper"] all new)]
    (util/add-class (if enabled? new all) "selected")
    (.addEventListener all "click" (partial set-gitique-state! "all") true)
    (.addEventListener new "click" (partial set-gitique-state! "new") true)
    (.insertBefore parent group sibling)))

(defn- reset-classes! []
  (let [classes ["gitique-hidden" "gitique-context"]]
    (doseq [element (util/qsa (string/join ", " (map #(str "." %) classes)))]
      (apply util/remove-class element classes))))

(defn- annotate-lines! [element file]
  (let [new-lines-list (flatten (:new (-> file :patch pure/parse-diff)))
        new-lines (zipmap (map :index new-lines-list) new-lines-list)]
    (doseq [line (util/qsa ".diff-table tr" element)]
      (let [line-number-elements (util/qsa "[data-line-number]" line)
            line-number (if (empty? line-number-elements) "0" (.getAttribute (last line-number-elements) "data-line-number"))]
        (if-let [new-line (new-lines (js/parseInt line-number 10))]
          (when (= :context (:type new-line)) (util/add-class line "gitique-context"))
          (util/add-class line "gitique-hidden"))))))

(defn- annotate-files! [files]
  (let [include-filenames (zipmap (map :filename files) files)]
    (doseq [element (util/qsa "#toc ol>li, #files .file")]
      (let [toc-link (util/qs "li>a" element)
            file-contents (util/qs "[data-path]" element)
            filename (if toc-link (.-textContent toc-link) (.getAttribute file-contents "data-path"))]
        (if-let [file (include-filenames filename)]
          (annotate-lines! element file)
          (util/add-class element "gitique-hidden"))))))

(defn- select-commit [event]
  (swap! state assoc :base-commit (commit-sha (.-parentElement (.-currentTarget event)))))

(defn- add-icon! [element override-class]
  (let [parent (.-parentElement (.-parentElement element))]
    (when-not (util/qs ".gitique-icon" parent)
      ;; This is kind of a hack, but it works fine for now. If this extension used more
      ;; than one icon then it would be better to include them as actual files.
      (let [svg-ns "http://www.w3.org/2000/svg"
            svg (js/document.createElementNS svg-ns "svg")
            path (js/document.createElementNS svg-ns "path")
            icon (dom/createDom "span" #js["gitique-icon"] svg)]
        (.setAttributeNS svg nil "width" "14")
        (.setAttributeNS svg nil "height" "16")
        (.setAttributeNS path nil "d" "M13 1H1C0.45 1 0 1.45 0 2v12c0 0.55 0.45 1 1 1h12c0.55 0 1-0.45 1-1V2c0-0.55-0.45-1-1-1z m0 13H1V2h12v12zM6 9H3V7h3V4h2v3h3v2H8v3H6V9z")
        (.appendChild svg path)
        (if override-class
          (util/add-class icon override-class)
          (.addEventListener icon "click" select-commit))
        (.appendChild parent icon)))))

(defn- add-icons! []
  (let [elements (util/qsa ".commit-id")]
    (doseq [element (butlast elements)] (add-icon! element nil))
    (when-let [last (last elements)] (add-icon! last "gitique-head"))))

(defn- find-commit
  "Find the link to a commit on the page by its SHA"
  [commit-id]
  (.-parentElement (.-parentElement (util/qs (str ".commit-id[href$='" commit-id "']")))))

(defn- update-icon! [commit-id new-class new-title]
  (let [element (if (string? commit-id)
                  (util/qs ".gitique-icon" (find-commit commit-id))
                  commit-id)]
    (util/remove-class element "gitique-reviewed" "gitique-basis" "gitique-new")
    (util/add-class element new-class)
    (.setAttribute element "title" new-title)))

(defn- update-icons! [[from & new]]
  (doseq [disabled-commit (util/qsa ".gitique-icon")]
    (update-icon! disabled-commit "gitique-reviewed" "Reviewed commit"))
  (when (and from new)
    (update-icon! from "gitique-basis" "Last reviewed commit")
    (doseq [new-commit (butlast new)]
      (update-icon! new-commit "gitique-new" "New commit"))))

(defn- update-dom! [files]
  (reset-classes!)
  (when (util/qs "#toc")
    (annotate-files! files)
    (add-button!)
    (update-overall!)))

(defn- maybe-show-new [repo pr]
  (if (and repo pr)
    (let [{:keys [last-reviewed-commit all-commits]} (commit-info)]
      (swap! state assoc :base-commit last-reviewed-commit :all-commits all-commits))
    (swap! state assoc :base-commit nil :all-commits nil)))

(defn- main []
  (let [components (string/split js/window.location.pathname "/")
        repo (str (get components 1) "/" (get components 2))
        pr (get components 4)
        current-pr (when (= (get components 3) "pull") {:repo repo :pr pr})
        view (if (util/qs ".file-diff-split") :split :unified)]
    (swap! state assoc :commit-count (count (util/qsa ".commit-id")))
    (swap! state assoc :current-pr current-pr)
    (swap! state assoc :view view)))

(defn ^:export watch
  "Run `main` once, then watch for DOM mutations in the PJAX container and run `main` when
  it changes"
  []
  (when pjax-wrapper
    (let [is-valid-mutation? #(and (= (.-type %) "childList") (seq (.-addedNodes %)))
          observer (js/MutationObserver. #(when (some is-valid-mutation? %) (main)))]
      (.observe observer pjax-wrapper #js{:childList true :attributes false :characterData false :subtree true})))
  (main))
