(ns app.app.test-support
  (:require [app.api-sdk.json :as json]
            [clojure.string :as str]))

(defn- respond [status body]
  (js/Promise.resolve {:status status :body (json/encode body)}))

(defn- next-id [state]
  (let [n (:next-id (swap! state update :next-id inc))]
    (.padStart (str n) 24 "0")))

(defn- route [state {:keys [method url headers body]}]
  (let [path (second (str/split url #"/api/v1" 2))
        data (json/decode body)
        token (some->> (get headers "Authorization") (re-find #"^Bearer (.+)$") second)
        user (get-in @state [:sessions token])]
    (cond
      (and (= :post method) (= "/auth/login" path))
      (if-let [{:keys [password token user]} (get-in @state [:accounts (:email data)])]
        (if (= password (:password data))
          (respond 200 {:token token :user user})
          (respond 401 {:error "invalid email or password"}))
        (respond 401 {:error "invalid email or password"}))

      (and (= :post method) (= "/auth/register" path))
      (if (get-in @state [:accounts (:email data)])
        (respond 409 {:error "email already registered"})
        (let [token (str "token-" (next-id state))
              user {:user/id (next-id state) :user/email (:email data) :user/name (:name data)}]
          (swap! state #(-> %
                            (assoc-in [:accounts (:email data)] {:password (:password data) :token token :user user})
                            (assoc-in [:sessions token] user)))
          (respond 201 {:token token :user user})))

      (nil? user)
      (respond 401 {:error "authentication required"})

      (= "/auth/me" path)
      (respond 200 user)

      (and (= :get method) (= "/todos" path))
      (respond 200 (vec (reverse (:todos @state))))

      (and (= :post method) (= "/todos" path))
      (let [todo {:todo/id (next-id state) :todo/title (:title data) :todo/done false :todo/owner-id (:user/id user)}]
        (swap! state update :todos conj todo)
        (respond 200 todo))

      :else
      (let [[_ id] (re-find #"^/todos/(.+)$" path)
            exists? (some #(= id (:todo/id %)) (:todos @state))]
        (cond
          (not exists?) (respond 404 {:error "todo not found"})
          (= :patch method) (do (swap! state update :todos
                                       (partial mapv #(if (= id (:todo/id %))
                                                        (cond-> %
                                                          (contains? data :done) (assoc :todo/done (:done data))
                                                          (contains? data :title) (assoc :todo/title (:title data)))
                                                        %)))
                                (respond 200 (some #(when (= id (:todo/id %)) %) (:todos @state))))
          (= :delete method) (do (swap! state update :todos (partial filterv #(not= id (:todo/id %))))
                                 (respond 200 {:deleted true})))))))

(defn fake-api []
  (let [ada {:user/id "000000000000000000000abc" :user/email "ada@example.com" :user/name "Ada Lovelace"}
        state (atom {:next-id 0
                     :accounts {"ada@example.com" {:password "password123" :token "token-ada" :user ada}}
                     :sessions {"token-ada" ada}
                     :todos []
                     :requests []})]
    {:state state
     :transport (fn [request]
                  (swap! state update :requests conj (select-keys request [:method :url]))
                  (route state request))}))

(defn requests [api]
  (:requests @(:state api)))

(defn memory-storage
  ([] (memory-storage {}))
  ([initial]
   (let [data (atom initial)]
     {:data data
      :get (fn [k] (js/Promise.resolve (get @data k)))
      :set (fn [k v] (swap! data assoc k v))
      :del (fn [k] (swap! data dissoc k))})))

(defn wait-for
  ([pred] (wait-for pred 2000))
  ([pred timeout-ms]
   (let [started (js/Date.now)]
     (js/Promise.
      (fn [resolve reject]
        (letfn [(check []
                  (cond
                    (pred) (resolve true)
                    (< timeout-ms (- (js/Date.now) started)) (reject (js/Error. "timed out waiting for condition"))
                    :else (js/setTimeout check 5)))]
          (check)))))))
