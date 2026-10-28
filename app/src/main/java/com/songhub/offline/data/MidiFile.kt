package com.songhub.offline.data

import android.net.Uri

data class MidiFile(
    val title: String,
    val uri: Uri,
    val lyrics: List<String> = emptyList()
)
