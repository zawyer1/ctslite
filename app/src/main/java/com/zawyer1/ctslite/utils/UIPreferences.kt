/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
    *  * Modifications Copyright (C) 2026 Zawyer1
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.zawyer1.ctslite.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zawyer1.ctslite.data.SearchEngine
import com.zawyer1.ctslite.data.SearchMode

class UIPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DESKTOP_MODE = "is_desktop_mode"
        private const val KEY_DESKTOP_MODE_ENGINES = "desktop_mode_engines"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_SHOW_GRADIENT_BORDER = "show_gradient_border"
        private const val KEY_SHOW_FRIENDLY_MESSAGES = "show_friendly_messages"
        private const val KEY_SEARCH_ENGINE_ORDER = "search_engine_order"
        private const val KEY_SEARCH_MODE = "search_mode"
    }

    fun getSearchMode(): SearchMode {
        val saved = prefs.getString(KEY_SEARCH_MODE, SearchMode.MultiSearch.name)
        return try {
            SearchMode.valueOf(saved ?: SearchMode.MultiSearch.name)
        } catch (_: IllegalArgumentException) {
            SearchMode.MultiSearch
        }
    }

    fun setSearchMode(mode: SearchMode) =
        prefs.edit { putString(KEY_SEARCH_MODE, mode.name) }

    fun isDesktopMode(): Boolean = prefs.getBoolean(KEY_DESKTOP_MODE, false)

    /**
     * Returns the set of engines currently in desktop mode, persisted across sessions.
     *
     * On first launch (or if no per-engine preference exists), falls back to the
     * legacy global [isDesktopMode] flag so existing user preference is honoured.
     */
    fun getDesktopModeEngines(allEngines: List<SearchEngine>): Set<SearchEngine> {
        val saved = prefs.getString(KEY_DESKTOP_MODE_ENGINES, null)
        return if (saved == null) {
            if (isDesktopMode()) allEngines.toSet() else emptySet()
        } else if (saved.isEmpty()) {
            emptySet()
        } else {
            saved.split(",")
                .mapNotNull { name -> allEngines.find { it.name == name } }
                .toSet()
        }
    }

    /**
     * Persists the set of engines currently in desktop mode.
     */
    fun setDesktopModeEngines(engines: Set<SearchEngine>) {
        prefs.edit { putString(KEY_DESKTOP_MODE_ENGINES, engines.joinToString(",") { it.name }) }
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)
    fun setDarkMode(isEnabled: Boolean) = prefs.edit { putBoolean(KEY_DARK_MODE, isEnabled) }

    fun isShowGradientBorder(): Boolean = prefs.getBoolean(KEY_SHOW_GRADIENT_BORDER, true)
    fun setShowGradientBorder(isEnabled: Boolean) = prefs.edit { putBoolean(KEY_SHOW_GRADIENT_BORDER, isEnabled) }

    fun isShowFriendlyMessages(): Boolean = prefs.getBoolean(KEY_SHOW_FRIENDLY_MESSAGES, true)
    fun setShowFriendlyMessages(isEnabled: Boolean) = prefs.edit { putBoolean(KEY_SHOW_FRIENDLY_MESSAGES, isEnabled) }

    fun getSearchEngineOrder(): String? = prefs.getString(KEY_SEARCH_ENGINE_ORDER, null)
    fun setSearchEngineOrder(order: String) = prefs.edit { putString(KEY_SEARCH_ENGINE_ORDER, order) }

    /**
     * Returns the search engine list in the user's preferred order.
     *
     * Engines stored in preferences are placed first, in the saved sequence.
     * Any engines not present in the current values() list (e.g. engines that
     * have since been removed from the app) are silently dropped and the cleaned
     * order is written back to preferences so stale names don't persist.
     * Any engines not present in the saved order are appended at the end.
     */
    fun getOrderedSearchEngines(): List<SearchEngine> {
        val allEngines = SearchEngine.values()
        val orderString = getSearchEngineOrder() ?: return allEngines
        val preferredNames = orderString.split(",")
        val ordered = mutableListOf<SearchEngine>()
        preferredNames.forEach { name ->
            allEngines.find { it.name == name }?.let { ordered.add(it) }
        }
        allEngines.forEach { if (!ordered.contains(it)) ordered.add(it) }

        val cleanedOrderString = ordered.joinToString(",") { it.name }
        if (cleanedOrderString != orderString) {
            setSearchEngineOrder(cleanedOrderString)
        }

        return ordered
    }
}
