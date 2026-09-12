(ns app.api.todos
  (:require [app.api.db :as db]
            [app.shared.result :as result]))

(defn- ->public [doc]
  (when doc
    {:todo/id (:_id doc)
     :todo/title (:title doc)
     :todo/done (boolean (:done doc))
     :todo/owner-id (:owner_id doc)
     :todo/created-at (:created_at doc)}))

(defn list-for [conn owner-id]
  (->> (db/find-many conn "todos" {:owner_id owner-id})
       (mapv ->public)
       (sort-by :todo/created-at)
       (reverse)
       (vec)))

(defn create [conn owner-id {:keys [title]}]
  (result/ok (->public (db/insert! conn "todos"
                                   {:title title
                                    :done false
                                    :owner_id owner-id
                                    :created_at (java.util.Date.)}))))

(defn- owned-query [id owner-id]
  {:_id (db/object-id id) :owner_id owner-id})

(defn update-todo [conn owner-id id patch]
  (if-not (db/find-one conn "todos" (owned-query id owner-id))
    (result/err :not-found "todo not found")
    (let [updates (cond-> {}
                    (contains? patch :title) (assoc :title (:title patch))
                    (contains? patch :done) (assoc :done (boolean (:done patch))))]
      (result/ok (->public (db/update-one! conn "todos" (owned-query id owner-id) updates))))))

(defn delete-todo [conn owner-id id]
  (if (db/delete-one! conn "todos" (owned-query id owner-id))
    (result/ok {:deleted true})
    (result/err :not-found "todo not found")))
