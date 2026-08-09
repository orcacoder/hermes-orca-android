package com.eatmoore.hermesorca

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

enum class Health { UNKNOWN, CHECKING, OK, OFFLINE }

data class UiState(
    val health: Health = Health.UNKNOWN,
    val busy: Boolean = false,
    val mode: String = "LIVE_ADVISORY?",
    val baseUrl: String = "http://127.0.0.1:8642",
    val apiKey: String = "",
    val lastOutput: String = "",
    val signals: String = "",
    val jobsJson: String = "",
    val connectorReport: String = "",
    val detailedHealth: String = "",
    val journalText: String = "",
    val systemMessage: String = "",
    val termuxInstalled: Boolean = false,
    val vaultUri: String? = null,
    val sharedStatus: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("hermes_orca", 0)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
        if (changed == "api_key" || changed == "base_url") {
            _state.value = _state.value.copy(
                apiKey = prefs.getString("api_key", "") ?: "",
                baseUrl = prefs.getString("base_url", "http://127.0.0.1:8642") ?: "http://127.0.0.1:8642",
                systemMessage = "Pairing data updated from Termux."
            )
            checkHealth()
        }
    }
    private val api = HermesApi()
    private val termux = TermuxBridge(app)

    private val _state = MutableStateFlow(
        UiState(
            apiKey = prefs.getString("api_key", "") ?: "",
            baseUrl = prefs.getString("base_url", "http://127.0.0.1:8642") ?: "http://127.0.0.1:8642",
            termuxInstalled = termux.isInstalled(),
            vaultUri = prefs.getString("vault_uri", null)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        checkHealth()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        _state.value = _state.value.copy(apiKey = key, systemMessage = "API key saved in app-private storage.")
        checkHealth()
    }

    fun checkHealth() {
        viewModelScope.launch {
            _state.value = _state.value.copy(health = Health.CHECKING)
            val ok = api.health(_state.value.baseUrl)
            _state.value = _state.value.copy(
                health = if (ok) Health.OK else Health.OFFLINE,
                systemMessage = if (ok) "Hermes API server reachable." else "Hermes API server not reachable on localhost:8642."
            )
            if (ok && _state.value.apiKey.isNotBlank()) refreshSystem()
        }
    }

    fun refreshSystem() {
        val key = _state.value.apiKey
        if (key.isBlank()) return
        viewModelScope.launch {
            val health = api.detailedHealth(_state.value.baseUrl, key).getOrElse { "ERROR: ${it.message}" }
            val caps = api.capabilities(_state.value.baseUrl, key).getOrElse { "ERROR: ${it.message}" }
            val skills = api.skills(_state.value.baseUrl, key).getOrElse { "ERROR: ${it.message}" }
            val tools = api.toolsets(_state.value.baseUrl, key).getOrElse { "ERROR: ${it.message}" }
            _state.value = _state.value.copy(
                detailedHealth = health,
                connectorReport = "CAPABILITIES\n$caps\n\nSKILLS\n$skills\n\nTOOLSETS / CONNECTORS\n$tools"
            )
        }
    }

    fun startHermesGateway() {
        _state.value = _state.value.copy(systemMessage = termux.startHermesGateway())
    }

    fun sendCommand(prompt: String) {
        viewModelScope.launch { runAgent(prompt, OutputTarget.COMMAND) }
    }

    fun runScan() {
        val prompt = """
Use the persistent orca-trading-desk skill.
Run one fresh advisory scan using only sources/tools currently available to Hermes.
Respect desk isolation, source/timestamp verification, calendar/broker truth, and human-execution-only law.
Journal the scan according to the installed ORCA automation if available.
Return only: source coverage, board summary, and any new/changed ARMED or ENTER tickets.
If no actionable result, say [SILENT].
        """.trimIndent()
        viewModelScope.launch { runAgent(prompt, OutputTarget.COMMAND) }
    }

    fun refreshSignals() {
        val prompt = """
Read the current ORCA state and journal files available to you.
Return a compact lock-screen-safe report of only currently active actionable cases in ARMED, ENTER, or MANAGE state.
Begin the response with exactly one line:
ALERTABLE=YES
if at least one NEW or MATERIALLY CHANGED ARMED/ENTER signal needs user attention, otherwise:
ALERTABLE=NO

For each case include desk, symbol, direction, grade, action, entry/zone, hard stop, TP1/TP2, risk status, expiry/cancel-by, source freshness, and exact required trigger.
Do not invent missing data. If none exist, return ALERTABLE=NO followed by NO ACTIONABLE SIGNALS.
        """.trimIndent()
        viewModelScope.launch { runAgent(prompt, OutputTarget.SIGNALS) }
    }

    fun loadJobs() {
        val key = _state.value.apiKey
        if (key.isBlank()) {
            _state.value = _state.value.copy(systemMessage = "Set HERMES API KEY first.")
            return
        }
        viewModelScope.launch {
            val r = api.getJobs(_state.value.baseUrl, key)
            _state.value = _state.value.copy(jobsJson = r.getOrElse { "ERROR: ${it.message}" })
        }
    }

    fun runThirtyMinuteJob() {
        val key = _state.value.apiKey
        if (key.isBlank()) {
            _state.value = _state.value.copy(systemMessage = "Set HERMES API KEY first.")
            return
        }
        viewModelScope.launch {
            val jobsText = api.getJobs(_state.value.baseUrl, key).getOrElse {
                _state.value = _state.value.copy(systemMessage = "Jobs read failed: ${it.message}")
                return@launch
            }
            val jobId = findOrcaJobId(jobsText)
            if (jobId == null) {
                _state.value = _state.value.copy(
                    jobsJson = jobsText,
                    systemMessage = "Could not find an active job named ORCA 30m Market Scan."
                )
                return@launch
            }
            val result = api.runJob(_state.value.baseUrl, key, jobId)
            _state.value = _state.value.copy(
                systemMessage = result.fold(
                    onSuccess = { "ORCA 30m Market Scan triggered. Job ID: $jobId" },
                    onFailure = { "Job trigger failed: ${it.message}" }
                )
            )
            loadJobs()
        }
    }

    private fun findOrcaJobId(text: String): String? {
        return runCatching {
            val array = when {
                text.trim().startsWith("[") -> JSONArray(text)
                else -> {
                    val obj = JSONObject(text)
                    obj.optJSONArray("jobs") ?: obj.optJSONArray("items") ?: JSONArray()
                }
            }
            for (i in 0 until array.length()) {
                val j = array.optJSONObject(i) ?: continue
                val name = j.optString("name").ifBlank { j.optString("title") }
                if (name.equals("ORCA 30m Market Scan", ignoreCase = true) ||
                    name.contains("ORCA 30m", ignoreCase = true)) {
                    return@runCatching j.optString("id").ifBlank { j.optString("job_id") }
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun runAgent(prompt: String, outputTarget: OutputTarget) {
        val key = _state.value.apiKey
        if (key.isBlank()) {
            _state.value = _state.value.copy(systemMessage = "Set HERMES API KEY in SYSTEM first.")
            return
        }
        _state.value = _state.value.copy(busy = true)
        val text = api.responses(
            baseUrl = _state.value.baseUrl,
            key = key,
            input = prompt,
            conversation = "orca-native"
        ).getOrElse { "ERROR: ${it.message}" }

        if (outputTarget == OutputTarget.SIGNALS && text.lineSequence().firstOrNull()?.trim() == "ALERTABLE=YES") {
            val body = text.lineSequence().drop(1).joinToString("\n").trim().take(1800)
            OrcaNotifications.signal(
                getApplication(),
                "HERMES // ORCA SIGNAL",
                body.ifBlank { "New actionable ORCA signal. Open the app for the verified ticket." }
            )
        }

        _state.value = when (outputTarget) {
            OutputTarget.COMMAND -> _state.value.copy(busy = false, lastOutput = text)
            OutputTarget.SIGNALS -> _state.value.copy(busy = false, signals = text)
        }
    }

    fun setObsidianVault(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("vault_uri", uri.toString()).apply()
            _state.value = _state.value.copy(vaultUri = uri.toString(), systemMessage = "Obsidian vault permission persisted.")
            refreshJournal()
        } catch (e: Exception) {
            _state.value = _state.value.copy(systemMessage = "Vault permission failed: ${e.message}")
        }
    }

    fun writeObsidianTest() {
        val raw = _state.value.vaultUri ?: run {
            _state.value = _state.value.copy(systemMessage = "Select an Obsidian vault first.")
            return
        }

        try {
            val resolver = getApplication<Application>().contentResolver
            val root = DocumentFile.fromTreeUri(getApplication(), Uri.parse(raw))
                ?: error("Could not open vault tree")
            val orca = childDir(root, "ORCA Trading")
            val system = childDir(orca, "99 System")
            val existing = system.findFile("Native App Connected.md")
            val file = existing ?: system.createFile("text/markdown", "Native App Connected.md")
                ?: error("Could not create test note")

            resolver.openOutputStream(file.uri, "wt")!!.bufferedWriter().use {
                it.write(
                    """
---
cssclasses:
  - orca-terminal
---

# HERMES // ORCA

`NATIVE APP` CONNECTED

Timestamp: ${ZonedDateTime.now()}

This note was written by the native Android companion through Android Storage Access Framework.
                    """.trimIndent()
                )
            }
            _state.value = _state.value.copy(systemMessage = "Obsidian write test passed.")
            refreshJournal()
        } catch (e: Exception) {
            _state.value = _state.value.copy(systemMessage = "Obsidian test failed: ${e.message}")
        }
    }

    fun refreshJournal() {
        val raw = _state.value.vaultUri ?: return
        try {
            val root = DocumentFile.fromTreeUri(getApplication(), Uri.parse(raw)) ?: return
            val journalDir = root.findFile("ORCA Trading")
                ?.findFile("03 Daily Journals")
            if (journalDir == null || !journalDir.isDirectory) {
                _state.value = _state.value.copy(journalText = "No ORCA Trading/03 Daily Journals folder found yet.")
                return
            }
            val files = journalDir.listFiles()
                .filter { it.isFile && (it.name?.endsWith(".md") == true) }
                .sortedByDescending { it.name }
                .take(3)

            val resolver = getApplication<Application>().contentResolver
            val text = buildString {
                for (file in files) {
                    append("=== ${file.name} ===\n")
                    val content = resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    append(content.takeLast(12000))
                    append("\n\n")
                }
            }.ifBlank { "No journal notes yet." }

            _state.value = _state.value.copy(journalText = text)
        } catch (e: Exception) {
            _state.value = _state.value.copy(journalText = "Journal read failed: ${e.message}")
        }
    }

    fun launchPackage(packageName: String) {
        val pm = getApplication<Application>().packageManager
        val launch = pm.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            _state.value = _state.value.copy(systemMessage = "App not found: $packageName")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(launch)
    }

    fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        when {
            intent.type?.startsWith("image/") == true -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) ingestImage(uri)
            }
            intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    viewModelScope.launch {
                        runAgent(
                            "Shared into HERMES // ORCA from another Android app:\n\n$text\n\nTreat this as PROVIDED data, verify before using as execution truth, and merge it into the relevant active case.",
                            OutputTarget.COMMAND
                        )
                    }
                }
            }
        }
    }

    private fun ingestImage(uri: Uri) {
        viewModelScope.launch {
            val key = _state.value.apiKey
            if (key.isBlank()) {
                _state.value = _state.value.copy(sharedStatus = "Image received, but Hermes API key is not configured.")
                return@launch
            }
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val mime = getApplication<Application>().contentResolver.getType(uri) ?: "image/png"
                val prompt = """
A chart/account screenshot was shared into HERMES // ORCA from Android.
Extract only visible facts first. Treat visible numbers as PROVIDED, not independently VERIFIED.
Identify likely desk/instrument if visible.
Merge with an existing active case only when symbol identity is clear.
State what minimum additional data is required before an actionable ticket can be issued.
Do not place or modify any order.
                """.trimIndent()

                _state.value = _state.value.copy(busy = true, sharedStatus = "Analyzing shared image…")
                val text = api.responseWithImage(_state.value.baseUrl, key, prompt, mime, bytes)
                    .getOrElse { "Share analysis failed: ${it.message}" }
                _state.value = _state.value.copy(
                    busy = false,
                    sharedStatus = text,
                    lastOutput = text
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(sharedStatus = "Could not read shared image: ${e.message}")
            }
        }
    }

    private fun childDir(parent: DocumentFile, name: String): DocumentFile {
        return parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: error("Could not create folder $name")
    }

    private enum class OutputTarget { COMMAND, SIGNALS }
}
