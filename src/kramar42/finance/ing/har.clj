(ns kramar42.finance.ing.har
  (:require [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [excel-clj.core :as excel])
  (:import (java.io ByteArrayInputStream)))

(defn str->stream [^String in]
  (new ByteArrayInputStream (.getBytes in)))

(defn parse-json [^String in]
  (some-> in
          str->stream
          (transit/reader :json)
          transit/read))

(defn transaction->map [transaction]
  {:subject  (get transaction "subject")
   :info     (str/join "\n" (get transaction "subjectLines"))
   :amount   (Float/parseFloat (get-in transaction ["amount" "value"]))
   :date     (get transaction "executionDate")
   ;:id       (get transaction "id")
   ;:currency (get-in transaction ["amount" "currency"])
   })

(defn read-har [filename]
  (-> filename
      io/input-stream
      (transit/reader :json)
      transit/read))

(defn extract-transactions [har]
  (->> (get-in har ["log" "entries"])
       (filter #(str/includes? (get-in % ["request" "url"]) "transactions?agreementType="))
       (map #(get-in % ["response" "content" "text"]))
       (map parse-json)
       (mapcat #(get % "transactions"))
       (map transaction->map)))

(defn extract-subjects [transactions]
  (->> transactions
       (group-by :subject)
       (map (fn [[k v]] {:subject k
                         :count   (count v)
                         :amount  (reduce + (map :amount v))}))
       (sort-by :count)
       reverse))

(defn read-directory [dir]
  (->> dir
       io/file
       file-seq
       (filter #(.isFile %))
       (map read-har)
       (mapcat extract-transactions)
       (into #{})))

(defn create-document [transactions filename]
  (let [subjects (extract-subjects transactions)
        document {"Transactions" (excel/table-grid transactions)
                  "Subjects"     (excel/table-grid subjects)}]
    (excel/write! document filename)))

(comment
(create-document (read-directory "resources/har") "ing.xlsx")
)

