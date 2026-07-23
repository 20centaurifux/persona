(ns persona.errors-tests
  (:require [clojure.test :refer [deftest is testing]]
            [persona.errors :as errors]))

(deftest test-throws-invalid-parameter
  (testing "evaluates spec once"
    (let [evaluations (atom 0)
          value nil
          exception
          (try
            (errors/throw-invalid-parameter
             (do
               (swap! evaluations inc)
               string?)
             value)
            (catch clojure.lang.ExceptionInfo exception
              exception))
          data (ex-data exception)]
      (is (= "Invalid parameter: value" (ex-message exception)))
      (is (= :value (:parameter data)))
      (is (= string? (:spec data)))
      (is (nil? (:value data)))
      (is (some? (:explain-data data)))
      (is (= 1 @evaluations)))))

(deftest test-validate-spec!
  (testing "evaluates spec once"
    (let [evaluations (atom 0)
          value nil
          exception
          (try
            (errors/validate-spec!
             (do
               (swap! evaluations inc)
               string?)
             value)
            nil
            (catch clojure.lang.ExceptionInfo exception
              exception))]
      (is (some? exception))
      (is (= :value (:parameter (ex-data exception))))
      (is (= 1 @evaluations)))))