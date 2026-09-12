(ns app.web.core
  (:require [app.ui.config :as config]
            [app.ui.events :as events]
            [app.ui.storage :as storage]
            [app.web.views :as views]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdom]))

(goog-define api-base-url "http://localhost:8080")

(defonce root (atom nil))

(defn- local-storage-backend []
  {:get (fn [k] (try (.getItem js/localStorage k) (catch :default _ nil)))
   :set (fn [k v] (try (.setItem js/localStorage k v) (catch :default _ nil)))
   :del (fn [k] (try (.removeItem js/localStorage k) (catch :default _ nil)))})

(defn mount! []
  (let [el (.getElementById js/document "root")]
    (when (nil? @root)
      (reset! root (rdom/create-root el)))
    (rdom/render @root [views/app])))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (mount!))

(defn init []
  (config/install! {:base-url api-base-url})
  (storage/install! (local-storage-backend))
  (rf/dispatch-sync [::events/boot])
  (mount!))
