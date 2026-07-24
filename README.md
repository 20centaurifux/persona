# persona

A lightweight hierarchical authorization library for Clojure.

`persona` provides an authorization model based on **namespaces**, **personas**,
**subjects**, and **scopes**. It is intentionally independent of authentication,
identity providers, and application-specific domain models.

The library answers a single question:

> Is a subject allowed to perform a permission on a resource?

## Design goals

- Simple, predictable API
- Hierarchical scope inheritance
- Dynamic personas
- No built-in user or group model
- No dependency on LDAP, OIDC, Keycloak, or other IAM systems
- Backend-agnostic
- Functional authorization decisions

## Quick start

The public API operates on stores. The included in-memory store supports both
reading and writing:

```clojure
(require '[persona.core :as persona]
         '[persona.store.memory :as memory])

(def store (memory/->memory-store))

(persona/put-persona!
 store
 :acme
 {:id :editor
  :name "Editor"
  :permissions #{:document/read
                 :document/write
                 :document/publish}})

(persona/put-assignments!
 store
 :acme
 :editor
 [{:subject {:provider :ldap
             :id "team:editors"}
   :scope {:kind :project
           :id "website"}}])

(persona/allowed?
 store
 :acme
 #{{:provider :ldap
    :id "team:editors"}}
 :document/publish
 [{:kind :document :id "design.md"}
  {:kind :project :id "website"}
  {:kind :organization :id "acme"}])
;; => true
```

## Concepts

### Namespace

A namespace separates personas and assignments belonging to different
applications, tenants, or other domains within the same store. It is supplied
as the `ns` parameter to every persona, assignment, resolution, and
authorization operation:

```clojure
(persona/personas store :acme)
(persona/personas store :other-tenant)
```

Personas and assignments in one namespace are isolated from every other
namespace. The same persona ID may therefore have different definitions and
assignments in different namespaces. Reading, resolving, authorizing, or
deleting data in one namespace does not access or affect data in another.

Namespace values may be keywords, integers, or non-blank strings.

### persona

A persona is a collection of permissions:

```clojure
{:id :editor
 :name "Editor"
 :description "Can edit and publish documents"
 :permissions #{:document/read
                :document/write
                :document/publish}}
```

The `:description` is optional. `persona` IDs may be keywords, integers, or
non-blank strings. The persona name must be a non-blank string.

`persona` definitions are application-defined and may be created, modified, or
removed at runtime.

### Subject

A subject is anything that can receive a persona assignment. Typical subjects
include IAM groups, users, service accounts, applications, and external
identities.

A subject is a map containing `:provider` and `:id`. Both values may be
keywords, integers, or non-blank strings:

```clojure
{:provider :ldap
 :id "team:engineering"}
```

### Scope

A scope identifies where a persona applies. `persona` does not define a scope
hierarchy. Instead, the application supplies the complete scope path during
authorization:

```clojure
[{:kind :document :id "design.md"}
 {:kind :project :id "website"}
 {:kind :organization :id "acme"}]
```

The first element represents the requested resource. Each following element is
a parent scope.

Every scope is a map containing a keyword `:kind` and an `:id`. Scope IDs may
be keywords, integers, or non-blank strings.

### Assignment

An assignment binds a subject to a persona on a scope:

```text
Subject ──► Persona ──► Scope
```

```clojure
(persona/put-assignments!
 store
 :acme
 :editor
 [{:subject {:provider :ldap
             :id "team:editors"}
   :scope {:kind :project
           :id "website"}}])
```

The assignment is effective for every descendant scope contained in the
supplied path.

## Authorization model

Authorization succeeds when one of the supplied subjects has a persona
assignment on any scope in the supplied path and the persona contains the
requested permission.

The application supplies the complete path. `persona` does not discover parent
scopes itself.

## Public API

All public data operations receive a store and a namespace. Read operations
require a `ReadableStore`; write operations require a `WritableStore`. A store
may implement both protocols.

Use the predicates to inspect a store's capabilities:

```clojure
(persona/readable-store? store)
(persona/writable-store? store)
```

### persona operations

```clojure
(persona/put-persona! writable-store ns persona)
(persona/remove-persona! writable-store ns id)

(persona/persona readable-store ns id)
(persona/personas readable-store ns)
```

`put-persona!` creates or replaces a persona. Removing a persona also removes
its assignments. Reading a missing persona returns `nil`; `personas` returns a
vector in unspecified order.

### Assignments

```clojure
(persona/put-assignments!
 writable-store
 ns
 persona-id
 [{:subject subject
   :scope scope}])

(persona/remove-all-assignments! writable-store ns)
(persona/remove-assignments! writable-store ns query)

(persona/assignments readable-store ns)
(persona/assignments readable-store ns query)
```

`put-assignments!` writes a vector of subject and scope pairs atomically. The
referenced persona must exist.

`remove-all-assignments!` removes every assignment in `ns`.
`remove-assignments!` requires a non-empty query, which may contain these keys:

- `:subject` matches a complete subject.
- `:subject-match` matches by `:provider`, `:id`, or both. At least one field
  must be present.
- `:persona-id` matches a persona ID.
- `:scope` matches a complete scope.

All supplied query fields must match. Unknown query keys are rejected.
`remove-all-assignments!` and `remove-assignments!` return the removed
assignments as a set; `assignments` returns the matching assignments as a
vector in unspecified order.

### Effective assignments and personas

```clojure
(persona/resolve-assignments readable-store ns subjects path)
(persona/resolve-persona-ids readable-store ns subjects path)
(persona/effective-personas readable-store ns subjects path)
```

`subjects` must be a set of subjects and `path` must be a vector of scopes.

- `resolve-assignments` returns all assignments effective for at least one
  subject on any scope in the path.
- `resolve-persona-ids` returns the corresponding unique persona IDs.
- `effective-personas` returns the corresponding existing persona definitions.
  Assignments referring to a missing persona are ignored.

### Authorization

```clojure
(persona/allowed? readable-store ns subjects permission path)
```

For example:

```clojure
(persona/allowed?
 store
 :acme
 #{{:provider :ldap
    :id "team:editors"}}
 :document/publish
 [{:kind :document :id "design.md"}
  {:kind :project :id "website"}
  {:kind :organization :id "acme"}])
```

`allowed?` returns `true` when an effective persona contains the requested
permission, otherwise `false`.

## Stores and backend protocols

Backend protocols live in `persona.protocols`.

A backend exposes its capabilities through store protocols:

- `ReadableStore/open-read` creates a `Reader`.
- `WritableStore/open-write` creates a `Writer`.
- `Store/init` initializes stores that require explicit initialization.

The access protocols contain the backend operations:

- `Reader` provides `read-persona`, `read-personas`, and both arities of
  `read-assignments`.
- `Writer` provides `write-persona!`, `delete-persona!`,
  `write-assignments!`, and `delete-assignments!`.

Every Reader and Writer operation receives `ns`; backend implementations must
keep persona and assignment data isolated by that namespace.

The public API opens a Reader or Writer for each operation and closes it with
`with-open`. `persona.core/open-read` and `persona.core/open-write` always
return a `java.io.Closeable`. If a backend handle is not closeable, `persona`
wraps it with a no-op `close` implementation. Backends can therefore return
lightweight handles, as the memory store does, or closeable resources such as
database sessions.

The store itself is not closed by public API operations. Its lifecycle remains
the application's responsibility.

### In-memory store

`persona.store.memory/->memory-store` creates an empty store implementing both
`ReadableStore` and `WritableStore`. It stores each namespace independently.
Each reader uses a snapshot taken when it is opened. Writers update the state
used for subsequently opened readers:

```clojure
(require '[persona.store.memory :as memory])

(def store (memory/->memory-store))
```

## Responsibilities

### The application provides

- Authentication
- Identity provider integration
- Subject resolution
- Scope hierarchy
- Permission definitions
- Store lifecycle management

### persona provides

- Persona management
- Assignment management
- Hierarchical authorization
- Effective persona resolution
- Reader and Writer resource management