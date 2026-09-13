(ns app.shared.result)

(defn ok
  ([] {:result/ok? true})
  ([value] {:result/ok? true :result/value value}))

(defn err
  ([kind] (err kind nil))
  ([kind detail] {:result/ok? false :result/kind kind :result/detail detail}))

(defn ok? [r] (true? (:result/ok? r)))
(defn err? [r] (false? (:result/ok? r)))
(defn value [r] (:result/value r))
(defn kind [r] (:result/kind r))
(defn detail [r] (:result/detail r))

(def kind->status
  {:not-found 404
   :unauthorized 401
   :forbidden 403
   :conflict 409
   :invalid 400
   :internal 500})

(defn status [r]
  (if (ok? r) 200 (get kind->status (kind r) 500)))

(defn status->kind [status]
  (or (some (fn [[k s]] (when (= s status) k)) kind->status)
      (if (<= 500 status) :internal :unexpected)))
