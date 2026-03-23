package com.sayaem.nebula.data.models

import android.net.Uri


data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val duration: Long,       // milliseconds
    val size: Long,
    val albumArtUri: Uri?,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val isVideo: Boolean = false,
    val filePath: String = "",
    var playCount: Int = 0,
    var skipCount: Int = 0,
    var isFavorite: Boolean = false,
) {
    val durationFormatted: String get() {
        val m = (duration / 60000).toString().padStart(2, '0')
        val s = ((duration % 60000) / 1000).toString().padStart(2, '0')
        return "$m:$s"
    }

    val sizeFormatted: String get() {
        return if (size < 1024 * 1024)
            "${size / 1024} KB"
        else
            "%.1f MB".format(size.toFloat() / (1024 * 1024))
    }
}

enum class RepeatMode { NONE, ALL, ONE }

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val isShuffled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    val isLoading: Boolean = false,
) {
    val progress: Float get() =
        if (duration == 0L) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)

    val hasNext: Boolean get() = queueIndex < queue.size - 1
    val hasPrev: Boolean get() = queueIndex > 0
}

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val songCount: Int get() = songIds.size
}

data class PlayStats(
    val songId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0L,
) {
    val skipRate: Float get() {
        val total = playCount + skipCount
        return if (total == 0) 0f else skipCount.toFloat() / total
    }
}
