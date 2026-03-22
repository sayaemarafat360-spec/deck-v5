package com.sayaem.nebula.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sayaem.nebula.data.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext


class MediaRepository(private val context: Context) {

    private val _songs     = MutableStateFlow<List<Song>>(emptyList())
    val songs: Flow<List<Song>> = _songs.asStateFlow()

    private val _videos    = MutableStateFlow<List<Song>>(emptyList())
    val videos: Flow<List<Song>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    suspend fun scanMedia() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            _songs.value  = queryAudio()
            _videos.value = queryVideo()
        } finally {
            _isScanning.value = false
        }
    }

    private fun queryAudio(): List<Song> {
        val songs      = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 50000"

        context.contentResolver.query(collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val trackCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id      = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val artUri  = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId)

                songs.add(Song(
                    id          = id,
                    title       = cursor.getString(titleCol)  ?: "Unknown",
                    artist      = cursor.getString(artistCol) ?: "Unknown Artist",
                    album       = cursor.getString(albumCol)  ?: "Unknown Album",
                    uri         = ContentUris.withAppendedId(collection, id),
                    duration    = cursor.getLong(durCol),
                    size        = cursor.getLong(sizeCol),
                    albumArtUri = artUri,
                    trackNumber = cursor.getInt(trackCol),
                    year        = cursor.getInt(yearCol),
                    filePath    = cursor.getString(dataCol) ?: "",
                    isVideo     = false,
                ))
            }
        }
        return songs
    }

    private fun queryVideo(): List<Song> {
        val videos     = mutableListOf<Song>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
        )
        context.contentResolver.query(collection, projection,
            "${MediaStore.Video.Media.SIZE} > 100000", null,
            "${MediaStore.Video.Media.TITLE} ASC")?.use { cursor ->
            val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val artCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
            val albCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ALBUM)
            val durCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos.add(Song(
                    id          = id,
                    title       = cursor.getString(titleCol) ?: "Unknown",
                    artist      = cursor.getString(artCol)   ?: "",
                    album       = cursor.getString(albCol)   ?: "",
                    uri         = ContentUris.withAppendedId(collection, id),
                    duration    = cursor.getLong(durCol),
                    size        = cursor.getLong(sizeCol),
                    albumArtUri = null,
                    filePath    = cursor.getString(dataCol)  ?: "",
                    isVideo     = true,
                ))
            }
        }
        return videos
    }

    fun search(query: String, songs: List<Song>): List<Song> {
        if (query.isBlank()) return songs
        val q = query.lowercase()
        return songs.filter {
            it.title.lowercase().contains(q)  ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    // ── Tag Editor: update title/artist/album via MediaStore ──────────
    suspend fun updateTags(
        context: android.content.Context,
        song: com.sayaem.nebula.data.models.Song,
        title: String,
        artist: String,
        album: String,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.TITLE, title.trim())
                put(android.provider.MediaStore.Audio.Media.ARTIST, artist.trim())
                put(android.provider.MediaStore.Audio.Media.ALBUM, album.trim())
            }
            val updated = context.contentResolver.update(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                "${android.provider.MediaStore.Audio.Media._ID} = ?",
                arrayOf(song.id.toString())
            )
            updated > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Recently Added: songs added in last N days ────────────────────
    suspend fun getRecentlyAdded(days: Int = 7): List<com.sayaem.nebula.data.models.Song> =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() / 1000 - (days * 86400)
            val uri    = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj   = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.SIZE,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DATE_ADDED,
            )
            val sel  = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} >= ?"
            val args = arrayOf(cutoff.toString())
            val sort = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"
            val results = mutableListOf<com.sayaem.nebula.data.models.Song>()
            try {
                context.contentResolver.query(uri, proj, sel, args, sort)?.use { cursor ->
                    val idCol     = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol  = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val albumCol  = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                    val durCol    = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val sizeCol   = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
                    val pathCol   = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    while (cursor.moveToNext()) {
                        val id  = cursor.getLong(idCol)
                        val cUri = android.net.Uri.withAppendedPath(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                        results.add(com.sayaem.nebula.data.models.Song(
                            id = id, title = cursor.getString(titleCol) ?: "Unknown",
                            artist = cursor.getString(artistCol) ?: "Unknown",
                            album  = cursor.getString(albumCol) ?: "Unknown",
                            uri    = cUri, duration = cursor.getLong(durCol),
                            size   = cursor.getLong(sizeCol),
                            albumArtUri = null, filePath = cursor.getString(pathCol) ?: "",
                        ))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            results
        }
}
