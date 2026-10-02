package com.mangesh.reader.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mangesh.reader.BuildConfig
import com.mangesh.reader.data.Entry
import org.json.JSONArray
import org.json.JSONObject

/** Own window keeps the study independent of the app tabs and restores normal chrome on exit. */
@Composable
fun PlaygroundDialog(vm: QueueViewModel, onBack: () -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = false, dismissOnClickOutside = false,
    )) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Surface(Modifier.fillMaxSize(), color = LocalPalette.current.bgColor) {
            PlaygroundScreen(vm, onBack)
        }
    }
}

/** Uses the real reader without changing any report's progress. */
@Composable
fun PlaygroundScreen(vm: QueueViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val prefs = remember { context.getSharedPreferences("swipe-playground", 0) }
    var params by remember { mutableStateOf(prefs.getString("params", "{}")!!) }
    val trials = remember { runCatching { JSONArray(prefs.getString("trials", "[]")) }.getOrDefault(JSONArray()) }
    var count by remember { mutableIntStateOf(trials.length()) }
    var selected by remember { mutableIntStateOf(trials.length() - 1) }
    var revision by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    var pageLabel by remember { mutableStateOf("Loading sample…") }
    var status by remember { mutableStateOf("") }
    fun persist() { prefs.edit().putString("trials", trials.toString()).apply(); revision++ }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) status = runCatching {
            val data = JSONObject().put("schema", 1).put("detector", "swipe-v1")
                .put("build", BuildConfig.VERSION_CODE).put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android", Build.VERSION.RELEASE).put("density", context.resources.displayMetrics.density.toDouble()).put("trials", trials)
            requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(data.toString(2)) }
            "Export saved. Send the JSON file in our chat for analysis."
        }.getOrElse { "Export failed: ${it.message}. Trials are still saved here." }
    }
    val web = remember {
        ReaderWebView(context, vm.store, palette.bgArgb, {}).apply {
            val fragment = "<article class=\"report\"><h1>Swipe playground</h1>" + (1..35).joinToString("") { i ->
                "<h2>Practice $i</h2><p>Try a short flick, a slow drag, a diagonal swipe, or a movement that should not turn the page. Swipe up for next and down for previous.</p>" +
                "<p>After each attempt, label what you wanted below. Misses are just as useful as successful gestures. Adjust one parameter at a time to compare the feel.</p>"
            } + "</article>"
            val entry = Entry.fromServer(JSONObject().put("id", -1).put("state", "done"))
            load(ReaderWebView.page(context, fragment, palette, 2, "standard", false, true, entry, null),
                0.3f, "[]", palette.bgArgb, "window.swipePlayground = true; configureSwipe($params);")
        }
    }
    DisposableEffect(web) { onDispose { web.bridge = null; web.destroy() } }
    SideEffect {
        web.bridge = object : ReaderWebView.Bridge {
            override fun onGesture(json: String) {
                if (trials.length() >= 300) trials.remove(0)
                trials.put(JSONObject(json)); count = trials.length(); selected = count - 1; persist()
            }
            override fun onPage(page: Int, pages: Int, fraction: Float, byUser: Boolean) { pageLabel = "Page ${page + 1} / $pages" }
            override fun onScroll(fraction: Float, dy: Float, y: Float) {}
            override fun onLongPress(line: Int, quote: String) {}
            override fun onDone() {}
            override fun onDrop() {}
            override fun onNext() {}
            override fun onReplace(uuid: String) {}
            override fun onTextStep(delta: Int) {}
            override fun onTap() {}
            override fun onSections(json: String) {}
        }
    }
    BackHandler {
        when {
            panel -> panel = false
            feedback -> feedback = false
            else -> onBack()
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Controls overlay the page: opening feedback never changes the gesture viewport.
        AndroidView(factory = { web.view }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding(), color = palette.bgColor) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextAction("Back", onBack)
                TextAction("Tune", { panel = true })
                TextAction("Feedback · $count", { feedback = true })
            }
        }
    }
    if (feedback) AlertDialog(onDismissRequest = { feedback = false },
        title = { Text("Swipe feedback · $pageLabel") },
        text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val trial = remember(selected, revision) { if (selected >= 0) trials.getJSONObject(selected) else null }
            if (trial == null) Text("Try a swipe above, then label what should have happened.", style = Type.figure)
            else {
                Text("Trial ${selected + 1} / $count · ${trial.getString("outcome")} · ${trial.getString("reason")}", style = Type.figure)
                Text("${trial.optDouble("distance").toInt()} px · ${"%.2f".format(trial.optDouble("velocity"))} px/ms · intended: ${trial.optString("expected", "unlabelled")}", style = Type.figure)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("next" to "Next", "previous" to "Previous", "none" to "No turn").forEach { (value, label) ->
                        TextAction(label, { trial.put("expected", value); persist() })
                    }
                }
                HairlineField(value = trial.optString("note"), onValueChange = { trial.put("note", it); persist() }, placeholder = "Optional: what felt wrong?")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (selected > 0) TextAction("Earlier", { selected-- })
                    if (selected < count - 1) TextAction("Later", { selected++ })
                    TextAction("Export $count trials", { exporter.launch("swipe-feedback-${System.currentTimeMillis()}.json") })
                }
            }
            if (status.isNotEmpty()) Text(status, style = Type.figure)
        }
    }, confirmButton = { TextButton(onClick = { feedback = false }) { Text("Keep swiping") } })
    if (panel) AlertDialog(onDismissRequest = { panel = false }, title = { Text("Tune swipes") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Changes apply to this playground only. The latest 300 trials stay on this device until exported.")
            val values = JSONObject(params)
            listOf(
                SwipeControl("slop", "Start distance (px)", 6f, 2f..30f),
                SwipeControl("dominance", "Vertical / horizontal ratio", 1f, 1f..3f),
                SwipeControl("distance", "Turn distance (page fraction)", 0.214f, 0.05f..0.5f),
                SwipeControl("velocity", "Flick speed (px/ms)", 0.4f, 0.1f..2f),
                SwipeControl("windowMs", "Speed sampling window (ms)", 100f, 30f..200f),
            ).forEach { c ->
                val value = values.optDouble(c.key, c.default.toDouble()).toFloat()
                Text("${c.label}: ${"%.2f".format(value)}", style = Type.figure)
                Slider(value = value, valueRange = c.range, onValueChange = {
                    params = JSONObject(params).put(c.key, it.toDouble()).toString()
                    prefs.edit().putString("params", params).apply()
                    web.restyle("window.swipePlayground = true; configureSwipe($params);", palette.bgArgb)
                })
            }
            Text("Lower distance or speed accepts smaller gestures. A higher ratio requires a more vertical start. A longer window smooths speed; pausing before release cancels a flick.", style = Type.figure)
            TextAction("Reset defaults", {
                params = "{}"; prefs.edit().putString("params", params).apply()
                web.restyle("configureSwipe({});", palette.bgArgb)
            })
        }
    }, confirmButton = { TextButton(onClick = { panel = false }) { Text("Try swipes") } })
}

private data class SwipeControl(val key: String, val label: String, val default: Float, val range: ClosedFloatingPointRange<Float>)
