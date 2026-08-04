#!/usr/bin/env bash
# Deletes the whole kind cluster, including its node container and all volumes
# (local-path-provisioner PVs live inside that container, so this wipes DB data too).
set -euo pipefail
kind delete cluster --name platform
