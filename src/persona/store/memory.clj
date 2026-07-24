(ns persona.store.memory
  (:require [persona.errors :as errors]
            [persona.protocols :as protocols]))

(defn- assignment
  [persona-id subject scope]
  {:persona-id persona-id
   :subject subject
   :scope scope})

(defn- subject-matches?
  [expected actual]
  (and (or (not (contains? expected :provider))
           (= (:provider expected) (:provider actual)))
       (or (not (contains? expected :id))
           (= (:id expected) (:id actual)))))

(defn- matches-query?
  [query assignment]
  (and
   (or (not (contains? query :subject-match))
       (subject-matches? (:subject-match query)
                         (:subject assignment)))
   (or (not (contains? query :subject))
       (= (:subject query)
          (:subject assignment)))
   (or (not (contains? query :persona-id))
       (= (:persona-id query)
          (:persona-id assignment)))
   (or (not (contains? query :scope))
       (= (:scope query)
          (:scope assignment)))))

(deftype MemoryWriter [state]
  protocols/Writer
  (write-persona! [_ ns persona]
    (swap! state assoc-in [:namespaces ns :personas (:id persona)] persona)
    nil)

  (delete-persona! [_ ns id]
    (swap! state
           (fn [store]
             (when-not (contains? (get-in store [:namespaces ns :personas]) id)
               (errors/throw-persona-not-found ns id))
             (-> store
                 (update-in [:namespaces ns :personas] dissoc id)
                 (update-in [:namespaces ns :assignments]
                         (fn [assignments]
                           (into #{}
                                 (remove #(= id (:persona-id %)))
                                 assignments))))))
    nil)

  (write-assignments! [_ ns persona-id assignments]
    (swap! state
           (fn [store]
             (when-not (contains? (get-in store [:namespaces ns :personas])
                                  persona-id)
               (errors/throw-persona-not-found ns persona-id))

             (update-in store [:namespaces ns :assignments]
                        (fnil into #{})
                        (map #(assignment persona-id (:subject %) (:scope %))
                             assignments))))
    nil)

  (delete-assignments! [_ ns query]
    (let [path [:namespaces ns :assignments]
          [before _]
          (swap-vals! state update-in path
                      #(into #{} (remove (partial matches-query? query)) %))]
      (into #{} (filter (partial matches-query? query))
            (get-in before path)))))

(deftype MemoryReader [snapshot]
  protocols/Reader
  (read-persona [_ ns id]
    (get-in snapshot [:namespaces ns :personas id]))

  (read-personas [_ ns]
    (vec (vals (get-in snapshot [:namespaces ns :personas]))))

  (read-assignments [_ ns]
    (vec (get-in snapshot [:namespaces ns :assignments])))

  (read-assignments [_ ns query]
    (into [] (filter (partial matches-query? query))
          (get-in snapshot [:namespaces ns :assignments]))))

(deftype MemoryStore [state]
  protocols/ReadableStore
  (open-read [_]
    (->MemoryReader @state))

  protocols/WritableStore
  (open-write [_]
    (->MemoryWriter state)))

(defn ->memory-store
  "Creates an empty in-memory ReadableStore and WritableStore.

  Each Reader operates on a snapshot taken when it is opened. Writers update
  the shared state used to create subsequent Reader snapshots."
  []
  (->MemoryStore
   (atom {:namespaces {}})))