(ns app.app.db)

(def empty-auth-form
  {:mode :login
   :email ""
   :password ""
   :name ""})

(def default-db
  {:auth {:status :starting
          :token nil
          :user nil
          :error nil}
   :auth-form empty-auth-form
   :todos {:status :idle
           :items []
           :draft ""
           :error nil}})
