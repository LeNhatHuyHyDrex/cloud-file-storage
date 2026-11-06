#!/bin/bash
# Chay 1 lan khi EC2 khoi tao: cai Docker, chuan bi thu muc va file .env cho Docker Compose.
set -euo pipefail

apt-get update
apt-get install -y docker.io docker-compose-v2 curl
systemctl enable --now docker
usermod -aG docker ubuntu

APP_DIR=/home/ubuntu/nimbus-drive
mkdir -p "$APP_DIR"
cat > "$APP_DIR/.env" <<EOF
COMPOSE_FILE=docker-compose.yml:docker-compose.aws.yml
COMPOSE_PROFILES=proxy
APP_IMAGE=${app_image}
DOMAIN=${domain}
DB_PASSWORD=${db_password}
JWT_SECRET=${jwt_secret}
S3_REGION=${region}
S3_BUCKET=${bucket}
EOF
chmod 600 "$APP_DIR/.env"
chown -R ubuntu:ubuntu "$APP_DIR"
# docker-compose.yml + deploy/ se duoc GitHub Actions copy len o lan deploy dau tien
