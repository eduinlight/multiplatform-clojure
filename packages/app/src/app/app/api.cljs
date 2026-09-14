(ns app.app.api
  (:require [app.api-sdk.core :as sdk]
            [app.shared.result :as result]
            [re-frame.core :as rf]))

(defonce ^:private client (atom (sdk/client {:base-url "http://localhost:8080"})))

(defn install! [opts]
  (reset! client (sdk/client opts)))

(rf/reg-fx
 :api/call
 (fn [{:keys [op token params body on-success on-failure]}]
   (-> (sdk/call (sdk/with-token @client token) op {:params params :body body})
       (.then (fn [r]
                (if (result/ok? r)
                  (when on-success (rf/dispatch (conj on-success (result/value r))))
                  (when on-failure (rf/dispatch (conj on-failure r)))))))))
