(ns gitique.api
  (:require [goog.net.XhrIo :as xhr]
            [goog.dom :as dom]
            [gitique.util :as util]))

(def token-key "gitique.github_access_token")

(declare get-new-commits!)

(defn- update-token! [url callback event]
  (let [element (.-target event)
        new-token (.-value (util/qs "input" element))]
    (js/localStorage.setItem token-key new-token)
    (get-new-commits! url callback)
    (.add (.-classList element) "gitique-hidden")
    (.preventDefault event)
    (.stopPropagation event)))

(defn- request-token! [url callback]
  (if-let [token-form (util/qs "#gitique-token-request")]
    (do
      (.remove (.-classList token-form) "gitique-hidden")
      (.add (.-classList (util/qs "input" token-form)) "error"))
    (let [parent (util/qs "#toc")
          sibling (util/qs "#toc .toc-diff-stats")
          current-token (js/localStorage.getItem token-key)
          input (dom/createDom "input" #js{:type "text"
                                           :length 50
                                           :value current-token
                                           :placeholder "Access token"
                                           :class (when (> (count current-token) 1) "error")})
          needs-repo? (util/qs ".repo-private-label")
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
                   #js{:class "right gitique-header-wrapper" :id "gitique-token-request"}
                   explanation input)]
      (.addEventListener input "input" #(.remove (.-classList (.-target %)) "error"))
      (.addEventListener wrapper "submit" (partial update-token! url callback))
      (.insertBefore parent wrapper sibling))))

(defn- get-new-commits!
  ([url callback]
   (let [auth-token (js/localStorage.getItem token-key)
         headers (if auth-token {"Authorization" (str "token " auth-token)} {})
         handler (fn [event]
                   (if-let [error (not= 200 (.getStatus (.-target event)))]
                     (request-token! (.getLastUri (.-target event)) callback)
                     (let [body (js->clj (.getResponseJson (.-target event)) :keywordize-keys true)]
                       (callback body))))]
     (xhr/send url handler "GET" nil headers)))
  ([repo [from to] callback]
   (when (and from to)
     (get-new-commits! (str "https://api.github.com/repos/" repo "/compare/" from "..." to) callback))))
