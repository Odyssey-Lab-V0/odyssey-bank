#!/bin/bash
set -e
echo "=== Starting K3d Cluster for Odyssey Bank ==="

# Create k3d cluster with port mappings
k3d cluster create odyssey \
  --agents 1 \
  --port "8080:80@loadbalancer" \
  --port "8443:443@loadbalancer" \
  --k3s-arg "--disable=traefik@server:0" \
  --wait

echo "=== Cluster created, configuring kubectl ==="
k3d kubeconfig merge odyssey --kubeconfig-switch-context

echo "=== Nodes ==="
kubectl get nodes

# Create namespaces
kubectl create namespace banking --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -

echo "=== Installing ArgoCD ==="
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "=== Waiting for ArgoCD to be ready (up to 5 min)... ==="
kubectl wait --for=condition=available --timeout=300s deployment/argocd-server -n argocd

echo "=== ArgoCD is ready! Getting admin password... ==="
ARGOCD_PWD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)
echo ""
echo "============================================"
echo "  ArgoCD Admin Password: $ARGOCD_PWD"
echo "  Port forward: kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo "  Then open: https://localhost:8080"
echo "  Username: admin"
echo "============================================"
echo ""
echo "=== Next: apply your secrets then bootstrap ArgoCD ==="
echo "  kubectl apply -f k8s/argocd/app-of-apps.yaml"
