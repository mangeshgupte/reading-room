package com.mangesh.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The three tabs: an icon over a meta label, above a hairline. The current one takes the accent as its
 * ink; there is no pill behind it, nothing is filled. In reading mode this bar overlays the page and
 * shows with the rest of the chrome.
 */
@Composable
fun AppTabs(vm: QueueViewModel) {
    val p = LocalPalette.current
    val tabs = listOf(
        Triple(QueueViewModel.Tab.Queue, "Queue", Icons.AutoMirrored.Filled.List),
        Triple(QueueViewModel.Tab.Reading, "Reading", Icons.AutoMirrored.Filled.MenuBook),
        Triple(QueueViewModel.Tab.Settings, "Settings", Icons.Filled.Settings),
    )
    Column(Modifier.fillMaxWidth().background(p.bgColor).windowInsetsPadding(WindowInsets.navigationBars)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            for ((tab, label, icon) in tabs) {
                val ink = if (vm.tab == tab) p.accentColor else p.mutedColor
                Column(Modifier.weight(1f).fillMaxHeight().pressTint(role = Role.Tab) { vm.selectTab(tab) },
                       horizontalAlignment = Alignment.CenterHorizontally,
                       verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                    Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))   // the label names it
                    MetaLabel(label, color = ink)
                }
            }
        }
    }
}
