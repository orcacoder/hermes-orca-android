#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

mkdir -p "$PREFIX/bin" "$HOME/ORCA/state" "$HOME/.hermes"
cat > "$PREFIX/bin/hermes-orca-pair" <<'PAIR'
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PKG="com.eatmoore.hermesorca"
BASE="http://127.0.0.1:8642"
ENV="$HOME/.hermes/.env"
LOG="$HOME/ORCA/state/native_gateway.log"

mkdir -p "$HOME/.hermes" "$HOME/ORCA/state"
touch "$ENV"

set_env() {
  key="$1"
  value="$2"
  if grep -q "^${key}=" "$ENV"; then
    sed -i "s#^${key}=.*#${key}=${value}#" "$ENV"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV"
  fi
}

KEY="$(grep -m1 '^API_SERVER_KEY=' "$ENV" 2>/dev/null | cut -d= -f2- || true)"
if [ -z "$KEY" ] || [ "${#KEY}" -lt 24 ]; then
  KEY="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
fi

set_env API_SERVER_ENABLED true
set_env API_SERVER_HOST 127.0.0.1
set_env API_SERVER_PORT 8642
set_env API_SERVER_KEY "$KEY"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " HERMES // ORCA  ONE-COMMAND PAIR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

server_ok=0
if curl -fsS --max-time 3 "$BASE/health" >/dev/null 2>&1; then
  if curl -fsS --max-time 5 -H "Authorization: Bearer $KEY" "$BASE/v1/models" >/dev/null 2>&1; then
    server_ok=1
  fi
fi

if [ "$server_ok" -ne 1 ]; then
  hermes gateway stop >/dev/null 2>&1 || true
  sleep 1
  nohup hermes gateway > "$LOG" 2>&1 < /dev/null &
  echo "[..] Hermes gateway starting"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    if curl -fsS --max-time 2 "$BASE/health" >/dev/null 2>&1 && \
       curl -fsS --max-time 3 -H "Authorization: Bearer $KEY" "$BASE/v1/models" >/dev/null 2>&1; then
      server_ok=1
      break
    fi
  done
fi

if [ "$server_ok" -ne 1 ]; then
  echo "[FAIL] Hermes API did not become healthy"
  tail -40 "$LOG" 2>/dev/null || true
  exit 1
fi

echo "[OK] Hermes backend online"
echo "[OK] API authentication verified"

if ! /system/bin/cmd package path "$PKG" >/dev/null 2>&1; then
  echo "[FAIL] HERMES // ORCA v0.3+ is not installed"
  exit 1
fi

PAIR_OUT="$(/system/bin/am broadcast --receiver-foreground \
  -n "$PKG/.PairingReceiver" \
  -a "$PKG.PAIR" \
  --es api_key "$KEY" \
  --es base_url "$BASE" 2>&1)"

if ! printf '%s\n' "$PAIR_OUT" | grep -q 'HERMES_ORCA_PAIRED'; then
  echo "[FAIL] Native pairing receiver rejected the request"
  printf '%s\n' "$PAIR_OUT"
  echo "Make sure HERMES // ORCA v0.3+ is installed."
  exit 1
fi

echo "[OK] API key saved inside native app"

/system/bin/am start \
  -n "$PKG/.MainActivity" \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  >/dev/null 2>&1 || true

sleep 1

echo "[OK] Native app launched"
echo
echo "HERMES BACKEND :: ONLINE"
echo "API AUTH       :: VERIFIED"
echo "NATIVE PAIRING :: SAVED"
echo "APP HEALTH     :: CHECK REQUESTED"
echo "ENDPOINT       :: 127.0.0.1:8642"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
PAIR

chmod 700 "$PREFIX/bin/hermes-orca-pair"
echo "Installed command: hermes-orca-pair"
