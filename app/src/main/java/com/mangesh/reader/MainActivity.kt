package com.mangesh.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.mangesh.reader.ui.ReaderHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangesh.reader.ui.AppTabs
import com.mangesh.reader.ui.LocalPalette
import com.mangesh.reader.ui.OutlineButton
import com.mangesh.reader.ui.QueueScreen
import com.mangesh.reader.ui.QueueViewModel
import com.mangesh.reader.ui.ReaderScreen
import com.mangesh.reader.ui.ReaderTheme
import com.mangesh.reader.ui.SettingsScreen
import com.mangesh.reader.ui.Type
import com.mangesh.reader.ui.fg2Color
import com.mangesh.reader.ui.fgColor

class MainActivity : ComponentActivity() {

    private val vm: QueueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReaderTheme(vm) {
                val host = remember { ReaderHost() }
                DisposableEffect(Unit) { onDispose { host.web?.destroy() } }
                Surface(Modifier.fillMaxSize()) {
                    BackHandler(enabled = vm.tab != QueueViewModel.Tab.Queue) { vm.back() }
                    Column(Modifier.fillMaxSize()) {
                        // In reading mode the page takes the whole screen and the tabs overlay it
                        // with the rest of the chrome (see ReaderScreen); otherwise they sit below.
                        val content = Modifier.fillMaxWidth().weight(1f).let {
                            if (vm.readingMode) it else it.consumeWindowInsets(WindowInsets.navigationBars)
                        }
                        Box(content) {
                            when (vm.tab) {
                                QueueViewModel.Tab.Queue -> QueueScreen(vm)
                                QueueViewModel.Tab.Reading -> vm.currentId?.let { ReaderScreen(vm, it, host) } ?: ReadingEmpty(vm)
                                QueueViewModel.Tab.Settings -> SettingsScreen(vm)
                            }
                        }
                        if (!vm.readingMode) AppTabs(vm)
                    }
                }
            }
        }
    }

    /** Sync on open: every foreground, debounced inside the view model. Never on a timer. */
    override fun onResume() {
        super.onResume()
        vm.syncOnOpen()
    }
}

@Composable
private fun ReadingEmpty(vm: QueueViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center) {
        val p = LocalPalette.current
        Text("Nothing open.", style = Type.sheetTitle, color = p.fgColor)
        Text("Pick something from the queue; this tab brings you back to it.", style = Type.excerpt, color = p.fg2Color,
             textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        OutlineButton("Queue", onClick = { vm.selectTab(QueueViewModel.Tab.Queue) }, modifier = Modifier.padding(top = 18.dp), primary = true)
    }
}
