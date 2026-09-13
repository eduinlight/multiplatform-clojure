(ns app.api-sdk.json
  (:require [clojure.string :as str]
            #?(:clj [jsonista.core :as j])))

#?(:clj (def ^:private mapper (j/object-mapper {:decode-key-fn true})))

(defn encode [data]
  (when (some? data)
    #?(:clj (j/write-value-as-string data mapper)
       :cljs (js/JSON.stringify (clj->js data :keyword-fn #(subs (str %) 1))))))

(defn decode [text]
  (when-not (str/blank? text)
    (try
      #?(:clj (j/read-value text mapper)
         :cljs (js->clj (js/JSON.parse text) :keywordize-keys true))
      (catch #?(:clj Exception :cljs :default) _ nil))))
