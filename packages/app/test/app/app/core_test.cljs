(ns app.app.core-test
  (:require [app.app.core :as app]
            [app.app.events :as events]
            [app.app.subs :as subs]
            [app.app.test-support :as ts]
            [clojure.test :refer [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

(defn- sub [id]
  @(rf/subscribe [id]))

(defn- screen-is [screen]
  (ts/wait-for #(= screen (sub ::subs/screen))))

(defn- fill-auth-form [fields]
  (doseq [[field value] fields]
    (rf/dispatch-sync [::events/set-auth-field field value])))

(defn- start [{:keys [token]}]
  (let [api (ts/fake-api)
        storage (ts/memory-storage (if token {events/token-key token} {}))]
    {:api api
     :storage storage
     :started (app/start! {:base-url "http://api.test" :transport (:transport api) :storage storage})}))

(deftest ^:async shows-loading-then-the-login-screen-without-a-stored-token
  (let [{:keys [api started]} (start {})]
    (is (= :loading (sub ::subs/screen)))
    (is (= "Loading…" (:label (sub ::subs/loading-screen))))
    (await started)
    (await (screen-is :auth))
    (let [{:keys [title fields submit-label switch-label error submitting?]} (sub ::subs/auth-screen)]
      (is (= "Welcome back" title))
      (is (= [:email :password] (mapv :id fields)))
      (is (= ["Email" "Password"] (mapv :label fields)))
      (is (= [:email :password] (mapv :kind fields)))
      (is (= "Sign in" submit-label))
      (is (= "I need an account" switch-label))
      (is (nil? error))
      (is (false? submitting?)))
    (is (empty? (ts/requests api)))))

(deftest ^:async the-auth-screen-follows-the-form-mode
  (let [{:keys [started]} (start {})]
    (await started)
    (await (screen-is :auth))
    (rf/dispatch-sync [::events/toggle-auth-mode])
    (let [{:keys [title fields submit-label switch-label]} (sub ::subs/auth-screen)]
      (is (= "Create account" title))
      (is (= [:name :email :password] (mapv :id fields)))
      (is (= "Ada Lovelace" (:placeholder (first fields))))
      (is (= "Sign up" submit-label))
      (is (= "I already have an account" switch-label)))
    (fill-auth-form {:email "typed@example.com"})
    (is (= "typed@example.com" (:value (second (:fields (sub ::subs/auth-screen))))))
    (rf/dispatch-sync [::events/toggle-auth-mode])
    (is (= "Welcome back" (:title (sub ::subs/auth-screen))))
    (is (= "typed@example.com" (:value (first (:fields (sub ::subs/auth-screen))))))))

(deftest ^:async invalid-input-is-reported-without-calling-the-api
  (let [{:keys [api started]} (start {})]
    (await started)
    (await (screen-is :auth))
    (fill-auth-form {:email "nope" :password "short"})
    (rf/dispatch-sync [::events/submit-auth])
    (await (ts/wait-for #(some? (:error (sub ::subs/auth-screen)))))
    (is (= "email must be a valid email; password must be at least 8 characters"
           (:error (sub ::subs/auth-screen))))
    (is (false? (:submitting? (sub ::subs/auth-screen))))
    (is (empty? (ts/requests api)))
    (rf/dispatch-sync [::events/toggle-auth-mode])
    (is (nil? (:error (sub ::subs/auth-screen))))))

(deftest ^:async logging-in-opens-the-todos-screen-and-remembers-the-session
  (let [{:keys [api storage started]} (start {})]
    (await started)
    (await (screen-is :auth))
    (fill-auth-form {:email "ada@example.com" :password "password123"})
    (rf/dispatch-sync [::events/submit-auth])
    (testing "a second submit while the first is in flight is ignored"
      (is (true? (:submitting? (sub ::subs/auth-screen))))
      (is (= "Working…" (:submit-label (sub ::subs/auth-screen))))
      (rf/dispatch-sync [::events/submit-auth]))
    (await (screen-is :todos))
    (await (ts/wait-for #(= :empty (:list-state (sub ::subs/todos-screen)))))
    (is (= 1 (count (filter #(re-find #"/auth/login$" (:url %)) (ts/requests api)))))
    (is (= "token-ada" (get @(:data storage) events/token-key)))
    (is (= "" (get-in @rf-db/app-db [:auth-form :password])))
    (let [{:keys [user-name initials summary sign-out-label empty-label items]} (sub ::subs/todos-screen)]
      (is (= "Ada Lovelace" user-name))
      (is (= "AL" initials))
      (is (= "0 of 0 tasks done" summary))
      (is (= "Sign out" sign-out-label))
      (is (= "Nothing here yet." empty-label))
      (is (= [] items)))))

(deftest ^:async managing-todos-updates-the-view-model
  (let [{:keys [api started]} (start {:token "token-ada"})]
    (await started)
    (await (screen-is :todos))
    (await (ts/wait-for #(= :empty (:list-state (sub ::subs/todos-screen)))))

    (testing "blank drafts are not sent"
      (let [before (count (ts/requests api))]
        (rf/dispatch-sync [::events/set-draft "   "])
        (rf/dispatch-sync [::events/create-todo])
        (is (= before (count (ts/requests api))))))

    (rf/dispatch-sync [::events/set-draft "Write tests"])
    (rf/dispatch-sync [::events/create-todo])
    (is (= "" (:draft (sub ::subs/todos-screen))))
    (await (ts/wait-for #(= 1 (count (:items (sub ::subs/todos-screen))))))
    (let [{:keys [id title done? delete-label]} (first (:items (sub ::subs/todos-screen)))]
      (is (= "Write tests" title))
      (is (false? done?))
      (is (= "Delete Write tests" delete-label))
      (is (= :items (:list-state (sub ::subs/todos-screen))))

      (rf/dispatch-sync [::events/toggle-todo id])
      (await (ts/wait-for #(:done? (first (:items (sub ::subs/todos-screen))))))
      (is (= "1 of 1 task done" (:summary (sub ::subs/todos-screen))))

      (rf/dispatch-sync [::events/delete-todo id])
      (await (ts/wait-for #(= :empty (:list-state (sub ::subs/todos-screen)))))
      (is (= "0 of 0 tasks done" (:summary (sub ::subs/todos-screen)))))))

(deftest ^:async a-stored-token-restores-the-session
  (let [{:keys [started]} (start {:token "token-ada"})]
    (await started)
    (await (screen-is :todos))
    (is (= "Ada Lovelace" (:user-name (sub ::subs/todos-screen))))))

(deftest ^:async a-rejected-token-signs-out-and-forgets-it
  (let [{:keys [storage started]} (start {:token "expired"})]
    (await started)
    (await (screen-is :auth))
    (is (nil? (get @(:data storage) events/token-key)))))

(deftest ^:async server-errors-surface-on-the-auth-screen
  (let [{:keys [started]} (start {})]
    (await started)
    (await (screen-is :auth))
    (rf/dispatch-sync [::events/toggle-auth-mode])
    (fill-auth-form {:name "Ada" :email "ada@example.com" :password "password123"})
    (rf/dispatch-sync [::events/submit-auth])
    (await (ts/wait-for #(some? (:error (sub ::subs/auth-screen)))))
    (is (= "email already registered" (:error (sub ::subs/auth-screen))))
    (is (= :auth (sub ::subs/screen)))))

(deftest ^:async signing-out-returns-to-login
  (let [{:keys [storage started]} (start {:token "token-ada"})]
    (await started)
    (await (screen-is :todos))
    (rf/dispatch-sync [::events/logout])
    (is (= :auth (sub ::subs/screen)))
    (is (nil? (get @(:data storage) events/token-key)))
    (is (= "Welcome back" (:title (sub ::subs/auth-screen))))))
