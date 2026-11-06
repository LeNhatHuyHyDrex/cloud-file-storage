#!/bin/sh
# Khoi dong SeaweedFS (master + volume + filer + S3 gateway trong 1 tien trinh).
# Sinh file cau hinh danh tinh S3 tu bien moi truong de khong phai commit secret.
set -e
cat > /tmp/s3.json <<EOF
{
  "identities": [
    {
      "name": "nimbus-app",
      "credentials": [{ "accessKey": "${S3_ACCESS_KEY}", "secretKey": "${S3_SECRET_KEY}" }],
      "actions": ["Admin", "Read", "Write", "List", "Tagging"]
    }
  ]
}
EOF
exec weed server \
  -dir=/data \
  -ip=seaweedfs \
  -ip.bind=0.0.0.0 \
  -master.volumeSizeLimitMB=1024 \
  -s3 \
  -s3.port=8333 \
  -s3.config=/tmp/s3.json
