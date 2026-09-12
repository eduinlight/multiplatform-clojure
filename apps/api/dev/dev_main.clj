(ns dev-main
  (:require [app.api.main :as main]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [nrepl.server :as nrepl]
            [taoensso.timbre :as log]))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "API_NREPL_PORT") "7888"))]
    (main/start! :dev)
    (nrepl/start-server :bind "0.0.0.0" :port port :handler cider-nrepl-handler)
    (log/info "nrepl server ready" {:port port})
    (spit ".nrepl-port" port)
    @(promise)))
