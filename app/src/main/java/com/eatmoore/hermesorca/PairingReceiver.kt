package com.eatmoore.hermesorca

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PairingReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PAIR = "com.eatmoore.hermesorca.PAIR"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_BASE_URL = "base_url"
        private const val ALLOWED_BASE_URL = "http://127.0.0.1:8642"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAIR) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "WRONG_ACTION"
            return
        }

        val key = intent.getStringExtra(EXTRA_API_KEY)?.trim().orEmpty()
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)?.trim()
            ?.ifBlank { ALLOWED_BASE_URL } ?: ALLOWED_BASE_URL

        if (baseUrl != ALLOWED_BASE_URL) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "BASE_URL_REJECTED"
            return
        }

        if (key.length < 24) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "KEY_REJECTED"
            return
        }

        context.getSharedPreferences("hermes_orca", Context.MODE_PRIVATE)
            .edit()
            .putString("api_key", key)
            .putString("base_url", ALLOWED_BASE_URL)
            .putString("pairing_status", "PAIRED_FROM_TERMUX")
            .putLong("paired_at_ms", System.currentTimeMillis())
            .apply()

        resultCode = Activity.RESULT_OK
        resultData = "HERMES_ORCA_PAIRED"
    }
}
