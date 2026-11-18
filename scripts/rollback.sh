#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Charger le fichier .env s'il existe
if [ -f ".env" ]; then
    set -a
    source .env
    set +a
fi

VENV_DIR=".venv"

if [ ! -d "$VENV_DIR" ]; then
    echo "Creation de l'environnement virtuel Python..."
    python3 -m venv "$VENV_DIR"
    "$VENV_DIR/bin/pip" install --quiet -r scripts/requirements-seed.txt
fi

"$VENV_DIR/bin/python" scripts/rollback_seed.py "$@"
