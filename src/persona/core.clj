(ns persona.core
  (:require [persona.errors :as errors]
            [persona.protocols :as protocols]
            [persona.specs :as specs]
            [smuk.core :as smuk]))

;;; Helpers

(smuk/defsmuk CloseShieldReader [reader]
  protocols/Reader
  java.io.Closeable
  (close [_]))

(defn open-read
  "Opens and returns a Closeable Reader from `store`.

  `store` must implement ReadableStore.
  The caller must close the returned Reader. A Reader that does not implement
  java.io.Closeable is wrapped with a no-op close implementation."
  [store]
  (let [reader (protocols/open-read store)]
    (cond-> reader
      (not (instance? java.io.Closeable reader)) ->CloseShieldReader)))

(smuk/defsmuk CloseShieldWriter [writer]
  protocols/Writer
  java.io.Closeable
  (close [_]))

(defn open-write
  "Opens and returns a Closeable Writer from `store`.

  `store` must implement WritableStore.
  The caller must close the returned Writer. A Writer that does not implement
  java.io.Closeable is wrapped with a no-op close implementation."
  [store]
  (let [writer (protocols/open-write store)]
    (cond-> writer
      (not (instance? java.io.Closeable writer)) ->CloseShieldWriter)))

;;; Public API

(defn writable-store?
  "Returns true if `store` implements WritableStore, otherwise false."
  [store]
  (satisfies? protocols/WritableStore store))

(defn readable-store?
  "Returns true if `store` implements ReadableStore, otherwise false."
  [store]
  (satisfies? protocols/ReadableStore store))

(defn put-persona!
  "Creates or replaces `persona` in `writable-store` and returns nil."
  [writable-store persona]
  (errors/validate-spec! writable-store? writable-store)
  (errors/validate-spec! ::specs/persona persona)

  (with-open [writer (open-write writable-store)]
    (protocols/write-persona! writer persona)))

(defn remove-persona!
  "Removes the persona identified by `id` from `writable-store`.

  All of its assignments are also removed. Returns nil. Throws ExceptionInfo
  if the persona does not exist."
  [writable-store id]
  (errors/validate-spec! writable-store? writable-store)
  (errors/validate-spec! ::specs/id id)

  (with-open [writer (open-write writable-store)]
    (protocols/delete-persona! writer id)))

(defn persona
  "Returns the persona identified by `id` from `readable-store`.

  Returns nil if the persona does not exist."
  [readable-store id]
  (errors/validate-spec! readable-store? readable-store)
  (errors/validate-spec! ::specs/id id)

  (with-open [reader (open-read readable-store)]
    (protocols/read-persona reader id)))

(defn personas
  "Returns all personas from `readable-store`.

  Returns a vector. The order of the returned personas is unspecified."
  [readable-store]
  (errors/validate-spec! readable-store? readable-store)

  (with-open [reader (open-read readable-store)]
    (vec (protocols/read-personas reader))))

(defn- resolve-assignments*
  [reader subjects path]
  (into #{}
        (for [scope path
              subject subjects
              assignment (protocols/read-assignments
                          reader
                          {:subject subject
                           :scope scope})]
          assignment)))

(defn resolve-assignments
  "Returns assignments effective for any supplied subject along `path`.

  `subjects` must be a set of subjects and `path` a vector of scopes. The
  result is a set containing each assignment at most once."
  [readable-store subjects path]
  (errors/validate-spec! readable-store? readable-store)
  (errors/validate-spec! ::specs/subjects subjects)
  (errors/validate-spec! ::specs/path path)

  (with-open [reader (open-read readable-store)]
    (resolve-assignments* reader subjects path)))

(defn- resolve-personas-ids*
  [reader subjects path]
  (into #{}
        (map :persona-id)
        (resolve-assignments* reader subjects path)))

(defn resolve-personas-ids
  "Returns persona IDs effective for any subject in `subjects` along `path`."
  [readable-store subjects path]
  (errors/validate-spec! readable-store? readable-store)
  (errors/validate-spec! ::specs/subjects subjects)
  (errors/validate-spec! ::specs/path path)

  (with-open [reader (open-read readable-store)]
    (resolve-personas-ids* reader subjects path)))

(defn effective-personas
  "Returns the set of existing personas effective for `subjects` along `path`.

  Assignments that refer to a missing persona are ignored."
  [readable-store subjects path]
  (errors/validate-spec! readable-store? readable-store)
  (errors/validate-spec! ::specs/subjects subjects)
  (errors/validate-spec! ::specs/path path)

  (with-open [reader (open-read readable-store)]
    (into #{}
          (keep (partial protocols/read-persona reader))
          (resolve-personas-ids* reader subjects path))))

(defn allowed?
  "Returns true if an effective persona contains `permission`.

  Assignments for every subject in `subjects` on every scope in `path` are
  considered. Returns false if no matching effective persona contains
  `permission`."
  [readable-store subjects permission path]
  (errors/validate-spec! ::specs/permission permission)

  (boolean
   (some #(contains? (:permissions %) permission)
         (effective-personas readable-store subjects path))))

(defn put-assignments!
  "Atomically persists `assignments` of `persona-id` in `writable-store`.

  `assignments` must be a vector of maps containing :subject and :scope.
  Persisting the same assignment more than once has no additional effect.

  Returns nil. Throws ExceptionInfo if the persona does not exist."
  [writable-store persona-id assignments]
  (errors/validate-spec! writable-store? writable-store)
  (errors/validate-spec! ::specs/persona-id persona-id)
  (errors/validate-spec! ::specs/assignment-values-list assignments)

  (with-open [writer (open-write writable-store)]
    (protocols/write-assignments! writer persona-id assignments)))

(defn remove-assignments!
  "Atomically removes matching assignments from `writable-store`.

  With no `query`, removes all assignments. With a `query`, removes every
  assignment matching all supplied :subject-match, :subject, :persona-id, and
  :scope values. `query` must be non-empty and may not contain other keys.

  Returns the removed assignments as a set, or an empty set if none match."
  ([writable-store]
   (errors/validate-spec! writable-store? writable-store)

   (with-open [writer (open-write writable-store)]
     (into #{} (protocols/delete-assignments! writer {}))))

  ([writable-store query]
   (errors/validate-spec! writable-store? writable-store)
   (errors/validate-spec! ::specs/assignments-query query)

   (with-open [writer (open-write writable-store)]
     (into #{} (protocols/delete-assignments! writer query)))))

(defn assignments
  "Returns assignments from `readable-store`.

  With no `query`, returns all assignments. With a `query`, returns assignments
  matching all supplied :subject-match, :subject, :persona-id, and :scope
  values. `query` must be non-empty and may not contain other keys. Returns a
  vector whose order is unspecified."
  ([readable-store]
   (errors/validate-spec! readable-store? readable-store)

   (with-open [reader (open-read readable-store)]
     (vec (protocols/read-assignments reader))))

  ([readable-store query]
   (errors/validate-spec! readable-store? readable-store)
   (errors/validate-spec! ::specs/assignments-query query)

   (with-open [reader (open-read readable-store)]
     (vec (protocols/read-assignments reader query)))))