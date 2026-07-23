(ns persona.errors
  (:require [clojure.spec.alpha :as s]))

(defn- invalid-parameter-form
  [spec value parameter]
  `(throw
    (ex-info
     ~(str "Invalid parameter: " (name parameter))
     {:parameter ~parameter
      :spec ~spec
      :value ~value
      :explain-data (s/explain-data ~spec ~value)})))

(defmacro throw-invalid-parameter
  "Throws ExceptionInfo describing a parameter that does not satisfy spec.

  `value` must be a symbol. Its unqualified name becomes the :parameter keyword
  in the exception data, which also contains :spec, :value, and :explain-data.
  `spec` identifies the specification used to explain the invalid value."
  [spec value]
  (when-not (symbol? value)
    (throw
     (IllegalArgumentException.
      "throw-invalid-parameter requires a parameter symbol")))
  (let [parameter (keyword (name value))
        spec-value (gensym "spec")
        parameter-value (gensym "value")]
    `(let [~spec-value ~spec
           ~parameter-value ~value]
       ~(invalid-parameter-form spec-value parameter-value parameter))))

(defmacro validate-spec!
  "Returns `value` if it satisfies `spec`, otherwise throws ExceptionInfo.

  `value` must be a parameter symbol. Its name identifies the invalid
  parameter in the exception data."
  [spec value]
  (when-not (symbol? value)
    (throw
     (IllegalArgumentException.
      "validate-spec! requires a parameter symbol")))
  (let [parameter (keyword (name value))
        spec-value (gensym "spec")
        parameter-value (gensym "value")]
    `(let [~spec-value ~spec
           ~parameter-value ~value]
       (if (s/valid? ~spec-value ~parameter-value)
         ~parameter-value
         ~(invalid-parameter-form spec-value parameter-value parameter)))))

(defn throw-persona-not-found
  "Throws ExceptionInfo for a missing persona.

  The exception data contains `persona-id` as :persona-id."
  [persona-id]
  (throw
   (ex-info
    (str "Persona does not exist: " persona-id)
    {:persona-id persona-id})))