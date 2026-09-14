(ns app.mobile.views
  (:require ["react-native" :as rn]
            ["react-native-safe-area-context" :as safe-area-context]
            [app.app.events :as events]
            [app.app.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(def view (r/adapt-react-class rn/View))
(def text (r/adapt-react-class rn/Text))
(def text-input (r/adapt-react-class rn/TextInput))
(def touchable (r/adapt-react-class rn/TouchableOpacity))
(def flat-list (r/adapt-react-class rn/FlatList))
(def safe-area (r/adapt-react-class safe-area-context/SafeAreaView))
(def switch-input (r/adapt-react-class rn/Switch))

(def styles
  {:screen {:flex 1 :backgroundColor "#0f1115" :padding 20 :justifyContent "center"}
   :card {:backgroundColor "#171a21" :borderRadius 14 :padding 20
          :borderWidth 1 :borderColor "#262b36"}
   :title {:color "#e8eaf0" :fontSize 20 :fontWeight "600" :marginBottom 4}
   :subtitle {:color "#9aa3b5" :fontSize 13 :marginBottom 16}
   :spacer {:height 16}
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

(defn- change! [event]
  (rf/dispatch-sync event)
  (r/flush))

(def ^:private keyboard
  {:text {}
   :email {:keyboardType "email-address"}
   :password {:secureTextEntry true}})

(defn- field [{:keys [id kind label placeholder value]}]
  [view
   [text {:style (:label styles)} label]
   [text-input (merge {:style (:input styles)
                       :value value
                       :onChangeText #(change! [::events/set-auth-field id %])
                       :autoCapitalize "none"
                       :autoCorrect false
                       :placeholder placeholder
                       :placeholderTextColor "#5a6478"}
                      (keyboard kind))]])

(defn loading-screen []
  (let [{:keys [label]} @(rf/subscribe [::subs/loading-screen])]
    [view {:style (:card styles)}
     [text {:style (:muted styles)} label]]))

(defn auth-screen []
  (let [{:keys [title fields error submitting? submit-label switch-label]}
        @(rf/subscribe [::subs/auth-screen])]
    [view {:style (:card styles)}
     [text {:style (:title styles)} title]
     [view {:style (:spacer styles)}]
     (for [f fields] ^{:key (:id f)} [field f])
     (when error [text {:style (:error styles)} error])
     [touchable {:style (:button styles)
                 :disabled submitting?
                 :onPress #(rf/dispatch [::events/submit-auth])}
      [text {:style (:button-text styles)} submit-label]]
     [touchable {:onPress #(rf/dispatch [::events/toggle-auth-mode])}
      [text {:style (:link styles)} switch-label]]]))

(defn- todo-row [{:keys [id title done? delete-label]}]
  [view {:style (:todo styles)}
   [switch-input {:value done?
                  :onValueChange #(rf/dispatch [::events/toggle-todo id])}]
   [text {:style (if done? (:todo-done styles) (:todo-title styles))} title]
   [touchable {:onPress #(rf/dispatch [::events/delete-todo id])
               :accessibilityLabel delete-label}
    [text {:style {:color "#9aa3b5" :fontSize 20 :paddingHorizontal 6}} "×"]]])

(defn todos-screen []
  (let [{:keys [user-name summary sign-out-label draft draft-placeholder
                error list-state loading-label empty-label items]}
        @(rf/subscribe [::subs/todos-screen])]
    [view {:style (:card styles)}
     [view {:style (:row styles)}
      [view {:style {:flex 1}}
       [text {:style (:title styles)} user-name]
       [text {:style (:subtitle styles)} summary]]
      [touchable {:onPress #(rf/dispatch [::events/logout])}
       [text {:style {:color "#9aa3b5"}} sign-out-label]]]
     [text-input {:style (:input styles)
                  :value draft
                  :placeholder draft-placeholder
                  :placeholderTextColor "#5a6478"
                  :onChangeText #(change! [::events/set-draft %])
                  :onSubmitEditing #(rf/dispatch [::events/create-todo])
                  :returnKeyType "done"}]
     (when error [text {:style (:error styles)} error])
     (case list-state
       :loading [text {:style (:muted styles)} loading-label]
       :empty [text {:style (:muted styles)} empty-label]
       (let [by-id (into {} (map (juxt :id identity)) items)]
         [flat-list {:data (to-array (map :id items))
                     :keyExtractor (fn [id] id)
                     :renderItem (fn [^js row]
                                   (r/as-element [todo-row (get by-id (.-item row))]))}]))]))

(defn app []
  [safe-area {:style (:screen styles)}
   (case @(rf/subscribe [::subs/screen])
     :loading [loading-screen]
     :todos [todos-screen]
     [auth-screen])])
