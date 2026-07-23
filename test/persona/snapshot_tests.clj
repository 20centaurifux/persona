(ns persona.snapshot-tests
  (:require [clojure.test :refer [is]]
            [persona.core :as persona]
            [persona.protocols :as protocols]))

(def ^:private editor
  {:id :editor
   :name "Editor"
   :permissions #{:document/read
                  :document/write}})

(def ^:private editors
  {:provider :test
   :id "team:editors"})

(def ^:private project
  {:kind :project
   :id "website"})

(defn test-reader-uses-snapshot
  [readable-store writable-store]
  (with-open [reader (persona/open-read readable-store)]
    (persona/put-persona! writable-store editor)
    (persona/put-assignments!
     writable-store
     :editor
     [{:subject editors
       :scope project}])

    (is (= [] (protocols/read-personas reader)))
    (is (= [] (protocols/read-assignments reader)))

    (with-open [current-reader (persona/open-read readable-store)]
      (is (= [editor]
             (protocols/read-personas current-reader)))
      (is (= [{:persona-id :editor
               :subject editors
               :scope project}]
             (protocols/read-assignments current-reader))))))