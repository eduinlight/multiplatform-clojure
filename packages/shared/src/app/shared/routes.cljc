(ns app.shared.routes
  (:require [clojure.string :as str]))

(def api-prefix "/api/v1")

(def endpoints
  {:health {:method :get :path "/health"}
   :auth/register {:method :post :path "/auth/register"}
   :auth/login {:method :post :path "/auth/login"}
   :auth/me {:method :get :path "/auth/me"}
   :todo/list {:method :get :path "/todos"}
   :todo/create {:method :post :path "/todos"}
   :todo/update {:method :patch :path "/todos/:id"}
   :todo/delete {:method :delete :path "/todos/:id"}})

(defn path-for
  ([id] (path-for id nil))
  ([id params]
   (let [{:keys [path]} (get endpoints id)]
     (reduce-kv (fn [p k v]
                  (str/replace p (str ":" (name k)) (str v)))
                path
                (or params {})))))

(defn url-for
  ([base id] (url-for base id nil))
  ([base id params] (str base api-prefix (path-for id params))))

(defn method-for [id]
  (get-in endpoints [id :method]))
