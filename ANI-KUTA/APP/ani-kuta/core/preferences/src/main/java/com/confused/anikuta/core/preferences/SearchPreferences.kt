package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 165 (STEP-3d's customizability): the SEARCH PAGE's appearance
 * preferences — the user's order: "in the search page settings, appearance,
 * search page" — the result card's title placement is the first option (the
 * AniList grid renders the title INSIDE the cover over a gradient; the TMDB
 * grid rendered it below — the user wants the choice, applied to BOTH).
 */
class SearchPreferences(private val store: PreferenceStore) {

    companion object {
        private const val KEY_TITLE_STYLE = "search_result_title_style"
    }

    var resultTitleStyle: ResultTitleStyle
        get() = ResultTitleStyle.fromId(store.getString(KEY_TITLE_STYLE, ResultTitleStyle.OVERLAY.id))
        set(value) = store.putString(KEY_TITLE_STYLE, value.id)

    private val _resultTitleStyle = MutableStateFlow(resultTitleStyle)
    val resultTitleStyleFlow: StateFlow<ResultTitleStyle> = _resultTitleStyle.asStateFlow()

    fun setResultTitleStyle(style: ResultTitleStyle) {
        resultTitleStyle = style
        _resultTitleStyle.value = style
    }

    /** The reactive read (the grids re-derive on the toggle). */
    val resultTitleStyleFlow2: Flow<ResultTitleStyle> get() = resultTitleStyleFlow
}

/** Where a search result card's title renders. */
enum class ResultTitleStyle(val id: String, val displayName: String, val subtitle: String) {
    OVERLAY("overlay", "Inside the cover", "A gradient at the cover's bottom carries the title (the AniList style)"),
    BELOW("below", "Under the poster", "The title + the year/rating sit under the poster"),
    ;

    companion object {
        fun fromId(id: String): ResultTitleStyle = entries.firstOrNull { it.id == id } ?: OVERLAY
    }
}
