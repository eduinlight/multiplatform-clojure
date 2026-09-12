(ns app.api.handlers
  (:require [app.api.auth.jwt :as jwt]
            [app.api.todos :as todos]
            [app.api.users :as users]
            [app.shared.result :as result]
            [app.shared.schema :as schema]))

(defn- respond [r]
  {:status (result/status r)
   :body (if (result/ok? r)
           (result/value r)
           {:error (:result/detail r)})})

(defn- validate [sch value]
  (when-let [errors (schema/explain sch value)]
    {:status 400 :body {:error "validation failed" :details errors}}))

(defn health [_]
  {:status 200 :body {:status "ok" :service "app-api"}})

(defn register [{:keys [db signer body-params]}]
  (or (validate schema/Registration body-params)
      (let [r (users/register db body-params)]
        (if (result/ok? r)
          {:status 201 :body {:token (jwt/sign signer (:user/id (result/value r)))
                              :user (result/value r)}}
          (respond r)))))

(defn login [{:keys [db signer body-params]}]
  (or (validate schema/Credentials body-params)
      (let [r (users/authenticate db body-params)]
        (if (result/ok? r)
          {:status 200 :body {:token (jwt/sign signer (:user/id (result/value r)))
                              :user (result/value r)}}
          (respond r)))))

(defn me [{:keys [identity]}]
  {:status 200 :body identity})

(defn list-todos [{:keys [db identity]}]
  {:status 200 :body (todos/list-for db (:user/id identity))})

(defn create-todo [{:keys [db identity body-params]}]
  (or (validate schema/NewTodo body-params)
      (respond (todos/create db (:user/id identity) body-params))))

(defn update-todo [{:keys [db identity body-params path-params]}]
  (or (validate schema/TodoPatch body-params)
      (respond (todos/update-todo db (:user/id identity) (:id path-params) body-params))))

(defn delete-todo [{:keys [db identity path-params]}]
  (respond (todos/delete-todo db (:user/id identity) (:id path-params))))
