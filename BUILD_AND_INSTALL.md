# Build + install checklist

1. Build a debug APK using Android Studio or the included GitHub Actions workflow.
2. Install the APK on the Nothing Phone.
3. In Termux, run `TERMUX_NATIVE_BRIDGE_SETUP.sh`.
4. Copy the generated `API_SERVER_KEY`.
5. In HERMES // ORCA → SYSTEM, save the same key.
6. Tap CHECK HERMES. Expected: `LOCAL // ONLINE`.
7. Select the Obsidian vault.
8. Tap WRITE CONNECTION TEST.
9. Tap REFRESH CONNECTORS / SKILLS / HEALTH.
10. Tap REFRESH ARMED / ENTER / MANAGE.
11. Share one TradingView screenshot to HERMES // ORCA.
12. Verify no live brokerage order is placed.
