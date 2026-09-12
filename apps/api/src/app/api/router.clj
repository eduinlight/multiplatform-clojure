(ns app.api.router
  (:require [app.api.handlers :as handlers]
            [app.api.middleware :as mw]
            [app.shared.routes :as routes]
            [integrant.core :as ig]
            [muuntaja.core :as mc]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]))

(def ^:private authenticated
  {:middleware [mw/wrap-authenticated]})

(defn routes []
  [routes/api-prefix
   ["/health" {:get handlers/health}]
   ["/auth"
    ["/register" {:post handlers/register}]
    ["/login" {:post handlers/login}]
    ["/me" (merge authenticated {:get handlers/me})]]
   ["/todos"
    ["" (merge authenticated {:get handlers/list-todos
                              :post handlers/create-todo})]
    ["/:id" (merge authenticated {:patch handlers/update-todo
                                  :delete handlers/delete-todo})]]])

(defn handler [deps]
  (ring/ring-handler
   (ring/router
    (routes)
    {:data {:muuntaja mc/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         muuntaja/format-request-middleware]}})
   (ring/create-default-handler
    {:not-found (constantly {:status 404 :body "{\"error\":\"not found\"}"
                             :headers {"Content-Type" "application/json"}})})
   {:middleware [mw/wrap-exceptions
                 mw/wrap-cors
                 (mw/wrap-identity deps)]}))

(defmethod ig/init-key :app.api.router/routes
  [_ {:keys [db signer]}]
  (handler {:db db :signer signer}))
