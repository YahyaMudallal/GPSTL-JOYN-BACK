#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Dedicated local database; no existing application database is used.
compose=(docker compose -p joyn-additional-tests -f compose.tests.yml)
command -v docker >/dev/null || { echo 'Installez/démarrez Docker Desktop puis relancez.' >&2; exit 1; }
trap '"${compose[@]}" down' EXIT
"${compose[@]}" up -d --wait
export DB_URL='jdbc:postgresql://127.0.0.1:55432/joyn_db'
export DB_USER='joyn_user' DB_PASSWORD='joyn_test_local'
export SPRING_DATASOURCE_URL="$DB_URL"
export SPRING_DATASOURCE_USERNAME="$DB_USER" SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
export JOYN_STORAGE_PROVIDER='local'
bash mvnw "-Dspring.datasource.url=$DB_URL" "-Dspring.datasource.username=$DB_USER" "-Dspring.datasource.password=$DB_PASSWORD" test "$@"
