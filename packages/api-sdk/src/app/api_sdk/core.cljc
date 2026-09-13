(ns app.api-sdk.core
  (:require [app.api-sdk.json :as json]
            [app.api-sdk.promise :as p]
            [app.api-sdk.transport :as transport]
            [app.shared.result :as result]
            [app.shared.routes :as routes]
            [app.shared.schema :as schema]
            [clojure.string :as str]))

(def default-timeout-ms 30000)

(def ^:private default-headers
  {"Accept" "application/json"
   "Content-Type" "application/json"})

(defn client
  ([] (client {}))
  ([{:keys [base-url token headers transport timeout-ms]}]
   {:base-url (str/replace (or base-url "") #"/+$" "")
    :token token
    :headers (or headers {})
    :transport (or transport
                   (transport/default-transport
                    {:timeout-ms (or timeout-ms default-timeout-ms)}))}))

(defn with-token [client token]
  (assoc client :token token))

(defn token [client]
  (:token client))

(defn- request-problem [{:keys [auth? params body] :as endpoint} client request]
  (cond
    (nil? endpoint)
    [:invalid "unknown operation" nil]

    (and auth? (str/blank? (:token client)))
    [:unauthorized "authentication required" nil]

    (and params (schema/explain params (:params request)))
    [:invalid "invalid request params" (schema/explain params (:params request))]

    (and body (schema/explain body (:body request)))
    [:invalid "invalid request body" (schema/explain body (:body request))]))

(defn- ->result [{:keys [status body error]}]
  (let [data (json/decode body)]
    (cond
      (zero? status)
      (result/err :network {:status 0 :message error :details nil})

      (<= 200 status 299)
      (result/ok data)

      :else
      (result/err (result/status->kind status)
                  {:status status
                   :message (or (:error data) (str "request failed with status " status))
                   :details (:details data)}))))

(defn call
  ([client op] (call client op nil))
  ([client op request]
   (let [endpoint (routes/endpoint op)]
     (if-let [[kind message details] (request-problem endpoint client request)]
       (p/resolved (result/err kind {:status nil :message message :details details}))
       (let [token (:token client)
             headers (cond-> (merge default-headers (:headers client))
                       (not (str/blank? token)) (assoc "Authorization" (str "Bearer " token)))]
         (-> ((:transport client)
              {:method (:method endpoint)
               :url (routes/url-for (:base-url client) op (:params request))
               :headers headers
               :body (json/encode (:body request))})
             (p/then ->result)))))))

(defn error-message [r]
  (let [{:keys [message details]} (result/detail r)]
    (cond
      (map? details)
      (->> details
           (map (fn [[k v]]
                  (str (name k) " " (str/join ", " (if (sequential? v) v [v])))))
           (str/join "; "))

      (string? (result/detail r)) (result/detail r)
      (some? message) message
      :else (some-> (result/kind r) name))))

(defn health [client]
  (call client :health))

(defn register [client registration]
  (call client :auth/register {:body registration}))

(defn login [client credentials]
  (call client :auth/login {:body credentials}))

(defn me [client]
  (call client :auth/me))

(defn list-todos [client]
  (call client :todo/list))

(defn create-todo [client new-todo]
  (call client :todo/create {:body new-todo}))

(defn update-todo [client id patch]
  (call client :todo/update {:params {:id id} :body patch}))

(defn delete-todo [client id]
  (call client :todo/delete {:params {:id id}}))
