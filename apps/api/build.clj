(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'app/api)
(def version "0.1.0")
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn- basis []
  (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (let [basis (basis)]
    (b/copy-dir {:src-dirs (:paths basis) :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :ns-compile '[app.api.main]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'app.api.main})
    (println "built" uber-file)))
