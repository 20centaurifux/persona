(ns persona.resource-tests
  (:require [clojure.test :refer [deftest is]]
            [persona.core :as persona]
            [persona.protocols :as protocols]))

(def ^:private editor
  {:id :editor
   :name "Editor"
   :permissions #{:document/write}})

(defn- readable-store
  [reader]
  (reify protocols/ReadableStore
    (open-read [_]
      reader)))

(defn- writable-store
  [writer]
  (reify protocols/WritableStore
    (open-write [_]
      writer)))

(deftest preserves-closeable-handles
  (let [reader
        (reify
          protocols/Reader
          (read-persona [_ _ _] nil)
          (read-personas [_ _] [])
          (read-assignments [_ _] [])
          (read-assignments [_ _ _] [])

          java.io.Closeable
          (close [_]))
        writer
        (reify
          protocols/Writer
          (write-persona! [_ _ _])
          (delete-persona! [_ _ _])
          (write-assignments! [_ _ _ _])
          (delete-assignments! [_ _ _])

          java.io.Closeable
          (close [_]))]
    (with-open [opened-reader (persona/open-read (readable-store reader))]
      (is (identical? reader opened-reader)))

    (with-open [opened-writer (persona/open-write (writable-store writer))]
      (is (identical? writer opened-writer)))))

(deftest wraps-non-closeable-handles
  (let [reader
        (reify
          protocols/Reader
          (read-persona [_ _ _] nil)
          (read-personas [_ _] [:result])
          (read-assignments [_ _] [])
          (read-assignments [_ _ _] []))
        writer-called? (atom false)
        writer
        (reify
          protocols/Writer
          (write-persona! [_ _ _]
            (reset! writer-called? true)
            nil)
          (delete-persona! [_ _ _])
          (write-assignments! [_ _ _ _])
          (delete-assignments! [_ _ _]))
        closeable-reader (persona/open-read (readable-store reader))
        closeable-writer (persona/open-write (writable-store writer))]
    (is (instance? java.io.Closeable closeable-reader))
    (is (satisfies? protocols/Reader closeable-reader))
    (is (= [:result] (protocols/read-personas closeable-reader :test)))
    (is (nil? (.close ^java.io.Closeable closeable-reader)))

    (is (instance? java.io.Closeable closeable-writer))
    (is (satisfies? protocols/Writer closeable-writer))
    (is (nil? (protocols/write-persona! closeable-writer :test editor)))
    (is (true? @writer-called?))
    (is (nil? (.close ^java.io.Closeable closeable-writer)))))

(deftest closes-handles-after-operation
  (let [reader-closes (atom 0)
        reader
        (reify
          protocols/Reader
          (read-persona [_ _ _] nil)
          (read-personas [_ _] [])
          (read-assignments [_ _] [])
          (read-assignments [_ _ _] [])

          java.io.Closeable
          (close [_]
            (swap! reader-closes inc)))
        writer-closes (atom 0)
        writer
        (reify
          protocols/Writer
          (write-persona! [_ _ _] nil)
          (delete-persona! [_ _ _])
          (write-assignments! [_ _ _ _])
          (delete-assignments! [_ _ _])

          java.io.Closeable
          (close [_]
            (swap! writer-closes inc)))]
    (is (= [] (persona/personas (readable-store reader) :test)))
    (is (= 1 @reader-closes))

    (is (nil? (persona/put-persona! (writable-store writer) :test editor)))
    (is (= 1 @writer-closes))))

(deftest closes-handles-after-exception
  (let [reader-closes (atom 0)
        reader
        (reify
          protocols/Reader
          (read-persona [_ _ _] nil)
          (read-personas [_ _]
            (throw (ex-info "Read failed" {})))
          (read-assignments [_ _] [])
          (read-assignments [_ _ _] [])

          java.io.Closeable
          (close [_]
            (swap! reader-closes inc)))
        writer-closes (atom 0)
        writer
        (reify
          protocols/Writer
          (write-persona! [_ _ _]
            (throw (ex-info "Write failed" {})))
          (delete-persona! [_ _ _])
          (write-assignments! [_ _ _ _])
          (delete-assignments! [_ _ _])

          java.io.Closeable
          (close [_]
            (swap! writer-closes inc)))]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Read failed"
         (persona/personas (readable-store reader) :test)))
    (is (= 1 @reader-closes))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Write failed"
         (persona/put-persona! (writable-store writer) :test editor)))
    (is (= 1 @writer-closes))))

(deftest realizes-results-before-closing-handles
  (let [reader-closed? (atom false)
        reader
        (reify
          protocols/Reader
          (read-persona [_ _ _] nil)
          (read-personas [_ _]
            (map (fn [value]
                   (when @reader-closed?
                     (throw (ex-info "Reader already closed" {})))
                   value)
                 [editor]))
          (read-assignments [_ _]
            (map (fn [assignment]
                   (when @reader-closed?
                     (throw (ex-info "Reader already closed" {})))
                   assignment)
                 [{:persona-id :editor
                   :subject {:provider :test :id :editors}
                   :scope {:kind :project :id :website}}]))
          (read-assignments [_ _ _] [])

          java.io.Closeable
          (close [_]
            (reset! reader-closed? true)))
        writer-closed? (atom false)
        writer
        (reify
          protocols/Writer
          (write-persona! [_ _ _])
          (delete-persona! [_ _ _])
          (write-assignments! [_ _ _ _])
          (delete-assignments! [_ _ _]
            (map (fn [assignment]
                   (when @writer-closed?
                     (throw (ex-info "Writer already closed" {})))
                   assignment)
                 [{:persona-id :editor
                   :subject {:provider :test :id :editors}
                   :scope {:kind :project :id :website}}]))

          java.io.Closeable
          (close [_]
            (reset! writer-closed? true)))]
    (is (= [editor]
           (persona/personas (readable-store reader) :test)))
    (is (true? @reader-closed?))

    (reset! reader-closed? false)
    (is (= 1
           (count (persona/assignments (readable-store reader) :test))))
    (is (true? @reader-closed?))

    (is (= 1
           (count (persona/remove-assignments! (writable-store writer) :test))))
    (is (true? @writer-closed?))))