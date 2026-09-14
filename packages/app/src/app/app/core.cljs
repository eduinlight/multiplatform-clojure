(ns app.app.core
  (:require [app.app.api :as api]
            [app.app.config :as config]
            [app.app.events :as events]
            [app.app.storage :as storage]
            [app.app.subs]
            [re-frame.core :as rf]))

(defn start!
  ([] (start! {}))
  ([{:keys [storage base-url transport]}]
   (api/install! (cond-> {:base-url (or base-url config/api-base-url)}
                   transport (assoc :transport transport)))
   (storage/install! storage)
   (rf/dispatch-sync [::events/initialize])
   (-> (storage/load events/token-key)
       (.catch (fn [_] nil))
       (.then (fn [token] (rf/dispatch [::events/boot token]))))))

(defn reload! []
  (rf/clear-subscription-cache!))
