(ns libx.schema
  (:require [libx.spec.sub :as sub]
            [libx.query :as q]
            [libx.state :refer [session-hierarchy]]
            [libx.util :refer [guid]]))

(defn by-ident [schema]
  (reduce (fn [acc [k v]] (assoc acc k (first v)))
    {} (group-by :db/ident schema)))

(defn schema-attr? [by-ident attr]
  (contains? by-ident attr))

(defn unique-attr? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= (or :db.unique/identity
              :db.unique/value)
          (get-in by-ident [attr :db/unique]))))

(defn one-to-many? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/many (get-in by-ident [attr :db/cardinality]))))

(defn unique-attrs [schema tuples]
  (reduce (fn [acc cur]
            (if (unique-attr? schema (second cur))
              (conj acc (second cur))
              acc))
    [] tuples))

(defn unique-facts [session unique-attrs]
  (mapcat #(q/facts-where session %) unique-attrs))

(defn attribute [ident type & {:as opts}]
  (merge {:db/id        (guid)
          :db/ident     ident
          :db/valueType type}
    {:db/cardinality :db.cardinality/one}
    opts))

(defn enum [ident & {:as fields}]
  (merge {:db/id    (guid)
          :db/ident ident}
    fields))

(def libx-schema
  [
   (attribute ::sub/request
     :db.type/vector
     :db/unique :db.unique/value)

   (attribute ::sub/response
     :db.type/any
     :db/unique :db.unique/value)])

(defn schema->hierarchy
  "Takes a Datomic schema"
  [schema]
  (let [h (atom (make-hierarchy))
        cardinality (group-by :db/cardinality schema)
        unique-attrs (map :db/ident (filter :db/unique schema))
        one-to-manys (map :db/ident (:db.cardinality/many cardinality))
        one-to-ones (clojure.set/difference (into #{} (map :db/ident schema)) (set one-to-manys))]
    (doseq [x one-to-ones] (swap! h derive x :one-to-one))
    (doseq [x one-to-manys] (swap! h derive x :one-to-many))
    (doseq [x unique-attrs] (swap! h derive x :unique))
    (swap! h derive :one-to-one :all)
    (swap! h derive :one-to-many :all)
    (swap! h derive :unique :one-to-one)
    (reset! session-hierarchy h)
    @h))

