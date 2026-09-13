(ns app.api-sdk.promise
  #?(:clj (:import [java.util.concurrent CompletableFuture]
                   [java.util.function Function])))

(defn resolved [v]
  #?(:clj (CompletableFuture/completedFuture v)
     :cljs (js/Promise.resolve v)))

(defn then [p f]
  #?(:clj (.thenApply ^CompletableFuture p (reify Function (apply [_ v] (f v))))
     :cljs (.then p f)))
