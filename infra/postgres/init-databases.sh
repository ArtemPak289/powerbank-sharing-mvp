#!/bin/bash
set -e

# Creates per-service databases inside the single PostgreSQL container.
# Runs automatically on first container start via /docker-entrypoint-initdb.d/.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE user_db;
    CREATE DATABASE payment_db;
    CREATE DATABASE station_db;
    CREATE DATABASE rental_db;
EOSQL
