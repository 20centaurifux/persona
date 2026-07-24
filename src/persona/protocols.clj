(ns persona.protocols)

(defprotocol Writer
  "Backend operations that atomically persist or delete persona data."

  (write-persona! [this ns persona]
    "Creates or replaces `persona` in `ns` and returns nil.")

  (delete-persona! [this ns id]
    "Deletes the persona identified by `id` in `ns` and all of its assignments.

    Returns nil. Throws ExceptionInfo if the persona does not exist.")

  (write-assignments! [this ns persona-id assignments]
    "Atomically persists `assignments` for `persona-id` in `ns` and returns nil.

    `assignments` is a vector of maps containing :subject and :scope.
    Persisting the same assignment more than once has no additional effect.
    Throws ExceptionInfo if the persona does not exist.")

  (delete-assignments! [this ns query]
    "Atomically deletes assignments matching `query` in `ns` and returns them as a set.

    Every supplied :subject-match, :subject, :persona-id, and :scope value must
    match. An empty `query` deletes all assignments. Returns an empty
    set if no assignment matches."))

(defprotocol Reader
  "Backend operations that retrieve personas or assignments."

  (read-persona [this ns id]
    "Returns the persona identified by `id` in `ns`, or nil when absent.")

  (read-personas [this ns]
    "Returns all personas in `ns` as a vector in unspecified order.")

  (read-assignments
    [this ns]
    [this ns query]
    "Returns assignments in `ns`, optionally restricted by `query`.

    Every supplied :subject-match, :subject, :persona-id, and :scope value must
    match. The order of the returned assignments is unspecified."))

(defprotocol Store
  "Lifecycle operations for a persona store."

  (init [this]
    "Initializes resources and data structures required by the store."))

(defprotocol ReadableStore
  "Store operations that provide read access."

  (open-read [this]
    "Creates a Reader for the store.

    The caller must close the Reader if it implements java.io.Closeable."))

(defprotocol WritableStore
  "Store operations that provide write access."

  (open-write [this]
    "Creates a Writer for the store.

    The caller must close the Writer if it implements java.io.Closeable."))