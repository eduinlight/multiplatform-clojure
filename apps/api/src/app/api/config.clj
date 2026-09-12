(ns app.api.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'ig/refset
  [_ _ value]
  (ig/refset value))

(defn read-config
  ([] (read-config :dev))
  ([profile]
   (aero/read-config (io/resource "config.edn") {:profile profile})))
