(ns app.api.sdk-contract-test
  (:require [app.api-sdk.core :as sdk]
            [app.api.fixtures :as fix]
            [app.shared.result :as result]
            [app.shared.schema :as schema]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once fix/with-server)

(defn- await! [p]
  (deref p 10000 ::timeout))

(defn- fresh-client []
  (sdk/client {:base-url fix/*base-url*}))

(deftest sdk-drives-the-real-api
  (let [anonymous (fresh-client)]

    (testing "health"
      (is (= "ok" (:status (result/value (await! (sdk/health anonymous)))))))

    (testing "register returns a session that matches the shared schema"
      (let [r (await! (sdk/register anonymous {:email "sdk@example.com"
                                               :password "password123"
                                               :name "Sdk User"}))]
        (is (result/ok? r))
        (is (string? (:token (result/value r))))
        (is (schema/valid? schema/Id (get-in (result/value r) [:user :user/id])))))

    (testing "server-side failures come back as result kinds"
      (let [r (await! (sdk/register anonymous {:email "sdk@example.com"
                                               :password "password123"
                                               :name "Sdk User"}))]
        (is (= :conflict (result/kind r)))
        (is (= 409 (:status (result/detail r))))
        (is (= "email already registered" (sdk/error-message r))))
      (is (= :unauthorized (result/kind (await! (sdk/login anonymous {:email "sdk@example.com"
                                                                      :password "wrong-password"})))))
      (is (= :unauthorized (result/kind (await! (sdk/me (sdk/with-token anonymous "forged.jwt.token")))))))

    (let [token (:token (result/value (await! (sdk/login anonymous {:email "SDK@example.com"
                                                                    :password "password123"}))))
          authed (sdk/with-token anonymous token)]

      (testing "login and me"
        (is (string? token))
        (is (= "sdk@example.com" (:user/email (result/value (await! (sdk/me authed)))))))

      (testing "todo lifecycle"
        (let [created (result/value (await! (sdk/create-todo authed {:title "via the sdk"})))
              id (:todo/id created)]
          (is (= "via the sdk" (:todo/title created)))
          (is (false? (:todo/done created)))
          (is (= [id] (map :todo/id (result/value (await! (sdk/list-todos authed))))))
          (is (true? (:todo/done (result/value (await! (sdk/update-todo authed id {:done true}))))))
          (is (= "renamed" (:todo/title (result/value (await! (sdk/update-todo authed id {:title "renamed"}))))))
          (is (= {:deleted true} (result/value (await! (sdk/delete-todo authed id)))))
          (is (= :not-found (result/kind (await! (sdk/delete-todo authed id)))))
          (is (= [] (result/value (await! (sdk/list-todos authed)))))))

      (testing "ownership is enforced through the sdk too"
        (let [id (:todo/id (result/value (await! (sdk/create-todo authed {:title "mine"}))))
              intruder (->> (await! (sdk/register anonymous {:email "intruder@sdk.example.com"
                                                             :password "password123"
                                                             :name "Intruder"}))
                            (result/value)
                            (:token)
                            (sdk/with-token anonymous))]
          (is (= [] (result/value (await! (sdk/list-todos intruder)))))
          (is (= :not-found (result/kind (await! (sdk/update-todo intruder id {:done true})))))
          (is (= :not-found (result/kind (await! (sdk/delete-todo intruder id))))))))))

(deftest unreachable-servers-resolve-as-network-failures
  (let [r (await! (sdk/health (sdk/client {:base-url "http://127.0.0.1:1" :timeout-ms 2000})))]
    (is (= :network (result/kind r)))
    (is (string? (sdk/error-message r)))))
