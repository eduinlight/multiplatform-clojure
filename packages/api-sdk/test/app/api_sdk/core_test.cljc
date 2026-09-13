(ns app.api-sdk.core-test
  (:require [app.api-sdk.core :as sdk]
            [app.api-sdk.json :as json]
            [app.api-sdk.promise :as p]
            [app.api-sdk.test-support :refer [fake-client resolve-all]]
            [app.shared.result :as result]
            [clojure.test :refer [deftest is]]))

(def todo-id "6aa50aeb07c18c4e72782caf")

(deftest builds-requests-from-the-shared-route-table
  (let [{:keys [client calls]} (fake-client {:status 200 :body {:todo/id todo-id :todo/done true}} "t0k3n")]
    (resolve-all
     [(sdk/update-todo client todo-id {:done true})]
     (fn [[r]]
       (let [request (first @calls)]
         (is (result/ok? r))
         (is (= {:todo/id todo-id :todo/done true} (result/value r)))
         (is (= :patch (:method request)))
         (is (= (str "http://api.test/api/v1/todos/" todo-id) (:url request)))
         (is (= "Bearer t0k3n" (get-in request [:headers "Authorization"])))
         (is (= "application/json" (get-in request [:headers "Content-Type"])))
         (is (re-find #"\"done\"\s*:\s*true" (:body request))))))))

(deftest public-endpoints-send-no-authorization
  (let [{:keys [client calls]} (fake-client {:status 200 :body {:status "ok"}})]
    (resolve-all
     [(sdk/health client)]
     (fn [[r]]
       (is (= {:status "ok"} (result/value r)))
       (is (nil? (get-in (first @calls) [:headers "Authorization"])))
       (is (nil? (:body (first @calls))))))))

(deftest rejects-invalid-input-without-a-round-trip
  (let [{:keys [client calls]} (fake-client {:status 200 :body {}} "t0k3n")]
    (resolve-all
     [(sdk/login client {:email "nope" :password "short"})
      (sdk/create-todo client {:title ""})
      (sdk/delete-todo client "not-an-id")
      (sdk/call client :does/not-exist)]
     (fn [[login create delete unknown]]
       (is (= :invalid (result/kind login)))
       (is (contains? (:details (result/detail login)) :email))
       (is (= "email must be a valid email; password must be at least 8 characters"
              (sdk/error-message login)))
       (is (= :invalid (result/kind create)))
       (is (= :invalid (result/kind delete)))
       (is (= "id must be a 24 character hex id" (sdk/error-message delete)))
       (is (= :invalid (result/kind unknown)))
       (is (empty? @calls))))))

(deftest protected-endpoints-require-a-token
  (let [{:keys [client calls]} (fake-client {:status 200 :body []})]
    (resolve-all
     [(sdk/list-todos client)
      (sdk/list-todos (sdk/with-token client "later"))]
     (fn [[without with]]
       (is (= :unauthorized (result/kind without)))
       (is (result/ok? with))
       (is (= 1 (count @calls)))))))

(deftest maps-http-failures-to-result-kinds
  (let [response (fn [status body] (:client (fake-client {:status status :body body} "t")))]
    (resolve-all
     [(sdk/me (response 401 {:error "authentication required"}))
      (sdk/update-todo (response 404 {:error "todo not found"}) todo-id {:done false})
      (sdk/register (response 409 {:error "email already registered"})
                    {:email "a@b.co" :password "password1" :name "A"})
      (sdk/create-todo (response 400 {:error "validation failed" :details {:title ["must not be blank"]}})
                       {:title "x"})
      (sdk/health (response 503 nil))
      (sdk/health (response 418 nil))]
     (fn [[unauthorized not-found conflict invalid unavailable teapot]]
       (is (= :unauthorized (result/kind unauthorized)))
       (is (= "authentication required" (sdk/error-message unauthorized)))
       (is (= :not-found (result/kind not-found)))
       (is (= 404 (:status (result/detail not-found))))
       (is (= :conflict (result/kind conflict)))
       (is (= "title must not be blank" (sdk/error-message invalid)))
       (is (= :internal (result/kind unavailable)))
       (is (= "request failed with status 503" (sdk/error-message unavailable)))
       (is (= :unexpected (result/kind teapot)))))))

(deftest network-failures-resolve-instead-of-throwing
  (let [client (sdk/client {:base-url "http://api.test"
                            :transport (fn [_] (p/resolved {:status 0 :error "connection refused"}))})]
    (resolve-all
     [(sdk/health client)]
     (fn [[r]]
       (is (= :network (result/kind r)))
       (is (= "connection refused" (sdk/error-message r)))))))

(deftest trailing-slashes-on-the-base-url-are-ignored
  (is (= "http://x" (:base-url (sdk/client {:base-url "http://x///"}))))
  (is (= "" (:base-url (sdk/client {:base-url "/"})))))

(deftest json-keeps-keyword-namespaces-on-every-platform
  (let [data {:todo/id todo-id :done true :tags ["a"]}]
    (is (= data (json/decode (json/encode data))))
    (is (re-find #"\"todo/id\"" (json/encode data)))
    (is (nil? (json/decode "")))
    (is (nil? (json/decode "not json")))))
