#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export MAVEN_OPTS="${MAVEN_OPTS:--Xmx768m}"

modules=(
  service-registry
  api-gateway
  auth-service
  room-service
  message-service
  media-service
  presence-service
  notification-service
  websocket-service
  payment-service
  admin-server
)

for module in "${modules[@]}"; do
  mvn -pl "$module" -am package -DskipTests
done

docker compose up --build -d
