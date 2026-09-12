(ns user
  (:require [app.api.config :as config]
            [app.api.db :as db]
            [app.api.router]
            [app.api.server]
            [app.api.auth.jwt]
            [integrant.core :as ig]
            [integrant.repl :as repl]
            [integrant.repl.state :as state]))

(repl/set-prep! #(config/read-config :dev))

(def go repl/go)
(def halt repl/halt)
(def reset repl/reset)

(defn system [] state/system)
(defn conn [] (:app.api.db/client state/system))
