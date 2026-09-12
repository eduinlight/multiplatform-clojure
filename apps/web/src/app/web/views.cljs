(ns app.web.views
  (:require [app.ui.events :as events]
            [app.ui.subs :as subs]
            [reagent.core :as r]
            [re-frame.core :as rf]))

(defn- field [{:keys [label type value on-change placeholder]}]
  [:label.field
   [:span.field-label label]
   [:input.field-input
    {:type (or type "text")
     :value value
     :placeholder placeholder
     :on-change #(on-change (.. % -target -value))}]])

(defn auth-panel []
  (let [mode (r/atom :login)
        form (r/atom {:email "" :password "" :name ""})]
    (fn []
      (let [pending? @(rf/subscribe [::subs/auth-pending?])
            error @(rf/subscribe [::subs/auth-error])
            register? (= :register @mode)
            submit (fn []
                     (if register?
                       (rf/dispatch [::events/register @form])
                       (rf/dispatch [::events/login (select-keys @form [:email :password])])))]
        [:div.card.auth
         [:h1.title (if register? "Create account" "Welcome back")]
         [:form.form
          {:on-submit (fn [e] (.preventDefault e) (submit))}
          (when register?
            [field {:label "Name"
                    :value (:name @form)
                    :placeholder "Ada Lovelace"
                    :on-change #(swap! form assoc :name %)}])
          [field {:label "Email"
                  :type "email"
                  :value (:email @form)
                  :placeholder "you@example.com"
                  :on-change #(swap! form assoc :email %)}]
          [field {:label "Password"
                  :type "password"
                  :value (:password @form)
                  :placeholder "at least 8 characters"
                  :on-change #(swap! form assoc :password %)}]
          (when error [:p.error error])
          [:button.btn.btn-primary
           {:type "submit" :disabled pending?}
           (cond
             pending? "Working…"
             register? "Sign up"
             :else "Sign in")]]
         [:button.btn.btn-link
          {:on-click #(swap! mode (fn [m] (if (= :login m) :register :login)))}
          (if register? "I already have an account" "I need an account")]]))))

(defn- todo-row [{:todo/keys [id title done] :as todo}]
  [:li.todo {:class (when done "todo-done")}
   [:label.todo-main
    [:input {:type "checkbox"
             :checked done
             :on-change #(rf/dispatch [::events/toggle-todo todo])}]
    [:span.todo-title title]]
   [:button.btn.btn-ghost
    {:on-click #(rf/dispatch [::events/delete-todo id])
     :aria-label (str "Delete " title)}
    "×"]])

(defn todo-panel []
  (let [todos @(rf/subscribe [::subs/todos])
        draft @(rf/subscribe [::subs/draft])
        loading? @(rf/subscribe [::subs/todos-loading?])
        error @(rf/subscribe [::subs/todos-error])
        summary @(rf/subscribe [::subs/summary])
        user @(rf/subscribe [::subs/current-user])
        initials @(rf/subscribe [::subs/user-initials])]
    [:div.card.todos
     [:header.todo-header
      [:div.avatar initials]
      [:div
       [:h1.title (:user/name user)]
       [:p.subtitle (:label summary)]]
      [:button.btn.btn-ghost {:on-click #(rf/dispatch [::events/logout])} "Sign out"]]
     [:form.form.row
      {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [::events/create-todo]))}
      [:input.field-input
       {:value draft
        :placeholder "What needs doing?"
        :on-change #(rf/dispatch [::events/set-draft (.. % -target -value)])}]
      [:button.btn.btn-primary {:type "submit"} "Add"]]
     (when error [:p.error error])
     (cond
       (and loading? (empty? todos)) [:p.muted "Loading…"]
       (empty? todos) [:p.muted "Nothing here yet."]
       :else [:ul.todo-list (for [t todos] ^{:key (:todo/id t)} [todo-row t])])]))

(defn app []
  (let [authenticated? @(rf/subscribe [::subs/authenticated?])]
    [:main.shell
     (if authenticated?
       [todo-panel]
       [auth-panel])]))
