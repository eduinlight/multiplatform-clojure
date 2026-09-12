(ns app.ui.events
  (:require [app.client.http :as http]
            [app.shared.schema :as schema]
            [app.ui.config :as config]
            [app.ui.db :as db]
            [app.ui.storage]
            [re-frame.core :as rf]))

(def token-key "app.auth.token")

(rf/reg-fx
 :http/request
 (fn [opts]
   (http/request (assoc opts :base-url (config/base-url)))))

(rf/reg-event-fx
 ::boot
 [(rf/inject-cofx :storage/get {:key token-key})]
 (fn [{:keys [storage/value]} _]
   (if value
     {:db (-> db/default-db
              (assoc-in [:auth :token] value)
              (assoc-in [:auth :status] :restoring))
      :fx [[:dispatch [::fetch-me value]]]}
     {:db db/default-db})))

(rf/reg-event-fx
 ::fetch-me
 (fn [_ [_ token]]
   {:http/request {:endpoint :auth/me
                   :token token
                   :on-success #(rf/dispatch [::me-success %])
                   :on-failure #(rf/dispatch [::logout])}}))

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

(rf/reg-event-fx
 ::login
 (fn [{:keys [db]} [_ credentials]]
   (if-let [errors (schema/explain schema/Credentials credentials)]
     {:db (assoc-in db [:auth :error] (str "Invalid input: " (pr-str errors)))}
     {:db (-> db
              (assoc-in [:auth :status] :pending)
              (assoc-in [:auth :error] nil))
      :http/request {:endpoint :auth/login
                     :body credentials
                     :on-success #(rf/dispatch [::auth-success %])
                     :on-failure #(rf/dispatch [::auth-failure %])}})))

(rf/reg-event-fx
 ::register
 (fn [{:keys [db]} [_ registration]]
   (if-let [errors (schema/explain schema/Registration registration)]
     {:db (assoc-in db [:auth :error] (str "Invalid input: " (pr-str errors)))}
     {:db (-> db
              (assoc-in [:auth :status] :pending)
              (assoc-in [:auth :error] nil))
      :http/request {:endpoint :auth/register
                     :body registration
                     :on-success #(rf/dispatch [::auth-success %])
                     :on-failure #(rf/dispatch [::auth-failure %])}})))

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
 (fn [db [_ {:keys [message]}]]
   (-> db
       (assoc-in [:auth :status] :anonymous)
       (assoc-in [:auth :error] message))))

(rf/reg-event-fx
 ::logout
 (fn [_ _]
   {:db db/default-db
    :storage/remove {:key token-key}}))

(rf/reg-event-fx
 ::load-todos
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:todos :status] :loading)
    :http/request {:endpoint :todo/list
                   :token (get-in db [:auth :token])
                   :on-success #(rf/dispatch [::todos-loaded %])
                   :on-failure #(rf/dispatch [::todos-failed %])}}))

(rf/reg-event-db
 ::todos-loaded
 (fn [db [_ todos]]
   (-> db
       (assoc-in [:todos :status] :ready)
       (assoc-in [:todos :items] (vec todos))
       (assoc-in [:todos :error] nil))))

(rf/reg-event-db
 ::todos-failed
 (fn [db [_ {:keys [message]}]]
   (-> db
       (assoc-in [:todos :status] :error)
       (assoc-in [:todos :error] message))))

(rf/reg-event-db
 ::set-draft
 (fn [db [_ text]]
   (assoc-in db [:todos :draft] text)))

(rf/reg-event-fx
 ::create-todo
 (fn [{:keys [db]} _]
   (let [title (get-in db [:todos :draft])]
     (if (schema/valid? schema/NewTodo {:title title})
       {:db (assoc-in db [:todos :draft] "")
        :http/request {:endpoint :todo/create
                       :token (get-in db [:auth :token])
                       :body {:title title}
                       :on-success #(rf/dispatch [::load-todos])
                       :on-failure #(rf/dispatch [::todos-failed %])}}
       {:db db}))))

(rf/reg-event-fx
 ::toggle-todo
 (fn [{:keys [db]} [_ {:todo/keys [id done]}]]
   {:http/request {:endpoint :todo/update
                   :params {:id id}
                   :token (get-in db [:auth :token])
                   :body {:done (not done)}
                   :on-success #(rf/dispatch [::load-todos])
                   :on-failure #(rf/dispatch [::todos-failed %])}}))

(rf/reg-event-fx
 ::delete-todo
 (fn [{:keys [db]} [_ id]]
   {:http/request {:endpoint :todo/delete
                   :params {:id id}
                   :token (get-in db [:auth :token])
                   :on-success #(rf/dispatch [::load-todos])
                   :on-failure #(rf/dispatch [::todos-failed %])}}))
