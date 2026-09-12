(ns app.api.shared-test
  (:require [app.shared.format :as fmt]
            [app.shared.result :as result]
            [app.shared.routes :as routes]
            [app.shared.schema :as schema]
            [clojure.test :refer [deftest is testing]]))

(deftest schema-validation
  (testing "credentials"
    (is (schema/valid? schema/Credentials {:email "a@b.co" :password "longenough"}))
    (is (not (schema/valid? schema/Credentials {:email "nope" :password "longenough"})))
    (is (not (schema/valid? schema/Credentials {:email "a@b.co" :password "short"}))))

  (testing "registration reports humanized errors"
    (let [errors (schema/explain schema/Registration {:email "x" :password "y" :name ""})]
      (is (contains? errors :email))
      (is (contains? errors :password))
      (is (contains? errors :name))))

  (testing "ids must be object-id shaped"
    (is (schema/valid? schema/Id "6aa50aeb07c18c4e72782caf"))
    (is (not (schema/valid? schema/Id "abc")))))

(deftest route-building
  (is (= "/todos" (routes/path-for :todo/list)))
  (is (= "/todos/123" (routes/path-for :todo/update {:id 123})))
  (is (= :patch (routes/method-for :todo/update)))
  (is (= "http://x/api/v1/todos/9" (routes/url-for "http://x" :todo/delete {:id 9}))))

(deftest result-helpers
  (is (result/ok? (result/ok 1)))
  (is (= 1 (result/value (result/ok 1))))
  (is (= 404 (result/status (result/err :not-found))))
  (is (= 500 (result/status (result/err :something-unmapped))))
  (is (= 200 (result/status (result/ok)))))

(deftest formatting
  (is (= "AL" (fmt/initials "Ada Lovelace")))
  (is (= "A" (fmt/initials "Ada")))
  (is (= "" (fmt/initials nil)))
  (is (= "2 tasks" (fmt/pluralize 2 "task" "tasks")))
  (is (= "1 task" (fmt/pluralize 1 "task" "tasks")))
  (is (= {:total 2 :done 1 :pending 1 :label "1 of 2 tasks done"}
         (fmt/summarize [{:todo/done true} {:todo/done false}]))))
