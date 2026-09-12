(ns app.shared.format
  (:require [clojure.string :as str]))

(defn initials [name]
  (->> (str/split (or name "") #"\s+")
       (remove str/blank?)
       (take 2)
       (map (comp str/upper-case #(subs % 0 1)))
       (apply str)))

(defn truncate [s n]
  (let [s (or s "")]
    (if (<= (count s) n) s (str (subs s 0 (max 0 (dec n))) "…"))))

(defn pluralize [n singular plural]
  (str n " " (if (= 1 n) singular plural)))

(defn summarize [todos]
  (let [total (count todos)
        done (count (filter :todo/done todos))]
    {:total total
     :done done
     :pending (- total done)
     :label (str done " of " (pluralize total "task" "tasks") " done")}))
