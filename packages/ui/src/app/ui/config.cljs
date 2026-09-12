(ns app.ui.config)

(defonce ^:private state (atom {:base-url "http://localhost:8080"}))

(defn install! [m]
  (swap! state merge m))

(defn base-url []
  (:base-url @state))
