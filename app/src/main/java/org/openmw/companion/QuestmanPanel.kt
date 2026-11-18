package org.openmw.companion

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.openmw.ui.theme.MwBone
import org.openmw.ui.theme.MwBoneDim
import org.openmw.ui.theme.MwBronzeDark
import org.openmw.ui.theme.MwStoneDark

/** Shared by the two activities. Native menus retain their own controller handling. */
internal object QuestmanBack {
    var onBack: (() -> Unit)? = null
    private var consumedKey: Int? = null

    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK) return false
        if (consumedKey == code) {
            if (event.action == KeyEvent.ACTION_UP) consumedKey = null
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0 || !RgdsControls.gameplay) return false
        val callback = onBack ?: return false
        consumedKey = code
        callback()
        return true
    }
}

private enum class QuestmanSection(val label: String, val wireName: String) {
    Quests("Quests", "quests"), Journal("Journal", "feed"), Notes("Notes", "notes")
}

@Composable
internal fun QuestmanPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    fallback: @Composable () -> Unit
) {
    val saveId by GameStateRepository.saveId.collectAsState()
    val page by QuestmanRepository.page.collectAsState()

    var section by remember(saveId) { mutableStateOf(QuestmanSection.Quests) }
    var selected by remember(saveId) { mutableStateOf<QuestmanQuest?>(null) }
    var limit by remember(saveId) { mutableIntStateOf(QuestmanRepository.PageSize) }
    var showAll by remember(saveId) { mutableStateOf(false) }
    var fallbackOpen by remember(saveId) { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshData by remember { mutableStateOf(true) }
    var pinChange by remember(saveId) { mutableStateOf<QuestmanQuest?>(null) }
    val title = selected?.title ?: "Questman"
    val pageKind = if (selected != null) "detail" else section.wireName

    val listState = key(saveId, pageKind, selected?.id) { rememberLazyListState() }

    LaunchedEffect(saveId, pageKind, selected?.id, limit, refresh) {
        timedOut = false
        val pin = pinChange
        QuestmanRepository.request(pageKind, limit, pin?.id ?: selected?.id, pinned = pin?.let { !it.pinned },
            refresh = refreshData)
        pinChange = null
        refreshData = false
        delay(5000)
        if (QuestmanRepository.page.value == null) timedOut = true
    }
    val back by rememberUpdatedState<() -> Unit> {
        when {
            fallbackOpen -> fallbackOpen = false
            selected == null -> onClose()
            else -> {
                selected = null
                limit = QuestmanRepository.PageSize
            }
        }
    }
    DisposableEffect(Unit) {
        QuestmanBack.onBack = { back() }
        onDispose {
            QuestmanBack.onBack = null
            QuestmanRepository.clear()
        }
    }

    if (fallbackOpen) {
        fallback()
        return
    }

    Column(
        modifier.fillMaxSize().background(MwStoneDark).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text(if (selected == null) "Close" else "Back") }
            Text(
                title, Modifier.weight(1f).padding(horizontal = 8.dp),
                color = MwBone, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2
            )
            TextButton(onClick = { refreshData = true; refresh++ }) { Text("Refresh") }
        }
        if (selected == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuestmanSection.entries.forEach { tab ->
                    TextButton(onClick = {
                        section = tab
                        limit = QuestmanRepository.PageSize
                    }) { Text(tab.label, fontWeight = if (section == tab) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }

        val current = page
        when {
            current == null && !timedOut -> QuestmanMessage("Loading Questmanâ€¦")
            current == null || !current.available -> {
                QuestmanMessage(if (timedOut) "Questman hasnâ€™t responded." else "Questman is unavailable.")
                TextButton(onClick = { fallbackOpen = true }) { Text("Open companion journal") }
            }
            pageKind == "quests" -> {
                val visible = remember(current, showAll) {
                    current.quests
                        .filter { showAll || (!it.hidden && (!it.completed || it.pinned)) }
                        .sortedByDescending { it.pinned }
                }
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show active quests" else "Show completed and hidden")
                }
                LazyColumn(Modifier.weight(1f), state = listState) {
                    if (visible.isEmpty()) item { QuestmanMessage("No quests in this view.") }
                    items(visible, key = { it.id }) { quest ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f).clickable(role = Role.Button) {
                                    selected = quest
                                    limit = QuestmanRepository.PageSize
                                }.padding(8.dp)
                            ) {
                                Text(quest.title, color = MwBone)
                                val details = listOfNotNull(
                                    quest.giver.takeIf { it.isNotBlank() },
                                    "Completed".takeIf { quest.completed },
                                    "Hidden".takeIf { quest.hidden }
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" Â· "), color = MwBoneDim, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                pinChange = quest
                                refresh++
                            }) { Text(if (quest.pinned) "Unpin" else "Pin") }
                        }
                        HorizontalDivider(color = MwBronzeDark)
                    }
                }
            }
            else -> key(pageKind, selected?.id) {
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.entries.isEmpty()) item { QuestmanMessage("No journal entries in this view.") }
                    items(current.entries) { entry ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            if (entry.title.isNotBlank()) Text(entry.title, color = MwBone, fontWeight = FontWeight.Bold)
                            if (entry.date.isNotBlank()) Text(entry.date, color = MwBoneDim, fontSize = 12.sp)
                            Text(entry.text, color = MwBone)
                        }
                    }
                    if (current.more) item {
                        TextButton(onClick = { limit += QuestmanRepository.PageSize }) { Text("Show older entries") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestmanMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(12.dp)) { Text(message, color = MwBone) }
}
