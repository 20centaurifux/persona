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
  (write-persona! [_ persona]
    (swap! state assoc-in [:personas (:id persona)] persona)
    nil)

  (delete-persona! [_ id]
    (swap! state
           (fn [store]
             (when-not (contains? (:personas store) id)
               (errors/throw-persona-not-found id))
             (-> store
                 (update :personas dissoc id)
                 (update :assignments
                         (fn [assignments]
                           (into #{}
                                 (remove #(= id (:persona-id %)))
                                 assignments))))))
    nil)

  (write-assignments! [_ persona-id assignments]
    (swap! state
           (fn [store]
             (when-not (contains? (:personas store) persona-id)
               (errors/throw-persona-not-found persona-id))

             (update store :assignments
                     into
                     (map #(assignment persona-id (:subject %) (:scope %))
                          assignments))))
    nil)

  (delete-assignments! [_ query]
    (let [[before _]
          (swap-vals! state update :assignments
                      #(into #{} (remove (partial matches-query? query)) %))]
      (into #{} (filter (partial matches-query? query))
            (:assignments before)))))

(deftype MemoryReader [snapshot]
  protocols/Reader
  (read-persona [_ id]
    (get-in snapshot [:personas id]))

  (read-personas [_]
    (vec (vals (:personas snapshot))))

  (read-assignments [_]
    (vec (:assignments snapshot)))

  (read-assignments [_ query]
    (into [] (filter (partial matches-query? query))
          (:assignments snapshot))))

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
   (atom {:personas {}
          :assignments #{}})))