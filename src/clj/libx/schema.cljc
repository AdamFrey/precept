(ns libx.schema
  (:require [libx.spec.sub :as sub]
            [libx.query :as q]
            [libx.util :refer [guid]]))

(defn by-ident [schema]
  (reduce (fn [acc [k v]] (assoc acc k (first v)))
    {} (group-by :db/ident schema)))

(defn schema-attr? [by-ident attr]
  (contains? by-ident attr))

(defn unique-attr? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.unique/identity (get-in by-ident [attr :db/unique]))))

(defn unique-value? [by-ident attr]
  (and (schema-attr? by-ident attr)
    (= :db.unique/value (get-in by-ident [attr :db/unique]))))

(defn one-to-one? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/one (get-in by-ident [attr :db/cardinality]))))

(defn one-to-many? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/many (get-in by-ident [attr :db/cardinality]))))

(defn unique-identity-attrs [schema tuples]
  (reduce (fn [acc cur]
            (if (unique-attr? schema (second cur))
              (conj acc (second cur))
              acc))
    [] tuples))

(defn unique-value-attrs [schema tuples]
  (reduce (fn [acc cur]
            (if (unique-value? schema (second cur))
              (conj acc (second cur))
              acc))
    [] tuples))

(defn unique-identity-facts [session unique-attrs]
  (mapcat #(q/facts-where session %) unique-attrs))

(defn unique-value-facts [session tups unique-attrs]
  (let [unique-tups (filter #((set unique-attrs) (second %)) tups)
        avs (map rest unique-tups)]
    (mapcat (fn [[a v]] (q/facts-where session a v))
      avs)))

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