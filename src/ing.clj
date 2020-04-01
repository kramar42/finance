(ns ing
  (:require
    [buddy.core.hash :as hash]
    [buddy.core.codecs.base64 :as base64]
    [buddy.core.keys :as keys]
    [buddy.core.dsa :as dsa]
    [clj-http.client :as http]
    [cognitect.transit :as transit]
    [clojure.java.io :as io])
  (:import
    (java.time ZonedDateTime ZoneOffset)
    (java.time.format DateTimeFormatter)
    (java.util Locale UUID)
    (java.io ByteArrayInputStream)))

(def endpoint "https://api.ing.com/oauth2/token")
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

(defn auth-header [signature]
  (format (str "Signature keyId=\"%s\",algorithm=\"rsa-sha256\""
               ",headers=\"(request-target) date digest x-ing-reqid\",signature=\"%s\"")
          client-id signature))

(defn token-request [endpoint]
  (let [payload   "grant_type=client_credentials"
        digest    (str "SHA-256=" (digest payload))
        date      (date-str)
        req-id    (rand-uuid)
        to-sign   (format "(request-target): %s %s\ndate: %s\ndigest: %s\nx-ing-reqid: %s"
                          "post" "/oauth2/token" date digest req-id)
        signature (sign to-sign)]
    (http/post endpoint
               {:headers       {:content-type  "application/x-www-form-urlencoded"
                                :x-ing-reqid   req-id
                                :date          date
                                :digest        digest
                                :authorization (auth-header signature)}
                :body          payload
                :keystore      tls-ks-file
                :keystore-pass tls-ks-pass})))

(defn str->stream [^String in]
  (new ByteArrayInputStream (.getBytes in)))

(defn parse-json [^String in]
  (-> in
      str->stream
      (transit/reader :json)
      transit/read))

(defn new-token [endpoint]
  (-> endpoint
      token-request
      :body
      parse-json
      (get "access_token")))

(def access-token (atom nil))

(defn reset-token []
  (reset! access-token (new-token endpoint)))

(comment
  (reset-token)
  )