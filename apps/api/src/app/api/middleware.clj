(ns app.api.middleware
  (:require [app.api.auth.jwt :as jwt]
            [app.api.users :as users]
            [taoensso.timbre :as log]))

(defn wrap-cors [handler]
  (fn [request]
    (let [origin (get-in request [:headers "origin"] "*")
          cors {"Access-Control-Allow-Origin" origin
                "Access-Control-Allow-Methods" "GET, POST, PATCH, PUT, DELETE, OPTIONS"
                "Access-Control-Allow-Headers" "Content-Type, Authorization"
                "Access-Control-Allow-Credentials" "true"
                "Access-Control-Max-Age" "86400"}]
      (if (= :options (:request-method request))
        {:status 204 :headers cors :body nil}
        (update (handler request) :headers merge cors)))))

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "unhandled exception" {:uri (:uri request)})
        {:status 500 :body {:error "internal server error"}}))))

(defn- bearer-token [request]
  (some-> (get-in request [:headers "authorization"])
          (->> (re-find #"^Bearer (.+)$"))
          (second)))

(defn wrap-identity [{:keys [db signer]}]
  (fn [handler]
    (fn [request]
      (let [claims (some->> (bearer-token request) (jwt/unsign signer))
            user (some->> (:sub claims) (users/by-id db))]
        (handler (assoc request :identity user :db db :signer signer))))))

(defn wrap-authenticated [handler]
  (fn [request]
    (if (:identity request)
      (handler request)
      {:status 401 :body {:error "authentication required"}})))
