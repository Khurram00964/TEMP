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

    // ── ROUND 168 (Phase G): the user's customization set ──
    // The rating badge on the result cards (ON default).
    var showRatingBadge: Boolean
        get() = store.getBoolean(KEY_SHOW_RATING, true)
        set(value) = store.putBoolean(KEY_SHOW_RATING, value)

    // The details line under the title (the year/type meta — ON default).
    var showDetailsLine: Boolean
        get() = store.getBoolean(KEY_SHOW_DETAILS, true)
        set(value) = store.putBoolean(KEY_SHOW_DETAILS, value)

    // The grid density: the covers per row (the adaptive default = 0 → the
    // screen-width math; 2-4 = the fixed counts).
    var gridColumns: Int
        get() = store.getInt(KEY_GRID_COLUMNS, 0)
        set(value) = store.putInt(KEY_GRID_COLUMNS, value)

    // The recent-searches strip's visibility (ON default).
    var showRecents: Boolean
        get() = store.getBoolean(KEY_SHOW_RECENTS, true)
        set(value) = store.putBoolean(KEY_SHOW_RECENTS, value)

    // The reactive mirrors (the grids re-derive on the toggle).
    private val _showRatingBadge = MutableStateFlow(showRatingBadge)
    val showRatingBadgeFlow: StateFlow<Boolean> = _showRatingBadge.asStateFlow()
    private val _showDetailsLine = MutableStateFlow(showDetailsLine)
    val showDetailsLineFlow: StateFlow<Boolean> = _showDetailsLine.asStateFlow()
    private val _gridColumns = MutableStateFlow(gridColumns)
    val gridColumnsFlow: StateFlow<Int> = _gridColumns.asStateFlow()
    private val _showRecents = MutableStateFlow(showRecents)
    val showRecentsFlow: StateFlow<Boolean> = _showRecents.asStateFlow()

    fun updateShowRatingBadge(value: Boolean) {
        showRatingBadge = value
        _showRatingBadge.value = value
    }

    fun updateShowDetailsLine(value: Boolean) {
        showDetailsLine = value
        _showDetailsLine.value = value
    }

    fun updateGridColumns(value: Int) {
        gridColumns = value
        _gridColumns.value = value
    }

    fun updateShowRecents(value: Boolean) {
        showRecents = value
        _showRecents.value = value
    }

    companion object {
        private const val KEY_TITLE_STYLE = "search_result_title_style"
        private const val KEY_SHOW_RATING = "search_show_rating"
        private const val KEY_SHOW_DETAILS = "search_show_details"
        private const val KEY_GRID_COLUMNS = "search_grid_columns"
        private const val KEY_SHOW_RECENTS = "search_show_recents"
    }

    var resultTitleStyle: ResultTitleStyle
        get() = ResultTitleStyle.fromId(store.getString(KEY_TITLE_STYLE, ResultTitleStyle.OVERLAY.id))
        set(value) = store.putString(KEY_TITLE_STYLE, value.id)

    private val _resultTitleStyle = MutableStateFlow(resultTitleStyle)
    val resultTitleStyleFlow: StateFlow<ResultTitleStyle> = _resultTitleStyle.asStateFlow()

    fun updateResultTitleStyle(style: ResultTitleStyle) {
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
