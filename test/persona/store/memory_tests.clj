(ns persona.store.memory-tests
  (:require [clojure.test :refer [deftest is]]
            [persona.store.memory :as memory]
            [persona.protocols :as protocols]
            [persona.core-tests :as core-tests]
            [persona.snapshot-tests :as snapshot-tests]))

(defmacro ^:private with-memory-store [f]
  `(let [store# (memory/->memory-store)]
     (~f store# store#)))

(deftest store-protocols
  (let [store (memory/->memory-store)
        reader (protocols/open-read store)
        writer (protocols/open-write store)]
    (is (satisfies? protocols/ReadableStore store))
    (is (satisfies? protocols/WritableStore store))
    (is (satisfies? protocols/Reader reader))
    (is (satisfies? protocols/Writer writer))))

;;; core tests

(deftest reader-and-writer-predicates
  (with-memory-store core-tests/test-predicates))

(deftest put-persona!
  (with-memory-store core-tests/test-put-persona!))

(deftest remove-persona!
  (with-memory-store core-tests/test-remove-persona!))

(deftest persona
  (with-memory-store core-tests/test-persona))

(deftest personas
  (with-memory-store core-tests/test-personas))

(deftest put-assignments!
  (with-memory-store core-tests/test-put-assignments!))

(deftest remove-all-assignments!
  (with-memory-store core-tests/test-remove-all-assignments!))

(deftest remove-assignments!
  (with-memory-store core-tests/test-remove-assignments!))

(deftest assignments
  (with-memory-store core-tests/test-assignments))

(deftest resolve-assignments
  (with-memory-store core-tests/test-resolve-assignments))

(deftest resolve-persona-ids
  (with-memory-store core-tests/test-resolve-persona-ids))

(deftest effective-personas
  (with-memory-store core-tests/test-effective-personas))

(deftest allowed?
  (with-memory-store core-tests/test-allowed?))

(deftest namespaces-are-isolated
  (with-memory-store core-tests/test-namespaces-are-isolated))

;;; snapshot tests

(deftest reader-uses-snapshot
  (with-memory-store snapshot-tests/test-reader-uses-snapshot))