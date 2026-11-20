package com.confused.anikuta.core.trackersimkl

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * ROUND 163 (STEP-2): the Simkl auth token storage (the PreferenceStore-backed
 * layer, the AniList token's pattern). The access token ages out in 7 days —
 * [accessExpiresAtMs] gates the silent refresh; the refresh token is
 * non-rotating and lives 180 days (sliding, refreshed on every use).
 */
class SimklPreferences(private val store: PreferenceStore) {

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
        private const val KEY_ACCESS_TOKEN = "simkl_access_token"
        private const val KEY_REFRESH_TOKEN = "simkl_refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "simkl_access_expires_at"
        private const val KEY_USER_NAME = "simkl_user_name"
    }
}
