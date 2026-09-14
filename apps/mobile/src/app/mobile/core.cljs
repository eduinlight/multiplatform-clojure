(ns app.mobile.core
  (:require ["expo-secure-store" :as secure-store]
            ["react-native" :as rn]
            ["react-native-safe-area-context" :as safe-area-context]
            [app.app.core :as app]
            [app.mobile.views :as views]
            [reagent.core :as r]))

(defn- secure-storage []
  {:get (fn [k] (.getItemAsync secure-store k))
   :set (fn [k v] (.setItemAsync secure-store k v))
   :del (fn [k] (.deleteItemAsync secure-store k))})

(defn- root []
  (r/as-element [:> safe-area-context/SafeAreaProvider [views/app]]))

(defn ^:dev/after-load reload! []
  (app/reload!))

(defn init []
  (app/start! {:storage (secure-storage)})
  (.registerComponent rn/AppRegistry "main" (fn [] root)))
