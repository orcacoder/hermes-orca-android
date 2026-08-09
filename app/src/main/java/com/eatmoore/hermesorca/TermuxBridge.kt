package com.eatmoore.hermesorca

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

class TermuxBridge(private val app: Application) {

    fun isInstalled(): Boolean {
        return try {
            app.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun startHermesGateway(): String {
        if (!isInstalled()) return "Termux is not installed."

        return try {
            val intent = Intent().apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf(
                        "-lc",
                        "mkdir -p ~/ORCA/state; " +
                            "(command -v termux-wake-lock >/dev/null && termux-wake-lock || true); " +
                            "if curl -fsS --max-time 1 http://127.0.0.1:8642/health >/dev/null 2>&1; then " +
                            "echo 'Hermes API already online'; " +
                            "else nohup hermes gateway > ~/ORCA/state/native_gateway.log 2>&1 < /dev/null & echo 'Hermes gateway start requested'; fi"
                    )
                )
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home/ORCA")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_LABEL", "HERMES // ORCA gateway")
                putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "Starts the local Hermes gateway/API server for the native companion.")
            }

            app.startService(intent)
            "Start request sent to Termux. Check Hermes health again in a few seconds."
        } catch (e: SecurityException) {
            "RUN_COMMAND permission or Termux allow-external-apps is not enabled: ${e.message}"
        } catch (e: Exception) {
            "Could not start Hermes through Termux: ${e.message}"
        }
    }
}
