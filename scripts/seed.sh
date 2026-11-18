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
fi

echo "Installation des dependances de seeding..."
"$VENV_DIR/bin/pip" install --quiet -r scripts/requirements-seed.txt

echo "Execution du peuplement de la base de donnees..."
"$VENV_DIR/bin/python" scripts/seed_test_data.py "$@"
