(ns app.api.db
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log])
  (:import [com.mongodb ConnectionString MongoClientSettings]
           [com.mongodb.client MongoClients MongoClient MongoDatabase MongoCollection]
           [org.bson Document]
           [org.bson.types ObjectId]))

(defmethod ig/init-key :app.api.db/client
  [_ {:keys [uri database]}]
  (log/info "connecting to mongodb" {:database database})
  (let [settings (-> (MongoClientSettings/builder)
                     (.applyConnectionString (ConnectionString. uri))
                     (.build))
        client (MongoClients/create settings)]
    {:client client
     :db (.getDatabase ^MongoClient client database)}))

(defmethod ig/halt-key! :app.api.db/client
  [_ {:keys [client]}]
  (when client
    (log/info "closing mongodb connection")
    (.close ^MongoClient client)))

(defn collection ^MongoCollection [{:keys [db]} name]
  (.getCollection ^MongoDatabase db name))

(defn object-id
  ([] (ObjectId.))
  ([s] (when (and s (ObjectId/isValid (str s))) (ObjectId. (str s)))))

(defn ->doc ^Document [m]
  (let [d (Document.)]
    (doseq [[k v] m]
      (.append d (name k) v))
    d))

(defn doc-> [^Document d]
  (when d
    (into {}
          (map (fn [k]
                 (let [v (.get d ^String k)]
                   [(keyword k) (if (instance? ObjectId v) (.toHexString ^ObjectId v) v)])))
          (.keySet d))))

(defn find-one [conn coll query]
  (-> (collection conn coll)
      (.find (->doc query))
      (.first)
      (doc->)))

(defn find-many [conn coll query]
  (->> (-> (collection conn coll) (.find (->doc query)))
       (.iterator)
       (iterator-seq)
       (mapv doc->)))

(defn insert! [conn coll m]
  (let [id (object-id)
        doc (->doc (assoc m :_id id))]
    (.insertOne (collection conn coll) doc)
    (doc-> doc)))

(defn update-one! [conn coll query updates]
  (.updateOne (collection conn coll)
              (->doc query)
              (Document. "$set" (->doc updates)))
  (find-one conn coll query))

(defn delete-one! [conn coll query]
  (let [r (.deleteOne (collection conn coll) (->doc query))]
    (pos? (.getDeletedCount r))))

(defn ensure-indexes! [conn]
  (.createIndex (collection conn "users")
                (->doc {:email 1})
                (doto (com.mongodb.client.model.IndexOptions.) (.unique true)))
  (.createIndex (collection conn "todos") (->doc {:owner_id 1})))
