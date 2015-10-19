(ns clj-gatling.report
  (:require [clj-time.format :refer [formatter unparse-local]]
            [clojure.core.async :as a :refer [thread <!!]]))

(defn- flatten-one-level [coll]
  (mapcat #(if (sequential? %) % [%]) coll))

(defn map-request [scenario-name request]
  (let [start (str (:start request))
        end (str (:end request))
        id (str (:id request))
        execution-start start
        request-end start
        response-start end
        execution-end end
        result (if (:result request) "OK" "KO")]
    [scenario-name id "REQUEST" "" (:name request)  execution-start request-end response-start execution-end result "\u0020"]))

(defn- scenario->rows [scenario]
  (let [start (str (:start scenario))
        end (str (:end scenario))
        id (str (:id scenario))
        scenario-start [(:name scenario) id "USER" "START" start id]
        scenario-end [(:name scenario) id "USER" "END" end end]
        requests (mapcat #(vector (map-request (:name scenario) %)) (:requests scenario))]
    (concat [scenario-start] requests [scenario-end])))

(defn- process [header idx results output-writer]
  (let [scenarios (mapcat #(vector (scenario->rows %)) results)]
    (output-writer idx (conj (flatten-one-level scenarios) header))))
(defn- to-vector [channel]
  (loop [results []]
    (if-let [result (<!! channel)]
      (recur (conj results result))
      results)))

(defn create-result-lines [start-time buffer-size results-channel output-writer]
  (let [timestamp (unparse-local (formatter "yyyyMMddhhmmss") start-time)
        header ["clj-gatling" "simulation" "RUN" timestamp "\u0020" "2.0"]
        ;Note! core.async/partition is deprecated function.
        ;This should be changed to use transducers instead
        results (a/partition buffer-size results-channel)]
    (loop [idx 0]
      (when-let [result (<!! results)]
        (thread (process header idx result output-writer))
        (recur (inc idx))))))
