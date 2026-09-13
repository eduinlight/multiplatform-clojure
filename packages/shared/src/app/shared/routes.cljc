(ns app.shared.routes
  (:require [app.shared.schema :as schema]
            [clojure.string :as str]))

(def api-prefix "/api/v1")

(def endpoints
  {:health {:method :get :path "/health"}
   :auth/register {:method :post :path "/auth/register" :body schema/Registration}
   :auth/login {:method :post :path "/auth/login" :body schema/Credentials}
   :auth/me {:method :get :path "/auth/me" :auth? true}
   :todo/list {:method :get :path "/todos" :auth? true}
   :todo/create {:method :post :path "/todos" :auth? true :body schema/NewTodo}
   :todo/update {:method :patch :path "/todos/:id" :auth? true
                 :params schema/IdParams :body schema/TodoPatch}
   :todo/delete {:method :delete :path "/todos/:id" :auth? true
                 :params schema/IdParams}})

(defn endpoint [id]
  (get endpoints id))

(defn path-for
  ([id] (path-for id nil))
  ([id params]
   (let [{:keys [path]} (endpoint id)]
     (reduce-kv (fn [p k v]
                  (str/replace p (str ":" (name k)) (str v)))
                path
                (or params {})))))

(defn url-for
  ([base id] (url-for base id nil))
  ([base id params] (str base api-prefix (path-for id params))))

(defn method-for [id]
  (:method (endpoint id)))
