package com.mangesh.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangesh.reader.data.Entry
import com.mangesh.reader.data.Store
import com.mangesh.reader.data.SyncLogic
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(vm: QueueViewModel) {
    val state by vm.store.state.collectAsState()
    val entries = state.visible
    val list = remember(entries, vm.sort, vm.filter, vm.query) { vm.visibleList(entries) }
    val queued = remember(entries) { entries.filter { it.state == "queued" } }
    val projects = remember(entries) { SyncLogic.projects(entries) }
    val resume = remember(entries) { SyncLogic.continueCandidate(entries) }
    val snackbarHost = remember { SnackbarHostState() }
    var dropTarget by remember { mutableStateOf<Entry?>(null) }
    val p = LocalPalette.current

    LaunchedEffect(Unit) {
        vm.snacks.collect { s ->
            val r = snackbarHost.showSnackbar(s.message, actionLabel = if (s.undo != null) "Undo" else null,
                                              duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed && s.undo != null) vm.undo(s.undo)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) { ReaderSnackbar(it) } }) { padding ->
        val refresh = rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = vm.syncing, onRefresh = { vm.sync(manual = true) }, state = refresh,
                         modifier = Modifier.padding(padding).fillMaxSize(),
                         indicator = {
                             PullToRefreshDefaults.Indicator(state = refresh, isRefreshing = vm.syncing,
                                                             modifier = Modifier.align(Alignment.TopCenter),
                                                             containerColor = p.surfaceColor, color = p.accentColor)
                         }) {
            // the header scrolls away with the list: the rows get the whole screen
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") { Header(vm, state, queued) }
                item(key = "chips") { Chips(vm, projects) }
                if (resume != null && vm.query.isBlank()) item(key = "resume") { ContinueRow(resume) { vm.open(resume.id) } }
                if (!state.everSynced) {
                    item(key = "empty") { EmptyState("Nothing here yet.", "On home Wi-Fi, pull down to sync.") { vm.openSettings() } }
                } else if (list.isEmpty()) {
                    item(key = "empty") { EmptyState(if (vm.query.isBlank()) "Nothing in this view." else "Nothing matches.", null, null) }
                }
                items(list, key = { it.id }) { e ->
                    EntryRow(e, onOpen = { vm.open(e.id) }, onDone = { vm.done(e) }, onDrop = { dropTarget = e })
                }
            }
        }
    }

    dropTarget?.let { e ->
        DropSheet(e.title, onDismiss = { dropTarget = null }) { reason -> vm.drop(e, reason); dropTarget = null }
    }
}

@Composable
private fun Header(vm: QueueViewModel, state: Store.State, queued: List<Entry>) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Reading Room", style = Type.screenTitle, color = p.fgColor, modifier = Modifier.weight(1f).alignByBaseline())
            MetaLabel("${queued.size} queued", Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(10.dp))
        Text(listOf(summaryLine(queued), statusLine(vm, state)).filter { it.isNotEmpty() }.joinToString(" · "),
             style = Type.figure, color = p.mutedColor)
        Spacer(Modifier.height(18.dp))
        HairlineField(value = vm.query, onValueChange = { vm.query = it }, placeholder = "Search the queue")
    }
}

/** What the count in the header leaves out: how long the queue is, and how old. */
private fun summaryLine(queued: List<Entry>): String {
    if (queued.isEmpty()) return ""
    val minutes = queued.sumOf { it.minutes }
    val time = if (minutes < 60) "~$minutes min" else "~${"%.1f".format(minutes / 60.0).removeSuffix(".0")} h"
    return "$time · oldest ${age(queued.minOf { it.added })}"
}

private fun statusLine(vm: QueueViewModel, s: Store.State): String {
    val pending = if (s.outbox.isNotEmpty()) " · ${s.outbox.size} to sync" else ""
    val last = if (s.lastSync > 0) ago(s.lastSync) else "never"
    return when {
        vm.syncing -> (vm.syncProgress.ifEmpty { "syncing…" }) + pending
        s.lastError.isNotEmpty() -> "${s.lastError} · last synced $last$pending"
        s.lastSync > 0 -> "synced $last$pending"
        else -> "never synced$pending"
    }
}

fun age(iso: String): String = try {
    val d = ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.now())
    if (d <= 0) "today" else "${d}d"
} catch (e: Exception) { iso }

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")

/** "12 Sept"; the year only when it is not this one. */
fun shortDate(iso: String, today: LocalDate = LocalDate.now()): String = try {
    val d = LocalDate.parse(iso.take(10))
    "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" + (if (d.year != today.year) " ${d.year}" else "")
} catch (e: Exception) { iso }

fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

/** The part-read item to resume: a row like the others, with its progress drawn out. */
@Composable
private fun ContinueRow(e: Entry, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().pressTint(onClick = onOpen).padding(horizontal = 24.dp, vertical = 18.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaLabel("Continue")
        Text(e.title, style = Type.rowTitle, color = p.fgColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressHairline(e.progress, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${(e.progress * 100).toInt()}% · ${e.minutesLeft} min left", style = Type.figure, color = p.mutedColor)
        }
    }
    Hairline(Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun Chips(vm: QueueViewModel, projects: List<String>) {
    val p = LocalPalette.current
    val chips = listOf("all" to "All", "short" to "≤10 min", "web" to "Web") +
        projects.map { it to it } + listOf("done" to "Done", "dropped" to "Dropped")
    var sortOpen by remember { mutableStateOf(false) }
    val small = Type.segment.copy(fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        LazyRow(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 24.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips, key = { it.first }) { (key, label) ->
                val on = vm.filter == key
                Text(label, style = small, color = if (on) p.accentColor else p.mutedColor, maxLines = 1,
                     modifier = Modifier.clip(CONTROL_SHAPE).border(1.dp, if (on) p.accentColor else p.ruleColor, CONTROL_SHAPE)
                         .pressTint(role = Role.RadioButton) { vm.chooseFilter(key) }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        Box(Modifier.padding(end = 12.dp)) {
            Text("${vm.sort} ▾", style = small, color = p.mutedColor,
                 modifier = Modifier.pressTint(role = Role.Button) { sortOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            ReaderMenu(sortOpen, onDismiss = { sortOpen = false }, selected = vm.sort,
                       items = listOf("newest", "shortest", "oldest").map { s -> s to { vm.chooseSort(s) } })
        }
    }
}

@Composable
private fun EmptyState(title: String, hint: String?, onSettings: (() -> Unit)?) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Type.sheetTitle, color = p.fgColor)
        if (hint != null) Text(hint, style = Type.excerpt, color = p.fg2Color)
        if (onSettings != null) TextAction("Settings", onSettings)
    }
}

/** Hairline rows, no cards. The accent appears only as the percentage of an unfinished item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(e: Entry, onOpen: () -> Unit, onDone: () -> Unit, onDrop: () -> Unit) {
    val p = LocalPalette.current
    val swipeable = e.state == "queued"
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> onDone()
                SwipeToDismissBoxValue.EndToStart -> onDrop()
                else -> {}
            }
            false   // never actually dismiss; the list updates from the store
        },
        positionalThreshold = { it * 0.45f },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = swipeable,
        enableDismissFromEndToStart = swipeable,
        backgroundContent = {
            val toDone = dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(Modifier.fillMaxSize().background(if (toDone) p.highlight else p.surfaceColor).padding(horizontal = 24.dp),
                contentAlignment = if (toDone) Alignment.CenterStart else Alignment.CenterEnd) {
                MetaLabel(if (toDone) "Read" else "Drop", color = if (toDone) p.accentColor else p.mutedColor)
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().background(p.bgColor).pressTint(onClick = onOpen).padding(horizontal = 24.dp, vertical = 18.dp),
               verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(e.title, style = Type.rowTitle, color = p.fgColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                     modifier = Modifier.weight(1f).alignByBaseline())
                if (e.started) {
                    Spacer(Modifier.width(12.dp))
                    Text("${(e.progress * 100).toInt()}%", style = Type.figure, color = p.accentColor, modifier = Modifier.alignByBaseline())
                }
            }
            if (e.note.isNotEmpty()) {
                Text(e.note, style = Type.excerpt, color = p.fg2Color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = buildList {
                add(shortDate(e.added))
                add(if (e.started) "${e.minutesLeft} min left" else "${e.minutes} min")
                add(if (e.isClip) "↗ ${e.source}" else e.project)
                if (e.version > 1) add("v${e.version}")
                if (e.state != "queued") add(e.state + (if (e.closed.isNotEmpty()) " ${shortDate(e.closed)}" else ""))
                if (e.missing) add("missing on Mac") else if (!e.downloaded) add("not downloaded")
            }
            Text(meta.joinToString(" · "), style = Type.figure, color = p.mutedColor)
        }
    }
    Hairline(Modifier.padding(horizontal = 24.dp))
}

@Composable
fun DropSheet(title: String, onDismiss: () -> Unit, onDrop: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    ReaderSheet(onDismiss = onDismiss, spacing = 16.dp) {
        MetaLabel("Drop")
        Text(title, style = Type.sheetTitle, color = LocalPalette.current.fgColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
        HairlineField(value = reason, onValueChange = { reason = it }, placeholder = "Why not (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlineButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            OutlineButton("Drop", onClick = { onDrop(reason) }, modifier = Modifier.weight(1f), primary = true)
        }
    }
}
