(ns app.mobile.views
  (:require ["react-native" :as rn]
            [app.ui.events :as events]
            [app.ui.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(def view (r/adapt-react-class rn/View))
(def text (r/adapt-react-class rn/Text))
(def text-input (r/adapt-react-class rn/TextInput))
(def touchable (r/adapt-react-class rn/TouchableOpacity))
(def flat-list (r/adapt-react-class rn/FlatList))
(def safe-area (r/adapt-react-class rn/SafeAreaView))
(def switch-input (r/adapt-react-class rn/Switch))

(def styles
  {:screen {:flex 1 :backgroundColor "#0f1115" :padding 20 :justifyContent "center"}
   :card {:backgroundColor "#171a21" :borderRadius 14 :padding 20
          :borderWidth 1 :borderColor "#262b36"}
   :title {:color "#e8eaf0" :fontSize 20 :fontWeight "600" :marginBottom 4}
   :subtitle {:color "#9aa3b5" :fontSize 13 :marginBottom 16}
   :label {:color "#9aa3b5" :fontSize 12 :marginBottom 6 :textTransform "uppercase"}
   :input {:backgroundColor "#11141a" :borderWidth 1 :borderColor "#262b36"
           :borderRadius 9 :paddingHorizontal 12 :paddingVertical 10
           :color "#e8eaf0" :marginBottom 14}
   :button {:backgroundColor "#5b8cff" :borderRadius 9 :paddingVertical 12 :alignItems "center"}
   :button-text {:color "#0b0e14" :fontWeight "700"}
   :link {:color "#9aa3b5" :textAlign "center" :marginTop 14}
   :error {:color "#ff6b6b" :fontSize 13 :marginBottom 8}
   :row {:flexDirection "row" :alignItems "center" :gap 10}
   :todo {:flexDirection "row" :alignItems "center" :gap 10
          :backgroundColor "#11141a" :borderRadius 9 :padding 12 :marginBottom 6
          :borderWidth 1 :borderColor "#262b36"}
   :todo-title {:color "#e8eaf0" :flex 1}
   :todo-done {:color "#9aa3b5" :flex 1 :textDecorationLine "line-through"}
   :muted {:color "#9aa3b5" :textAlign "center" :marginTop 16}})

(defn- field [{:keys [label value on-change secure? placeholder keyboard]}]
  [view
   [text {:style (:label styles)} label]
   [text-input {:style (:input styles)
                :value value
                :onChangeText on-change
                :secureTextEntry (boolean secure?)
                :autoCapitalize "none"
                :autoCorrect false
                :keyboardType (or keyboard "default")
                :placeholder placeholder
                :placeholderTextColor "#5a6478"}]])

(defn auth-screen []
  (let [mode (r/atom :login)
        form (r/atom {:email "" :password "" :name ""})]
    (fn []
      (let [pending? @(rf/subscribe [::subs/auth-pending?])
            error @(rf/subscribe [::subs/auth-error])
            register? (= :register @mode)]
        [view {:style (:card styles)}
         [text {:style (:title styles)} (if register? "Create account" "Welcome back")]
         [text {:style (:subtitle styles)} "Same logic, native shell."]
         (when register?
           [field {:label "Name"
                   :value (:name @form)
                   :placeholder "Ada Lovelace"
                   :on-change #(swap! form assoc :name %)}])
         [field {:label "Email"
                 :value (:email @form)
                 :keyboard "email-address"
                 :placeholder "you@example.com"
                 :on-change #(swap! form assoc :email %)}]
         [field {:label "Password"
                 :value (:password @form)
                 :secure? true
                 :placeholder "at least 8 characters"
                 :on-change #(swap! form assoc :password %)}]
         (when error [text {:style (:error styles)} error])
         [touchable {:style (:button styles)
                     :disabled pending?
                     :onPress #(if register?
                                 (rf/dispatch [::events/register @form])
                                 (rf/dispatch [::events/login (select-keys @form [:email :password])]))}
          [text {:style (:button-text styles)}
           (cond pending? "Working…" register? "Sign up" :else "Sign in")]]
         [touchable {:onPress #(swap! mode (fn [m] (if (= :login m) :register :login)))}
          [text {:style (:link styles)}
           (if register? "I already have an account" "I need an account")]]]))))

(defn- todo-row [{:todo/keys [id title done] :as todo}]
  [view {:style (:todo styles)}
   [switch-input {:value done
                  :onValueChange #(rf/dispatch [::events/toggle-todo todo])}]
   [text {:style (if done (:todo-done styles) (:todo-title styles))} title]
   [touchable {:onPress #(rf/dispatch [::events/delete-todo id])}
    [text {:style {:color "#9aa3b5" :fontSize 20 :paddingHorizontal 6}} "×"]]])

(defn todo-screen []
  (let [todos @(rf/subscribe [::subs/todos])
        draft @(rf/subscribe [::subs/draft])
        error @(rf/subscribe [::subs/todos-error])
        summary @(rf/subscribe [::subs/summary])
        user @(rf/subscribe [::subs/current-user])]
    [view {:style (:card styles)}
     [view {:style (:row styles)}
      [view {:style {:flex 1}}
       [text {:style (:title styles)} (:user/name user)]
       [text {:style (:subtitle styles)} (:label summary)]]
      [touchable {:onPress #(rf/dispatch [::events/logout])}
       [text {:style {:color "#9aa3b5"}} "Sign out"]]]
     [text-input {:style (:input styles)
                  :value draft
                  :placeholder "What needs doing?"
                  :placeholderTextColor "#5a6478"
                  :onChangeText #(rf/dispatch [::events/set-draft %])
                  :onSubmitEditing #(rf/dispatch [::events/create-todo])
                  :returnKeyType "done"}]
     (when error [text {:style (:error styles)} error])
     (if (empty? todos)
       [text {:style (:muted styles)} "Nothing here yet."]
       [flat-list {:data (clj->js todos)
                   :keyExtractor (fn [item] (str (aget item "todo/id")))
                   :renderItem (fn [^js row]
                                 (r/as-element [todo-row (js->clj (.-item row) :keywordize-keys true)]))}])]))

(defn app []
  (let [authenticated? @(rf/subscribe [::subs/authenticated?])]
    [safe-area {:style (:screen styles)}
     (if authenticated? [todo-screen] [auth-screen])]))
