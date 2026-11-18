package com.playmusic.classic.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RatingState {
    NONE, THUMBS_UP, THUMBS_DOWN
}

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri,
    val dateAdded: Long,
    val trackNumber: Int,
    val genre: String = "Bilinmeyen Tür",
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val rating: RatingState = RatingState.NONE
) {
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri,
    val songCount: Int
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val heroArtUri: Uri?
)

data class GenreItem(
    val name: String,
    val songCount: Int
)

@Entity(tableName = "song_stats")
data class SongStatsEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val rating: String = RatingState.NONE.name
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int
)

@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey val position: Int,
    val songId: Long,
    val sourceTitle: String
)
