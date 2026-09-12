(ns app.mobile.core
  (:require ["expo-secure-store" :as secure-store]
            ["react-native" :as rn]
            [app.mobile.views :as views]
            [app.ui.config :as config]
            [app.ui.events :as events]
            [app.ui.storage :as storage]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(goog-define api-base-url "http://localhost:8080")

(defonce cached-token (atom nil))

(defn- secure-store-backend []
  {:get (fn [_] @cached-token)
   :set (fn [k v]
          (reset! cached-token v)
          (.setItemAsync secure-store k v))
   :del (fn [k]
          (reset! cached-token nil)
          (.deleteItemAsync secure-store k))})

(defn- root []
  (r/as-element [views/app]))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!))

(defn init []
  (config/install! {:base-url api-base-url})
  (storage/install! (secure-store-backend))
  (-> (.getItemAsync secure-store events/token-key)
      (.then (fn [token]
               (reset! cached-token token)
               (rf/dispatch-sync [::events/boot])))
      (.catch (fn [_] (rf/dispatch-sync [::events/boot]))))
  (.registerComponent rn/AppRegistry "main" (fn [] root)))
