(ns app.web.core
  (:require [app.app.core :as app]
            [app.web.views :as views]
            [reagent.dom.client :as rdom]))

(defonce root (atom nil))

(defn- local-storage []
  {:get (fn [k] (try (.getItem js/localStorage k) (catch :default _ nil)))
   :set (fn [k v] (try (.setItem js/localStorage k v) (catch :default _ nil)))
   :del (fn [k] (try (.removeItem js/localStorage k) (catch :default _ nil)))})

(defn mount! []
  (when (nil? @root)
    (reset! root (rdom/create-root (.getElementById js/document "root"))))
  (rdom/render @root [views/app]))

(defn ^:dev/after-load reload! []
  (app/reload!)
  (mount!))

(defn init []
  (app/start! {:storage (local-storage)})
  (mount!))
