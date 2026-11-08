#!/bin/sh
# Starts the container's PostgreSQL, then HostelFix (SPRING_PROFILES_ACTIVE=docker).
# The database listens only inside the container and needs no password.
set -e

PGBIN=/usr/lib/postgresql/16/bin
PGDATA=/var/lib/postgresql/data

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    su postgres -c "$PGBIN/initdb -D $PGDATA -U postgres --auth=trust --encoding=UTF8 --locale=C.UTF-8" > /dev/null
fi

su postgres -c "$PGBIN/pg_ctl -D $PGDATA -o '-c listen_addresses=127.0.0.1' -l /tmp/postgres.log -w start" > /dev/null

if ! su postgres -c "psql -tAc \"SELECT 1 FROM pg_database WHERE datname = 'hostelfix'\"" | grep -q 1; then
    su postgres -c "createdb hostelfix"
fi

echo "HostelFix is starting. Open http://localhost:8080 once it says 'Started DemoApplication'."
if [ -z "$ADMIN_EMAILS" ]; then
    echo "No ADMIN_EMAILS given: nobody can use Staff Login. Add -e ADMIN_EMAILS=you@example.com to docker run."
fi

exec java $JAVA_OPTS -jar /app/app.jar
