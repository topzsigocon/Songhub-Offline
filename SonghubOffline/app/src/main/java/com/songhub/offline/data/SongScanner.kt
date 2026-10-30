package com.songhub.offline.data

import android.content.Context
import com.songhub.offline.util.StorageUtils
import java.io.File

/**
 * Nag-i-scan ng lahat ng .mid / .midi / .kar files.
 *
 * Saan naghahanap (in order):
 *  1. <storage>/Songhub/        (kasama ang Songhub/MIDI/)
 *  2. <storage>/SDCard/Songhub/ (kasama ang SDCard/Songhub/MIDI/)
 *  3. Mga folder na pinili gamit ang built-in folder picker
 *  4. Buong SD card - kung naka-ON ang "Scan buong SD card",
 *     o kung walang Songhub folder at walang piniling folder.
 */
object SongScanner {

    private const val MAX_DEPTH = 12

    /** Mga Songhub folder na awtomatikong nakita sa phone/SD card. */
    fun detectSonghubFolders(context: Context): List<File> =
        StorageUtils.roots(context).mapNotNull { StorageUtils.findSonghubDir(it.dir) }

    fun scan(context: Context, prefs: Prefs): List<Song> {
        val roots = StorageUtils.roots(context)
        val targets = LinkedHashSet<File>()

        val songhub = detectSonghubFolders(context)
        targets.addAll(songhub)

        val custom = prefs.customFolders.map { File(it) }.filter { it.isDirectory }
        targets.addAll(custom)

        if (prefs.scanWholeSd || (songhub.isEmpty() && custom.isEmpty())) {
            val sd = roots.filter { it.removable }.map { it.dir }
            targets.addAll(sd)
        }

        val seen = HashSet<String>()
        val songs = ArrayList<Song>()
        for (t in targets) walk(t, 0, seen, songs)
        return songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun walk(dir: File, depth: Int, seen: MutableSet<String>, out: MutableList<Song>) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                // Laktawan ang system folders ng apps (hindi rin naman mababasa)
                if (depth == 0 && name.equals("Android", ignoreCase = true)) continue
                walk(f, depth + 1, seen, out)
            } else if (Song.isMidi(name) && seen.add(f.absolutePath)) {
                out.add(Song.fromFile(f))
            }
        }
    }
}
