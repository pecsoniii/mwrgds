package org.openmw.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class QuestmanQuest(
    val id: String,
    val title: String,
    val giver: String,
    val pinned: Boolean,
    val completed: Boolean,
    val hidden: Boolean
)

internal data class QuestmanEntry(val title: String, val date: String, val text: String)

internal data class QuestmanPage(
    val quests: List<QuestmanQuest>,
    val entries: List<QuestmanEntry>,
    val available: Boolean,
    val more: Boolean
)

internal object QuestmanRepository {
    const val PageSize = 40
    private const val Prefix = "COMPANION_QUESTMAN:"
    private val escape = Regex("%(?:25|7C|0A|0D)")
    private var sequence = 0L
    private var pending = 0L
    private var accepted = false
    private val quests = mutableListOf<QuestmanQuest>()
    private val entries = mutableListOf<QuestmanEntry>()
    private val _page = MutableStateFlow<QuestmanPage?>(null)
    val page = _page.asStateFlow()

    private fun encode(s: String) =
        s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D")

    private fun decode(s: String) = escape.replace(s) {
        when (it.value) {
            "%25" -> "%"
            "%7C" -> "|"
            "%0A" -> "\n"
            else -> "\r"
        }
    }

    fun request(section: String, limit: Int, questId: String?, pinned: Boolean? = null, refresh: Boolean = false) {
        val request = synchronized(this) {
            pending = ++sequence
            accepted = false
            quests.clear()
            entries.clear()
            _page.value = null
            pending
        }
        val pin = when (pinned) {
            true -> "1"
            false -> "0"
            null -> ""
        }
        CompanionActions.requestQuestman(
            "$request|$section|$limit|${encode(questId.orEmpty())}|$pin|${if (refresh) 1 else 0}"
        )
    }

    @Synchronized
    fun clear() {
        pending = 0
        accepted = false
        quests.clear()
        entries.clear()
        _page.value = null
    }

    @Synchronized
    fun receive(line: String): Boolean {
        if (!line.startsWith(Prefix)) return false
        val parts = line.removePrefix(Prefix).split('|')
        if (parts.size < 2 || pending == 0L || parts[0].toLongOrNull() != pending) return true
        when (parts[1]) {
            "begin" -> {
                accepted = true
                quests.clear()
                entries.clear()
            }
            "quest" -> if (accepted && parts.size == 8) {
                quests += QuestmanQuest(
                    decode(parts[2]), decode(parts[3]), decode(parts[4]),
                    parts[5] == "1", parts[6] == "1", parts[7] == "1"
                )
            }
            "entry" -> if (accepted && parts.size == 5) {
                entries += QuestmanEntry(decode(parts[2]), decode(parts[3]), decode(parts[4]))
            }
            "end", "unavailable" -> if (accepted || parts[1] == "unavailable") {
                _page.value = QuestmanPage(
                    quests.toList(), entries.toList(), parts[1] == "end", parts.getOrNull(2) == "1"
                )
                pending = 0
                accepted = false
                quests.clear()
                entries.clear()
            }
        }
        return true
    }
}
