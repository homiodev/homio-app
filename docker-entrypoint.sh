#!/bin/sh
set -ex

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "Initializing PostgreSQL database..."
    gosu postgres initdb -D "$PGDATA"
    echo "host all all 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
    gosu postgres pg_ctl -D "$PGDATA" -o "-c listen_addresses='localhost'" -w start
    echo "Creating database 'homio' and setting user password..."
    gosu postgres psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
        CREATE DATABASE homio;
        ALTER USER postgres WITH ENCRYPTED PASSWORD 'secret';
EOSQL
    gosu postgres pg_ctl -D "$PGDATA" -m fast -w stop
    echo "PostgreSQL initialization complete."
fi

echo "Starting PostgreSQL..."
gosu postgres pg_ctl -D "$PGDATA" -o "-c listen_addresses='localhost'" -w start

echo "Starting Homio application..."
exec java \
  "-Ddb-url=jdbc:postgresql://localhost:5432/homio?user=postgres&password=secret" \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED \
  -jar /homio-app.jar