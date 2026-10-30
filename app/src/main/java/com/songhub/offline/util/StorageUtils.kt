package com.songhub.offline.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/** Isang storage location: Internal Storage o SD Card. */
data class StorageRoot(val label: String, val dir: File, val removable: Boolean)

object StorageUtils {

    /**
     * Hanapin lahat ng storage: internal + lahat ng SD card / USB OTG.
     * Gumagana sa Android 5 hanggang 11+.
     */
    fun roots(context: Context): List<StorageRoot> {
        val result = LinkedHashMap<String, StorageRoot>()

        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null) result[primary.absolutePath] = StorageRoot("Internal Storage", primary, false)

        // Paraan 1: getExternalFilesDirs -> /storage/XXXX-XXXX/Android/data/<pkg>/files
        for (f in ContextCompat.getExternalFilesDirs(context, null)) {
            if (f == null) continue
            val path = f.absolutePath
            val idx = path.indexOf("/Android/data/")
            if (idx <= 0) continue
            val root = File(path.substring(0, idx))
            if (!result.containsKey(root.absolutePath)) {
                result[root.absolutePath] = StorageRoot("SD Card (${root.name})", root, true)
            }
        }

        // Paraan 2: tingnan ang /storage/ folder
        File("/storage").listFiles()?.forEach { f ->
            val n = f.name
            if (f.isDirectory && n != "emulated" && n != "self" && f.canRead() &&
                !result.containsKey(f.absolutePath)
            ) {
                result[f.absolutePath] = StorageRoot("SD Card ($n)", f, true)
            }
        }
        return result.values.toList()
    }

    /** Hanapin ang "Songhub" o "SDCard/Songhub" folder (case-insensitive). */
    fun findSonghubDir(root: File): File? =
        findChild(root, "Songhub") ?: findChild(findChild(root, "SDCard"), "Songhub")

    private fun findChild(dir: File?, name: String): File? =
        dir?.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }
}

/**
 * Storage permission helper.
 *  - Android 11+ (API 30): MANAGE_EXTERNAL_STORAGE ("All files access")
 *  - Android 6-10: READ_EXTERNAL_STORAGE (runtime permission)
 *  - Android 5: granted na sa install
 */
object StoragePermission {

    const val REQUEST_CODE = 1001

    fun has(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager() || readGranted(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> readGranted(context)
        else -> true
    }

    private fun readGranted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + activity.packageName)
                    )
                )
            } catch (e: Exception) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    // Walang settings screen (ilang Android Go phones) -> normal na permission
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE
            )
        }
    }
}

/** Format ng oras: 125000 ms -> "2:05" */
fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
