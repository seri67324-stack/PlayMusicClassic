package com.playmusic.classic.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.playmusic.classic.data.*
import com.playmusic.classic.media.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val queueSourceTitle: String = "TÜM ŞARKILARIM",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val showLockScreenArtwork: Boolean = true,
    val gaplessPlayback: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PlayMusicDatabase.getInstance(application)
    private val repository = MusicRepository(application, database.dao())
    private var mediaController: MediaController? = null

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(
        _rawSongs,
        repository.songStatsFlow
    ) { rawList, stats ->
        val statMap = stats.associateBy { it.songId }
        rawList.map { song ->
            val st = statMap[song.id]
            song.copy(
                playCount = st?.playCount ?: 0,
                lastPlayedAt = st?.lastPlayedAt ?: 0L,
                rating = runCatching { RatingState.valueOf(st?.rating ?: "NONE") }
                    .getOrDefault(RatingState.NONE)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = songs.map { list ->
        list.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = albumId,
                title = first.album,
                artist = first.artist,
                albumArtUri = first.albumArtUri,
                songCount = albumSongs.size
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = songs.map { list ->
        list.groupBy { it.artist }.map { (artistName, artistSongs) ->
            val first = artistSongs.first()
            Artist(
                id = first.artistId,
                name = artistName,
                albumCount = artistSongs.map { it.albumId }.distinct().size,
                songCount = artistSongs.size,
                heroArtUri = first.albumArtUri
            )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<GenreItem>> = songs.map { list ->
        list.groupBy { it.genre }.map { (genreName, genreSongs) ->
            GenreItem(name = genreName, songCount = genreSongs.size)
        }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistCrossRefs: StateFlow<List<PlaylistSongCrossRef>> = repository.playlistSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        connectToPlaybackService()
        rescanLibrary()
        startProgressTicker()

        // Şarkı istatistikleri güncellendiğinde aktif şarkının beğeni durumunu senkronize et
        viewModelScope.launch {
            songs.collect { updatedSongs ->
                val currentId = _playbackState.value.currentSong?.id ?: return@collect
                val updatedCurrent = updatedSongs.find { it.id == currentId }
                if (updatedCurrent != null) {
                    _playbackState.update { state ->
                        state.copy(
                            currentSong = updatedCurrent,
                            queue = state.queue.map { q -> updatedSongs.find { it.id == q.id } ?: q }
                        )
                    }
                }
            }
        }
    }

    private fun connectToPlaybackService() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            mediaController = controller

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId?.toLongOrNull() ?: return
                    val matched = songs.value.find { it.id == songId }
                    _playbackState.update {
                        it.copy(
                            currentSong = matched,
                            durationMs = matched?.duration ?: controller.duration.coerceAtLeast(0L)
                        )
                    }
                    viewModelScope.launch {
                        repository.incrementPlayCount(songId)
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _playbackState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _playbackState.update { it.copy(repeatMode = repeatMode) }
                }
            })
        }
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = if (controller.duration > 0L) controller.duration else it.durationMs
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            val scanned = repository.scanDeviceMusic()
            _rawSongs.value = scanned

            // Daha önce kaydedilmiş bir sıra varsa ve şu an sıra boşsa geri yükle
            if (_playbackState.value.queue.isEmpty() && scanned.isNotEmpty()) {
                val savedQueue = repository.loadPersistedQueue()
                if (savedQueue.isNotEmpty()) {
                    val songMap = scanned.associateBy { it.id }
                    val restoredSongs = savedQueue.mapNotNull { songMap[it.songId] }
                    if (restoredSongs.isNotEmpty()) {
                        _playbackState.update {
                            it.copy(
                                currentSong = restoredSongs.first(),
                                queue = restoredSongs,
                                queueSourceTitle = savedQueue.first().sourceTitle,
                                durationMs = restoredSongs.first().duration
                            )
                        }
                    }
                }
            }
        }
    }

    fun playSongs(
        list: List<Song>,
        startIndex: Int = 0,
        sourceTitle: String = "TÜM ŞARKILARIM",
        shuffle: Boolean = false
    ) {
        if (list.isEmpty()) return
        val controller = mediaController ?: return
        val showArt = _playbackState.value.showLockScreenArtwork

        val mediaItems = list.map { song -> song.toMediaItem(showArt) }
        val safeIndex = startIndex.coerceIn(0, list.lastIndex)

        controller.shuffleModeEnabled = shuffle
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()

        val activeSong = list[safeIndex]
        _playbackState.update {
            it.copy(
                currentSong = activeSong,
                queue = list,
                queueSourceTitle = sourceTitle,
                isPlaying = true,
                shuffleEnabled = shuffle,
                durationMs = activeSong.duration,
                currentPositionMs = 0L
            )
        }

        // Çalma sayısı onMediaItemTransition içinde artırılır (çift sayımı önlemek için burada artırılmaz)
        viewModelScope.launch {
            repository.persistQueue(list, sourceTitle)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && _playbackState.value.queue.isNotEmpty()) {
            playSongs(_playbackState.value.queue, 0, _playbackState.value.queueSourceTitle)
            return
        }
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3500L) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        controller.addMediaItem(song.toMediaItem(_playbackState.value.showLockScreenArtwork))
        val updatedQueue = _playbackState.value.queue + song
        _playbackState.update { it.copy(queue = updatedQueue) }
        viewModelScope.launch {
            repository.persistQueue(updatedQueue, _playbackState.value.queueSourceTitle)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        mediaController?.moveMediaItem(fromIndex, toIndex)
        _playbackState.update { it.copy(queue = currentQueue) }
        viewModelScope.launch {
            repository.persistQueue(currentQueue, _playbackState.value.queueSourceTitle)
        }
    }

    fun clearQueue() {
        mediaController?.clearMediaItems()
        _playbackState.update {
            it.copy(
                currentSong = null,
                queue = emptyList(),
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
        viewModelScope.launch {
            repository.persistQueue(emptyList(), "")
        }
    }

    fun toggleRating(songId: Long, target: RatingState) {
        viewModelScope.launch {
            repository.toggleRating(songId, target)
        }
    }

    fun createPlaylist(name: String, initialSongs: List<Long> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim(), initialSongs)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLockScreenArtwork(enabled: Boolean) {
        _playbackState.update { it.copy(showLockScreenArtwork = enabled) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _playbackState.update { it.copy(gaplessPlayback = enabled) }
    }

    private fun Song.toMediaItem(includeArtwork: Boolean): MediaItem {
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        if (includeArtwork) {
            metaBuilder.setArtworkUri(albumArtUri)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }
}
