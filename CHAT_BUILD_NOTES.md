# Chat-built replacement for the stalled Replit build

Architecture used:

Android native app
→ Hermes official localhost API server (`127.0.0.1:8642`)
→ existing Hermes Agent in Termux
→ existing ORCA workspace / skills / cron / Obsidian

Termux `RUN_COMMAND` is only used to request gateway startup. Normal prompts, screenshots and scanner controls use the Hermes HTTP API instead of the unstable terminal TUI.

This avoids:
- nesting another Hermes runtime,
- depending on the Hermes TUI,
- copying private Termux files into the APK,
- autonomous brokerage execution.
