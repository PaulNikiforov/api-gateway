#!/usr/bin/env bash
# Builds all 5 service images and loads them into the kind cluster's containerd,
# so kubelet never has to pull them from a registry (imagePullPolicy: IfNotPresent).
set -euo pipefail
cd "$(dirname "$0")/.."

CLUSTER=platform
SERVICES="authservice userservice orderservice paymentservice api-gateway"

for s in $SERVICES; do
  echo "==> Building $s:local"
  docker build -t "$s:local" "./$s"
  echo "==> Loading $s:local into kind cluster '$CLUSTER'"
  kind load docker-image "$s:local" --name "$CLUSTER"
done

echo "==> All images built and loaded."
