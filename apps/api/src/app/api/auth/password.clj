(ns app.api.auth.password
  (:require [buddy.hashers :as hashers]))

(defn hash-password [plain]
  (hashers/derive plain {:alg :bcrypt+sha512}))

(defn verify [plain hashed]
  (boolean (and plain hashed (:valid (hashers/verify plain hashed)))))
