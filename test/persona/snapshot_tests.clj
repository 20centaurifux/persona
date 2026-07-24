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
    (persona/put-persona! writable-store :test editor)
    (persona/put-assignments! writable-store :test
     :editor
     [{:subject editors
       :scope project}])

    (is (= [] (protocols/read-personas reader :test)))
    (is (= [] (protocols/read-assignments reader :test)))

    (with-open [current-reader (persona/open-read readable-store)]
      (is (= [editor]
             (protocols/read-personas current-reader :test)))
      (is (= [{:persona-id :editor
               :subject editors
               :scope project}]
             (protocols/read-assignments current-reader :test))))))