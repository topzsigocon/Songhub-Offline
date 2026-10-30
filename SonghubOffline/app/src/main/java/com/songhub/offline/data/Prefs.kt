package com.songhub.offline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lahat ng naka-save na settings: folders, favorites, playlists, key per song.
 * Naka-store lang sa SharedPreferences ng phone (offline).
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("songhub_prefs", Context.MODE_PRIVATE)

    // ---------- Folders ----------
    /** Mga folder na pinili ng user gamit ang built-in folder picker. */
    var customFolders: Set<String>
        get() = sp.getStringSet(KEY_FOLDERS, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_FOLDERS, HashSet(v)).apply()

    /** I-scan ang buong SD card (hindi lang ang Songhub folder). */
    var scanWholeSd: Boolean
        get() = sp.getBoolean(KEY_SCAN_WHOLE, false)
        set(v) = sp.edit().putBoolean(KEY_SCAN_WHOLE, v).apply()

    /** Huling na-scan na listahan, para mabilis lumabas pagbukas ng app. */
    var cachedSongs: List<String>
        get() = sp.getString(KEY_CACHE, "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString(KEY_CACHE, v.joinToString("\n")).apply()

    // ---------- Favorites ----------
    var favorites: Set<String>
        get() = sp.getStringSet(KEY_FAVS, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_FAVS, HashSet(v)).apply()

    fun isFavorite(path: String) = path in favorites

    /** @return true kung naging favorite, false kung inalis. */
    fun toggleFavorite(path: String): Boolean {
        val s = favorites.toMutableSet()
        val nowFav = if (s.contains(path)) { s.remove(path); false } else { s.add(path); true }
        favorites = s
        return nowFav
    }

    // ---------- Player settings ----------
    fun getKey(path: String): Int = sp.getInt("key_" + path.hashCode(), 0)
    fun setKey(path: String, key: Int) = sp.edit().putInt("key_" + path.hashCode(), key).apply()

    var volume: Int
        get() = sp.getInt(KEY_VOLUME, 80)
        set(v) = sp.edit().putInt(KEY_VOLUME, v).apply()

    // ---------- Playlists ----------
    /** Naka-save bilang JSON: [{"name":"...","songs":["path",...]}] */
    fun playlists(): LinkedHashMap<String, MutableList<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        try {
            val arr = JSONArray(sp.getString(KEY_PLAYLISTS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val songs = o.optJSONArray("songs") ?: JSONArray()
                map[o.getString("name")] = MutableList(songs.length()) { songs.getString(it) }
            }
        } catch (e: Exception) { }
        return map
    }

    private fun savePlaylists(map: Map<String, List<String>>) {
        val arr = JSONArray()
        for ((name, songs) in map) {
            arr.put(JSONObject().put("name", name).put("songs", JSONArray(songs)))
        }
        sp.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun createPlaylist(name: String): Boolean {
        val map = playlists()
        if (name.isBlank() || map.containsKey(name)) return false
        map[name] = mutableListOf()
        savePlaylists(map)
        return true
    }

    fun addToPlaylist(name: String, path: String): Boolean {
        val map = playlists()
        val list = map[name] ?: return false
        if (list.contains(path)) return false
        list.add(path)
        savePlaylists(map)
        return true
    }

    fun removeFromPlaylist(name: String, path: String) {
        val map = playlists()
        map[name]?.remove(path)
        savePlaylists(map)
    }

    fun deletePlaylist(name: String) {
        val map = playlists()
        map.remove(name)
        savePlaylists(map)
    }

    fun renamePlaylist(old: String, new: String): Boolean {
        val map = playlists()
        if (new.isBlank() || map.containsKey(new)) return false
        val rebuilt = LinkedHashMap<String, List<String>>()
        for ((k, v) in map) rebuilt[if (k == old) new else k] = v
        savePlaylists(rebuilt)
        return true
    }

    companion object {
        private const val KEY_FOLDERS = "custom_folders"
        private const val KEY_SCAN_WHOLE = "scan_whole_sd"
        private const val KEY_CACHE = "cached_songs"
        private const val KEY_FAVS = "favorites"
        private const val KEY_VOLUME = "volume"
        private const val KEY_PLAYLISTS = "playlists"
    }
}
