(ns app.web.views
  (:require [app.app.events :as events]
            [app.app.subs :as subs]
            [re-frame.core :as rf]))

(def ^:private input-type
  {:text "text" :email "email" :password "password"})

(defn- on-submit [event]
  (fn [e]
    (.preventDefault e)
    (rf/dispatch event)))

(defn- field [{:keys [id kind label placeholder value]}]
  [:label.field
   [:span.field-label label]
   [:input.field-input
    {:type (input-type kind)
     :value value
     :placeholder placeholder
     :on-change #(rf/dispatch-sync [::events/set-auth-field id (.. % -target -value)])}]])

(defn loading-screen []
  (let [{:keys [label]} @(rf/subscribe [::subs/loading-screen])]
    [:div.card [:p.muted label]]))

(defn auth-screen []
  (let [{:keys [title fields error submitting? submit-label switch-label]}
        @(rf/subscribe [::subs/auth-screen])]
    [:div.card.auth
     [:h1.title title]
     [:form.form {:on-submit (on-submit [::events/submit-auth])}
      (for [f fields] ^{:key (:id f)} [field f])
      (when error [:p.error error])
      [:button.btn.btn-primary {:type "submit" :disabled submitting?} submit-label]]
     [:button.btn.btn-link {:on-click #(rf/dispatch [::events/toggle-auth-mode])} switch-label]]))

(defn- todo-row [{:keys [id title done? delete-label]}]
  [:li.todo {:class (when done? "todo-done")}
   [:label.todo-main
    [:input {:type "checkbox"
             :checked done?
             :on-change #(rf/dispatch [::events/toggle-todo id])}]
    [:span.todo-title title]]
   [:button.btn.btn-ghost
    {:on-click #(rf/dispatch [::events/delete-todo id])
     :aria-label delete-label}
    "×"]])

(defn todos-screen []
  (let [{:keys [user-name initials summary sign-out-label draft draft-placeholder add-label
                error list-state loading-label empty-label items]}
        @(rf/subscribe [::subs/todos-screen])]
    [:div.card.todos
     [:header.todo-header
      [:div.avatar initials]
      [:div
       [:h1.title user-name]
       [:p.subtitle summary]]
      [:button.btn.btn-ghost {:on-click #(rf/dispatch [::events/logout])} sign-out-label]]
     [:form.form.row {:on-submit (on-submit [::events/create-todo])}
      [:input.field-input
       {:value draft
        :placeholder draft-placeholder
        :on-change #(rf/dispatch-sync [::events/set-draft (.. % -target -value)])}]
      [:button.btn.btn-primary {:type "submit"} add-label]]
     (when error [:p.error error])
     (case list-state
       :loading [:p.muted loading-label]
       :empty [:p.muted empty-label]
       [:ul.todo-list (for [item items] ^{:key (:id item)} [todo-row item])])]))

(defn app []
  [:main.shell
   (case @(rf/subscribe [::subs/screen])
     :loading [loading-screen]
     :todos [todos-screen]
     [auth-screen])])
