(ns app.app.subs
  (:require [app.app.copy :as copy]
            [app.shared.format :as fmt]
            [re-frame.core :as rf]))

(rf/reg-sub ::auth (fn [db _] (:auth db)))
(rf/reg-sub ::auth-form (fn [db _] (:auth-form db)))
(rf/reg-sub ::todos-root (fn [db _] (:todos db)))

(rf/reg-sub ::current-user :<- [::auth] (fn [auth _] (:user auth)))
(rf/reg-sub ::todos :<- [::todos-root] (fn [t _] (:items t)))

(rf/reg-sub
 ::screen
 :<- [::auth]
 (fn [{:keys [status]} _]
   (case status
     (:starting :restoring) :loading
     :authenticated :todos
     :auth)))

(rf/reg-sub
 ::loading-screen
 (fn [_ _]
   {:label (copy/t :app/loading)}))

(defn- field [form id kind]
  {:id id
   :kind kind
   :label (copy/t (keyword "field" (name id)))
   :placeholder (copy/t (keyword "field" (str (name id) "-placeholder")))
   :value (get form id "")})

(rf/reg-sub
 ::auth-screen
 :<- [::auth]
 :<- [::auth-form]
 (fn [[{:keys [status error]} {:keys [mode] :as form}] _]
   (let [register? (= :register mode)
         submitting? (= :pending status)]
     {:mode mode
      :title (copy/t (if register? :auth/register-title :auth/login-title))
      :fields (cond-> []
                register? (conj (field form :name :text))
                true (conj (field form :email :email)
                           (field form :password :password)))
      :error error
      :submitting? submitting?
      :submit-label (copy/t (cond
                              submitting? :auth/submitting
                              register? :auth/register-submit
                              :else :auth/login-submit))
      :switch-label (copy/t (if register? :auth/to-login :auth/to-register))})))

(rf/reg-sub
 ::todos-screen
 :<- [::current-user]
 :<- [::todos-root]
 (fn [[user {:keys [status items draft error]}] _]
   (let [user-name (:user/name user)]
     {:user-name user-name
      :initials (fmt/initials user-name)
      :summary (:label (fmt/summarize items))
      :sign-out-label (copy/t :todos/sign-out)
      :draft draft
      :draft-placeholder (copy/t :todos/draft-placeholder)
      :add-label (copy/t :todos/add)
      :error error
      :list-state (cond
                    (seq items) :items
                    (= :loading status) :loading
                    :else :empty)
      :loading-label (copy/t :app/loading)
      :empty-label (copy/t :todos/empty)
      :items (mapv (fn [{:todo/keys [id title done]}]
                     {:id id
                      :title title
                      :done? (boolean done)
                      :delete-label (str (copy/t :todos/delete) " " title)})
                   items)})))
