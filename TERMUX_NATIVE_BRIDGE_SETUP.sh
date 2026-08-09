#!/data/data/com.termux/files/usr/bin/bash
set -e

mkdir -p "$HOME/.termux" "$HOME/ORCA/state"
PROP="$HOME/.termux/termux.properties"
touch "$PROP"

if ! grep -q '^allow-external-apps=true' "$PROP"; then
  printf '\nallow-external-apps=true\n' >> "$PROP"
fi

ENV="$HOME/.hermes/.env"
mkdir -p "$HOME/.hermes"
touch "$ENV"

if ! grep -q '^API_SERVER_ENABLED=' "$ENV"; then
  printf '\nAPI_SERVER_ENABLED=true\n' >> "$ENV"
fi

if ! grep -q '^API_SERVER_KEY=' "$ENV"; then
  KEY="$(python - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
  printf 'API_SERVER_KEY=%s\n' "$KEY" >> "$ENV"
  echo "Generated local API key:"
  echo "$KEY"
  echo
  echo "Save this key in the HERMES // ORCA native app under SYSTEM."
else
  echo "API_SERVER_KEY already exists in ~/.hermes/.env"
fi

termux-reload-settings 2>/dev/null || true

echo
echo "Starting Hermes gateway..."
echo "Keep API_SERVER_HOST at the default 127.0.0.1."
echo
cd "$HOME/ORCA"
exec hermes gateway
