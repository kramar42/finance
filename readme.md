Main goal: auto-import all ING transactions to YNAB.
Secondary goal: auto-apply YNAB category based on transaction info.

#### ING setup
[Getting started link](https://developer.ing.com/openbanking/get-started/openbanking)

``` bash
# generate http key
openssl genrsa -out httpCert.key -passout stdin 2048
# generate Certificate Signing Request
openssl req -sha256 -new -key httpCert.key -out httpCert.csr
# generate X.509 certificate
openssl x509 -req -sha256 -days 365 -in httpCert.csr -signkey httpCert.key -out httpCert.crt

# generate tls key
openssl genrsa -out tlsCert.key -passout stdin 2048
openssl req -sha256 -new -key tlsCert.key -out tlsCert.csr
openssl x509 -req -sha256 -days 365 -in tlsCert.csr -signkey tlsCert.key -out tlsCert.crt
# convert tls key & certificate to PKCS 12 format
openssl pkcs12 -export -out keystore_tls.p12 -inkey tlsCert.key -in tlsCert.crt
```

Upload `httpCert.crt` and `tlsCert.crt` to ING app Certificates section.

#### Runtime
Required environment variables:
- CLIENT_ID
- HTTP_CERT_PATH
- TLS_KS_PATH
- TLS_KS_PASS