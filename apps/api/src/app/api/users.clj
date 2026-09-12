(ns app.api.users
  (:require [app.api.auth.password :as password]
            [app.api.db :as db]
            [app.shared.result :as result]
            [clojure.string :as str]))

(defn- ->public [doc]
  (when doc
    {:user/id (:_id doc)
     :user/email (:email doc)
     :user/name (:name doc)
     :user/created-at (:created_at doc)}))

(defn by-id [conn id]
  (some-> (db/find-one conn "users" {:_id (db/object-id id)}) ->public))

(defn by-email [conn email]
  (db/find-one conn "users" {:email (str/lower-case email)}))

(defn register [conn {:keys [email password name]}]
  (let [email (str/lower-case email)]
    (if (by-email conn email)
      (result/err :conflict "email already registered")
      (result/ok (->public (db/insert! conn "users"
                                       {:email email
                                        :name name
                                        :password_hash (password/hash-password password)
                                        :created_at (java.util.Date.)}))))))

(defn authenticate [conn {:keys [email password]}]
  (let [doc (by-email conn email)]
    (if (and doc (password/verify password (:password_hash doc)))
      (result/ok (->public doc))
      (result/err :unauthorized "invalid email or password"))))
