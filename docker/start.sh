#!/bin/sh
# Sobe o PostgreSQL e, em seguida, a aplicação Kotlin (Tacuapi PoC).
set -e

echo "[start] Subindo o PostgreSQL..."
docker-entrypoint.sh postgres &

echo "[start] Aguardando o PostgreSQL ficar pronto..."
until pg_isready --host=127.0.0.1 --port=5432 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" > /dev/null 2>&1; do
    sleep 0.5
done

echo "[start] Subindo a aplicação..."
exec /app/bin/poc-example
