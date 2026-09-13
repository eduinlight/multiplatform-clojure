(ns app.ui.events
  (:require [app.api-sdk.core :as sdk]
            [app.ui.api]
            [app.ui.db :as db]
            [app.ui.storage]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(def token-key "app.auth.token")

(rf/reg-event-fx
 ::boot
 [(rf/inject-cofx :storage/get {:key token-key})]
 (fn [{:keys [storage/value]} _]
   (if value
     {:db (-> db/default-db
              (assoc-in [:auth :token] value)
              (assoc-in [:auth :status] :restoring))
      :api/call {:op :auth/me
                 :token value
                 :on-success [::me-success]
                 :on-failure [::logout]}}
     {:db db/default-db})))

(rf/reg-event-fx
 ::me-success
 (fn [{:keys [db]} [_ user]]
   {:db (-> db
            (assoc-in [:auth :status] :authenticated)
            (assoc-in [:auth :user] user)
            (assoc :route :todos))
    :fx [[:dispatch [::load-todos]]]}))

(rf/reg-event-db
 ::navigate
 (fn [db [_ route]]
   (assoc db :route route)))

(defn- authenticate [db op body]
  {:db (-> db
           (assoc-in [:auth :status] :pending)
           (assoc-in [:auth :error] nil))
   :api/call {:op op
              :body body
              :on-success [::auth-success]
              :on-failure [::auth-failure]}})

(rf/reg-event-fx
 ::login
 (fn [{:keys [db]} [_ credentials]]
   (authenticate db :auth/login credentials)))

(rf/reg-event-fx
 ::register
 (fn [{:keys [db]} [_ registration]]
   (authenticate db :auth/register registration)))

(rf/reg-event-fx
 ::auth-success
 (fn [{:keys [db]} [_ {:keys [token user]}]]
   {:db (-> db
            (assoc-in [:auth :status] :authenticated)
            (assoc-in [:auth :token] token)
            (assoc-in [:auth :user] user)
            (assoc-in [:auth :error] nil)
            (assoc :route :todos))
    :storage/set {:key token-key :value token}
    :fx [[:dispatch [::load-todos]]]}))

(rf/reg-event-db
 ::auth-failure
 (fn [db [_ failure]]
   (-> db
       (assoc-in [:auth :status] :anonymous)
       (assoc-in [:auth :error] (sdk/error-message failure)))))

(rf/reg-event-fx
 ::logout
 (fn [_ _]
   {:db db/default-db
    :storage/remove {:key token-key}}))

(defn- todo-call [db op request]
  (merge {:op op
          :token (get-in db [:auth :token])
          :on-success [::load-todos]
          :on-failure [::todos-failed]}
         request))

(rf/reg-event-fx
 ::load-todos
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:todos :status] :loading)
    :api/call (todo-call db :todo/list {:on-success [::todos-loaded]})}))

(rf/reg-event-db
 ::todos-loaded
 (fn [db [_ todos]]
   (-> db
       (assoc-in [:todos :status] :ready)
       (assoc-in [:todos :items] (vec todos))
       (assoc-in [:todos :error] nil))))

(rf/reg-event-db
 ::todos-failed
 (fn [db [_ failure]]
   (-> db
       (assoc-in [:todos :status] :error)
       (assoc-in [:todos :error] (sdk/error-message failure)))))

(rf/reg-event-db
 ::set-draft
 (fn [db [_ text]]
   (assoc-in db [:todos :draft] text)))

(rf/reg-event-fx
 ::create-todo
 (fn [{:keys [db]} _]
   (let [title (get-in db [:todos :draft])]
     (if (str/blank? title)
       {:db db}
       {:db (assoc-in db [:todos :draft] "")
        :api/call (todo-call db :todo/create {:body {:title title}})}))))

(rf/reg-event-fx
 ::toggle-todo
 (fn [{:keys [db]} [_ {:todo/keys [id done]}]]
   {:api/call (todo-call db :todo/update {:params {:id id} :body {:done (not done)}})}))

(rf/reg-event-fx
 ::delete-todo
 (fn [{:keys [db]} [_ id]]
   {:api/call (todo-call db :todo/delete {:params {:id id}})}))
