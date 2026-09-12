(ns app.ui.db)

(def default-db
  {:route :login
   :auth {:status :anonymous
          :token nil
          :user nil
          :error nil}
   :todos {:status :idle
           :items []
           :draft ""
           :error nil}})
