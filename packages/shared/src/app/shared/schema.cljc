(ns app.shared.schema
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]))

(def non-blank-string
  [:and :string [:fn {:error/message "must not be blank"}
                 (fn [s] (not (str/blank? s)))]])

(def Email
  [:re {:error/message "must be a valid email"}
   #"^[^@\s]+@[^@\s]+\.[^@\s]+$"])

(def Password
  [:string {:min 8 :max 200 :error/message "must be at least 8 characters"}])

(def Id
  [:re {:error/message "must be a 24 character hex id"} #"^[0-9a-fA-F]{24}$"])

(def User
  [:map
   [:user/id Id]
   [:user/email Email]
   [:user/name non-blank-string]
   [:user/created-at inst?]])

(def Credentials
  [:map
   [:email Email]
   [:password Password]])

(def Registration
  [:map
   [:email Email]
   [:password Password]
   [:name non-blank-string]])

(def Session
  [:map
   [:token non-blank-string]
   [:user User]])

(def Todo
  [:map
   [:todo/id Id]
   [:todo/title non-blank-string]
   [:todo/done boolean?]
   [:todo/owner-id Id]
   [:todo/created-at inst?]])

(def NewTodo
  [:map
   [:title non-blank-string]])

(def TodoPatch
  [:map
   [:title {:optional true} non-blank-string]
   [:done {:optional true} boolean?]])

(def registry
  {:user/entity User
   :user/credentials Credentials
   :user/registration Registration
   :user/session Session
   :todo/entity Todo
   :todo/new NewTodo
   :todo/patch TodoPatch})

(defn valid? [schema value]
  (m/validate schema value))

(defn explain [schema value]
  (some-> (m/explain schema value) me/humanize))

(defn coerce [schema value]
  (m/decode schema value (mt/transformer mt/string-transformer mt/json-transformer)))
