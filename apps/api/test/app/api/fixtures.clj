(ns app.api.fixtures
  (:require [app.api.config :as config]
            [app.api.db :as db]
            [app.api.router]
            [app.api.server]
            [app.api.auth.jwt]
            [integrant.core :as ig]
            [muuntaja.core :as mc]))

(def ^:dynamic *system* nil)
(def ^:dynamic *handler* nil)
(def ^:dynamic *conn* nil)
(def ^:dynamic *base-url* nil)

(defn- test-config []
  (-> (config/read-config :test)
      (assoc-in [:app.api.db/client :database]
                (str "app_test_" (System/currentTimeMillis) "_" (rand-int 100000)))))

(defn- run-system [config f]
  (let [system (ig/init config)]
    (try
      (db/ensure-indexes! (:app.api.db/client system))
      (binding [*system* system
                *handler* (:app.api.router/routes system)
                *conn* (:app.api.db/client system)
                *base-url* (some-> (:app.api.server/jetty system)
                                   (.getConnectors)
                                   (first)
                                   (.getLocalPort)
                                   (->> (str "http://127.0.0.1:")))]
        (f))
      (finally
        (let [{:keys [db]} (:app.api.db/client system)]
          (.drop db)
          (ig/halt! system))))))

(defn with-system [f]
  (run-system (dissoc (test-config) :app.api.server/jetty) f))

(defn with-server [f]
  (run-system (-> (test-config)
                  (assoc-in [:app.api.server/jetty :port] 0)
                  (assoc-in [:app.api.server/jetty :host] "127.0.0.1"))
              f))

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
