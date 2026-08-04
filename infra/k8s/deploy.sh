#!/usr/bin/env bash
# Applies all manifests in dependency order: namespace -> secrets/configmap -> infra -> apps.
# Run from anywhere; paths are resolved relative to this script.
set -euo pipefail
cd "$(dirname "$0")"

NS=platform

echo "==> Namespace"
kubectl apply -f namespace.yaml

echo "==> ConfigMap"
kubectl apply -f configmap.yaml

echo "==> Secret: platform-secrets"
if [ ! -f secrets.env ]; then
  echo "ERROR: k8s/secrets.env not found." >&2
  echo "       Copy k8s/secrets.env.example -> k8s/secrets.env and fill in real values." >&2
  exit 1
fi
kubectl create secret generic platform-secrets \
  --from-env-file=secrets.env -n "$NS" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Secret: platform-jwt-keys"
if [ ! -f secrets/jwt-private.pem ] || [ ! -f secrets/jwt-public.pem ]; then
  echo "ERROR: k8s/secrets/jwt-private.pem or jwt-public.pem not found." >&2
  echo "       See k8s/secrets/README.md to generate them." >&2
  exit 1
fi
kubectl create secret generic platform-jwt-keys \
  --from-file=JWT_PRIVATE_KEY=secrets/jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=secrets/jwt-public.pem \
  -n "$NS" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Infra: DBs, cache, kafka"
kubectl apply -n "$NS" -f infra/

echo "==> Waiting for infra to become ready (this can take ~1-2 min, Mongo/Kafka are slow to start)"
kubectl rollout status deployment/auth-db -n "$NS" --timeout=120s
kubectl rollout status deployment/user-db -n "$NS" --timeout=120s
kubectl rollout status deployment/order-db -n "$NS" --timeout=120s
kubectl rollout status deployment/redis -n "$NS" --timeout=120s
kubectl rollout status deployment/payment-db -n "$NS" --timeout=150s
kubectl rollout status deployment/kafka -n "$NS" --timeout=180s

echo "==> App: authservice (others depend on its JWKS endpoint)"
kubectl apply -n "$NS" -f apps/authservice.yaml
kubectl rollout status deployment/authservice -n "$NS" --timeout=180s

echo "==> Apps: userservice, orderservice, paymentservice"
kubectl apply -n "$NS" -f apps/userservice.yaml
kubectl apply -n "$NS" -f apps/orderservice.yaml
kubectl apply -n "$NS" -f apps/paymentservice.yaml
kubectl rollout status deployment/userservice -n "$NS" --timeout=180s
kubectl rollout status deployment/orderservice -n "$NS" --timeout=180s
kubectl rollout status deployment/paymentservice -n "$NS" --timeout=180s

echo "==> App: api-gateway"
kubectl apply -n "$NS" -f apps/api-gateway.yaml
kubectl rollout status deployment/api-gateway -n "$NS" --timeout=180s

echo "==> Ingress controller (ingress-nginx, kind provider)"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/kind/deploy.yaml
echo "    Waiting for the ingress-nginx controller pod to become ready"
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=180s

echo "==> Ingress: api-gateway"
kubectl apply -n "$NS" -f apps/ingress.yaml

echo
echo "==> Done. Platform reachable at http://localhost:8090"
