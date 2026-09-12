(ns app.api.server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

(defmethod ig/init-key :app.api.server/jetty
  [_ {:keys [handler port host]}]
  (log/info "starting http server" {:host host :port port})
  (jetty/run-jetty handler {:port port :host host :join? false}))

(defmethod ig/halt-key! :app.api.server/jetty
  [_ server]
  (log/info "stopping http server")
  (.stop server))
