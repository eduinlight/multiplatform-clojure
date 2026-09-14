(ns app.app.storage
  (:require [re-frame.core :as rf]))

(def ^:private no-storage
  {:get (fn [_] nil)
   :set (fn [_ _] nil)
   :del (fn [_] nil)})

(defonce ^:private backend (atom no-storage))

(defn install! [impl]
  (reset! backend (merge no-storage impl)))

(defn load [k]
  (js/Promise. (fn [resolve _] (resolve ((:get @backend) k)))))

(rf/reg-fx
 :storage/set
 (fn [{:keys [key value]}]
   ((:set @backend) key value)))

(rf/reg-fx
 :storage/remove
 (fn [{:keys [key]}]
   ((:del @backend) key)))
