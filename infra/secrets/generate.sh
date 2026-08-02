#!/usr/bin/env bash
# Generate the secret files docker-compose.prod.yml mounts at /run/secrets.
#
#   ./infra/secrets/generate.sh /opt/careconnect/secrets
#
# One file per secret, named exactly as the property the services read
# (JWT_SECRET, not careconnect.jwt.secret) — Spring's configtree import uses the
# filename verbatim as the property name, and a Kubernetes Secret mounted as a
# volume produces the same layout from the same keys. See
# backend/platform-starter/.../FileSecretsConfigTreeTest.java, which pins that.
#
# Refuses to overwrite. Rotating JWT_SECRET invalidates every issued token and
# rotating POSTGRES_PASSWORD after the database is initialised locks the
# services out of it — neither should happen because somebody re-ran a setup
# script. Rotate deliberately, with the runbook:
# docs/operations/deployment-oracle-vm.md
set -euo pipefail

DIR="${1:-./secrets}"

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required" >&2
    exit 1
fi

mkdir -p "$DIR"
# Before writing anything: the directory holds plaintext credentials, so it is
# owner-only. Docker reads these as root when it builds the mount, so nothing
# needs broader access.
chmod 700 "$DIR"

write_secret() {
    local name="$1" value="$2"
    local path="$DIR/$name"

    if [ -s "$path" ]; then
        echo "  = $name (exists, left alone)"
        return
    fi
    # printf, not echo: a trailing newline is tolerated by Spring's configtree
    # (it trims) but not by everything that might read these files later.
    printf '%s' "$value" > "$path"
    chmod 600 "$path"
    echo "  + $name"
}

echo "Writing secrets to $DIR"

# 64 bytes: the JWT signing key. HS256 needs at least 32, and there is no reason
# to be near the floor.
write_secret JWT_SECRET             "$(openssl rand -base64 64 | tr -d '\n')"
write_secret GATEWAY_SHARED_SECRET  "$(openssl rand -base64 48 | tr -d '\n')"
# Alphanumeric only: this one ends up inside a JDBC URL and a libpq connection
# string, where +/= would need escaping in two different dialects.
write_secret POSTGRES_PASSWORD      "$(openssl rand -hex 24)"

# The admin password is typed by a human at a login form, so it is generated
# shorter and printed once. Everything above is only ever read by a machine.
if [ ! -s "$DIR/SEED_ADMIN_PASSWORD" ]; then
    admin_password="$(openssl rand -base64 12 | tr -d '\n/+=' | cut -c1-14)Aa1"
    write_secret SEED_ADMIN_PASSWORD "$admin_password"
    echo
    echo "  Administrator sign-in password (shown once, not recoverable):"
    echo "      $admin_password"
else
    write_secret SEED_ADMIN_PASSWORD ""
fi

echo
echo "Done. Point compose at them with SECRETS_DIR=$DIR"
