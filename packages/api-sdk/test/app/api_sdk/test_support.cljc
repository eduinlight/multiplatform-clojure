(ns app.api-sdk.test-support
  (:require [app.api-sdk.core :as sdk]
            [app.api-sdk.json :as json]
            [app.api-sdk.promise :as p]
            #?(:cljs [cljs.test :refer [async]])))

(defn fake-client
  ([response] (fake-client response nil))
  ([response token]
   (let [calls (atom [])]
     {:calls calls
      :client (sdk/client {:base-url "http://api.test/"
                           :token token
                           :transport (fn [request]
                                        (swap! calls conj request)
                                        (p/resolved (update response :body json/encode)))})})))

(defn resolve-all [promises f]
  #?(:clj (f (mapv #(deref % 5000 ::timeout) promises))
     :cljs (async done
                  (-> (js/Promise.all (into-array promises))
                      (.then (fn [results] (f (vec results))))
                      (.catch (fn [e] (cljs.test/is (nil? e))))
                      (.finally done)))))
