(ns taoensso.timbre.frequencies
  "Frequency logger for Timbre. ALPHA quality."
  {:author "Peter Taoussanis"}
  (:require [clojure.string        :as str]
            [taoensso.timbre       :as timbre]
            [taoensso.timbre.utils :as utils]))

(def ^:dynamic *fdata* "{::fname {form-value frequency}}" nil)

(declare fdata-table)

(defmacro with-fdata
  [level & body]
  `(if-not (timbre/logging-enabled? ~level)
     {:result (do ~@body)}
     (binding [*fdata* (atom {})]
       {:result (do ~@body) :stats @*fdata*})))

(defmacro log-frequencies
  "When logging is enabled, executes named body with frequency counting enabled.
  Body forms wrapped in (fspy) will have their result frequencies logged. Always
  returns body's result.

  Note that logging appenders will receive both a frequencies table string AND
  the raw frequency stats under a special :frequency-stats key (useful for
  queryable db logging)."
  [level name & body]
  (let [name (utils/fq-keyword name)]
    `(let [{result# :result stats# :stats} (with-fdata ~level ~@body)]
       (when stats#
         (timbre/log* ~level
                      {:frequency-stats stats#}
                      (str "Frequencies " ~name)
                      (str "\n" (fdata-table stats#))))
       result#)))

(defmacro fspy
  "Frequency spy. When in the context of a *fdata* binding, records the frequency
  of each enumerated result. Always returns the body's result."
  [name & body]
  (let [name (utils/fq-keyword name)]
    `(if-not *fdata*
       (do ~@body)
       (let [name#   ~name
             result# (do ~@body)]
         (swap! *fdata* #(assoc-in % [name# result#]
                                  (inc (get-in % [name# result#] 0))))
         result#))))

(defmacro f [name & body] `(fspy name ~@body)) ; Alias

(defn fdata-table
  "Returns formatted table string for given fdata stats."
  [stats]
  (let [sorted-fnames (sort (keys stats))
        sorted-fvals  (fn [form-stats] (reverse (sort-by form-stats
                                                        (keys form-stats))))]
    (str/join "\n"
     (for [fname sorted-fnames]
       (let [form-stats (stats fname)
             sorted-fvs (sorted-fvals form-stats)]
         (str fname " "
              (str/join " "
                (mapv (fn [v] (vector v (get form-stats v 0)))
                      sorted-fvs))))))))

(comment (fdata-table {:name1 {:a 10 :b 4 :c 20}
                       :name2 {33 8 12 2 false 6}}))

(comment
  (with-fdata :info
    (vec (repeatedly 20 (fn [] (fspy :rand-nth (rand-nth [:a :b :c]))))))

  (log-frequencies
   :info :my-frequencies
   (vec (repeatedly 20 (fn [] (fspy :rand-nth (rand-nth [:a :b :c])))))))