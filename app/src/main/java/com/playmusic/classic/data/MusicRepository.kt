package com.playmusic.classic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val dao: PlayMusicDao
) {
    val songStatsFlow: Flow<List<SongStatsEntity>> = dao.observeAllSongStats()
    val playlistsFlow: Flow<List<PlaylistEntity>> = dao.observePlaylists()
    val playlistSongsFlow: Flow<List<PlaylistSongCrossRef>> = dao.observeAllPlaylistSongs()

    suspend fun scanDeviceMusic(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val genreMap = loadGenreMap()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            val artworkBaseUri = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Bilinmeyen Şarkı"
                val rawArtist = cursor.getString(artistCol)
                val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Bilinmeyen Sanatçı" else rawArtist
                val artistId = cursor.getLong(artistIdCol)
                val rawAlbum = cursor.getString(albumCol)
                val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") "Bilinmeyen Albüm" else rawAlbum
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val track = cursor.getInt(trackCol)

                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val albumArtUri = ContentUris.withAppendedId(artworkBaseUri, albumId)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        artistId = artistId,
                        album = album,
                        albumId = albumId,
                        duration = duration,
                        contentUri = contentUri,
                        albumArtUri = albumArtUri,
                        dateAdded = dateAdded,
                        trackNumber = track,
                        genre = genreMap[id] ?: "Genel / Pop"
                    )
                )
            }
        }
        songs
    }

    private fun loadGenreMap(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        runCatching {
            val genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genreProj = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)
            context.contentResolver.query(genreUri, genreProj, null, null, null)?.use { gCursor ->
                val idIdx = gCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameIdx = gCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (gCursor.moveToNext()) {
                    val genreId = gCursor.getLong(idIdx)
                    val genreName = gCursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { mCursor ->
                        val audioIdIdx = mCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (mCursor.moveToNext()) {
                            map[mCursor.getLong(audioIdIdx)] = genreName
                        }
                    }
                }
            }
        }
        return map
    }

    suspend fun incrementPlayCount(songId: Long) {
        val current = dao.getSongStat(songId) ?: SongStatsEntity(songId = songId)
        dao.upsertSongStat(
            current.copy(
                playCount = current.playCount + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleRating(songId: Long, target: RatingState) {
        val current = dao.getSongStat(songId) ?: SongStatsEntity(songId = songId)
        val currentRating = runCatching { RatingState.valueOf(current.rating) }.getOrDefault(RatingState.NONE)
        val nextRating = if (currentRating == target) RatingState.NONE else target
        dao.upsertSongStat(current.copy(rating = nextRating.name))
    }

    suspend fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) {
        val playlistId = dao.createPlaylist(PlaylistEntity(name = name))
        initialSongs.forEachIndexed { idx, songId ->
            dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, idx))
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val existing = dao.getSongIdsForPlaylist(playlistId)
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, existing.size))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun persistQueue(songs: List<Song>, sourceTitle: String) {
        dao.clearQueue()
        dao.saveQueue(
            songs.mapIndexed { index, song ->
                PlaybackQueueEntity(position = index, songId = song.id, sourceTitle = sourceTitle)
            }
        )
    }

    suspend fun loadPersistedQueue(): List<PlaybackQueueEntity> = dao.getSavedQueue()
}
