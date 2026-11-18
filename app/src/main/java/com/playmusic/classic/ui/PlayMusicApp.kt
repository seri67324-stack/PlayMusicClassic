package com.playmusic.classic.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.playmusic.classic.data.*
import com.playmusic.classic.media.EqualizerManager
import com.playmusic.classic.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class RootSection(val title: String) {
    LISTEN_NOW("Şimdi Dinle"),
    MUSIC_LIBRARY("Müzik kitaplığı"),
    SETTINGS("Müzik Ayarları"),
    EQUALIZER("Ekolayzer")
}

sealed class DetailOverlay {
    data class AlbumDetail(val album: Album) : DetailOverlay()
    data class ArtistDetail(val artist: Artist) : DetailOverlay()
    data class PlaylistDetail(val title: String, val songs: List<Song>) : DetailOverlay()
    data class GenreDetail(val genre: GenreItem) : DetailOverlay()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayMusicApp(
    viewModel: MainViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1100L)
        showSplash = false
    }

    if (showSplash) {
        ClassicSplashScreen()
        return
    }

    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistCrossRefs by viewModel.playlistCrossRefs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentSection by remember { mutableStateOf(RootSection.MUSIC_LIBRARY) }
    var detailOverlay by remember { mutableStateOf<DetailOverlay?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isFullPlayerOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var queueSaveDialog by remember { mutableStateOf(false) }

    // Sesli Arama (Speech Recognizer)
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                isSearchActive = true
                viewModel.setSearchQuery(spoken)
            }
        }
    }
    val launchVoiceSearch: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Şarkı, sanatçı veya albüm söyleyin")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    BackHandler(enabled = isQueueOpen || isFullPlayerOpen || isSearchActive || detailOverlay != null) {
        when {
            isQueueOpen -> isQueueOpen = false
            isFullPlayerOpen -> isFullPlayerOpen = false
            isSearchActive -> {
                isSearchActive = false
                viewModel.setSearchQuery("")
            }
            detailOverlay != null -> detailOverlay = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = Color.White
            ) {
                // Klasik Google Play Music Sol Çekmece Başlığı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .background(PlayMusicOrange)
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headset,
                                contentDescription = null,
                                tint = PlayMusicOrange,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Google Play Müzik",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Yerel Çevrimdışı Kitaplık",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                DrawerMenuItem(
                    icon = Icons.Default.PlayCircleFilled,
                    label = "Şimdi Dinle",
                    selected = currentSection == RootSection.LISTEN_NOW && detailOverlay == null
                ) {
                    currentSection = RootSection.LISTEN_NOW
                    detailOverlay = null
                    scope.launch { drawerState.close() }
                }
                DrawerMenuItem(
                    icon = Icons.Default.LibraryMusic,
                    label = "Müzik kitaplığı",
                    selected = currentSection == RootSection.MUSIC_LIBRARY && detailOverlay == null
                ) {
                    currentSection = RootSection.MUSIC_LIBRARY
                    detailOverlay = null
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DrawerMenuItem(
                    icon = Icons.Default.Equalizer,
                    label = "Ekolayzer",
                    selected = currentSection == RootSection.EQUALIZER
                ) {
                    currentSection = RootSection.EQUALIZER
                    detailOverlay = null
                    scope.launch { drawerState.close() }
                }
                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    label = "Ayarlar",
                    selected = currentSection == RootSection.SETTINGS
                ) {
                    currentSection = RootSection.SETTINGS
                    detailOverlay = null
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    if (isSearchActive) {
                        SearchTopBar(
                            query = searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            onClose = {
                                isSearchActive = false
                                viewModel.setSearchQuery("")
                            },
                            onVoiceClick = launchVoiceSearch
                        )
                    } else {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = PlayMusicOrange,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            ),
                            navigationIcon = {
                                if (detailOverlay != null) {
                                    IconButton(onClick = { detailOverlay = null }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                                    }
                                } else {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menü")
                                    }
                                }
                            },
                            title = {
                                val titleText = when (val overlay = detailOverlay) {
                                    is DetailOverlay.AlbumDetail -> overlay.album.title
                                    is DetailOverlay.ArtistDetail -> overlay.artist.name
                                    is DetailOverlay.PlaylistDetail -> overlay.title
                                    is DetailOverlay.GenreDetail -> overlay.genre.name
                                    null -> currentSection.title
                                }
                                Text(
                                    text = titleText,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Ara")
                                }
                                IconButton(onClick = launchVoiceSearch) {
                                    Icon(Icons.Default.Mic, contentDescription = "Sesli arama")
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (playbackState.currentSong != null) {
                        PersistentMiniPlayer(
                            state = playbackState,
                            onTogglePlay = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            onSkipPrev = viewModel::skipPrevious,
                            onExpand = { isFullPlayerOpen = true }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(PlayMusicBackground)
                ) {
                    if (!hasPermission) {
                        PermissionCard(onRequestPermission = onRequestPermission)
                    } else if (isSearchActive) {
                        SearchResultsScreen(
                            query = searchQuery,
                            songs = songs,
                            albums = albums,
                            artists = artists,
                            onPlaySong = { song ->
                                viewModel.playSongs(listOf(song), 0, "ARAMA SONUCU")
                            },
                            onAlbumClick = { album ->
                                isSearchActive = false
                                detailOverlay = DetailOverlay.AlbumDetail(album)
                            },
                            onArtistClick = { artist ->
                                isSearchActive = false
                                detailOverlay = DetailOverlay.ArtistDetail(artist)
                            }
                        )
                    } else if (detailOverlay != null) {
                        when (val d = detailOverlay!!) {
                            is DetailOverlay.AlbumDetail -> {
                                val albumSongs = songs.filter { it.albumId == d.album.id }
                                CollectionDetailScreen(
                                    title = d.album.title,
                                    subtitle = "${d.album.artist} • ${albumSongs.size} şarkı",
                                    artUri = d.album.albumArtUri,
                                    songs = albumSongs,
                                    onPlayAll = { shuffle ->
                                        viewModel.playSongs(albumSongs, 0, d.album.title, shuffle)
                                    },
                                    onSongClick = { idx ->
                                        viewModel.playSongs(albumSongs, idx, d.album.title)
                                    },
                                    onAddToQueue = viewModel::addToQueue,
                                    onAddToPlaylist = { songForPlaylistDialog = it }
                                )
                            }
                            is DetailOverlay.ArtistDetail -> {
                                val artistSongs = songs.filter { it.artist == d.artist.name }
                                CollectionDetailScreen(
                                    title = d.artist.name,
                                    subtitle = "${d.artist.albumCount} albüm • ${artistSongs.size} şarkı",
                                    artUri = d.artist.heroArtUri,
                                    songs = artistSongs,
                                    onPlayAll = { shuffle ->
                                        viewModel.playSongs(artistSongs, 0, d.artist.name, shuffle)
                                    },
                                    onSongClick = { idx ->
                                        viewModel.playSongs(artistSongs, idx, d.artist.name)
                                    },
                                    onAddToQueue = viewModel::addToQueue,
                                    onAddToPlaylist = { songForPlaylistDialog = it }
                                )
                            }
                            is DetailOverlay.PlaylistDetail -> {
                                CollectionDetailScreen(
                                    title = d.title,
                                    subtitle = "${d.songs.size} şarkı",
                                    artUri = d.songs.firstOrNull()?.albumArtUri,
                                    songs = d.songs,
                                    onPlayAll = { shuffle ->
                                        viewModel.playSongs(d.songs, 0, d.title, shuffle)
                                    },
                                    onSongClick = { idx ->
                                        viewModel.playSongs(d.songs, idx, d.title)
                                    },
                                    onAddToQueue = viewModel::addToQueue,
                                    onAddToPlaylist = { songForPlaylistDialog = it }
                                )
                            }
                            is DetailOverlay.GenreDetail -> {
                                val genreSongs = songs.filter { it.genre == d.genre.name }
                                CollectionDetailScreen(
                                    title = d.genre.name,
                                    subtitle = "${genreSongs.size} şarkı",
                                    artUri = genreSongs.firstOrNull()?.albumArtUri,
                                    songs = genreSongs,
                                    onPlayAll = { shuffle ->
                                        viewModel.playSongs(genreSongs, 0, d.genre.name, shuffle)
                                    },
                                    onSongClick = { idx ->
                                        viewModel.playSongs(genreSongs, idx, d.genre.name)
                                    },
                                    onAddToQueue = viewModel::addToQueue,
                                    onAddToPlaylist = { songForPlaylistDialog = it }
                                )
                            }
                        }
                    } else {
                        when (currentSection) {
                            RootSection.LISTEN_NOW -> ListenNowScreen(
                                songs = songs,
                                albums = albums,
                                onShuffleAll = {
                                    viewModel.playSongs(songs, 0, "TÜM ŞARKILARIM", true)
                                },
                                onAlbumClick = { detailOverlay = DetailOverlay.AlbumDetail(it) },
                                onPlayAlbum = { album ->
                                    val list = songs.filter { it.albumId == album.id }
                                    viewModel.playSongs(list, 0, album.title)
                                }
                            )
                            RootSection.MUSIC_LIBRARY -> MusicLibraryPagerScreen(
                                songs = songs,
                                albums = albums,
                                artists = artists,
                                genres = genres,
                                playlists = playlists,
                                playlistCrossRefs = playlistCrossRefs,
                                onPlaySongs = viewModel::playSongs,
                                onAlbumClick = { detailOverlay = DetailOverlay.AlbumDetail(it) },
                                onArtistClick = { detailOverlay = DetailOverlay.ArtistDetail(it) },
                                onGenreClick = { detailOverlay = DetailOverlay.GenreDetail(it) },
                                onPlaylistClick = { title, list ->
                                    detailOverlay = DetailOverlay.PlaylistDetail(title, list)
                                },
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onAddToQueue = viewModel::addToQueue,
                                onAddToPlaylist = { songForPlaylistDialog = it }
                            )
                            RootSection.SETTINGS -> MusicSettingsScreen(
                                playbackState = playbackState,
                                onLockScreenArtToggle = viewModel::setLockScreenArtwork,
                                onGaplessToggle = viewModel::setGaplessPlayback,
                                onRescanLibrary = viewModel::rescanLibrary,
                                onOpenEqualizer = { currentSection = RootSection.EQUALIZER }
                            )
                            RootSection.EQUALIZER -> EqualizerScreen()
                        }
                    }
                }
            }

            // Tam Ekran Oynatıcı (Now Playing)
            AnimatedVisibility(
                visible = isFullPlayerOpen && playbackState.currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                FullScreenPlayer(
                    state = playbackState,
                    onCollapse = { isFullPlayerOpen = false },
                    onOpenQueue = { isQueueOpen = true },
                    onTogglePlay = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrev = viewModel::skipPrevious,
                    onSeek = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onToggleRating = viewModel::toggleRating,
                    onAddToPlaylist = { songForPlaylistDialog = playbackState.currentSong },
                    onGoToArtist = {
                        val current = playbackState.currentSong ?: return@FullScreenPlayer
                        val matchedArtist = artists.find { it.name == current.artist }
                        if (matchedArtist != null) {
                            isFullPlayerOpen = false
                            detailOverlay = DetailOverlay.ArtistDetail(matchedArtist)
                        }
                    },
                    onGoToAlbum = {
                        val current = playbackState.currentSong ?: return@FullScreenPlayer
                        val matchedAlbum = albums.find { it.id == current.albumId }
                        if (matchedAlbum != null) {
                            isFullPlayerOpen = false
                            detailOverlay = DetailOverlay.AlbumDetail(matchedAlbum)
                        }
                    },
                    onClearQueue = {
                        viewModel.clearQueue()
                        isFullPlayerOpen = false
                    },
                    onSaveQueue = { queueSaveDialog = true }
                )
            }

            // Sıra (Queue) Ekranı
            AnimatedVisibility(
                visible = isQueueOpen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                QueueScreen(
                    state = playbackState,
                    onClose = { isQueueOpen = false },
                    onSelectQueueItem = { index ->
                        viewModel.playSongs(
                            playbackState.queue,
                            index,
                            playbackState.queueSourceTitle,
                            playbackState.shuffleEnabled
                        )
                    },
                    onMoveQueueItem = viewModel::moveQueueItem,
                    onClearQueue = {
                        viewModel.clearQueue()
                        isQueueOpen = false
                        isFullPlayerOpen = false
                    }
                )
            }
        }
    }

    // Yeni Çalma Listesi Oluşturma Diyaloğu
    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Yeni şarkı listesi") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Şarkı listesi adı") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(playlistName)
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text("OLUŞTUR", color = PlayMusicOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("İPTAL")
                }
            }
        )
    }

    // Sırayı Çalma Listesi Olarak Kaydetme Diyaloğu
    if (queueSaveDialog) {
        var queuePlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { queueSaveDialog = false },
            title = { Text("Sırayı şarkı listesi olarak kaydet") },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it },
                    label = { Text("Liste adı") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(
                            queuePlaylistName,
                            playbackState.queue.map { it.id }
                        )
                        queueSaveDialog = false
                    }
                ) {
                    Text("KAYDET", color = PlayMusicOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { queueSaveDialog = false }) {
                    Text("İPTAL")
                }
            }
        )
    }

    // Şarkıyı Çalma Listesine Ekleme Diyaloğu
    songForPlaylistDialog?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForPlaylistDialog = null },
            title = { Text("Şarkı listesine ekle") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            songForPlaylistDialog = null
                            showCreatePlaylistDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PlayMusicOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("YENİ ŞARKI LİSTESİ", color = PlayMusicOrange, fontWeight = FontWeight.Bold)
                    }
                    playlists.forEach { pl ->
                        TextButton(
                            onClick = {
                                viewModel.addSongToPlaylist(pl.playlistId, targetSong.id)
                                songForPlaylistDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pl.name, color = PlayMusicTextPrimary, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songForPlaylistDialog = null }) {
                    Text("KAPAT")
                }
            }
        )
    }
}

@Composable
fun ClassicSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = PlayMusicOrange,
                shadowElevation = 6.dp,
                modifier = Modifier.size(116.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Headset,
                        contentDescription = "Google Play Müzik",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Google Play Müzik",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = PlayMusicTextSecondary
            )
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) PlayMusicOrange.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PlayMusicOrange else PlayMusicTextSecondary
        )
        Spacer(Modifier.width(24.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) PlayMusicOrange else PlayMusicTextPrimary
        )
    }
}

@Composable
fun ListenNowScreen(
    songs: List<Song>,
    albums: List<Album>,
    onShuffleAll: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            val heroArt = songs.maxByOrNull { it.lastPlayedAt }?.albumArtUri
                ?: songs.firstOrNull()?.albumArtUri
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(PlayMusicDrawerHeaderBg)
            ) {
                if (heroArt != null) {
                    AsyncImage(
                        model = heroArt,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            "ŞİMDİ DİNLE",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kendini şanslı hissediyor musun?",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kitaplığındaki ${songs.size} şarkıyı karıştırmak için dokun",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
                FloatingActionButton(
                    onClick = onShuffleAll,
                    containerColor = PlayMusicOrange,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Karıştır")
                }
            }
        }
        item {
            Text(
                text = "Kitaplığında",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = PlayMusicTextPrimary,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(albums.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { album ->
                    Box(modifier = Modifier.weight(1f)) {
                        AlbumGridCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                            onPlay = { onPlayAlbum(album) }
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryPagerScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    genres: List<GenreItem>,
    playlists: List<PlaylistEntity>,
    playlistCrossRefs: List<PlaylistSongCrossRef>,
    onPlaySongs: (List<Song>, Int, String, Boolean) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onGenreClick: (GenreItem) -> Unit,
    onPlaylistClick: (String, List<Song>) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val tabs = listOf("ÇALMA LİSTELERİ", "SANATÇILAR", "ALBÜMLER", "ŞARKILAR", "TÜRLER")
    val pagerState = rememberPagerState(initialPage = 3) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = PlayMusicOrange,
            contentColor = Color.White,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 3.dp,
                    color = Color.White
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> PlaylistsTab(
                    songs = songs,
                    playlists = playlists,
                    crossRefs = playlistCrossRefs,
                    onOpenPlaylist = onPlaylistClick,
                    onCreatePlaylist = onCreatePlaylistClick,
                    onDeletePlaylist = onDeletePlaylist
                )
                1 -> ArtistsTab(artists = artists, onArtistClick = onArtistClick)
                2 -> AlbumsTab(
                    albums = albums,
                    onAlbumClick = onAlbumClick,
                    onPlayAlbum = { album ->
                        val albumSongs = songs.filter { it.albumId == album.id }
                        onPlaySongs(albumSongs, 0, album.title, false)
                    }
                )
                3 -> SongsTab(
                    songs = songs,
                    onShuffleAll = { onPlaySongs(songs, 0, "TÜM ŞARKILARIM", true) },
                    onSongClick = { idx -> onPlaySongs(songs, idx, "TÜM ŞARKILARIM", false) },
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist
                )
                4 -> GenresTab(genres = genres, onGenreClick = onGenreClick)
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    songs: List<Song>,
    playlists: List<PlaylistEntity>,
    crossRefs: List<PlaylistSongCrossRef>,
    onOpenPlaylist: (String, List<Song>) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit
) {
    val recentlyAdded = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(100) }
    val mostPlayed = remember(songs) { songs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(100) }
    val thumbsUp = remember(songs) { songs.filter { it.rating == RatingState.THUMBS_UP } }
    val songMap = remember(songs) { songs.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Otomatik Şarkı Listeleri",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = PlayMusicTextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        item {
            AutoPlaylistCard(
                title = "Beğenilenler (👍)",
                subtitle = "${thumbsUp.size} şarkı",
                onClick = { onOpenPlaylist("Beğenilenler", thumbsUp) }
            )
        }
        item {
            AutoPlaylistCard(
                title = "Son eklenenler",
                subtitle = "${recentlyAdded.size} şarkı",
                onClick = { onOpenPlaylist("Son eklenenler", recentlyAdded) }
            )
        }
        item {
            AutoPlaylistCard(
                title = "En çok çalınanlar",
                subtitle = "${mostPlayed.size} şarkı",
                onClick = { onOpenPlaylist("En çok çalınanlar", mostPlayed) }
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Şarkı Listelerin",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = PlayMusicTextSecondary
                )
                TextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = PlayMusicOrange)
                    Spacer(Modifier.width(4.dp))
                    Text("YENİ LİSTE", color = PlayMusicOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
        items(playlists) { playlist ->
            val plSongs = crossRefs
                .filter { it.playlistId == playlist.playlistId }
                .sortedBy { it.position }
                .mapNotNull { songMap[it.songId] }
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(playlist.name, plSongs) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = PlayMusicOrange)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${plSongs.size} şarkı", fontSize = 12.sp, color = PlayMusicTextSecondary)
                    }
                    IconButton(onClick = { onDeletePlaylist(playlist.playlistId) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", tint = PlayMusicTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AutoPlaylistCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = PlayMusicOrange,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 13.sp, color = PlayMusicTextSecondary)
            }
        }
    }
}

@Composable
fun ArtistsTab(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(artists) { artist ->
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(56.dp)
                        )
                        AsyncImage(
                            model = artist.heroArtUri,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = artist.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${artist.albumCount} albüm • ${artist.songCount} şarkı",
                            fontSize = 12.sp,
                            color = PlayMusicTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsTab(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(albums) { album ->
            AlbumGridCard(
                album = album,
                onClick = { onAlbumClick(album) },
                onPlay = { onPlayAlbum(album) }
            )
        }
    }
}

@Composable
fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(56.dp)
                )
                AsyncImage(
                    model = album.albumArtUri,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        fontSize = 12.sp,
                        color = PlayMusicTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menü",
                            tint = PlayMusicTextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Oynat") },
                            onClick = {
                                menuExpanded = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Albümü aç") },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongsTab(
    songs: List<Song>,
    onShuffleAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShuffleAll)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = PlayMusicOrange)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "TÜMÜNÜ KARIŞTIR",
                        color = PlayMusicOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
            SongListRow(
                song = song,
                onClick = { onSongClick(idx) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }
            )
            HorizontalDivider(color = Color(0xFFEEEEEE))
        }
    }
}

@Composable
fun SongListRow(
    song: Song,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = PlayMusicTextPrimary
            )
            Text(
                text = "${song.artist} • ${song.formattedDuration}",
                fontSize = 13.sp,
                color = PlayMusicTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler", tint = PlayMusicTextSecondary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Şimdi oynat") },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Sıraya ekle") },
                    onClick = {
                        menuExpanded = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Şarkı listesine ekle") },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    }
                )
            }
        }
    }
}

@Composable
fun GenresTab(genres: List<GenreItem>, onGenreClick: (GenreItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres) { genre ->
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGenreClick(genre) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Category, contentDescription = null, tint = PlayMusicOrange)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(genre.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${genre.songCount} şarkı", fontSize = 13.sp, color = PlayMusicTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    artUri: android.net.Uri?,
    songs: List<Song>,
    onPlayAll: (Boolean) -> Unit,
    onSongClick: (Int) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(PlayMusicDrawerHeaderBg)
            ) {
                if (artUri != null) {
                    AsyncImage(
                        model = artUri,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { onPlayAll(false) },
                    containerColor = PlayMusicOrange,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Oynat")
                }
            }
        }
        itemsIndexed(songs) { idx, song ->
            SongListRow(
                song = song,
                onClick = { onSongClick(idx) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }
            )
            HorizontalDivider(color = Color(0xFFEEEEEE))
        }
    }
}

@Composable
fun PersistentMiniPlayer(
    state: PlaybackUiState,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onExpand: () -> Unit
) {
    val song = state.currentSong ?: return
    Surface(
        color = Color.White,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -45f) onSkipNext()
                    else if (dragAmount > 45f) onSkipPrev()
                }
            }
    ) {
        Column {
            val progress = if (state.durationMs > 0) {
                (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                color = PlayMusicOrange,
                trackColor = Color(0xFFE0E0E0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = PlayMusicTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Oynat/Duraklat",
                        tint = PlayMusicOrange,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenPlayer(
    state: PlaybackUiState,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleRating: (Long, RatingState) -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueue: () -> Unit
) {
    val song = state.currentSong ?: return
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlayMusicOrange)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Küçült", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Sıra", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Şarkı listesine ekle") },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sanatçıya git") },
                            onClick = {
                                menuExpanded = false
                                onGoToArtist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Albüme git") },
                            onClick = {
                                menuExpanded = false
                                onGoToAlbum()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sırayı kaydet") },
                            onClick = {
                                menuExpanded = false
                                onSaveQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sırayı temizle") },
                            onClick = {
                                menuExpanded = false
                                onClearQueue()
                            }
                        )
                    }
                }
            }

            // Orta: Tam Genişlik Albüm Kapağı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF212121))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -50f) onSkipNext()
                            else if (dragAmount > 50f) onSkipPrev()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(120.dp)
                )
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Alt Kontroller
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val duration = state.durationMs.coerceAtLeast(1L)
                Slider(
                    value = state.currentPositionMs.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = PlayMusicOrange,
                        activeTrackColor = PlayMusicOrange,
                        inactiveTrackColor = Color(0xFFE0E0E0)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDurationMs(state.currentPositionMs), fontSize = 12.sp, color = PlayMusicTextSecondary)
                    Text(formatDurationMs(state.durationMs), fontSize = 12.sp, color = PlayMusicTextSecondary)
                }
                Spacer(Modifier.height(8.dp))

                // Tekrarla, Önceki, Oynat/Duraklat, Sonraki, Karıştır
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCycleRepeat) {
                        val icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        }
                        val tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) PlayMusicOrange else PlayMusicTextSecondary
                        Icon(icon, contentDescription = "Tekrarla", tint = tint)
                    }
                    IconButton(onClick = onSkipPrev) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Önceki",
                            tint = PlayMusicTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = onTogglePlay,
                        containerColor = PlayMusicOrange,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Oynat/Duraklat",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Sonraki",
                            tint = PlayMusicTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Karıştır",
                            tint = if (state.shuffleEnabled) PlayMusicOrange else PlayMusicTextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Beğenmeme ve Beğenme Satırı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleRating(song.id, RatingState.THUMBS_DOWN) }) {
                        Icon(
                            imageVector = if (song.rating == RatingState.THUMBS_DOWN) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Beğenme",
                            tint = if (song.rating == RatingState.THUMBS_DOWN) PlayMusicOrange else PlayMusicTextSecondary
                        )
                    }
                    Text(
                        text = state.queueSourceTitle,
                        fontSize = 12.sp,
                        color = PlayMusicTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onToggleRating(song.id, RatingState.THUMBS_UP) }) {
                        Icon(
                            imageVector = if (song.rating == RatingState.THUMBS_UP) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Beğen",
                            tint = if (song.rating == RatingState.THUMBS_UP) PlayMusicOrange else PlayMusicTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    state: PlaybackUiState,
    onClose: () -> Unit,
    onSelectQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PlayMusicOrange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Column {
                        Text(state.queueSourceTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("${state.queue.size} şarkı sırada", fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onClearQueue) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Sırayı temizle")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            itemsIndexed(state.queue) { index, song ->
                val isCurrent = state.currentSong?.id == song.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) PlayMusicOrange.copy(alpha = 0.10f) else Color.White)
                        .clickable { onSelectQueueItem(index) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Sırala",
                        tint = PlayMusicTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) PlayMusicOrange else PlayMusicTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${song.artist} • ${song.formattedDuration}",
                            fontSize = 12.sp,
                            color = PlayMusicTextSecondary,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = { if (index > 0) onMoveQueueItem(index, index - 1) },
                        enabled = index > 0
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı taşı")
                    }
                    IconButton(
                        onClick = { if (index < state.queue.lastIndex) onMoveQueueItem(index, index + 1) },
                        enabled = index < state.queue.lastIndex
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı taşı")
                    }
                }
                HorizontalDivider(color = Color(0xFFEEEEEE))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onVoiceClick: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kapat", tint = PlayMusicTextSecondary)
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Müzik ara...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Temizle")
                }
            }
            IconButton(onClick = onVoiceClick) {
                Icon(Icons.Default.Mic, contentDescription = "Sesli Arama", tint = PlayMusicOrange)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    onPlaySong: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit
) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Şarkı, sanatçı veya albüm arayın", color = PlayMusicTextSecondary)
        }
        return
    }

    val matchedArtists = artists.filter { it.name.lowercase().contains(q) }
    val matchedAlbums = albums.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    val matchedSongs = songs.filter {
        it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp)
    ) {
        if (matchedArtists.isNotEmpty()) {
            item {
                Text(
                    "SANATÇILAR",
                    fontWeight = FontWeight.Bold,
                    color = PlayMusicOrange,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(matchedArtists) { artist ->
                ListItem(
                    headlineContent = { Text(artist.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${artist.songCount} şarkı") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.clickable { onArtistClick(artist) }
                )
            }
        }
        if (matchedAlbums.isNotEmpty()) {
            item {
                Text(
                    "ALBÜMLER",
                    fontWeight = FontWeight.Bold,
                    color = PlayMusicOrange,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(matchedAlbums) { album ->
                ListItem(
                    headlineContent = { Text(album.title, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(album.artist) },
                    leadingContent = { Icon(Icons.Default.Album, contentDescription = null) },
                    modifier = Modifier.clickable { onAlbumClick(album) }
                )
            }
        }
        if (matchedSongs.isNotEmpty()) {
            item {
                Text(
                    "ŞARKILAR",
                    fontWeight = FontWeight.Bold,
                    color = PlayMusicOrange,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(matchedSongs) { song ->
                ListItem(
                    headlineContent = { Text(song.title, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${song.artist} • ${song.formattedDuration}") },
                    leadingContent = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    modifier = Modifier.clickable { onPlaySong(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSettingsScreen(
    playbackState: PlaybackUiState,
    onLockScreenArtToggle: (Boolean) -> Unit,
    onGaplessToggle: (Boolean) -> Unit,
    onRescanLibrary: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("ÇALMA", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            ListItem(
                headlineContent = { Text("Ekolayzer") },
                supportingContent = { Text("5 bantlı ekolayzer ve Bas Güçlendirme ayarları") },
                modifier = Modifier.clickable(onClick = onOpenEqualizer)
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                headlineContent = { Text("Boşluksuz oynatma (Gapless)") },
                supportingContent = { Text("Şarkılar arasında duraksama olmadan çal") },
                trailingContent = {
                    Switch(
                        checked = playbackState.gaplessPlayback,
                        onCheckedChange = onGaplessToggle
                    )
                }
            )
            HorizontalDivider()
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("KİLİTLİ EKRAN", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            ListItem(
                headlineContent = { Text("Kilit ekranında albüm kapağı göster") },
                supportingContent = { Text("Cihaz kilitliyken medya bildiriminde albüm kapağını göster") },
                trailingContent = {
                    Switch(
                        checked = playbackState.showLockScreenArtwork,
                        onCheckedChange = onLockScreenArtToggle
                    )
                }
            )
            HorizontalDivider()
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("KÜTÜPHANE VE DEPOLAMA", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            ListItem(
                headlineContent = { Text("Yerel müzik kitaplığını yeniden tara") },
                supportingContent = { Text("Yeni indirilen MP3, FLAC, WAV ve M4A dosyalarını bul") },
                modifier = Modifier.clickable(onClick = onRescanLibrary)
            )
            HorizontalDivider()
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("UYGULAMA HAKKINDA", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        item {
            ListItem(
                headlineContent = { Text("Derleme Sürümü") },
                supportingContent = { Text("v1.0.0-Classic (Media3 ExoPlayer & Room)") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen() {
    val eqState by EqualizerManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ekolayzer Etkin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it) }
                )
            }
            HorizontalDivider()
        }
        item {
            Text("Önayarlar (Presets)", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eqState.presets.take(5).forEach { preset ->
                    FilterChip(
                        selected = eqState.selectedPreset == preset,
                        onClick = { EqualizerManager.selectPreset(preset) },
                        label = { Text(preset) }
                    )
                }
            }
        }
        items(eqState.bands) { band ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${band.centerFreqHz} Hz", fontWeight = FontWeight.Medium)
                    Text("${band.currentLevel / 100} dB", color = PlayMusicTextSecondary)
                }
                Slider(
                    value = band.currentLevel.toFloat(),
                    onValueChange = { EqualizerManager.setBandLevel(band.index, it.toInt().toShort()) },
                    valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                    enabled = eqState.enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = PlayMusicOrange,
                        activeTrackColor = PlayMusicOrange
                    )
                )
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            Text("Bas Güçlendirme (Bass Boost)", color = PlayMusicOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Slider(
                value = eqState.bassStrength.toFloat(),
                onValueChange = { EqualizerManager.setBassStrength(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = eqState.enabled,
                colors = SliderDefaults.colors(
                    thumbColor = PlayMusicOrange,
                    activeTrackColor = PlayMusicOrange
                )
            )
        }
    }
}

@Composable
fun PermissionCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = PlayMusicOrange,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Müzik Kitaplığına Erişim İzni",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cihazınızdaki MP3, FLAC ve diğer ses dosyalarını tarayıp çalabilmek için müzik erişim iznine ihtiyaç var.",
                    fontSize = 14.sp,
                    color = PlayMusicTextSecondary
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = PlayMusicOrange)
                ) {
                    Text("İZİN VER VE TARA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
