(ns ing
  (:require [buddy.core.hash :as hash]
            [buddy.core.codecs.base64 :as base64]
            [buddy.core.keys :as keys]
            [buddy.core.dsa :as dsa]
            [clj-http.client :as http]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import (java.time ZonedDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)
           (java.util Locale UUID)
           (java.io ByteArrayInputStream)))

(def api-endpoint "https://api.ing.com")
(def client-id (System/getenv "CLIENT_ID"))

(def http-key (keys/private-key (System/getenv "HTTP_CERT_PATH")))

(def tls-ks-file (System/getenv "TLS_KS_PATH"))
(def tls-ks-pass (System/getenv "TLS_KS_PASS"))

(def date-format (DateTimeFormatter/ofPattern "E, dd MMM yyyy HH:mm:ss O" Locale/US))
(defn date-str []
  (.format (ZonedDateTime/now ZoneOffset/UTC) date-format))

(defn rand-uuid []
  (str (UUID/randomUUID)))

(defn base64-str [data]
  (new String
       (base64/encode data)
       "UTF-8"))

(defn digest [in]
  (-> in
      hash/sha256
      base64-str))

(defn sign [msg]
  (-> msg
      (dsa/sign {:key http-key
                 :alg :rsassa-pkcs15+sha256})
      base64-str))

(defn sign-headers [headers-map]
  (let [headers-names (str/join " " (map name (keys headers-map)))
        headers-vals  (str/join "\n" (map (fn [[k v]] (str (name k) ": " v)) headers-map))]
    (println headers-vals)
    (format (str "Signature keyId=\"%s\",algorithm=\"rsa-sha256\""
                 ",headers=\"%s\",signature=\"%s\"")
            client-id headers-names (sign headers-vals))))

(defn request-target [{:keys [api/method api/path api/query]}]
  (str (name method) " " path (if query
                                (str "?" (http/generate-query-string query))
                                "")))

(defn api-headers
  [{:keys [api/payload api/auth api/headers] :as request}]
  (let [date      (date-str)
        digest    (str "SHA-256=" (digest (or payload "")))
        headers   (merge {"(request-target)" (request-target request)
                          :date              date
                          :digest            digest
                          :x-ing-reqid       (rand-uuid)}
                         headers)
        signature (sign-headers headers)]
    (merge
      {:content-type  "application/x-www-form-urlencoded"
       :authorization (or auth signature)
       :signature signature}
      (dissoc headers "(request-target)"))))

(defn api-request
  [{:keys [api/method api/path api/query api/payload] :as request}]
  {:pre [(every? some? [method path])]}
  (http/request
    {:method               method
     :url                  (str api-endpoint path)
     :query-params         query
     :headers              (api-headers request)
     :body                 payload
     :keystore             tls-ks-file
     :keystore-pass        tls-ks-pass
     :unexceptional-status #{200 400 401}}))

(defn token-request []
  (api-request {:api/method  :post
                :api/path    "/oauth2/token"
                :api/payload (http/generate-query-string
                               {:grant_type "client_credentials"})}))

(defn str->stream [^String in]
  (new ByteArrayInputStream (.getBytes in)))

(defn parse-json [^String in]
  (-> in
      str->stream
      (transit/reader :json)
      transit/read))

(defn new-token []
  (-> (token-request)
      :body
      parse-json
      (get "access_token")))

(def access-token (atom nil))

(defn reset-token [] (reset! access-token (new-token)))

(comment
  (token-request)
  (reset-token)
  @access-token

  (api-request {:api/method :get
                :api/path   "/greetings/single"
                :api/query  {:scope "greetings:view"}
                :api/auth   (str "Bearer " @access-token)})

  )