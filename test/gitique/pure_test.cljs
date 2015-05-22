(ns gitique.pure-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [gitique.pure :as pure]))

(deftest test-parse-diff
  (let [addition "@@ -0,0 +1,3 @@
+This is a hunk which has been added.
+
+There are no old lines."
        deletion "@@ -1,3 +0,0 @@
-This is a hunk which has been deleted.
-
-There are no new lines."
        patch "@@ -1,7 +1,6 @@
-The Way that can be told of is not the eternal Way;
-The name that can be named is not the eternal name.
 The Nameless is the origin of Heaven and Earth;
-The Named is the mother of all things.
+The named is the mother of all things.
+
 Therefore let there always be non-being,
   so we may see their subtlety,
 And let there always be being,
@@ -9,3 +8,6 @@
 The two are the same,
 But after they are produced,
   they have different names.
+They both may be called deep and profound.
+Deeper and more profound,
+The door of all subtleties!"]
    (testing "Testing parse-diff"
      (is (= {:old [[{:type :placeholder}
                     {:type :placeholder}
                     {:type :placeholder}]]
              :new [[{:index 1 :type :change :content "This is a hunk which has been added."}
                     {:index 2 :type :change :content ""}
                     {:index 3 :type :change :content "There are no old lines."}]]}
             (pure/parse-diff addition) ))
      (is (= {:old [[{:index 1 :type :change :content "This is a hunk which has been deleted."}
                     {:index 2 :type :change :content ""}
                     {:index 3 :type :change :content "There are no new lines."}]]
              :new [[{:type :placeholder}
                     {:type :placeholder}
                     {:type :placeholder}]]}
             (pure/parse-diff deletion) ))
      (let [{:keys [old new]} (pure/parse-diff patch)]
        (is (= [[{:index 1 :type :change :content "The Way that can be told of is not the eternal Way;"}
                 {:index 2 :type :change :content "The name that can be named is not the eternal name."}
                 {:index 3 :type :context :content "The Nameless is the origin of Heaven and Earth;"}
                 {:index 4 :type :change :content "The Named is the mother of all things."}
                 {:type :placeholder}
                 {:index 5 :type :context :content "Therefore let there always be non-being,"}
                 {:index 6 :type :context :content "  so we may see their subtlety,"}
                 {:index 7 :type :context :content "And let there always be being,"}]
                [{:index 9 :type :context :content "The two are the same,"}
                 {:index 10 :type :context :content "But after they are produced,"}
                 {:index 11 :type :context :content "  they have different names."}
                 {:type :placeholder}
                 {:type :placeholder}
                 {:type :placeholder}]]
               old))
        (is (= [[{:type :placeholder}
                 {:type :placeholder}
                 {:index 1 :type :context :content "The Nameless is the origin of Heaven and Earth;"}
                 {:index 2 :type :change :content "The named is the mother of all things."}
                 {:index 3 :type :change :content ""}
                 {:index 4 :type :context :content "Therefore let there always be non-being,"}
                 {:index 5 :type :context :content "  so we may see their subtlety,"}
                 {:index 6 :type :context :content "And let there always be being,"}]
                [{:index 8 :type :context :content "The two are the same,"}
                 {:index 9 :type :context :content "But after they are produced,"}
                 {:index 10 :type :context :content "  they have different names."}
                 {:index 11 :type :change :content "They both may be called deep and profound."}
                 {:index 12 :type :change :content "Deeper and more profound,"}
                 {:index 13 :type :change :content "The door of all subtleties!"}]]
               new))))))
