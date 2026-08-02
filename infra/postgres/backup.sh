#!/bin/sh
# Periodic logical backup of every CareConnect database.
#
# Runs as a long-lived container rather than a host cron job on purpose: the
# schedule then travels with the stack. Moving to Kubernetes turns this into a
# CronJob running the same image and the same script; moving it to host cron
# would mean the backup silently does not exist on the next machine.
#
# pg_dumpall, not pg_dump: there are eight databases (ADR-003, database per
# service) plus the roles that own them, and a restore that recreates seven of
# eight databases is not a restore.
#
# Logical dumps rather than WAL archiving / PITR. The recovery objective here is
# "get the clinic back", not "get the clinic back to 14:32:07", and a logical
# dump restores with one psql invocation and no archive to babysit. Revisit this
# the moment losing a day of data stops being acceptable.
set -eu

BACKUP_DIR=/backups
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
INTERVAL="${BACKUP_INTERVAL_SECONDS:-86400}"

# The password arrives as a mounted file, never as an environment variable
# (see the secrets block in docker-compose.prod.yml). Exporting it into this
# process's environment is the narrowest place it can live: pg_dumpall has no
# file-based option, and a ~/.pgpass would put it on disk unencrypted anyway.
if [ -f /run/secrets/POSTGRES_PASSWORD ]; then
    PGPASSWORD="$(cat /run/secrets/POSTGRES_PASSWORD)"
    export PGPASSWORD
fi

log() {
    echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') [backup] $*"
}

mkdir -p "$BACKUP_DIR"

# Trap so `docker compose down` during a dump does not leave a truncated file
# that looks like a valid backup.
cleanup() {
    rm -f "$BACKUP_DIR"/*.partial 2>/dev/null || true
    exit 0
}
trap cleanup TERM INT

log "starting; interval=${INTERVAL}s retention=${RETENTION_DAYS}d"

while true; do
    stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
    target="$BACKUP_DIR/careconnect-$stamp.sql.gz"

    # Write to .partial, then rename. Rename is atomic, so a file with the final
    # name is always a dump that finished — which is the property that makes an
    # unattended backup worth having.
    if pg_dumpall --clean --if-exists | gzip -9 > "$target.partial" 2>/dev/null; then
        mv "$target.partial" "$target"
        log "wrote $(basename "$target") ($(du -h "$target" | cut -f1))"
    else
        rm -f "$target.partial"
        # Do not exit: a database that is briefly unreachable must not disable
        # backups until somebody notices. restart:unless-stopped would mask the
        # difference between "one dump failed" and "backups are broken".
        log "FAILED — will retry at the next interval"
    fi

    # Prune after a successful write, never before: a failing dump must not also
    # delete the last good one.
    find "$BACKUP_DIR" -name 'careconnect-*.sql.gz' -type f \
        -mtime "+$RETENTION_DAYS" -print -delete | while read -r old; do
        log "pruned $(basename "$old")"
    done

    sleep "$INTERVAL"
done
