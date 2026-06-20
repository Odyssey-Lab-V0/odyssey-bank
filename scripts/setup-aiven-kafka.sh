#!/bin/bash
# Run this on the master node: 129.213.82.14
# Creates the aiven-kafka-ssl secret and removes in-cluster Kafka/Zookeeper
set -e

NAMESPACE=banking

echo "=== Creating Aiven Kafka SSL secret ==="

# Write certs to temp files
cat > /tmp/access.key << 'KEYEOF'
-----BEGIN PRIVATE KEY-----
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
AA5m2I0wigRM0M4VBTffBqs=
-----END PRIVATE KEY-----
KEYEOF

cat > /tmp/access.cert << 'CERTEOF'
-----BEGIN CERTIFICATE-----
MIIEYTCCAsmgAwIBAgIUC2jLwb6bkFdRA89byqZUseMqvRcwDQYJKoZIhvcNAQEM
BQAwOjE4MDYGA1UEAwwvM2YwYmNiYzgtNzBmZS00OGRjLWI5ZjgtMzkyMmE0NGRm
MDViIFByb2plY3QgQ0EwHhcNMjYwNjIwMDEwNTA2WhcNMjgwOTE3MDEwNTA2WjA/
MRcwFQYDVQQKDA5rYWZrYS0zMjM5ZmYyYTERMA8GA1UECwwIdThxMHczeDExETAP
BgNVBAMMCGF2bmFkbWluMIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA
v/qKKA0gW/lSULrfk9q9r/BuIGXSjpclO9VIb1zX7oSwEhVUNDDS/dJEFBSnPpS2
TCtD+Jb3uzZereq0sF5ms1D1cZovyKpai7lkw9C7BmkPaiux6WNqBJlD4CsOJ41R
csIHXazryU2zXimIb2ohJIjHIBILsLb5K6yeBl7TaoT4q0/my6m4Tj59yOayCxnd
P6lJ7MC6jb5/OeuzXXNAlUeWbS2LnqH4ODNaP+rpDSBLwgy6iN5aCdK3BgGDm5lU
A2xTpdOIGXa5hbCLvr94bB1ZUFSz4/D6fs/v4WO2AlKdfC0b7fRElBQH11g2X+Bh
ROWrKs3wlAkKXpJD94FkmBQoxN4qPjQk6qSF0cfwYK8EOZ7YGx5jr3qZv1CEggAu
T7HAEHD8dGenlIioEPx9tkrRYeS0M1PJ96dZy1m4TKONHCGMiKek+bqb9W9Cg6qg
hQf2D21F85YwGRThuHYH+54qOvHsLcdIZtaTcrrHvABTux/azuXkhIFU/3KTtPKb
AgMBAAGjWjBYMB0GA1UdDgQWBBSFXRdHXO22/8NhAZmMHBjSV+UDwjAJBgNVHRME
AjAAMAsGA1UdDwQEAwIFoDAfBgNVHSMEGDAWgBQ6LFkp/34A/I0TLR90d2m2fY6J
vzANBgkqhkiG9w0BAQwFAAOCAYEAUbfcKFRR4D7jYWfpoU0K0virVvaFfMkF+E77
ryqbHRlVf30EqDWjilINLwjU29LehC4kYGsyWRHgb576JbkppS/b65BqTWv+e5lU
H8YSn8bwh7NGzTbwK7bf8leZytMEUiIvnMAqP1ihA5dGlkfVwepxlisC/G7mEEQy
D9DASgtPnoCS9B3GHkY9i0mpWaOupsG4OD6MkZobsKQ0tbfTPQu6VpfldBXPo7nU
+xJ4mTZbgC69RVxSAnwwOLv0rKAZoPG/V62PeoLiKPcU8dC/YQkm+Z3Xq6hb4v4m
wO0M5HHk9zBjqhDglen6wOiMFv8EFk4sEQrv2lSgR/NWM8x6P/BTveSG2Ketim6w
d1iCahDmLFeCO9VeqJK4BQ7E95aG8EiAO4SlMlwUjRG0iw49uJMx6YKr7epMkEBH
VF9plEJb+LpkglPOt1l7mict2pVK8WPYNspCCu7l6tpEi6BMfgownWZP5TaSwLIf
j6P5r/Sw6IBi2wcjg0eJxdQQJFZj
-----END CERTIFICATE-----
CERTEOF

cat > /tmp/ca.cert << 'CAEOF'
-----BEGIN CERTIFICATE-----
MIIERDCCAqygAwIBAgIUDGkKulBj9ZyhhxIdf7GIuM7LLrkwDQYJKoZIhvcNAQEM
BQAwOjE4MDYGA1UEAwwvM2YwYmNiYzgtNzBmZS00OGRjLWI5ZjgtMzkyMmE0NGRm
MDViIFByb2plY3QgQ0EwHhcNMjYwNjIwMDA1OTMzWhcNMzYwNjE3MDA1OTMzWjA6
MTgwNgYDVQQDDC8zZjBiY2JjOC03MGZlLTQ4ZGMtYjlmOC0zOTIyYTQ0ZGYwNWIg
UHJvamVjdCBDQTCCAaIwDQYJKoZIhvcNAQEBBQADggGPADCCAYoCggGBAPB2zVSg
3+A1mOSGTeFdp0wcoMWeAVbHD0+h1gueEHvrXsRQOHuVshpWMPGFUVXF2wNH+jcl
heuuHEDMTXYn0RTiOAjH+4dxGQsj/ol7yrlr4D7UWRindBKIr6XF04d2+Sm9r2ZR
YZ+QO8ELLzemmGieVg+pj/5cKOzSmZyF05gGqhc1RWwr/SidOP1JAQnRyObWfXKC
3XxheT71hafkF+8hEaRyQLmpgPdjC6Gg1hEHr+Lrhe9i8KkTv8vNJD4Tdt1prd+n
ICjQdaR1Idt8LSn9+dOYqgBgcUaLooHaqq4BhXUE+Kj2dMtuI/4oqwNtC6Wp3mSk
3C4Q1NSqtzIrQ0WAe3OHqffYNwuKLqJSDV6/YUgk6B3QKera/RO1bLXP7+77Uhae
9tbZlQrgL9OT2lUpoG1XnF35rydPDT7ZRfL/uPLUn0BwIrI2qzjRbwOidtHuIftX
Y9/bKOQGI1Z3vU8Zoani0uw2HIpadeLMU0+lss4rikdR6c98f2erSDIYswIDAQAB
o0IwQDAdBgNVHQ4EFgQUOixZKf9+APyNEy0fdHdptn2Oib8wEgYDVR0TAQH/BAgw
BgEB/wIBADALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQEMBQADggGBADCws+mPf4cS
PeQY/0x3y6B8t2ShmXNHZvx9dsO0IXneLoWLtyRrTb0SfL3iFToZ4BCo6cu69x/m
ztlkJzTpnjxaoURoCMiqypODF2MgLU1KmuKprilqixc2U+nzGhXnmi+6IEyZ3xHx
7gPChaKorbGNkK6oHOb9ElZGGMiUzKsUEer1CbL7lzI8e0Psd+BIHMI4Vgd2Mvrt
kxeRiCZUCMHGiUvrYrNrqVPi6uCAP+Mzaci/33kSh9iPHKMbeAxGExJuku6BpGL8
B8heDcnf3OknS/yKPqqlK9dlWBbGMon3aSUQz43vbHzBhULpeMH5tHuoeDUuT7Fw
YKJ/UVYZ0x4W489QozaALXEkBWKE8T/o04jSiNK6iR6ewCoGFIBbsJYh/RDv+0Ms
sL0Iyc1R2Sm6v6PiA/MlmgRM/bKsY9+h/mWlBbAAtgfMAKSJPz96rcIrU1gL7f0O
a7pUTRNie9WMtHa0fyc7Rsxps7KF/qj4BPPAWn1Ci1L/e8Jw5JZlVQ==
-----END CERTIFICATE-----
CAEOF

echo "=== Deleting old in-cluster Kafka and Zookeeper ==="
kubectl delete deployment kafka zookeeper -n $NAMESPACE --ignore-not-found
kubectl delete service kafka zookeeper -n $NAMESPACE --ignore-not-found

echo "=== Creating aiven-kafka-ssl secret ==="
kubectl create secret generic aiven-kafka-ssl \
  --from-file=access.key=/tmp/access.key \
  --from-file=access.cert=/tmp/access.cert \
  --from-file=ca.cert=/tmp/ca.cert \
  -n $NAMESPACE \
  --dry-run=client -o yaml | kubectl apply -f -

echo "=== Cleaning up temp files ==="
rm -f /tmp/access.key /tmp/access.cert /tmp/ca.cert

echo "=== Patching all service configmaps with Aiven broker + SSL ==="
for svc in iam-service banking-core-service kyc-service onboarding-service aml-service notification-service; do
  CM="${svc}-config"
  echo "Patching $CM..."
  kubectl patch configmap $CM -n $NAMESPACE --type merge -p '{
    "data": {
      "KAFKA_BROKERS": "kafka-3239ff2a-odesssey-bank.c.aivencloud.com:23611",
      "KAFKA_SECURITY_PROTOCOL": "SSL",
      "KAFKA_SSL_KEYSTORE_TYPE": "PEM",
      "KAFKA_SSL_TRUSTSTORE_TYPE": "PEM",
      "KAFKA_SSL_KEY_LOCATION": "/etc/kafka-ssl/access.key",
      "KAFKA_SSL_CERTIFICATE_LOCATION": "/etc/kafka-ssl/access.cert",
      "KAFKA_SSL_CA_LOCATION": "/etc/kafka-ssl/ca.cert"
    }
  }' 2>/dev/null || echo "  ConfigMap $CM not found yet, will be created by ArgoCD"
done

echo ""
echo "=== Restarting deployments to pick up new config ==="
kubectl rollout restart deployment iam-service banking-core-service kyc-service onboarding-service aml-service notification-service -n $NAMESPACE

echo ""
echo "=== Waiting for rollout (60s)... ==="
sleep 60
kubectl get pods -n $NAMESPACE

echo ""
echo "=== Done! Check logs if any pod is not Running: ==="
echo "  kubectl logs -n banking <pod-name> --tail=50"
