package com.confused.anikuta.core.trackermal

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * ROUND 168 (the tracker redo, Phase C): the MAL auth token storage (the
 * PreferenceStore-backed layer, the Simkl token's pattern). MAL's access
 * token lives ~31 days; the refresh token renews it.
 */
class MalPreferences(private val store: PreferenceStore) {

    var accessToken: String
        get() = store.getString(KEY_ACCESS_TOKEN, "")
        set(value) = store.putString(KEY_ACCESS_TOKEN, value)

    var refreshToken: String
        get() = store.getString(KEY_REFRESH_TOKEN, "")
        set(value) = store.putString(KEY_REFRESH_TOKEN, value)

    /** The epoch-ms the access token expires (now + expires_in at write time). */
    var accessExpiresAtMs: Long
        get() = store.getLong(KEY_ACCESS_EXPIRES_AT, 0L)
        set(value) = store.putLong(KEY_ACCESS_EXPIRES_AT, value)

    var userName: String
        get() = store.getString(KEY_USER_NAME, "")
        set(value) = store.putString(KEY_USER_NAME, value)

    val isLinked: Boolean get() = accessToken.isNotBlank()

    fun clear() {
        accessToken = ""
        refreshToken = ""
        accessExpiresAtMs = 0L
        userName = ""
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "mal_access_token"
        private const val KEY_REFRESH_TOKEN = "mal_refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "mal_access_expires_at"
        private const val KEY_USER_NAME = "mal_user_name"
    }
}
