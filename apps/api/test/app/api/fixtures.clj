(ns app.api.fixtures
  (:require [app.api.config :as config]
            [app.api.db :as db]
            [app.api.router]
            [app.api.auth.jwt]
            [integrant.core :as ig]
            [muuntaja.core :as mc]))

(def ^:dynamic *system* nil)
(def ^:dynamic *handler* nil)
(def ^:dynamic *conn* nil)

(defn- test-config []
  (-> (config/read-config :test)
      (dissoc :app.api.server/jetty)
      (assoc-in [:app.api.db/client :database]
                (str "app_test_" (System/currentTimeMillis)))))

(defn with-system [f]
  (let [system (ig/init (test-config))]
    (try
      (db/ensure-indexes! (:app.api.db/client system))
      (binding [*system* system
                *handler* (:app.api.router/routes system)
                *conn* (:app.api.db/client system)]
        (f))
      (finally
        (let [{:keys [db]} (:app.api.db/client system)]
          (.drop db)
          (ig/halt! system))))))

(defn- decode-body [response]
  (update response :body
          (fn [body]
            (if (instance? java.io.InputStream body)
              (try
                (mc/decode mc/instance "application/json" body)
                (catch Exception _ nil))
              body))))

(defn request
  ([method uri] (request method uri nil nil))
  ([method uri body] (request method uri body nil))
  ([method uri body token]
   (-> {:request-method method
        :uri uri
        :headers (cond-> {"accept" "application/json"}
                   token (assoc "authorization" (str "Bearer " token))
                   (some? body) (assoc "content-type" "application/json"))}
       (cond-> (some? body)
         (assoc :body (mc/encode mc/instance "application/json" body)))
       (*handler*)
       (decode-body))))
