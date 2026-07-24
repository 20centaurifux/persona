(ns persona.core-tests
  (:require [clojure.test :refer [is testing]]
            [persona.core :as persona]))

(def ^:private tenant1-ns :t1)
(def ^:private tenant2-ns :t2)

(def ^:private editor
  {:id :editor
   :name "Editor"
   :permissions #{:document/read
                  :document/write}})

(def ^:private viewer
  {:id :viewer
   :name "Viewer"
   :permissions #{:document/read}})

(def ^:private editors {:provider tenant1-ns
                        :id "team:editors"})

(def ^:private viewers {:provider tenant1-ns
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
  (persona/put-assignments! writable-store
                            tenant1-ns
                            persona-id
                            [{:subject subject :scope scope}]))

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
    (is (nil? (persona/put-persona! writable-store tenant1-ns editor)))
    (is (= editor (persona/persona readable-store tenant1-ns :editor))))

  (testing "replacing a persona with the same ID"
    (let [replacement (assoc editor :name "Senior Editor")]
      (is (nil? (persona/put-persona! writable-store tenant1-ns replacement)))
      (is (= replacement (persona/persona readable-store tenant1-ns :editor)))
      (is (= [replacement] (persona/personas readable-store tenant1-ns)))))

  (testing "invalid writable-store"
    (is-invalid-parameter :writable-store nil
                          #(persona/put-persona! nil tenant1-ns editor)))

  (testing "invalid namespace"
    (is-invalid-parameter :ns nil
                          #(persona/put-persona! writable-store nil editor)))

  (testing "invalid persona"
    (is-invalid-parameter :persona nil #(persona/put-persona! writable-store tenant1-ns nil))
    (is-invalid-parameter :persona (dissoc editor :id)
                          #(persona/put-persona! writable-store tenant1-ns (dissoc editor :id)))
    (is-invalid-parameter :persona (assoc editor :name " ")
                          #(persona/put-persona! writable-store tenant1-ns (assoc editor :name " ")))
    (is-invalid-parameter :persona (assoc editor :permissions [:document/read])
                          #(persona/put-persona! writable-store tenant1-ns (assoc editor :permissions [:document/read])))))

(defn test-remove-persona!
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer viewers project)

  (testing "removing a persona and its assignments"
    (is (nil? (persona/remove-persona! writable-store tenant1-ns :editor)))
    (is (nil? (persona/persona readable-store tenant1-ns :editor)))
    (is (= #{(assignment :viewer viewers project)}
           (set (persona/assignments readable-store tenant1-ns)))))

  (testing "removing a missing persona"
    (try
      (persona/remove-persona! writable-store tenant1-ns :missing)
      (is false "Expected missing persona to throw ExceptionInfo")
      (catch clojure.lang.ExceptionInfo exception
        (is (= "Persona does not exist: :missing"
               (ex-message exception)))
        (is (= {:namespace tenant1-ns
                :persona-id :missing}
               (ex-data exception)))))
    (is (= viewer (persona/persona readable-store tenant1-ns :viewer))))

  (testing "invalid parameters"
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-persona! nil tenant1-ns :viewer))
    (is-invalid-parameter :ns nil
                          #(persona/remove-persona! writable-store nil :viewer))
    (is-invalid-parameter :id nil #(persona/remove-persona! writable-store tenant1-ns nil))))

(defn test-persona
  [readable-store writable-store]
  (testing "missing persona"
    (is (nil? (persona/persona readable-store tenant1-ns :missing))))

  (testing "existing persona"
    (persona/put-persona! writable-store tenant1-ns editor)
    (is (= editor (persona/persona readable-store tenant1-ns :editor))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil #(persona/persona nil tenant1-ns :editor))
    (is-invalid-parameter :ns nil #(persona/persona readable-store nil :editor))
    (is-invalid-parameter :id " " #(persona/persona readable-store tenant1-ns " "))))

(defn test-personas
  [readable-store writable-store]
  (testing "empty store"
    (is (= [] (persona/personas readable-store tenant1-ns))))

  (testing "all personas"
    (persona/put-persona! writable-store tenant1-ns editor)
    (persona/put-persona! writable-store tenant1-ns viewer)
    (is (= #{editor viewer} (set (persona/personas readable-store tenant1-ns)))))

  (testing "invalid readable-store"
    (is-invalid-parameter :readable-store nil #(persona/personas nil tenant1-ns)))

  (testing "invalid namespace"
    (is-invalid-parameter :ns nil #(persona/personas readable-store nil))))

(defn test-put-assignments!
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)

  (testing "creating multiple assignments"
    (is (nil? (persona/put-assignments! writable-store tenant1-ns
                                        :editor
                                        [{:subject editors :scope project}
                                         {:subject viewers :scope document}])))
    (is (= #{(assignment :editor editors project)
             (assignment :editor viewers document)}
           (set (persona/assignments readable-store tenant1-ns)))))

  (testing "empty batch"
    (is (nil? (persona/put-assignments! writable-store tenant1-ns :editor [])))
    (is (= 2 (count (persona/assignments readable-store tenant1-ns)))))

  (testing "duplicate assignments"
    (is (nil? (persona/put-assignments! writable-store tenant1-ns
                                        :editor
                                        [{:subject editors :scope project}
                                         {:subject editors :scope project}])))
    (is (= 2 (count (persona/assignments readable-store tenant1-ns)))))

  (testing "assigning a missing persona"
    (try
      (persona/put-assignments! writable-store tenant1-ns :missing [{:subject editors :scope project}])
      (is false "Expected missing persona to throw ExceptionInfo")
      (catch clojure.lang.ExceptionInfo exception
        (is (= "Persona does not exist: :missing"
               (ex-message exception)))
        (is (= {:namespace tenant1-ns
                :persona-id :missing}
               (ex-data exception)))))
    (is (= 2 (count (persona/assignments readable-store tenant1-ns)))))

  (testing "invalid parameters and batches"
    (is-invalid-parameter
     :writable-store nil
     #(persona/put-assignments! nil tenant1-ns :editor [{:subject editors :scope project}]))
    (is-invalid-parameter
     :ns nil
     #(persona/put-assignments! writable-store nil :editor [{:subject editors :scope project}]))
    (is-invalid-parameter
     :persona-id nil
     #(persona/put-assignments! writable-store tenant1-ns nil [{:subject editors :scope project}]))
    (is-invalid-parameter :assignments nil
                          #(persona/put-assignments! writable-store tenant1-ns :editor nil))
    (is-invalid-parameter
     :assignments
     [{:subject editors :scope project}
      {:subject {:provider tenant1-ns} :scope document}]
     #(persona/put-assignments! writable-store tenant1-ns
                                :editor
                                [{:subject editors :scope project}
                                 {:subject {:provider tenant1-ns} :scope document}]))
    (is (= 2 (count (persona/assignments readable-store tenant1-ns))))))

(defn test-remove-all-assignments!
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (doseq [[persona-id subject scope]
          [[:editor editors project]
           [:editor viewers document]
           [:viewer viewers project]]]
    (assign! writable-store persona-id subject scope))

  (testing "removing all assignments"
    (is (= #{(assignment :editor editors project)
             (assignment :editor viewers document)
             (assignment :viewer viewers project)}
           (persona/remove-all-assignments! writable-store tenant1-ns)))
    (is (empty? (persona/assignments readable-store tenant1-ns))))

  (testing "invalid parameters"
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-all-assignments! nil tenant1-ns))
    (is-invalid-parameter :ns nil
                          #(persona/remove-all-assignments! writable-store nil))))

(defn test-remove-assignments!
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (doseq [[persona-id subject scope]
          [[:editor editors project]
           [:editor viewers document]
           [:viewer viewers project]]]
    (assign! writable-store persona-id subject scope))

  (testing "removing matching assignments"
    (is (= #{(assignment :editor editors project)
             (assignment :editor viewers document)}
           (persona/remove-assignments! writable-store tenant1-ns
                                        {:subject-match {:provider tenant1-ns}
                                         :persona-id :editor})))
    (is (= #{(assignment :viewer viewers project)}
           (set (persona/assignments readable-store tenant1-ns)))))

  (testing "query without matches"
    (is (= #{}
           (persona/remove-assignments! writable-store tenant1-ns
                                        {:persona-id :missing})))
    (is (= 1 (count (persona/assignments readable-store tenant1-ns)))))

  (testing "invalid parameters"
    (is-invalid-parameter :writable-store nil
                          #(persona/remove-assignments! nil tenant1-ns {}))
    (is-invalid-parameter :ns nil
                          #(persona/remove-assignments! writable-store nil
                                                        {:persona-id :viewer}))
    (is-invalid-parameter :query {}
                          #(persona/remove-assignments! writable-store tenant1-ns {}))
    (is-invalid-parameter :query nil
                          #(persona/remove-assignments! writable-store tenant1-ns nil))
    (is-invalid-parameter
     :query {:persona-idd :viewer}
     #(persona/remove-assignments! writable-store tenant1-ns {:persona-idd :viewer}))
    (is-invalid-parameter
     :query {:subject-match {}}
     #(persona/remove-assignments! writable-store tenant1-ns {:subject-match {}}))))

(defn test-assignments
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)

  (doseq [[persona-id subject scope]
          [[:editor editors project]
           [:editor viewers document]
           [:viewer viewers project]]]
    (assign! writable-store persona-id subject scope))
  (let [all #{(assignment :editor editors project)
              (assignment :editor viewers document)
              (assignment :viewer viewers project)}]
    (testing "all assignments"
      (is (= all (set (persona/assignments readable-store tenant1-ns))))
      (is-invalid-parameter :query {}
                            #(persona/assignments readable-store tenant1-ns {})))

    (testing "exact subject filter"
      (is (= #{(assignment :editor editors project)}
             (set (persona/assignments readable-store tenant1-ns {:subject editors})))))

    (testing "partial subject filters"
      (is (= #{(assignment :editor editors project)
               (assignment :editor viewers document)}
             (set (persona/assignments readable-store tenant1-ns {:subject-match {:provider tenant1-ns}
                                                                  :persona-id :editor}))))
      (is (= #{(assignment :editor viewers document)
               (assignment :viewer viewers project)}
             (set (persona/assignments readable-store tenant1-ns {:subject-match {:id "team:viewers"}})))))

    (testing "combined filters"
      (is (= #{(assignment :viewer viewers project)}
             (set (persona/assignments readable-store tenant1-ns {:subject-match viewers
                                                                  :persona-id :viewer :scope project})))))

    (testing "query without matches"
      (is (empty? (persona/assignments readable-store tenant1-ns
                                       {:persona-id "not-assigned"})))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil #(persona/assignments nil tenant1-ns))
    (is-invalid-parameter :readable-store nil #(persona/assignments nil tenant1-ns {}))
    (is-invalid-parameter :ns nil #(persona/assignments readable-store nil))
    (is-invalid-parameter :ns nil
                          #(persona/assignments readable-store nil
                                                {:persona-id :editor}))
    (is-invalid-parameter :query nil #(persona/assignments readable-store tenant1-ns nil))
    (is-invalid-parameter :query {:persona-idd :editor}
                          #(persona/assignments readable-store tenant1-ns {:persona-idd :editor}))
    (is-invalid-parameter :query {:subject-match {}}
                          #(persona/assignments readable-store tenant1-ns {:subject-match {}}))))

(defn test-resolve-assignments
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :editor editors document)
  (assign! writable-store :viewer viewers project)

  (testing "one subject along the complete path"
    (is (= #{(assignment :editor editors project)
             (assignment :editor editors document)}
           (persona/resolve-assignments readable-store tenant1-ns #{editors} [document project]))))

  (testing "multiple subjects"
    (is (= #{(assignment :editor editors project)
             (assignment :editor editors document)
             (assignment :viewer viewers project)}
           (persona/resolve-assignments readable-store tenant1-ns #{editors viewers} [document project]))))

  (testing "empty subjects or path"
    (is (= #{} (persona/resolve-assignments readable-store tenant1-ns #{} [project])))
    (is (= #{} (persona/resolve-assignments readable-store tenant1-ns #{editors} []))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/resolve-assignments nil tenant1-ns #{editors} [project]))
    (is-invalid-parameter :ns nil
                          #(persona/resolve-assignments readable-store nil
                                                        #{editors} [project]))
    (is-invalid-parameter :subjects [editors]
                          #(persona/resolve-assignments readable-store tenant1-ns [editors] [project]))
    (is-invalid-parameter :path #{project}
                          #(persona/resolve-assignments readable-store tenant1-ns #{editors} #{project}))))

(defn test-resolve-persona-ids
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (assign! writable-store :editor editors document)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer editors project)

  (testing "unique persona IDs along the path"
    (is (= #{:editor :viewer}
           (persona/resolve-persona-ids readable-store tenant1-ns #{editors} [document project]))))

  (testing "empty subjects"
    (is (= #{} (persona/resolve-persona-ids readable-store tenant1-ns #{} [project]))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/resolve-persona-ids nil tenant1-ns #{editors} [project]))
    (is-invalid-parameter :ns nil
                          #(persona/resolve-persona-ids readable-store nil
                                                        #{editors} [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/resolve-persona-ids readable-store tenant1-ns nil [project]))
    (is-invalid-parameter :path nil
                          #(persona/resolve-persona-ids readable-store tenant1-ns #{editors} nil))))

(defn test-effective-personas
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (assign! writable-store :editor editors document)
  (assign! writable-store :viewer editors project)

  (testing "effective personas along the path"
    (is (= #{editor viewer}
           (persona/effective-personas readable-store tenant1-ns #{editors} [document project]))))

  (testing "subjects without effective personas"
    (is (= #{} (persona/effective-personas readable-store tenant1-ns #{} [project])))
    (is (= #{} (persona/effective-personas readable-store tenant1-ns #{viewers} [project]))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/effective-personas nil tenant1-ns #{editors} [project]))
    (is-invalid-parameter :ns nil
                          #(persona/effective-personas readable-store nil
                                                       #{editors} [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/effective-personas readable-store tenant1-ns nil [project]))
    (is-invalid-parameter :path nil
                          #(persona/effective-personas readable-store tenant1-ns #{editors} nil))))

(defn test-allowed?
  [readable-store writable-store]
  (persona/put-persona! writable-store tenant1-ns editor)
  (persona/put-persona! writable-store tenant1-ns viewer)
  (assign! writable-store :editor editors project)
  (assign! writable-store :viewer viewers document)

  (testing "permission granted directly or through a parent scope"
    (is (true? (persona/allowed? readable-store tenant1-ns #{editors} :document/write [document project])))
    (is (true? (persona/allowed? readable-store tenant1-ns #{viewers} :document/read [document]))))

  (testing "permission denied"
    (is (false? (persona/allowed? readable-store tenant1-ns #{viewers} :document/write [document project]))))

  (testing "empty subjects or path"
    (is (false? (persona/allowed? readable-store tenant1-ns #{} :document/read [document project])))
    (is (false? (persona/allowed? readable-store tenant1-ns #{editors} :document/read []))))

  (testing "invalid parameters"
    (is-invalid-parameter :readable-store nil
                          #(persona/allowed? nil tenant1-ns #{editors} :document/read [project]))
    (is-invalid-parameter :ns nil
                          #(persona/allowed? readable-store nil
                                             #{editors} :document/read [project]))
    (is-invalid-parameter :subjects nil
                          #(persona/allowed? readable-store tenant1-ns nil :document/read [project]))
    (is-invalid-parameter :permission "document/read"
                          #(persona/allowed? readable-store tenant1-ns #{editors} "document/read" [project]))
    (is-invalid-parameter :path nil
                          #(persona/allowed? readable-store tenant1-ns #{editors} :document/read nil))))

(defn test-namespaces-are-isolated
  [readable-store writable-store]
  (let [scope {:kind :project :id :shared}
        subject {:provider :test :id :user}
        assignment-value {:subject subject :scope scope}
        persisted-assignment (assignment :member subject scope)
        tenant-a-persona {:id :member
                          :name "Tenant A member"
                          :permissions #{:document/read}}
        tenant-b-persona {:id :member
                          :name "Tenant B member"
                          :permissions #{:document/write}}]
    (persona/put-persona! writable-store tenant1-ns tenant-a-persona)
    (persona/put-persona! writable-store tenant2-ns tenant-b-persona)
    (persona/put-assignments!
     writable-store tenant1-ns :member [assignment-value])
    (persona/put-assignments!
     writable-store tenant2-ns :member [assignment-value])

    (is (= tenant-a-persona
           (persona/persona readable-store tenant1-ns :member)))
    (is (= tenant-b-persona
           (persona/persona readable-store tenant2-ns :member)))
    (is (= [tenant-a-persona]
           (persona/personas readable-store tenant1-ns)))
    (is (= [tenant-b-persona]
           (persona/personas readable-store tenant2-ns)))
    (is (= #{persisted-assignment}
           (set (persona/assignments readable-store tenant1-ns))))
    (is (= #{persisted-assignment}
           (set (persona/assignments readable-store tenant2-ns))))
    (is (= #{persisted-assignment}
           (persona/resolve-assignments
            readable-store tenant1-ns #{subject} [scope])))
    (is (= #{persisted-assignment}
           (persona/resolve-assignments
            readable-store tenant2-ns #{subject} [scope])))
    (is (= #{:member}
           (persona/resolve-persona-ids
            readable-store tenant1-ns #{subject} [scope])))
    (is (= #{:member}
           (persona/resolve-persona-ids
            readable-store tenant2-ns #{subject} [scope])))
    (is (= #{tenant-a-persona}
           (persona/effective-personas
            readable-store tenant1-ns #{subject} [scope])))
    (is (= #{tenant-b-persona}
           (persona/effective-personas
            readable-store tenant2-ns #{subject} [scope])))
    (is (true? (persona/allowed?
                readable-store tenant1-ns
                #{subject} :document/read [scope])))
    (is (false? (persona/allowed?
                 readable-store tenant2-ns
                 #{subject} :document/read [scope])))

    (persona/remove-all-assignments! writable-store tenant1-ns)
    (is (empty? (persona/assignments readable-store tenant1-ns)))
    (is (= 1 (count (persona/assignments readable-store tenant2-ns))))

    (persona/remove-persona! writable-store tenant1-ns :member)
    (is (nil? (persona/persona readable-store tenant1-ns :member)))
    (is (= tenant-b-persona
           (persona/persona readable-store tenant2-ns :member)))
    (is (= 1 (count (persona/assignments readable-store tenant2-ns))))))