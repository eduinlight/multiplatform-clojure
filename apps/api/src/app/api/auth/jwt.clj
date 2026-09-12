(ns app.api.auth.jwt
  (:require [buddy.sign.jwt :as jwt]
            [integrant.core :as ig]))

(defmethod ig/init-key :app.api.auth.jwt/signer
  [_ {:keys [secret ttl-seconds]}]
  {:secret secret :ttl-seconds ttl-seconds})

(defn sign [{:keys [secret ttl-seconds]} user-id]
  (let [now (quot (System/currentTimeMillis) 1000)]
    (jwt/sign {:sub user-id :iat now :exp (+ now ttl-seconds)} secret)))

(defn unsign [{:keys [secret]} token]
  (try
    (jwt/unsign token secret)
    (catch Exception _ nil)))
