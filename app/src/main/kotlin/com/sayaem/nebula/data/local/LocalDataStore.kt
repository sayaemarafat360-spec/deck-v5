package com.sayaem.nebula.data.local

import android.content.Context
import android.content.SharedPreferences
import com.sayaem.nebula.data.models.Playlist
import com.sayaem.nebula.data.models.PlayStats
import org.json.JSONArray
import org.json.JSONObject


/**
 * Single source of truth for all persisted user data.
 * Uses SharedPreferences + JSON — zero external dependencies.
 */
class LocalDataStore(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("deck_data", Context.MODE_PRIVATE)

    // ─── Keys ────────────────────────────────────────────────────────
    companion object {
        private const val KEY_FAVORITES   = "favorites_v1"
        private const val KEY_PLAY_STATS  = "play_stats_v1"
        private const val KEY_PLAYLISTS   = "playlists_v1"
        private const val KEY_RECENT      = "recent_plays_v1"
    }

    // ─── Favorites ────────────────────────────────────────────────────
    fun getFavorites(): Set<Long> {
        val json = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun saveFavorites(ids: Set<Long>) {
        val arr = JSONArray().also { ids.forEach(it::put) }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply()
    }

    fun toggleFavorite(id: Long): Boolean {
        val favs = getFavorites().toMutableSet()
        val isNowFav = if (id in favs) { favs.remove(id); false } else { favs.add(id); true }
        saveFavorites(favs)
        return isNowFav
    }

    // ─── Play / Skip Stats ────────────────────────────────────────────
    fun getPlayStats(): Map<Long, PlayStats> {
        val json = prefs.getString(KEY_PLAY_STATS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Long, PlayStats>()
            obj.keys().forEach { key ->
                val id = key.toLongOrNull() ?: return@forEach
                val entry = obj.getJSONObject(key)
                map[id] = PlayStats(
                    songId    = id,
                    playCount = entry.optInt("plays", 0),
                    skipCount = entry.optInt("skips", 0),
                    lastPlayed = entry.optLong("last", 0L),
                )
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun recordPlay(songId: Long) {
        val stats = getPlayStats().toMutableMap()
        val s     = stats.getOrDefault(songId, PlayStats(songId))
        stats[songId] = s.copy(playCount = s.playCount + 1, lastPlayed = System.currentTimeMillis())
        savePlayStats(stats)
    }

    fun recordSkip(songId: Long, positionMs: Long, durationMs: Long) {
        if (durationMs == 0L) return
        val pct = positionMs.toFloat() / durationMs
        if (pct > 0.30f) return  // only count as skip if left before 30%
        val stats = getPlayStats().toMutableMap()
        val s     = stats.getOrDefault(songId, PlayStats(songId))
        stats[songId] = s.copy(skipCount = s.skipCount + 1)
        savePlayStats(stats)
    }

    fun shouldAutoSkip(songId: Long): Boolean {
        val s = getPlayStats()[songId] ?: return false
        val total = s.playCount + s.skipCount
        if (total < 5) return false          // not enough data
        return s.skipCount.toFloat() / total > 0.70f
    }

    private fun savePlayStats(stats: Map<Long, PlayStats>) {
        val obj = JSONObject()
        stats.forEach { (id, s) ->
            obj.put(id.toString(), JSONObject().apply {
                put("plays", s.playCount)
                put("skips", s.skipCount)
                put("last",  s.lastPlayed)
            })
        }
        prefs.edit().putString(KEY_PLAY_STATS, obj.toString()).apply()
    }

    // ─── Recent plays ─────────────────────────────────────────────────
    fun getRecentIds(): List<Long> {
        val json = prefs.getString(KEY_RECENT, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun recordRecentPlay(songId: Long) {
        val list = getRecentIds().filter { it != songId }.toMutableList()
        list.add(0, songId)
        val trimmed = list.take(50)
        val arr = JSONArray().also { trimmed.forEach(it::put) }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply()
    }

    // ─── Playlists ────────────────────────────────────────────────────
    fun getPlaylists(): List<Playlist> {
        val json = prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val idsArr = obj.getJSONArray("songIds")
                Playlist(
                    id        = obj.getString("id"),
                    name      = obj.getString("name"),
                    songIds   = (0 until idsArr.length()).map { idsArr.getLong(it) },
                    createdAt = obj.getLong("createdAt"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val idsArr = JSONArray().also { pl.songIds.forEach(it::put) }
            arr.put(JSONObject().apply {
                put("id",        pl.id)
                put("name",      pl.name)
                put("songIds",   idsArr)
                put("createdAt", pl.createdAt)
            })
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun createPlaylist(name: String): Playlist {
        val pl  = Playlist(
            id        = System.currentTimeMillis().toString(),
            name      = name,
            songIds   = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        savePlaylists(getPlaylists() + pl)
        return pl
    }

    fun deletePlaylist(id: String) =
        savePlaylists(getPlaylists().filter { it.id != id })

    fun renamePlaylist(id: String, newName: String) =
        savePlaylists(getPlaylists().map { if (it.id == id) it.copy(name = newName) else it })

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        savePlaylists(getPlaylists().map { pl ->
            if (pl.id == playlistId && songId !in pl.songIds)
                pl.copy(songIds = pl.songIds + songId)
            else pl
        })
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        savePlaylists(getPlaylists().map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = pl.songIds.filter { it != songId })
            else pl
        })
    }

    fun reorderPlaylist(playlistId: String, newOrder: List<Long>) {
        savePlaylists(getPlaylists().map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = newOrder) else pl
        })
    }


    // ─── Settings persistence ─────────────────────────────────────────
    fun setGapless(enabled: Boolean) = prefs.edit().putBoolean("gapless", enabled).apply()
    fun setSmartSkip(enabled: Boolean) = prefs.edit().putBoolean("smart_skip", enabled).apply()
    fun setCrossfade(seconds: Float) = prefs.edit().putFloat("crossfade", seconds).apply()
    fun getGapless(): Boolean = prefs.getBoolean("gapless", true)
    fun getSmartSkip(): Boolean = prefs.getBoolean("smart_skip", false)
    fun getCrossfade(): Float = prefs.getFloat("crossfade", 0f)


    // ─── Local premium cache (works offline) ──────────────────────────
    fun saveLocalPremium(plan: String, expiresAt: Long) {
        prefs.edit()
            .putString("premium_plan", plan)
            .putLong("premium_expires_at", expiresAt)
            .apply()
    }

    fun getLocalPremiumPlan(): String = prefs.getString("premium_plan", "none") ?: "none"

    fun isLocalPremiumActive(): Boolean {
        val plan = getLocalPremiumPlan()
        if (plan == "none")     return false
        if (plan == "lifetime") return true
        val expiresAt = prefs.getLong("premium_expires_at", 0L)
        return expiresAt > System.currentTimeMillis()
    }

    fun clearLocalPremium() {
        prefs.edit().remove("premium_plan").remove("premium_expires_at").apply()
    }

    // ─── EQ profiles per song ─────────────────────────────────────────
    fun saveEqProfile(songId: Long, bands: List<Float>, preset: String) {
        val key = "eq_profile_$songId"
        val v   = bands.joinToString(",") + "|$preset"
        prefs.edit().putString(key, v).apply()
    }

    fun loadEqProfile(songId: Long): Pair<List<Float>, String>? {
        val raw = prefs.getString("eq_profile_$songId", null) ?: return null
        return try {
            val parts = raw.split("|")
            val bands = parts[0].split(",").map { it.toFloat() }
            val preset = parts.getOrElse(1) { "Custom" }
            Pair(bands, preset)
        } catch (_: Exception) { null }
    }

    fun deleteEqProfile(songId: Long) = prefs.edit().remove("eq_profile_$songId").apply()


    // ─── Audio bookmarks ──────────────────────────────────────────────
    data class Bookmark(val songId: Long, val positionMs: Long, val label: String, val createdAt: Long)

    fun saveBookmark(songId: Long, positionMs: Long, label: String) {
        val key  = "bookmarks_$songId"
        val list = getBookmarks(songId).toMutableList()
        list.add(Bookmark(songId, positionMs, label, System.currentTimeMillis()))
        val json = org.json.JSONArray().also { arr ->
            list.forEach { b -> arr.put(org.json.JSONObject()
                .put("pos", b.positionMs).put("label", b.label).put("at", b.createdAt)) }
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    fun getBookmarks(songId: Long): List<Bookmark> {
        return try {
            val raw = prefs.getString("bookmarks_$songId", "[]") ?: "[]"
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Bookmark(songId, obj.getLong("pos"), obj.getString("label"), obj.getLong("at"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun deleteBookmark(songId: Long, positionMs: Long) {
        val list = getBookmarks(songId).filter { it.positionMs != positionMs }
        val json = org.json.JSONArray().also { arr ->
            list.forEach { b -> arr.put(org.json.JSONObject()
                .put("pos", b.positionMs).put("label", b.label).put("at", b.createdAt)) }
        }
        prefs.edit().putString("bookmarks_$songId", json.toString()).apply()
    }

    // ─── Onboarding ─────────────────────────────────────────────────
    fun isOnboardingDone(): Boolean = prefs.getBoolean("onboarding_done", false)
    fun markOnboardingDone() { prefs.edit().putBoolean("onboarding_done", true).apply() }
}
