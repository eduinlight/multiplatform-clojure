(ns app.ui.subs
  (:require [app.shared.format :as fmt]
            [re-frame.core :as rf]))

(rf/reg-sub ::route (fn [db _] (:route db)))
(rf/reg-sub ::auth (fn [db _] (:auth db)))
(rf/reg-sub ::todos-root (fn [db _] (:todos db)))

(rf/reg-sub ::authenticated? :<- [::auth] (fn [auth _] (= :authenticated (:status auth))))
(rf/reg-sub ::auth-pending? :<- [::auth] (fn [auth _] (= :pending (:status auth))))
(rf/reg-sub ::auth-error :<- [::auth] (fn [auth _] (:error auth)))
(rf/reg-sub ::current-user :<- [::auth] (fn [auth _] (:user auth)))

(rf/reg-sub ::user-initials :<- [::current-user]
            (fn [user _] (fmt/initials (:user/name user))))

(rf/reg-sub ::todos :<- [::todos-root] (fn [t _] (:items t)))
(rf/reg-sub ::todos-loading? :<- [::todos-root] (fn [t _] (= :loading (:status t))))
(rf/reg-sub ::todos-error :<- [::todos-root] (fn [t _] (:error t)))
(rf/reg-sub ::draft :<- [::todos-root] (fn [t _] (:draft t)))

(rf/reg-sub ::summary :<- [::todos] (fn [todos _] (fmt/summarize todos)))

(rf/reg-sub ::pending-todos :<- [::todos]
            (fn [todos _] (remove :todo/done todos)))

(rf/reg-sub ::completed-todos :<- [::todos]
            (fn [todos _] (filter :todo/done todos)))
