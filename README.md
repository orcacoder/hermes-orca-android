# HERMES // ORCA — Native Android Companion v0.2

A native Android front end for the existing Hermes Agent running in Termux on the same Nothing Phone.

## Architecture

Native APK
→ `127.0.0.1:8642`
→ Hermes API server/gateway in Termux
→ existing ORCA workspace, skills, cron jobs, tools and Obsidian workflow

The app does not embed a second Hermes runtime and does not depend on the unstable Hermes TUI.

## v0.2 features

- DESK dashboard with local Hermes status.
- Native COMMAND screen using `POST /v1/responses`.
- Ad-hoc ORCA scan.
- Read Hermes scheduled jobs from `GET /api/jobs`.
- Trigger the `ORCA 30m Market Scan` job with `POST /api/jobs/{id}/run`.
- Active ARMED / ENTER / MANAGE ticket view.
- Native high-priority Android notification only when Hermes explicitly returns `ALERTABLE=YES`.
- TradingView / MT5 screenshot share-target using Hermes inline image support.
- Native Obsidian vault selection through Android Storage Access Framework.
- Latest ORCA daily-journal reader.
- Connector/skill/toolset inspection using:
  - `/health/detailed`
  - `/v1/capabilities`
  - `/v1/skills`
  - `/v1/toolsets`
- Quick-launch controls for TradingView, MT5 and Obsidian.
- Termux `RUN_COMMAND` bridge to request `hermes gateway` startup.
- Advisory-only execution boundary; no broker order-routing implementation.

## Hermes / phone setup

Copy `TERMUX_NATIVE_BRIDGE_SETUP.sh` to the phone and run it in Termux, or perform the equivalent manually.

The important Hermes settings are:

```text
API_SERVER_ENABLED=true
API_SERVER_KEY=<private-local-key>
```

Then start:

```text
cd ~/ORCA
hermes gateway
```

The native app connects to:

```text
http://127.0.0.1:8642
```

Keep Hermes bound to loopback. The API exposes the full Hermes toolset.

For the native START HERMES button, Termux must allow external apps and Android must grant the app the Termux RUN_COMMAND permission.

## Build

The project can be opened directly in Android Studio.

A GitHub Actions workflow is also included. It installs Android API 36, Gradle 8.10.2 and builds:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Execution boundary

HERMES // ORCA is an advisory/command front end. It intentionally contains no real-money broker order placement or modification path.
