(ns app.api.main
  (:gen-class)
  (:require [app.api.config :as config]
            [app.api.db :as db]
            [app.api.router]
            [app.api.server]
            [app.api.auth.jwt]
            [integrant.core :as ig]
            [taoensso.timbre :as log]))

(defn start!
  ([] (start! :prod))
  ([profile]
   (let [cfg (config/read-config profile)
         system (ig/init cfg)]
     (db/ensure-indexes! (:app.api.db/client system))
     system)))

(defn -main [& _]
  (let [system (start! :prod)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (log/info "shutting down")
                                 (ig/halt! system))))
    @(promise)))
