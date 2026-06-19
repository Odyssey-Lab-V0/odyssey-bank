#!/bin/bash
set -e
echo "=== Setting up Odyssey Bank Dev Environment ==="

# Install k3d
echo "Installing k3d..."
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash

# Install ArgoCD CLI
echo "Installing ArgoCD CLI..."
curl -sSL -o /usr/local/bin/argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
chmod +x /usr/local/bin/argocd

# Install Helm
echo "Installing Helm..."
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

echo "=== Versions ==="
docker --version
k3d version
kubectl version --client
argocd version --client
helm version

echo "=== Setup complete! Run start-cluster.sh to launch K3s ==="
