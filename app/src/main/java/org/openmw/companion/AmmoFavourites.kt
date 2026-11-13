package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The HUD tab's two ammo favourites (arrows, bolts, ...), per character like the item and spell
 * favourites. Each slot holds an inventory record id; assigning ammo that already sits in the other
 * slot swaps the two, so a slot never holds a duplicate.
 */
internal object AmmoFavourites {
    const val SLOTS = 2
    private const val PREFS = "rgds_ammo_favourites"
    private var prefs: SharedPreferences? = null
    private var character = ""

    var slots by mutableStateOf(List<String?>(SLOTS) { null })
        private set

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Switch to [name]'s slots (no-op while the name is unknown or unchanged). */
    fun useCharacter(name: String) {
        if (name.isBlank() || name == character) return
        character = name
        val stored = prefs?.getString(key(name), null)?.split('|').orEmpty()
        slots = List(SLOTS) { i -> stored.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun assign(index: Int, id: String) {
        if (index !in 0 until SLOTS || character.isBlank()) return
        val next = slots.toMutableList()
        val other = next.indexOf(id)
        if (other >= 0 && other != index) next[other] = next[index]
        next[index] = id
        save(next)
    }

    fun clear(index: Int) {
        if (index !in 0 until SLOTS || character.isBlank()) return
        save(slots.toMutableList().also { it[index] = null })
    }

    private fun save(next: List<String?>) {
        slots = next
        prefs?.edit()?.putString(key(character), next.joinToString("|") { it.orEmpty() })?.apply()
    }

    private fun key(name: String) = "ammo_" + name
}
