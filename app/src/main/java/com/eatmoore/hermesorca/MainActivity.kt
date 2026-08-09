package com.eatmoore.hermesorca

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val sharedIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedIntent.value = intent

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
        }

        setContent {
            HermesOrcaTheme {
                val vm: MainViewModel = viewModel()
                val incoming = sharedIntent.value
                LaunchedEffect(incoming) {
                    incoming?.let { vm.handleShareIntent(it) }
                    sharedIntent.value = null
                }
                App(vm = vm, activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedIntent.value = intent
    }
}

private enum class Tab(val label: String) {
    DESK("DESK"),
    COMMAND("CMD"),
    SIGNALS("SIGNALS"),
    JOURNAL("JOURNAL"),
    SYSTEM("SYSTEM")
}

@Composable
private fun App(vm: MainViewModel, activity: Activity) {
    var tab by rememberSaveable { mutableStateOf(Tab.DESK) }
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = AppColors.Black,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Panel) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(if (tab == item) "■" else "□", fontFamily = FontFamily.Monospace) },
                        label = { Text(item.label, fontFamily = FontFamily.Monospace) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Jade,
                            selectedTextColor = AppColors.Jade,
                            unselectedIconColor = AppColors.Faint,
                            unselectedTextColor = AppColors.Faint,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(AppColors.Black)
        ) {
            Header(state)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.DESK -> DeskScreen(vm, state)
                    Tab.COMMAND -> CommandScreen(vm, state)
                    Tab.SIGNALS -> SignalsScreen(vm, state)
                    Tab.JOURNAL -> JournalScreen(vm, state)
                    Tab.SYSTEM -> SystemScreen(vm, state, activity)
                }
            }
        }
    }
}

@Composable
private fun Header(state: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Panel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("HERMES // ORCA", color = AppColors.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("NATIVE DESK // v0.2", color = AppColors.Faint, fontFamily = FontFamily.Monospace)
        }
        StatusPill(
            text = when {
                state.health == Health.OK -> "LOCAL // ONLINE"
                state.health == Health.CHECKING -> "CHECKING"
                else -> "OFFLINE"
            },
            good = state.health == Health.OK
        )
    }
}

@Composable
private fun DeskScreen(vm: MainViewModel, state: UiState) {
    ScreenScroll {
        SectionTitle("SYSTEM")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("MODE", state.mode, Modifier.weight(1f))
            MetricCard("HERMES", state.health.name, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        ActionButton("CHECK HERMES") { vm.checkHealth() }
        ActionButton("START LOCAL HERMES") { vm.startHermesGateway() }

        SectionTitle("SCAN CONTROL")
        ActionButton("RUN AD-HOC MARKET SCAN") { vm.runScan() }
        ActionButton("TRIGGER ORCA 30M JOB") { vm.runThirtyMinuteJob() }
        ActionButton("REFRESH JOB STATUS") { vm.loadJobs() }
        if (state.jobsJson.isNotBlank()) CodeBlock(state.jobsJson)

        SectionTitle("QUICK LAUNCH")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("TRADINGVIEW", Modifier.weight(1f)) { vm.launchPackage("com.tradingview.tradingviewapp") }
            SmallAction("MT5", Modifier.weight(1f)) { vm.launchPackage("net.metaquotes.metatrader5") }
        }
        SmallAction("OBSIDIAN", Modifier.fillMaxWidth()) { vm.launchPackage("md.obsidian") }

        SectionTitle("DESK ROUTING")
        CodeBlock("""
FX / XAUUSD       KESTREL
SWING OPT/EQ      ORCA
0DTE / 1DTE       PEREGRINE
FUTURES           TALON
CRYPTO PERPS      MAKO
DERIV SYNTH       SYNTHII
        """.trimIndent())

        SectionTitle("EXECUTION LAW")
        Notice("ADVISORY ONLY", "Hermes may stage exact tickets. Real-money orders remain human-executed.")
    }
}

@Composable
private fun CommandScreen(vm: MainViewModel, state: UiState) {
    var input by rememberSaveable { mutableStateOf("") }
    ScreenScroll {
        SectionTitle("COMMAND")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, color = AppColors.White),
            label = { Text("ASK HERMES", fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Jade,
                unfocusedBorderColor = AppColors.Rule,
                focusedLabelColor = AppColors.Jade
            )
        )
        ActionButton(if (state.busy) "WORKING..." else "EXECUTE") {
            if (input.isNotBlank() && !state.busy) vm.sendCommand(input)
        }
        state.lastOutput.takeIf { it.isNotBlank() }?.let {
            SectionTitle("OUTPUT")
            CodeBlock(it)
        }
    }
}

@Composable
private fun SignalsScreen(vm: MainViewModel, state: UiState) {
    ScreenScroll {
        SectionTitle("ACTIVE / TICKETS")
        ActionButton("REFRESH ARMED / ENTER / MANAGE") { vm.refreshSignals() }
        Notice(
            "LOCK-SCREEN LOGIC",
            "Hermes must explicitly return ALERTABLE=YES before the native app generates a high-priority Android signal notification."
        )
        if (state.signals.isBlank()) {
            Notice("NO LOCAL RESULT", "Refresh to ask Hermes for current cases. Missing data remains explicit.")
        } else {
            CodeBlock(state.signals)
        }

        state.sharedStatus.takeIf { it.isNotBlank() }?.let {
            SectionTitle("SHARED INTO ORCA")
            CodeBlock(it)
        }
    }
}

@Composable
private fun JournalScreen(vm: MainViewModel, state: UiState) {
    ScreenScroll {
        SectionTitle("OBSIDIAN // ORCA JOURNAL")
        ActionButton("REFRESH LATEST JOURNALS") { vm.refreshJournal() }
        if (state.vaultUri == null) {
            Notice("VAULT NOT LINKED", "Open SYSTEM and select the Obsidian vault. Existing Hermes journaling remains authoritative.")
        }
        CodeBlock(state.journalText.ifBlank { "No journal loaded." })
    }
}

@Composable
private fun SystemScreen(vm: MainViewModel, state: UiState, activity: Activity) {
    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.setObsidianVault(it) }
    }

    ScreenScroll {
        SectionTitle("PAIRING")
        Notice(
            "LOCAL LOOPBACK",
            "Hermes target: ${state.baseUrl}. Keep it bound to 127.0.0.1. API key stays in app-private storage."
        )

        var key by remember(state.apiKey) { mutableStateOf(state.apiKey) }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HERMES API KEY") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, color = AppColors.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Jade,
                unfocusedBorderColor = AppColors.Rule
            )
        )
        ActionButton("SAVE KEY") { vm.saveApiKey(key) }
        ActionButton("CHECK HERMES") { vm.checkHealth() }
        ActionButton("REFRESH CONNECTORS / SKILLS / HEALTH") { vm.refreshSystem() }

        SectionTitle("TERMUX BRIDGE")
        Notice(
            if (state.termuxInstalled) "TERMUX FOUND" else "TERMUX NOT FOUND",
            "RUN_COMMAND is only used to request local gateway startup. Normal Hermes work goes over localhost HTTP."
        )
        ActionButton("OPEN APP PERMISSIONS") {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(i)
        }
        ActionButton("START HERMES GATEWAY") { vm.startHermesGateway() }

        SectionTitle("OBSIDIAN")
        Text(
            state.vaultUri?.let { "VAULT LINKED\n$it" } ?: "NO VAULT SELECTED",
            color = if (state.vaultUri != null) AppColors.Jade else AppColors.Amber,
            fontFamily = FontFamily.Monospace
        )
        ActionButton("SELECT OBSIDIAN VAULT") { vaultPicker.launch(null) }
        ActionButton("WRITE CONNECTION TEST") { vm.writeObsidianTest() }

        SectionTitle("TERMUX SETUP")
        CodeBlock("""
mkdir -p ~/.termux
grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties

nano ~/.hermes/.env

API_SERVER_ENABLED=true
API_SERVER_KEY=<choose-private-local-key>

termux-reload-settings
cd ~/ORCA
hermes gateway
        """.trimIndent())

        if (state.detailedHealth.isNotBlank()) {
            SectionTitle("DETAILED HEALTH")
            CodeBlock(state.detailedHealth)
        }

        if (state.connectorReport.isNotBlank()) {
            SectionTitle("CAPABILITIES / CONNECTORS")
            CodeBlock(state.connectorReport)
        }

        SectionTitle("STATE")
        CodeBlock(state.systemMessage.ifBlank { "No system errors." })
    }
}

@Composable
private fun ScreenScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AppColors.Jade,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(AppColors.Panel, RoundedCornerShape(2.dp)).padding(12.dp)
    ) {
        Text(label, color = AppColors.Faint, fontFamily = FontFamily.Monospace)
        Text(value, color = AppColors.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.DarkJade, contentColor = AppColors.White),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("> $label", fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Jade)
    ) {
        Text(label, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text,
        color = AppColors.White,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().background(AppColors.Panel, RoundedCornerShape(2.dp)).padding(12.dp)
    )
}

@Composable
private fun Notice(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Panel, RoundedCornerShape(2.dp)).padding(12.dp)
    ) {
        Text(title, color = AppColors.Amber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = AppColors.White, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    Text(
        text,
        color = if (good) AppColors.Jade else AppColors.Amber,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.background(AppColors.Black, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

private object AppColors {
    val Black = Color(0xFF050706)
    val Panel = Color(0xFF0A0F0C)
    val Rule = Color(0xFF1B3328)
    val DarkJade = Color(0xFF173D2D)
    val Jade = Color(0xFF73C69B)
    val Amber = Color(0xFFD6A64A)
    val White = Color(0xFFDDE2DF)
    val Faint = Color(0xFF7F8D86)
}

@Composable
private fun HermesOrcaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Jade,
            secondary = AppColors.Amber,
            background = AppColors.Black,
            surface = AppColors.Panel,
            onBackground = AppColors.White,
            onSurface = AppColors.White
        ),
        typography = Typography(),
        content = content
    )
}
