(ns app.api-sdk.transport
  (:require [clojure.string :as str])
  #?(:clj (:import [java.net URI]
                   [java.net.http HttpClient HttpClient$Version HttpRequest
                    HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers]
                   [java.time Duration]
                   [java.util.function BiFunction])))

(defn- verb [method]
  (str/upper-case (name method)))

#?(:clj
   (defn http-client-transport [{:keys [timeout-ms]}]
     (let [timeout (Duration/ofMillis timeout-ms)
           client (-> (HttpClient/newBuilder)
                      (.version HttpClient$Version/HTTP_1_1)
                      (.connectTimeout timeout)
                      (.build))]
       (fn [{:keys [method url headers body]}]
         (let [builder (-> (HttpRequest/newBuilder (URI/create url))
                           (.timeout timeout)
                           (.method (verb method)
                                    (if body
                                      (HttpRequest$BodyPublishers/ofString body)
                                      (HttpRequest$BodyPublishers/noBody))))]
           (doseq [[k v] headers]
             (.header builder k v))
           (-> (.sendAsync ^HttpClient client (.build builder) (HttpResponse$BodyHandlers/ofString))
               (.handle (reify BiFunction
                          (apply [_ response ex]
                            (if ex
                              {:status 0 :error (or (ex-message (or (ex-cause ex) ex)) "network error")}
                              {:status (.statusCode ^HttpResponse response)
                               :body (.body ^HttpResponse response)}))))))))))

#?(:cljs
   (defn fetch-transport [{:keys [timeout-ms]}]
     (fn [{:keys [method url headers body]}]
       (let [controller (js/AbortController.)
             timer (js/setTimeout #(.abort controller) timeout-ms)
             init (cond-> #js {:method (verb method)
                               :headers (clj->js headers)
                               :signal (.-signal controller)}
                    (some? body) (doto (aset "body" body)))]
         (-> (js/fetch url init)
             (.then (fn [res]
                      (.then (.text res)
                             (fn [text] {:status (.-status res) :body text}))))
             (.catch (fn [e]
                       {:status 0 :error (or (.-message e) "network error")}))
             (.finally #(js/clearTimeout timer)))))))

(defn default-transport [opts]
  #?(:clj (http-client-transport opts)
     :cljs (fetch-transport opts)))
