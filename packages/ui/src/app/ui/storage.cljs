(ns app.ui.storage
  (:require [re-frame.core :as rf]))

(defonce ^:private backend (atom {:get (fn [_] nil) :set (fn [_ _] nil) :del (fn [_] nil)}))

(defn install! [impl]
  (reset! backend impl))

(rf/reg-fx
 :storage/set
 (fn [{:keys [key value]}]
   ((:set @backend) key value)))

(rf/reg-fx
 :storage/remove
 (fn [{:keys [key]}]
   ((:del @backend) key)))

(rf/reg-cofx
 :storage/get
 (fn [cofx {:keys [key]}]
   (assoc cofx :storage/value ((:get @backend) key))))
