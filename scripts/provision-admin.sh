#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
read -r -p 'Email du nouvel administrateur : ' JOYN_ADMIN_EMAIL
read -r -s -p 'Mot de passe privé (16 caractères minimum) : ' JOYN_ADMIN_PASSWORD
printf '\n'
read -r -s -p 'Confirmez le mot de passe : ' joyn_admin_confirmation
printf '\n'
if [[ "$JOYN_ADMIN_PASSWORD" != "$joyn_admin_confirmation" ]]; then
  echo 'Les mots de passe diffèrent. Aucun compte créé.' >&2
  exit 1
fi
unset joyn_admin_confirmation
export JOYN_ADMIN_EMAIL JOYN_ADMIN_PASSWORD
trap 'unset JOYN_ADMIN_EMAIL JOYN_ADMIN_PASSWORD' EXIT
bash mvnw spring-boot:run -Dspring-boot.run.arguments='--joyn.admin.provision.enabled=true --server.address=127.0.0.1 --server.port=0'
