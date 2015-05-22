(ns gitique.pure
  (:require [clojure.string :as string]))

(enable-console-print!)

(defn- merge-as-vectors
  "Merge a sequence of maps, where the new values are a vector of the previous values"
  [keys maps]
  (apply merge-with conj (cons (zipmap keys (repeat (count keys) [])) maps)))

(defn- remove-adjacent-placeholders
  "Given sequences of diff maps in :new and :old, remove the placeholder lines
  which occur at an offset of one position in both"
  [{:keys [old new]} diffs]
  (let [remove-placeholders (fn [other operation i item]
                              (if-let [next-item (nth other (operation i) {})]
                                (when-not (= :placeholder (:type item) (:type next-item)) item)
                                item))]
    {:new (into [] (keep-indexed (partial remove-placeholders old inc) new))
     :old (into [] (keep-indexed (partial remove-placeholders new dec) old))}))

(defn- parse-hunk-line [old-index new-index line]
  (let [[prefix & characters] (split-at 1 line)
        type ({"+" :new "-" :old " " :context} (first prefix))
        other-type ({:new :old :old :new} type)
        new-line {:index (if (= type :old) old-index new-index)
                  :type (if other-type :change :context)
                  :content (apply str (flatten characters))}
        placeholder {:type :placeholder}]
    {:type type
     :new-line (if other-type
                 {type new-line other-type placeholder}
                 {:new (assoc new-line :index new-index) :old (assoc new-line :index old-index)})}))

(defn- parse-hunk [hunk]
  (let [[header & lines] (string/split-lines hunk)
        [old-start new-start] (map #(js/parseInt (last %) 10) (re-seq #"(\d+)," header))]
    (loop [lines lines
           parsed-lines []
           old-index old-start
           new-index new-start]
      (let [[current & rest] lines
            {:keys [new-line type]} (parse-hunk-line old-index new-index current)
            old-index (if (= type :new) old-index (inc old-index))
            new-index (if (= type :old) new-index (inc new-index))]
        (if current
          (recur rest (conj parsed-lines new-line) old-index new-index)
          (remove-adjacent-placeholders (merge-as-vectors [:old :new] parsed-lines)))))))

(defn parse-diff
  "Given a unified diff, return a map containing the old hunks and the new hunks"
  [diff]
  (let [hunks (remove empty? (string/split diff #"(?m)^@@"))]
    (merge-as-vectors [:old :new] (map parse-hunk hunks))))
