(ns persona.core-tests
  (:require [clojure.test :refer [is testing]]
            [persona.core :as persona]))

(def ^:private editor
  {:id :editor
   :name "Editor"
   :permissions #{:document/read
                  :document/write}})

(def ^:private viewer
  {:id :viewer
   :name "Viewer"
   :permissions #{:document/read}})

(def ^:private editors {:provider :test
                        :id "team:editors"})

(def ^:private viewers {:provider :test
                        :id "team:viewers"})

(def ^:private project {:kind :project
                        :id "website"})

(def ^:private document {:kind :document
                         :id "index.html"})

(defn- assignment
  [persona-id subject scope]
  {:persona-id persona-id
   :subject subject
   :scope scope})

(defn- assign!
  [writable-store persona-id subject scope]
  (persona/put-assignments!
   writable-store persona-id [{:subject subject :scope scope}]))

(defn- is-invalid-parameter
  [parameter value f]
  (try
    (f)
    (is false (str "Expected invalid parameter " parameter))
    (catch clojure.lang.ExceptionInfo exception
      (is (= parameter (:parameter (ex-data exception))))
      (is (= value (:value (ex-data exception))))
      (is (some? (:explain-data (ex-data exception)))))))

(defn test-predicates
  [readable-store writable-store]
  (testing "implemented protocols"
    (is (true? (persona/readable-store? readable-store)))
    (is (true? (persona/writable-store? writable-store))))

  (testing "values without the protocols"
    (is (false? (persona/readable-store? nil)))
    (is (false? (persona/writable-store? nil)))))

(defn test-put-persona!
  [readable-store writable-store]
  (testing "creating a persona"
    (is (nil? (persona/put-persona! writable-store editor)))
    (is (= editor (persona/persona readable-store :editor))))

  (testing "replacing a persona with the same ID"
    (let [replacement (assoc editor :name "Senior Editor")]
      (is (nil? (persona/put-persona! writable-store replacement)))
      (is (= replacement (persona/persona readable-store :editor)))
      (is (= [replacement] (persona/personas readable-store)))))

  (testing "invalid writable-store"
    (is-invalid-parameter :writable-store nil
                          #(persona/put-persona! nil editor)))

  (testing "invalid persona"
    (is-invalid-parameter :persona nil #(persona/put-persona! writable-store nil))
    (is-invalid-parameter :persona (dissoc editor :id)
                          #(persona/put-persona! writable-store (dissoc editor :id)))
    (is-invalid-parameter :persona (assoc editor :name " ")
                          #(persona/put-persona! writable-store (assoc editor :name " ")))
    (is-invalid-parameter :persona (assoc editor :permissions [:document/read])
                          #(persona/put-persona!
                            writable-store (assoc editor :permissions [:document/read])))))

(defn test-remove-persona!
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer viewers project)

  (testing "removing a persona and its assignments"
    (is (nil? (persona/remove-persona! writable-store :editor)))
    (is (nil? (persona/persona readable-store :editor)))
    (is (= #{(assignment :viewer viewers project)}
           (set (persona/assignments readable-store)))))

  (testing "removing a missing persona"
    (try
      (persona/remove-persona! writable-store :missing)
      (is false "Expected missing persona to throw ExceptionInfo")
      (catch clojure.lang.ExceptionInfo exception
        (is (= "Persona does not exist: :missing"
               (ex-message exception)))
        (is (= {:persona-id :missing}
               (ex-data exception)))))
    (is (= viewer (persona/persona readable-store :viewer))))

  (testing "invalid parameters"
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-persona! nil :viewer))
    (is-invalid-parameter :id nil #(persona/remove-persona! writable-store nil))))

(defn test-persona
  [readable-store writable-store]
  (testing "missing persona"
    (is (nil? (persona/persona readable-store :missing))))

  (testing "existing persona"
    (persona/put-persona! writable-store editor)
    (is (= editor (persona/persona readable-store :editor))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil #(persona/persona nil :editor))
    (is-invalid-parameter :id " " #(persona/persona readable-store " "))))

(defn test-personas
  [readable-store writable-store]
  (testing "empty store"
    (is (= [] (persona/personas readable-store))))

  (testing "all personas"
    (persona/put-persona! writable-store editor)
    (persona/put-persona! writable-store viewer)
    (is (= #{editor viewer} (set (persona/personas readable-store)))))

  (testing "invalid readable-store"
    (is-invalid-parameter :readable-store nil #(persona/personas nil))))

(defn test-put-assignments!
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)

  (testing "creating multiple assignments"
    (is (nil? (persona/put-assignments!
               writable-store
               :editor
               [{:subject editors :scope project}
                {:subject viewers :scope document}])))
    (is (= #{(assignment :editor editors project)
             (assignment :editor viewers document)}
           (set (persona/assignments readable-store)))))

  (testing "empty batch"
    (is (nil? (persona/put-assignments! writable-store :editor [])))
    (is (= 2 (count (persona/assignments readable-store)))))

  (testing "duplicate assignments"
    (is (nil? (persona/put-assignments!
               writable-store
               :editor
               [{:subject editors :scope project}
                {:subject editors :scope project}])))
    (is (= 2 (count (persona/assignments readable-store)))))

  (testing "assigning a missing persona"
    (try
      (persona/put-assignments!
       writable-store :missing [{:subject editors :scope project}])
      (is false "Expected missing persona to throw ExceptionInfo")
      (catch clojure.lang.ExceptionInfo exception
        (is (= "Persona does not exist: :missing"
               (ex-message exception)))
        (is (= {:persona-id :missing} (ex-data exception)))))
    (is (= 2 (count (persona/assignments readable-store)))))

  (testing "invalid parameters and batches"
    (is-invalid-parameter
     :writable-store nil
     #(persona/put-assignments!
       nil :editor [{:subject editors :scope project}]))
    (is-invalid-parameter
     :persona-id nil
     #(persona/put-assignments!
       writable-store nil [{:subject editors :scope project}]))
    (is-invalid-parameter :assignments nil
                          #(persona/put-assignments! writable-store :editor nil))
    (is-invalid-parameter
     :assignments
     [{:subject editors :scope project}
      {:subject {:provider :test} :scope document}]
     #(persona/put-assignments!
       writable-store
       :editor
       [{:subject editors :scope project}
        {:subject {:provider :test} :scope document}]))
    (is (= 2 (count (persona/assignments readable-store))))))

(defn test-remove-assignments!
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (doseq [[persona-id subject scope]
          [[:editor editors project]
           [:editor viewers document]
           [:viewer viewers project]]]
    (assign! writable-store persona-id subject scope))

  (testing "removing matching assignments"
    (is (= #{(assignment :editor editors project)
             (assignment :editor viewers document)}
           (persona/remove-assignments!
            writable-store {:subject-match {:provider :test}
                            :persona-id :editor})))
    (is (= #{(assignment :viewer viewers project)}
           (set (persona/assignments readable-store)))))

  (testing "query without matches"
    (is (= #{}
           (persona/remove-assignments!
            writable-store {:persona-id :missing})))
    (is (= 1 (count (persona/assignments readable-store)))))

  (testing "removing all assignments"
    (is (= #{(assignment :viewer viewers project)}
           (persona/remove-assignments! writable-store)))
    (is (empty? (persona/assignments readable-store))))

  (testing "invalid parameters"
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-assignments! nil))
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-assignments! nil {}))
    (is-invalid-parameter :query {}
                          #(persona/remove-assignments! writable-store {}))
    (is-invalid-parameter :query nil
                          #(persona/remove-assignments! writable-store nil))
    (is-invalid-parameter
     :query {:persona-idd :viewer}
     #(persona/remove-assignments! writable-store {:persona-idd :viewer}))
    (is-invalid-parameter
     :query {:subject-match {}}
     #(persona/remove-assignments! writable-store {:subject-match {}}))))

(defn test-assignments
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)

  (doseq [[persona-id subject scope]
          [[:editor editors project]
           [:editor viewers document]
           [:viewer viewers project]]]
    (assign! writable-store persona-id subject scope))
  (let [all #{(assignment :editor editors project)
              (assignment :editor viewers document)
              (assignment :viewer viewers project)}]
    (testing "all assignments"
      (is (= all (set (persona/assignments readable-store))))
      (is-invalid-parameter :query {}
                            #(persona/assignments readable-store {})))

    (testing "exact subject filter"
      (is (= #{(assignment :editor editors project)}
             (set (persona/assignments readable-store {:subject editors})))))

    (testing "partial subject filters"
      (is (= #{(assignment :editor editors project)
               (assignment :editor viewers document)}
             (set (persona/assignments
                   readable-store {:subject-match {:provider :test}
                                   :persona-id :editor}))))
      (is (= #{(assignment :editor viewers document)
               (assignment :viewer viewers project)}
             (set (persona/assignments
                   readable-store {:subject-match {:id "team:viewers"}})))))

    (testing "combined filters"
      (is (= #{(assignment :viewer viewers project)}
             (set (persona/assignments
                   readable-store {:subject-match viewers
                                   :persona-id :viewer :scope project})))))

    (testing "query without matches"
      (is (empty? (persona/assignments readable-store
                                       {:persona-id "not-assigned"})))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil #(persona/assignments nil))
    (is-invalid-parameter :readable-store nil #(persona/assignments nil {}))
    (is-invalid-parameter :query nil #(persona/assignments readable-store nil))
    (is-invalid-parameter :query {:persona-idd :editor}
                          #(persona/assignments
                            readable-store {:persona-idd :editor}))
    (is-invalid-parameter :query {:subject-match {}}
                          #(persona/assignments readable-store {:subject-match {}}))))

(defn test-resolve-assignments
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :editor editors document)
  (assign! writable-store :viewer viewers project)

  (testing "one subject along the complete path"
    (is (= #{(assignment :editor editors project)
             (assignment :editor editors document)}
           (persona/resolve-assignments
            readable-store #{editors} [document project]))))

  (testing "multiple subjects"
    (is (= #{(assignment :editor editors project)
             (assignment :editor editors document)
             (assignment :viewer viewers project)}
           (persona/resolve-assignments
            readable-store #{editors viewers} [document project]))))

  (testing "empty subjects or path"
    (is (= #{} (persona/resolve-assignments readable-store #{} [project])))
    (is (= #{} (persona/resolve-assignments readable-store #{editors} []))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/resolve-assignments nil #{editors} [project]))
    (is-invalid-parameter :subjects [editors]
                          #(persona/resolve-assignments
                            readable-store [editors] [project]))
    (is-invalid-parameter :path #{project}
                          #(persona/resolve-assignments
                            readable-store #{editors} #{project}))))

(defn test-resolve-persona-ids
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (assign! writable-store :editor editors document)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer editors project)

  (testing "unique persona IDs along the path"
    (is (= #{:editor :viewer}
           (persona/resolve-personas-ids
            readable-store #{editors} [document project]))))

  (testing "empty subjects"
    (is (= #{} (persona/resolve-personas-ids readable-store #{} [project]))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/resolve-personas-ids
                            nil #{editors} [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/resolve-personas-ids readable-store nil [project]))
    (is-invalid-parameter :path nil
                          #(persona/resolve-personas-ids readable-store #{editors} nil))))

(defn test-effective-personas
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (assign! writable-store :editor editors document)
  (assign! writable-store :viewer editors project)

  (testing "effective personas along the path"
    (is (= #{editor viewer}
           (persona/effective-personas
            readable-store #{editors} [document project]))))

  (testing "subjects without effective personas"
    (is (= #{} (persona/effective-personas readable-store #{} [project])))
    (is (= #{} (persona/effective-personas readable-store #{viewers} [project]))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/effective-personas
                            nil #{editors} [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/effective-personas readable-store nil [project]))
    (is-invalid-parameter :path nil
                          #(persona/effective-personas readable-store #{editors} nil))))

(defn test-allowed?
  [readable-store writable-store]
  (persona/put-persona! writable-store editor)
  (persona/put-persona! writable-store viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer viewers document)

  (testing "permission granted directly or through a parent scope"
    (is (true? (persona/allowed?
                readable-store #{editors} :document/write [document project])))
    (is (true? (persona/allowed?
                readable-store #{viewers} :document/read [document]))))

  (testing "permission denied"
    (is (false? (persona/allowed?
                 readable-store #{viewers} :document/write [document project]))))

  (testing "empty subjects or path"
    (is (false? (persona/allowed?
                 readable-store #{} :document/read [document project])))
    (is (false? (persona/allowed?
                 readable-store #{editors} :document/read []))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/allowed?
                            nil #{editors} :document/read [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/allowed?
                            readable-store nil :document/read [project]))
    (is-invalid-parameter :permission "document/read"
                          #(persona/allowed?
                            readable-store #{editors} "document/read" [project]))
    (is-invalid-parameter :path nil
                          #(persona/allowed?
                            readable-store #{editors} :document/read nil))))