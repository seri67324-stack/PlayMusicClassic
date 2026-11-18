package com.playmusic.classic.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayMusicDao {
    @Query("SELECT * FROM song_stats")
    fun observeAllSongStats(): Flow<List<SongStatsEntity>>

    @Query("SELECT * FROM song_stats WHERE songId = :songId")
    suspend fun getSongStat(songId: Long): SongStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongStat(stat: SongStatsEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongIdsForPlaylist(playlistId: Long): List<Long>

    @Query("SELECT * FROM playlist_songs")
    fun observeAllPlaylistSongs(): Flow<List<PlaylistSongCrossRef>>

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQueue(items: List<PlaybackQueueEntity>)

    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    suspend fun getSavedQueue(): List<PlaybackQueueEntity>
}

@Database(
    entities = [
        SongStatsEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlaybackQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlayMusicDatabase : RoomDatabase() {
    abstract fun dao(): PlayMusicDao

    companion object {
        @Volatile
        private var INSTANCE: PlayMusicDatabase? = null

        fun getInstance(context: Context): PlayMusicDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PlayMusicDatabase::class.java,
                    "classic_play_music.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
