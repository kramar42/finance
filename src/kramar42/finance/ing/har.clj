(ns kramar42.finance.ing.har
  (:require [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [excel-clj.core :as excel])
  (:import (java.io ByteArrayInputStream)))

(def har
  (-> "resources/mijn.ing.nl.har"
      io/input-stream
      (transit/reader :json)
      transit/read))

(defn str->stream [^String in]
  (new ByteArrayInputStream (.getBytes in)))

(defn parse-json [^String in]
  (when in
    (-> in
        str->stream
        (transit/reader :json)
        transit/read)))

(defn process-transaction [transaction]
  {:id (get transaction "id")
   :subject (get transaction "subject")
   :info (str/join "\n" (get transaction "subjectLines"))
   :amount (get-in transaction ["amount" "value"])
   :currency (get-in transaction ["amount" "currency"])
   :date (get transaction "executionDate")})

(def transactions
  (->> (get-in har ["log" "entries"])
       (filter #(str/includes? (get-in % ["request" "url"]) "transactions?agreementType="))
       (map #(get-in % ["response" "content" "text"]))
       (map parse-json)
       (mapcat #(get % "transactions"))
       (map process-transaction)))
(comment
  (->> transactions
       (group-by :subject)
       (sort-by (comp count second))
       reverse
       (take 5)))

(let [subjects (->> transactions
                    (map :subject)
                    (into #{})
                    (map #(hash-map :subject %)))
      document {"Transactions" (excel/table-grid transactions)
                "Subjects"     (excel/table-grid subjects)}]
  (excel/write! document "ing.xlsx"))