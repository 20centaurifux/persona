(ns persona.protocols)

(defprotocol Writer
  "Backend operations that atomically persist or delete persona data."

  (write-persona! [this persona]
    "Creates or replaces `persona` and returns nil.")

  (delete-persona! [this id]
    "Deletes the persona identified by `id` and all of its assignments.

    Returns nil. Throws ExceptionInfo if the persona does not exist.")

  (write-assignments! [this persona-id assignments]
    "Atomically persists `assignments` for `persona-id` and returns nil.

    `assignments` is a vector of maps containing :subject and :scope.
    Persisting the same assignment more than once has no additional effect.
    Throws ExceptionInfo if the persona does not exist.")

  (delete-assignments! [this query]
    "Atomically deletes assignments matching `query` and returns them as a set.

    Every supplied :subject-match, :subject, :persona-id, and :scope value must
    match. An empty `query` deletes all assignments. Returns an empty
    set if no assignment matches."))

(defprotocol Reader
  "Backend operations that retrieve personas or assignments."

  (read-persona [this id]
    "Returns the persona identified by `id`, or nil when it does not exist.")

  (read-personas [this]
    "Returns a vector containing all personas in unspecified order.")

  (read-assignments
    [this]
    [this query]
    "Returns a vector containing all assignments or those matching `query`.

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