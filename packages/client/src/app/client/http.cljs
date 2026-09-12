(ns app.client.http
  (:require [app.shared.routes :as routes]
            [clojure.string :as str]))

(defn- transit-free-parse [text]
  (when-not (str/blank? text)
    (js->clj (js/JSON.parse text) :keywordize-keys true)))

(defn- ->body [data]
  (when (some? data) (js/JSON.stringify (clj->js data))))

(defn- headers [token]
  (cond-> #js {"Content-Type" "application/json"
               "Accept" "application/json"}
    (some? token) (doto (aset "Authorization" (str "Bearer " token)))))

(defn request
  [{:keys [base-url endpoint params body token on-success on-failure]}]
  (let [url (routes/url-for base-url endpoint params)
        method (name (routes/method-for endpoint))
        init (cond-> #js {:method (str/upper-case method)
                          :headers (headers token)}
               (some? body) (doto (aset "body" (->body body))))]
    (-> (js/fetch url init)
        (.then (fn [res]
                 (-> (.text res)
                     (.then (fn [text]
                              {:status (.-status res)
                               :ok? (.-ok res)
                               :body (transit-free-parse text)})))))
        (.then (fn [{:keys [ok? status body]}]
                 (if ok?
                   (on-success body)
                   (on-failure {:status status
                                :message (or (:error body) "Request failed")}))))
        (.catch (fn [e]
                  (on-failure {:status 0 :message (.-message e)}))))
    nil))
