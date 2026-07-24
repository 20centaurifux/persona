(ns persona.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn- non-blank-string?
  [value]
  (and (string? value)
       (not (str/blank? value))))

(s/def ::id
  (s/or :keyword keyword?
        :integer integer?
        :string non-blank-string?))

(s/def ::namespace ::id)

(s/def ::provider ::id)

(s/def ::subject
  (s/keys :req-un [::provider ::id]))

(s/def ::subjects
  (s/coll-of ::subject :kind set?))

(s/def ::subject-match
  (s/and
   (s/keys :opt-un [::provider ::id])
   #(or (contains? % :provider)
        (contains? % :id))))

(s/def ::name non-blank-string?)

(s/def ::description string?)

(s/def ::permission keyword?)

(s/def ::permissions (s/coll-of keyword? :kind set?))

(s/def ::persona
  (s/keys :req-un [::id ::name ::permissions]
          :opt-un [::description]))

(s/def ::kind keyword?)

(s/def ::scope
  (s/keys :req-un [::kind ::id]))

(s/def ::path
  (s/coll-of ::scope :kind vector?))

(s/def ::persona-id ::id)

(s/def ::assignment-values
  (s/keys :req-un [::subject ::scope]))

(s/def ::assignment-values-list
  (s/coll-of ::assignment-values :kind vector?))

(s/def ::assignments-query
  (s/and
   (s/keys :opt-un [::subject-match
                    ::subject
                    ::persona-id
                    ::scope])
   seq
   #(every? #{:subject-match :subject :persona-id :scope}
            (keys %))))