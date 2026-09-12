(ns app.api.api-test
  (:require [app.api.fixtures :as fix :refer [request]]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once fix/with-system)

(def ^:private base "/api/v1")

(defn- register! [email]
  (request :post (str base "/auth/register")
           {:email email :password "password123" :name "Test User"}))

(deftest health-endpoint
  (let [{:keys [status body]} (request :get (str base "/health"))]
    (is (= 200 status))
    (is (= "ok" (:status body)))))

(deftest registration-and-login
  (testing "registering returns a token and user"
    (let [{:keys [status body]} (register! "flow@example.com")]
      (is (= 201 status))
      (is (string? (:token body)))
      (is (= "flow@example.com" (get-in body [:user :user/email])))))

  (testing "duplicate email is rejected"
    (is (= 409 (:status (register! "flow@example.com")))))

  (testing "email is normalized to lowercase"
    (is (= 409 (:status (register! "FLOW@EXAMPLE.COM")))))

  (testing "invalid payload is rejected"
    (is (= 400 (:status (request :post (str base "/auth/register")
                                 {:email "bad" :password "x" :name ""})))))

  (testing "login works with correct credentials"
    (let [{:keys [status body]} (request :post (str base "/auth/login")
                                         {:email "flow@example.com" :password "password123"})]
      (is (= 200 status))
      (is (string? (:token body)))))

  (testing "login fails with wrong password"
    (is (= 401 (:status (request :post (str base "/auth/login")
                                 {:email "flow@example.com" :password "wrongpassword"}))))))

(deftest protected-routes-require-a-token
  (is (= 401 (:status (request :get (str base "/auth/me")))))
  (is (= 401 (:status (request :get (str base "/todos")))))
  (is (= 401 (:status (request :get (str base "/todos") nil "not-a-jwt")))))

(deftest todo-lifecycle
  (let [token (get-in (register! "todos@example.com") [:body :token])
        created (request :post (str base "/todos") {:title "first"} token)
        id (get-in created [:body :todo/id])]

    (testing "create"
      (is (= 200 (:status created)))
      (is (= "first" (get-in created [:body :todo/title])))
      (is (false? (get-in created [:body :todo/done]))))

    (testing "list"
      (let [{:keys [status body]} (request :get (str base "/todos") nil token)]
        (is (= 200 status))
        (is (= 1 (count body)))))

    (testing "toggle done"
      (let [{:keys [status body]} (request :patch (str base "/todos/" id) {:done true} token)]
        (is (= 200 status))
        (is (true? (:todo/done body)))))

    (testing "rename"
      (is (= "renamed" (get-in (request :patch (str base "/todos/" id) {:title "renamed"} token)
                               [:body :todo/title]))))

    (testing "invalid patch is rejected"
      (is (= 400 (:status (request :patch (str base "/todos/" id) {:title ""} token)))))

    (testing "another user cannot see or touch it"
      (let [other (get-in (register! "intruder@example.com") [:body :token])]
        (is (= [] (:body (request :get (str base "/todos") nil other))))
        (is (= 404 (:status (request :patch (str base "/todos/" id) {:done false} other))))
        (is (= 404 (:status (request :delete (str base "/todos/" id) nil other))))))

    (testing "delete"
      (is (= 200 (:status (request :delete (str base "/todos/" id) nil token))))
      (is (= 404 (:status (request :delete (str base "/todos/" id) nil token))))
      (is (= [] (:body (request :get (str base "/todos") nil token)))))))
