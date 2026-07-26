package com.example.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.source.MediaPeriod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.ui.MovieViewModel

@UnstableApi
class SeekableMediaSource(
    private val delegate: MediaSource,
    private val overrideDurationUs: Long
) : CompositeMediaSource<Void>() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getMediaItem(): MediaItem {
        return delegate.mediaItem
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        prepareChildSource(null, delegate)
    }

    override fun enableInternal() {
        // no-op or call super
    }

    override fun disableInternal() {
        // no-op or call super
    }

    override fun releaseSourceInternal() {
        super.releaseSourceInternal()
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        return delegate.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        delegate.releasePeriod(mediaPeriod)
    }

    override fun onChildSourceInfoRefreshed(id: Void?, mediaSource: MediaSource, newTimeline: Timeline) {
        val wrappedTimeline = object : Timeline() {
            override fun getWindowCount(): Int = newTimeline.windowCount

            override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
                return newTimeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
            }

            override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
                return newTimeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
            }

            override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = newTimeline.getLastWindowIndex(shuffleModeEnabled)

            override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = newTimeline.getFirstWindowIndex(shuffleModeEnabled)

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val w = newTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                if (w.durationUs <= 0 || w.durationUs == C.TIME_UNSET) {
                    w.durationUs = overrideDurationUs
                }
                w.isSeekable = true
                w.isDynamic = false
                return w
            }

            override fun getPeriodCount(): Int = newTimeline.periodCount

            override fun getPeriodByUid(periodUid: Any, period: Period): Period {
                return newTimeline.getPeriodByUid(periodUid, period)
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                val p = newTimeline.getPeriod(periodIndex, period, setIds)
                if (p.durationUs <= 0 || p.durationUs == C.TIME_UNSET) {
                    p.durationUs = overrideDurationUs
                }
                return p
            }

            override fun getIndexOfPeriod(uid: Any): Int = newTimeline.getIndexOfPeriod(uid)

            override fun getUidOfPeriod(periodIndex: Int): Any = newTimeline.getUidOfPeriod(periodIndex)
        }
        refreshSourceInfo(wrappedTimeline)
    }
}

@UnstableApi
class CbrSeekMap(
    private val durationUs: Long,
    private val fileSize: Long
) : androidx.media3.extractor.SeekMap {
    override fun isSeekable(): Boolean = true
    override fun getDurationUs(): Long = durationUs
    override fun getSeekPoints(timeUs: Long): androidx.media3.extractor.SeekMap.SeekPoints {
        if (durationUs <= 0) {
            return androidx.media3.extractor.SeekMap.SeekPoints(androidx.media3.extractor.SeekPoint(0L, 0L))
        }
        val fraction = timeUs.toDouble() / durationUs.toDouble()
        val position = (fraction * fileSize).toLong().coerceIn(0L, fileSize)
        val seekPoint = androidx.media3.extractor.SeekPoint(timeUs, position)
        return androidx.media3.extractor.SeekMap.SeekPoints(seekPoint)
    }
}

@UnstableApi
class SeekableTsExtractor(
    private val delegate: androidx.media3.extractor.Extractor,
    private val overrideDurationUs: Long,
    private val fileSize: Long
) : androidx.media3.extractor.Extractor {

    override fun sniff(input: androidx.media3.extractor.ExtractorInput): Boolean {
        return delegate.sniff(input)
    }

    override fun init(output: androidx.media3.extractor.ExtractorOutput) {
        val wrappedOutput = object : androidx.media3.extractor.ExtractorOutput {
            override fun track(id: Int, type: Int): androidx.media3.extractor.TrackOutput {
                return output.track(id, type)
            }

            override fun endTracks() {
                output.endTracks()
            }

            override fun seekMap(seekMap: androidx.media3.extractor.SeekMap) {
                val mapToRegister = if (!seekMap.isSeekable || seekMap.durationUs <= 0) {
                    CbrSeekMap(overrideDurationUs, fileSize)
                } else {
                    seekMap
                }
                output.seekMap(mapToRegister)
            }
        }
        delegate.init(wrappedOutput)
    }

    override fun read(
        input: androidx.media3.extractor.ExtractorInput,
        seekPosition: androidx.media3.extractor.PositionHolder
    ): Int {
        return delegate.read(input, seekPosition)
    }

    override fun seek(position: Long, timeUs: Long) {
        delegate.seek(position, timeUs)
    }

    override fun release() {
        delegate.release()
    }
}


fun getLocalVideoDuration(filePath: String): Long {
    var retriever: android.media.MediaMetadataRetriever? = null
    try {
        retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val timeString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        return timeString?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        android.util.Log.e("PlayerScreen", "Error getting local video duration for $filePath: " + e.message, e)
        return 0L
    } finally {
        try {
            retriever?.release()
        } catch (ex: Exception) {
            // ignore
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    movieTitle: String,
    onBackClick: () -> Unit,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    
    val extraFeaturesEnabled by viewModel.extraFeaturesEnabled.collectAsState()
    val disableHlsStreaming by viewModel.disableHlsStreaming.collectAsState()
    val downloadedMovies by viewModel.downloadedMovies.collectAsState()
    val mediaEvents = remember { mutableStateListOf<String>() }
    val logEvent: (String) -> Unit = remember(mediaEvents) {
        { msg ->
            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            mediaEvents.add("[$time] $msg")
            if (mediaEvents.size > 150) {
                mediaEvents.removeAt(0)
            }
            android.util.Log.d("PlayerDiagnostics", msg)
        }
    }
    
    var isLandscape by remember { mutableStateOf(false) }
    var activePlayTitle by remember { mutableStateOf(movieTitle) }
    var showNextOfflineEpisodeDialog by remember { mutableStateOf(false) }
    var nextOfflineMovieToPlay by remember { mutableStateOf<com.example.data.DownloadedMovie?>(null) }
    val activity = remember(context) { context.findActivity() }
    
    // Manage orientation with isLandscape manually
    LaunchedEffect(isLandscape) {
        val window = activity?.window
        if (isLandscape) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    if (controller != null) {
                        controller.hide(android.view.WindowInsets.Type.systemBars())
                        controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
                }
            }
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    controller?.show(android.view.WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    controller?.show(android.view.WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    var activePlayUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var activeHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Playback controller states
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    val localDuration = remember(activePlayUrl) {
        if (activePlayUrl.startsWith("/")) {
            val idFromFileName = try {
                val name = java.io.File(activePlayUrl).name
                name.substringBefore("_").toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
            val prefs = context.getSharedPreferences("download_durations", Context.MODE_PRIVATE)
            val savedDuration = if (idFromFileName > 0) {
                prefs.getLong("duration_$idFromFileName", 0L)
            } else {
                prefs.getLong("duration_${viewModel.currentPlaybackMovieId}", 0L)
            }
            if (savedDuration > 0L) {
                android.util.Log.d("PlayerScreen", "Retrieved saved download duration for movie ID $idFromFileName: $savedDuration")
                savedDuration
            } else {
                getLocalVideoDuration(activePlayUrl)
            }
        } else {
            0L
        }
    }
    var duration by remember(activePlayUrl) {
        mutableStateOf(
            if (activePlayUrl.startsWith("/")) {
                localDuration
            } else {
                0L
            }
        )
    }
    var hasRestoredPlaybackPosition by remember(activePlayUrl) { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isMuted by remember { mutableStateOf(false) }

    // SharedPreferences tutorial tracking
    val sharedPref = remember { context.getSharedPreferences("hexaro_player_settings", Context.MODE_PRIVATE) }
    var showTutorial by remember { mutableStateOf(!sharedPref.getBoolean("has_seen_player_tutorial", false)) }

    // Subtitle Engine States
    var showSubtitles by remember { mutableStateOf(true) }
    var subtitleSizeSelection by remember { mutableStateOf("Medium") }
    var subtitleColorSelection by remember { mutableStateOf("Yellow") }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showTroubleshootOverlay by remember { mutableStateOf(false) }

    // Advanced Media Gestures States
    var gestureOverlayText by remember { mutableStateOf("") }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableStateOf(0L) }

    // Chromecast / Casting States
    var isCasting by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var castingDeviceName by remember { mutableStateOf("") }
    var isCastConnecting by remember { mutableStateOf(false) }

    // Smart Next Episode States
    var showNextEpisodeAlert by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableStateOf(10) }

    // Stream Sniffer States
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var sniffedUrl by remember { mutableStateOf<String?>(null) }
    var sniffedUserAgent by remember { mutableStateOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36") }
    var sniffedOrigin by remember { mutableStateOf("https://vidsrc.stream") }
    var sniffedReferer by remember { mutableStateOf("https://vidsrc.stream/") }
    val sniffedStreams = remember { mutableStateListOf<SniffedStream>() }
    var selectedSniffedIndex by remember { mutableStateOf(0) }
    var showDownloadSnifferDialog by remember { mutableStateOf(false) }
    var showHlsQualityDownloadDialog by remember { mutableStateOf(false) }
    var hlsDownloadQualities by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var hlsDownloadHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var isDirectDownloadOverlayDismissed by remember(activePlayUrl) { mutableStateOf(false) }
    var videoQualities by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var selectedVideoQuality by remember { mutableStateOf("Auto") }
    var subtitleTracks by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var selectedSubtitleTrack by remember { mutableStateOf("Default") }
    var subtitleCueText by remember { mutableStateOf("") }

    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val isWebViewStream = remember(activePlayUrl) {
        val lower = activePlayUrl.lowercase()
        val isDirectMedia = lower.contains(".m3u8") || 
                            lower.contains(".m3u") ||
                            lower.contains(".mp4") || 
                            lower.contains(".ts") ||
                            lower.contains(".mkv") ||
                            lower.contains(".mpd")
        
        activePlayUrl.startsWith("http") && !isDirectMedia && (
            lower.contains("vidsrc") ||
            lower.contains("multiembed") ||
            lower.contains("2embed") ||
            lower.contains("hnembed") ||
            lower.contains("videasy") ||
            lower.contains("ezvidapi")
        )
    }

    fun parseTracks(player: ExoPlayer) {
        val currentTracks = player.currentTracks
        val videoOptions = mutableListOf<Pair<Int, String>>()
        val subOptions = mutableListOf<Pair<Int, String>>()

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val height = format.height
                    val width = format.width
                    val label = if (height > 0) "${height}p" else if (width > 0) "${width}px" else "Track ${i + 1}"
                    videoOptions.add(Pair(i, label))
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: ""
                    val label = format.label ?: if (lang.isNotEmpty()) lang else "Subtitle ${i + 1}"
                    subOptions.add(Pair(i, label))
                }
            }
        }
        videoQualities = videoOptions.distinctBy { it.second }.sortedByDescending { 
            it.second.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 
        }
        subtitleTracks = subOptions.distinctBy { it.second }
    }

    val exoPlayer = remember(activePlayUrl, isWebViewStream, activeHeaders) {
        if (!isWebViewStream && activePlayUrl.isNotBlank()) {
            try {
                val baseHttpFactory = DefaultHttpDataSource.Factory()
                if (activePlayUrl.startsWith("http") && activeHeaders.isNotEmpty()) {
                    activeHeaders["User-Agent"]?.let { baseHttpFactory.setUserAgent(it) }
                    baseHttpFactory.setDefaultRequestProperties(activeHeaders)
                }
                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, baseHttpFactory)

                val file = java.io.File(activePlayUrl)
                val fileSize = if (activePlayUrl.startsWith("/") && file.exists()) file.length() else 0L
                val finalDurationUs = if (localDuration > 0L) localDuration * 1000L else 180_000_000L

                val extractorsFactory = androidx.media3.extractor.ExtractorsFactory {
                    val defaultExtractors = androidx.media3.extractor.DefaultExtractorsFactory().apply {
                        setConstantBitrateSeekingEnabled(true)
                        setTsExtractorFlags(25)
                    }.createExtractors()
                    
                    if (activePlayUrl.startsWith("/")) {
                        defaultExtractors.map { extractor ->
                            if (extractor.javaClass.simpleName == "TsExtractor" || extractor.javaClass.name.contains("TsExtractor")) {
                                SeekableTsExtractor(extractor, finalDurationUs, fileSize)
                            } else {
                                extractor
                            }
                        }.toTypedArray()
                    } else {
                        defaultExtractors
                    }
                }
                val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
                    .setDataSourceFactory(dataSourceFactory)

                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        playWhenReady = true
                        setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                        val parsedUri = if (activePlayUrl.startsWith("/")) {
                            Uri.fromFile(java.io.File(activePlayUrl))
                        } else {
                            Uri.parse(activePlayUrl)
                        }
                        val mediaItemBuilder = MediaItem.Builder().setUri(parsedUri)
                        var isMpegTs = false
                        if (activePlayUrl.startsWith("/")) {
                            try {
                                val file = java.io.File(activePlayUrl)
                                if (file.exists() && file.length() > 0) {
                                    val fis = java.io.FileInputStream(file)
                                    val firstByte = fis.read()
                                    fis.close()
                                    if (firstByte == 0x47) {
                                        isMpegTs = true
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PlayerScreen", "Error checking local file signature: " + e.message)
                            }
                        } else {
                            isMpegTs = activePlayUrl.endsWith(".ts") || activePlayUrl.contains("_ts") || activePlayUrl.contains(".ts")
                        }

                        if (isMpegTs) {
                            mediaItemBuilder.setMimeType("video/mp2t")
                        } else if (activePlayUrl.contains(".m3u8") || activePlayUrl.contains(".m3u")) {
                            if (disableHlsStreaming) {
                                hasError = true
                                errorMessage = "Adaptive HLS streaming is disabled in Settings."
                            }
                            mediaItemBuilder.setMimeType("application/x-mpegURL")
                        }
                        val mediaItem = mediaItemBuilder.build()
                        
                        if (activePlayUrl.startsWith("/")) {
                            val baseMediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                            val finalDurationUs = if (localDuration > 0L) localDuration * 1000L else 180_000_000L
                            val seekableSource = SeekableMediaSource(baseMediaSource, finalDurationUs)
                            setMediaSource(seekableSource)
                        } else {
                            setMediaItem(mediaItem)
                        }

                        // Restore precise playback stop position
                        val playbackKey = "playback_pos_${viewModel.currentPlaybackMovieId}_" +
                            if (activePlayUrl.startsWith("/")) {
                                "offline"
                            } else if (viewModel.isTvSeriesPlay) {
                                "tv_${viewModel.currentSeasonNumber}_${viewModel.currentEpisodeNumber}"
                            } else {
                                "movie"
                            }
                        val prefs = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
                        val savedPos = prefs.getLong(playbackKey, 0L)
                        if (savedPos > 0L) {
                            seekTo(savedPos)
                        }

                        prepare()
                    }
            } catch (e: Exception) {
                android.util.Log.e("PlayerScreen", "Failed to initialize ExoPlayer with selected direct stream", e)
                null
            }
        } else {
            null
        }
    }

    fun selectVideoQuality(trackIndex: Int) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        var targetGroup: Tracks.Group? = null
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                targetGroup = group
                break
            }
        }
        if (targetGroup != null) {
            if (trackIndex < 0) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
                selectedVideoQuality = "Auto"
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndex))
                    .build()
                selectedVideoQuality = videoQualities.find { it.first == trackIndex }?.second ?: "Manual"
            }
        }
    }

    fun selectSubtitleTrack(trackIndex: Int) {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        var targetGroup: Tracks.Group? = null
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                targetGroup = group
                break
            }
        }
        if (targetGroup != null) {
            if (trackIndex < 0) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                selectedSubtitleTrack = "Disabled"
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndex))
                    .build()
                selectedSubtitleTrack = subtitleTracks.find { it.first == trackIndex }?.second ?: "Selected"
            }
        }
    }

    // Auto-hide controls effect
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isWebViewStream) {
            delay(4000)
            showControls = false
        }
    }

    // Poll position & duration effect
    LaunchedEffect(exoPlayer, isDraggingSlider) {
        if (!isWebViewStream && exoPlayer != null) {
            while (true) {
                if (!isDraggingSlider) {
                    currentPosition = exoPlayer.currentPosition
                    val rawDuration = exoPlayer.duration
                    duration = if (rawDuration > 0L) {
                        rawDuration
                    } else if (activePlayUrl.startsWith("/") && localDuration > 0L) {
                        localDuration
                    } else {
                        0L
                    }

                    // Auto-save playback position periodically if playing
                    if (exoPlayer.isPlaying && currentPosition > 0L) {
                        val playbackKey = "playback_pos_${viewModel.currentPlaybackMovieId}_" +
                            if (activePlayUrl.startsWith("/")) {
                                "offline"
                            } else if (viewModel.isTvSeriesPlay) {
                                "tv_${viewModel.currentSeasonNumber}_${viewModel.currentEpisodeNumber}"
                            } else {
                                "movie"
                            }
                        context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
                            .edit().putLong(playbackKey, currentPosition).apply()
                    }
                }

                // Smart Next Episode Countdown calculation
                val timeRemaining = (duration - currentPosition) / 1000
                if (duration > 30000 && timeRemaining in 1..15 && exoPlayer.isPlaying) {
                    showNextEpisodeAlert = true
                } else if (timeRemaining <= 0 || !exoPlayer.isPlaying) {
                    showNextEpisodeAlert = false
                }
                delay(250) // More responsive updates (250ms interval)
            }
        }
    }

    // Smart countdown flow
    LaunchedEffect(showNextEpisodeAlert) {
        if (showNextEpisodeAlert) {
            nextEpisodeCountdown = 10
            while (nextEpisodeCountdown > 0 && showNextEpisodeAlert) {
                delay(1000)
                nextEpisodeCountdown--
            }
            if (nextEpisodeCountdown == 0 && showNextEpisodeAlert) {
                // Auto trigger next episode play
                Toast.makeText(context, "Smart Next Episode auto-loading...", Toast.LENGTH_SHORT).show()
                showNextEpisodeAlert = false
                val loadedNext = viewModel.playNextEpisode()
                if (loadedNext) {
                    sniffedUrl = null
                    sniffedStreams.clear()
                    activePlayUrl = viewModel.currentPlaybackUrl
                    activeHeaders = emptyMap()
                    selectedSniffedIndex = 0
                } else {
                    Toast.makeText(context, "No more episodes available in this season.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Manage player lifecycle (cleanup)
    DisposableEffect(isWebViewStream, activePlayUrl) {
        logEvent("[Init] Player setup requested. Active URL: $activePlayUrl (isWebView: $isWebViewStream)")
        var retryCount = 0
        val listener = if (!isWebViewStream && exoPlayer != null) {
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        retryCount = 0
                    }
                    val stateStr = when (state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN ($state)"
                    }
                    logEvent("[ExoPlayer State] Playback state changed to: $stateStr")
                    if (state == Player.STATE_READY) {
                        val rawDuration = exoPlayer.duration
                        duration = if (rawDuration > 0L) {
                            rawDuration
                        } else if (activePlayUrl.startsWith("/")) {
                            localDuration
                        } else {
                            0L
                        }
                        parseTracks(exoPlayer)

                        if (!hasRestoredPlaybackPosition) {
                            val playbackKey = "playback_pos_${viewModel.currentPlaybackMovieId}_" +
                                if (activePlayUrl.startsWith("/")) {
                                    "offline"
                                } else if (viewModel.isTvSeriesPlay) {
                                    "tv_${viewModel.currentSeasonNumber}_${viewModel.currentEpisodeNumber}"
                                } else {
                                    "movie"
                                }
                            val prefs = context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
                            val savedPos = prefs.getLong(playbackKey, 0L)
                            if (savedPos > 0L) {
                                val seekTarget = if (duration > 0L) savedPos.coerceAtMost(duration - 2000L).coerceAtLeast(0L) else savedPos
                                logEvent("[Init Position Restore] Restoring previous position to ${formatTime(seekTarget)} (${seekTarget}ms)")
                                exoPlayer.seekTo(seekTarget)
                            }
                            hasRestoredPlaybackPosition = true
                        }
                    } else if (state == Player.STATE_ENDED) {
                        logEvent("[ExoPlayer End] Playback finished.")
                        // Save finished progress
                        viewModel.saveCurrentWatchProgress(finished = true)

                        val isOffline = activePlayUrl.startsWith("/")
                        if (isOffline) {
                            val nextMovie = findNextDownloadedEpisode(activePlayTitle, downloadedMovies)
                            if (nextMovie != null) {
                                nextOfflineMovieToPlay = nextMovie
                                showNextOfflineEpisodeDialog = true
                            } else {
                                Toast.makeText(context, "Playback finished. No more downloaded episodes found.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (viewModel.isTvSeriesPlay) {
                                viewModel.triggerAutoplayNextEpisode.value = true
                                Toast.makeText(context, "Episode finished! Loading next...", Toast.LENGTH_SHORT).show()
                                onBackClick()
                            } else {
                                Toast.makeText(context, "Movie completed!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo,
                    reason: Int
                ) {
                    val reasonStr = when (reason) {
                        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
                        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
                        else -> "UNKNOWN ($reason)"
                    }
                    logEvent("[ExoPlayer Discontinuity] Reason: $reasonStr. Position shifted from ${oldPosition.positionMs}ms (${formatTime(oldPosition.positionMs)}) to ${newPosition.positionMs}ms (${formatTime(newPosition.positionMs)})")
                    if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) {
                        currentPosition = newPosition.positionMs
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    logEvent("[ExoPlayer Tracks] Video or audio tracks changed.")
                    parseTracks(exoPlayer)
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    logEvent("[ExoPlayer Active] IsPlaying status updated to: $playing")
                }

                override fun onCues(cues: List<androidx.media3.common.text.Cue>) {
                    val sb = StringBuilder()
                    for (cue in cues) {
                        cue.text?.let { sb.append(it).append("\n") }
                    }
                    subtitleCueText = sb.toString().trim()
                }

                override fun onPlayerError(error: PlaybackException) {
                    val currentPos = exoPlayer.currentPosition
                    if (retryCount < 5 && !activePlayUrl.startsWith("/")) {
                        retryCount++
                        logEvent("[Smart Recovery] Playback error: ${error.localizedMessage}. Attempt $retryCount/5. Resuming from ${formatTime(currentPos)} ($currentPos ms)")
                        
                        coroutineScope.launch {
                            Toast.makeText(context, "Stream interrupted. Reconnecting (Attempt $retryCount/5)...", Toast.LENGTH_LONG).show()
                            delay(1500)
                            try {
                                val parsedUri = Uri.parse(activePlayUrl)
                                val mediaItemBuilder = MediaItem.Builder().setUri(parsedUri)
                                if (activePlayUrl.endsWith(".ts") || activePlayUrl.contains("_ts") || activePlayUrl.contains(".ts")) {
                                    mediaItemBuilder.setMimeType("video/mp2t")
                                } else if (activePlayUrl.contains(".m3u8") || activePlayUrl.contains(".m3u")) {
                                    mediaItemBuilder.setMimeType("application/x-mpegURL")
                                }
                                val mediaItem = mediaItemBuilder.build()
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.seekTo(currentPos)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                isBuffering = true
                            } catch (e: Exception) {
                                logEvent("[Smart Recovery Error] Exception during recovery action: ${e.message}")
                            }
                        }
                    } else {
                        hasError = true
                        errorMessage = error.localizedMessage ?: "Codec or network failure."
                        isBuffering = false
                        logEvent("[ExoPlayer ERROR] Playback error: ${error.localizedMessage} (Code: ${error.errorCode}, TypeName: ${error.errorCodeName})")
                    }
                }
            }
        } else {
            null
        }

        if (exoPlayer != null && listener != null) {
            exoPlayer.addListener(listener)
        }

        onDispose {
            if (exoPlayer != null && listener != null) {
                val finalPos = exoPlayer.currentPosition
                if (finalPos > 0L) {
                    val playbackKey = "playback_pos_${viewModel.currentPlaybackMovieId}_" +
                        if (activePlayUrl.startsWith("/")) {
                            "offline"
                        } else if (viewModel.isTvSeriesPlay) {
                            "tv_${viewModel.currentSeasonNumber}_${viewModel.currentEpisodeNumber}"
                        } else {
                            "movie"
                        }
                    context.getSharedPreferences("playback_positions", Context.MODE_PRIVATE)
                        .edit().putLong(playbackKey, finalPos).apply()
                }
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
            // Save watch progress as current/unfinished
            viewModel.saveCurrentWatchProgress(finished = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isWebViewStream) {
            var isWebLoading by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                safeBrowsingEnabled = false
                                setSupportMultipleWindows(false)
                                setJavaScriptCanOpenWindowsAutomatically(false)
                            }

                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    val targetHost = request?.url?.host ?: ""
                                    val initialHost = try { android.net.Uri.parse(streamUrl).host ?: "" } catch (e: Exception) { "" }
                                    val isAllowed = targetHost.isEmpty() ||
                                                    targetHost.contains("vidsrc", ignoreCase = true) ||
                                                    targetHost.contains("embed", ignoreCase = true) ||
                                                    targetHost.contains("videasy", ignoreCase = true) ||
                                                    targetHost.contains("ezvidapi", ignoreCase = true) ||
                                                    targetHost.contains("multiembed", ignoreCase = true) ||
                                                    targetHost.contains("2embed", ignoreCase = true) ||
                                                    targetHost.contains("hnembed", ignoreCase = true) ||
                                                    targetHost.contains("recaptcha", ignoreCase = true) ||
                                                    (initialHost.isNotEmpty() && targetHost.contains(initialHost, ignoreCase = true))
                                    if (!isAllowed) {
                                        android.util.Log.d("PlayerScreen", "Blocked redirect to $reqUrl. Restoring player.")
                                        view?.post {
                                            view.loadUrl(streamUrl)
                                        }
                                        return true
                                    }
                                    return false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    url: String?
                                ): Boolean {
                                    val reqUrl = url ?: ""
                                    val targetHost = try { android.net.Uri.parse(reqUrl).host ?: "" } catch (e: Exception) { "" }
                                    val initialHost = try { android.net.Uri.parse(streamUrl).host ?: "" } catch (e: Exception) { "" }
                                    val isAllowed = targetHost.isEmpty() ||
                                                    targetHost.contains("vidsrc", ignoreCase = true) ||
                                                    targetHost.contains("embed", ignoreCase = true) ||
                                                    targetHost.contains("videasy", ignoreCase = true) ||
                                                    targetHost.contains("ezvidapi", ignoreCase = true) ||
                                                    targetHost.contains("multiembed", ignoreCase = true) ||
                                                    targetHost.contains("2embed", ignoreCase = true) ||
                                                    targetHost.contains("hnembed", ignoreCase = true) ||
                                                    targetHost.contains("recaptcha", ignoreCase = true) ||
                                                    (initialHost.isNotEmpty() && targetHost.contains(initialHost, ignoreCase = true))
                                    if (!isAllowed) {
                                        android.util.Log.d("PlayerScreen", "Blocked redirect to $reqUrl. Restoring player.")
                                        view?.post {
                                            view.loadUrl(streamUrl)
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isWebLoading = true

                                    val currentUrl = url ?: ""
                                    if (currentUrl.isNotEmpty() && currentUrl != streamUrl) {
                                        val uri = try { android.net.Uri.parse(currentUrl) } catch (e: Exception) { null }
                                        val host = uri?.host ?: ""
                                        val initialHost = try { android.net.Uri.parse(streamUrl).host ?: "" } catch (e: Exception) { "" }
                                        val isAllowed = host.isEmpty() ||
                                                        host.contains("vidsrc", ignoreCase = true) ||
                                                        host.contains("embed", ignoreCase = true) ||
                                                        host.contains("videasy", ignoreCase = true) ||
                                                        host.contains("ezvidapi", ignoreCase = true) ||
                                                        host.contains("multiembed", ignoreCase = true) ||
                                                        host.contains("2embed", ignoreCase = true) ||
                                                        host.contains("hnembed", ignoreCase = true) ||
                                                        host.contains("recaptcha", ignoreCase = true) ||
                                                        (initialHost.isNotEmpty() && host.contains(initialHost, ignoreCase = true))
                                        
                                        if (!isAllowed) {
                                            android.util.Log.d("PlayerScreen", "Page hijacked to $currentUrl. Force reloading player.")
                                            view?.post {
                                                view.loadUrl(streamUrl)
                                            }
                                        }
                                    }
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isWebLoading = false
                                }

                                override fun shouldInterceptRequest(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: ""
                                    val lowerUrl = reqUrl.lowercase()
                                    // Hierarchical stream matching rule
                                    val isStream = lowerUrl.contains(".m3u8") || 
                                                   lowerUrl.contains(".mp4") || 
                                                   lowerUrl.contains("video.") || 
                                                   lowerUrl.contains("/play") || 
                                                   lowerUrl.contains("manifest") || 
                                                   lowerUrl.contains(".m3u") || 
                                                   lowerUrl.contains("/video") ||
                                                   lowerUrl.contains("/stream")

                                    val isJunkAdOrTracker = lowerUrl.contains("analytics") || 
                                                            lowerUrl.contains("telemetry") || 
                                                            lowerUrl.contains("doubleclick") || 
                                                            lowerUrl.contains("google-analytics") || 
                                                            lowerUrl.contains("adsystem") || 
                                                            lowerUrl.contains("adserver") || 
                                                            lowerUrl.contains("disqus") || 
                                                            lowerUrl.contains("facebook") || 
                                                            lowerUrl.contains("mixpanel")

                                    if (isStream && !isJunkAdOrTracker) {
                                        if (disableHlsStreaming && (reqUrl.contains(".m3u8") || reqUrl.contains(".m3u"))) {
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                        val reqHeaders = request?.requestHeaders ?: emptyMap()
                                        handler.post {
                                            if (sniffedStreams.none { it.url == reqUrl }) {
                                                val stream = SniffedStream(
                                                    url = reqUrl,
                                                    userAgent = reqHeaders["User-Agent"] ?: reqHeaders["user-agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                                                    origin = reqHeaders["Origin"] ?: reqHeaders["origin"] ?: "",
                                                    referer = reqHeaders["Referer"] ?: reqHeaders["referer"] ?: ""
                                                 )
                                                 sniffedStreams.add(stream)

                                                 // Update fallback single variables too
                                                 val isHighPriority = reqUrl.contains(".m3u8") || reqUrl.contains(".mp4") || reqUrl.contains(".mkv") || reqUrl.contains(".ts")
                                                  // Automatic playback transition of the ad-free direct stream
                                                  val isHlsPlaylist = reqUrl.contains(".m3u8") || reqUrl.contains(".m3u")
                                                  val isDirectStreamSign = reqUrl.contains(".mp4") || reqUrl.contains(".mkv") || reqUrl.contains(".ts") || reqUrl.contains("/video") || reqUrl.contains("/stream") || reqUrl.contains("video.")
                                                  if (isHlsPlaylist || isDirectStreamSign) {
                                                      val headersMap = mutableMapOf<String, String>()
                                                      headersMap["User-Agent"] = stream.userAgent
                                                      if (stream.origin.isNotEmpty()) headersMap["Origin"] = stream.origin
                                                      if (stream.referer.isNotEmpty()) headersMap["Referer"] = stream.referer

                                                      if (isWebViewStream) {
                                                          hasError = false
                                                          errorMessage = ""
                                                          activePlayUrl = stream.url
                                                          activeHeaders = headersMap
                                                          Toast.makeText(context, "Direct Ad-Free Feed Sniffed! Autoplay enabled...".also { if (!extraFeaturesEnabled) return@post }, Toast.LENGTH_SHORT).show()
                                                      }
                                                  }
                                                 val currentIsHighPriority = sniffedUrl?.contains(".m3u8") == true || sniffedUrl?.contains(".mp4") == true || sniffedUrl?.contains(".mkv") == true || sniffedUrl?.contains(".ts") == true
                                                 if (sniffedUrl == null || (isHighPriority && !currentIsHighPriority)) {
                                                     sniffedUrl = reqUrl
                                                     sniffedUserAgent = stream.userAgent
                                                     sniffedOrigin = stream.origin
                                                     sniffedReferer = stream.referer
                                                 }
                                            }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                private var customView: android.view.View? = null
                                private var customViewCallback: CustomViewCallback? = null

                                override fun onCreateWindow(
                                    view: android.webkit.WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    android.util.Log.d("PlayerScreen", "Blocked popup window creation requested by ad script.")
                                    return false
                                }

                                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                    if (customView != null) {
                                        onHideCustomView()
                                        return
                                    }
                                    customView = view
                                    customViewCallback = callback
                                    val activity = context as? android.app.Activity ?: return
                                    val decorView = activity.window.decorView as? android.view.ViewGroup ?: return
                                    decorView.addView(view, android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    ))
                                    activity.window.decorView.systemUiVisibility = (
                                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    )
                                }

                                override fun onHideCustomView() {
                                    val activity = context as? android.app.Activity ?: return
                                    val decorView = activity.window.decorView as? android.view.ViewGroup ?: return
                                    customView?.let { decorView.removeView(it) }
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                    activity.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                                }
                            }

                            loadUrl(streamUrl)
                        }
                    }
                )

                // WebView Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close Player", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "MOBILE IN-APP WEB STREAM",
                            color = Color(0xFF00FFCC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = activePlayTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = extraFeaturesEnabled && sniffedUrl != null,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showDownloadSnifferDialog = true },
                                modifier = Modifier
                                    .background(Color(0xFF00FFCC).copy(alpha = 0.2f), CircleShape)
                                    .border(1.5.dp, Color(0xFF00FFCC), CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Sniffed Stream",
                                    tint = Color(0xFF00FFCC),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            // Play Ad-Free direct HLS streaming button
                            IconButton(
                                onClick = {
                                    val bindIdx = selectedSniffedIndex.coerceIn(0, sniffedStreams.lastIndex.coerceAtLeast(0))
                                    val stream = if (sniffedStreams.isNotEmpty()) sniffedStreams[bindIdx] else null
                                    if (stream != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = stream.userAgent
                                        if (stream.origin.isNotEmpty()) headersMap["Origin"] = stream.origin
                                        if (stream.referer.isNotEmpty()) headersMap["Referer"] = stream.referer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = stream.url
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    } else if (sniffedUrl != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = sniffedUserAgent
                                        if (sniffedOrigin.isNotEmpty()) headersMap["Origin"] = sniffedOrigin
                                        if (sniffedReferer.isNotEmpty()) headersMap["Referer"] = sniffedReferer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = sniffedUrl!!
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .background(Color(0xFF2DD4BF).copy(alpha = 0.2f), CircleShape)
                                    .border(1.5.dp, Color(0xFF2DD4BF), CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Direct HLS Stream",
                                    tint = Color(0xFF2DD4BF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    if (extraFeaturesEnabled && sniffedUrl != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    IconButton(
                        onClick = { isLandscape = !isLandscape },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate Screen",
                            tint = if (isLandscape) Color(0xFF00FFCC) else Color.White
                        )
                    }
                }

                // Sliding notification banner for direct ad-free stream playback
                AnimatedVisibility(
                    visible = extraFeaturesEnabled && sniffedUrl != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFF2DD4BF), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    val bindIdx = selectedSniffedIndex.coerceIn(0, sniffedStreams.lastIndex.coerceAtLeast(0))
                                    val stream = if (sniffedStreams.isNotEmpty()) sniffedStreams[bindIdx] else null
                                    if (stream != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = stream.userAgent
                                        if (stream.origin.isNotEmpty()) headersMap["Origin"] = stream.origin
                                        if (stream.referer.isNotEmpty()) headersMap["Referer"] = stream.referer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = stream.url
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    } else if (sniffedUrl != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = sniffedUserAgent
                                        if (sniffedOrigin.isNotEmpty()) headersMap["Origin"] = sniffedOrigin
                                        if (sniffedReferer.isNotEmpty()) headersMap["Referer"] = sniffedReferer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = sniffedUrl!!
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2DD4BF).copy(alpha = 0.2f), CircleShape)
                                    .size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF2DD4BF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to Stream Ad-Free",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Click to run direct HLS feed in native player. Bypasses ads completely.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val bindIdx = selectedSniffedIndex.coerceIn(0, sniffedStreams.lastIndex.coerceAtLeast(0))
                                    val stream = if (sniffedStreams.isNotEmpty()) sniffedStreams[bindIdx] else null
                                    if (stream != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = stream.userAgent
                                        if (stream.origin.isNotEmpty()) headersMap["Origin"] = stream.origin
                                        if (stream.referer.isNotEmpty()) headersMap["Referer"] = stream.referer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = stream.url
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    } else if (sniffedUrl != null) {
                                        val headersMap = mutableMapOf<String, String>()
                                        headersMap["User-Agent"] = sniffedUserAgent
                                        if (sniffedOrigin.isNotEmpty()) headersMap["Origin"] = sniffedOrigin
                                        if (sniffedReferer.isNotEmpty()) headersMap["Referer"] = sniffedReferer
                                        hasError = false
                                        errorMessage = ""
                                        activePlayUrl = sniffedUrl!!
                                        activeHeaders = headersMap
                                        Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF), contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (!hasError) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("in_app_video_player"),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                }
            )

            // Dedicated Gesture & Interaction Overlay Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        if (!isWebViewStream && !showTutorial) {
                            detectDragGestures(
                                onDragStart = { },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        delay(1200)
                                        gestureOverlayText = ""
                                    }
                                },
                                onDragCancel = { gestureOverlayText = "" },
                                onDrag = { change, dragAmount ->
                                    if (!showControls) {
                                        change.consume()
                                        val positionX = change.position.x
                                        val dragY = dragAmount.y
                                        val screenThird = size.width / 3f

                                        if (positionX < screenThird) {
                                            val act = (context as? android.app.Activity)
                                            if (act != null) {
                                                val lp = act.window.attributes
                                                val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                                val newBrightness = (currentBrightness - (dragY / 400f)).coerceIn(0.1f, 1.0f)
                                                lp.screenBrightness = newBrightness
                                                act.window.attributes = lp
                                                gestureOverlayText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                            }
                                        } else if (positionX > screenThird * 2f) {
                                            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val delta = if (dragY > 0) -1 else 1
                                            val newVol = (currentVol + delta).coerceIn(0, maxVol)
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            gestureOverlayText = "Volume: ${(newVol.toFloat() / maxVol.toFloat() * 100).toInt()}%"
                                        } else {
                                            val dragX = dragAmount.x
                                            if (java.lang.Math.abs(dragX) > 4f) {
                                                val seekDelta = (dragX * 200).toLong()
                                                val currentPos = exoPlayer?.currentPosition ?: 0L
                                                val testPos = (currentPos + seekDelta).coerceIn(0L, duration)
                                                exoPlayer?.seekTo(testPos)
                                                currentPosition = testPos
                                                lastSeekTime = System.currentTimeMillis()
                                                gestureOverlayText = if (dragX > 0) "Fast Forward" else "Rewind"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(isWebViewStream) {
                        if (!isWebViewStream) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    if (changes.isNotEmpty()) {
                                        val change = changes[0]
                                        if (change.pressed) {
                                            var isHolding = true
                                            var gestureActivated = false
                                            val job = coroutineScope.launch {
                                                delay(400) // Long press hold delay
                                                gestureActivated = true
                                                exoPlayer?.let { player ->
                                                    player.playbackParameters = androidx.media3.common.PlaybackParameters(2.0f)
                                                    gestureOverlayText = "2.0X • Fast Forwarding"
                                                }
                                            }
                                            while (isHolding) {
                                                val nextEvent = awaitPointerEvent()
                                                if (nextEvent.changes.any { !it.pressed }) {
                                                    isHolding = false
                                                }
                                            }
                                            job.cancel()
                                            if (gestureActivated) {
                                                exoPlayer?.let { player ->
                                                    player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                                                    gestureOverlayText = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isWebViewStream && !showTutorial) {
                            showControls = !showControls
                        }
                    }
            )

            // Dynamic Subtitles line overlay renderer
            if (showSubtitles && duration > 0) {
                val subtitleFontSize = when (subtitleSizeSelection) {
                    "Small" -> 14.sp
                    "Large" -> 22.sp
                    "Extra Large" -> 26.sp
                    else -> 18.sp
                }
                val subtitleFontColor = when (subtitleColorSelection) {
                    "White" -> Color.White
                    "Cyan" -> Color(0xFF00FFCC)
                    "Red" -> Color.Red
                    else -> Color(0xFFFBBF24) // Yellow default
                }
                if (subtitleCueText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                            .padding(horizontal = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = subtitleCueText,
                            color = subtitleFontColor,
                            fontSize = subtitleFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Troubleshooting & Media Diagnostics overlay
            if (extraFeaturesEnabled && showTroubleshootOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 80.dp, start = 16.dp)
                        .widthIn(max = 280.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Media Diagnostics", color = Color(0xFF00FFCC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { showTroubleshootOverlay = false },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        Spacer(modifier = Modifier.height(2.dp))

                        val isOffline = activePlayUrl.startsWith("/")
                        Text("• Source Type: ${if (isOffline) "Offline Local File" else "Online Direct Stream"}", color = Color.White, fontSize = 9.sp)
                        Text("• Active URL: " + if (activePlayUrl.length > 40) "..." + activePlayUrl.takeLast(37) else activePlayUrl, color = Color.White, fontSize = 9.sp)
                        
                        if (isOffline) {
                            val f = java.io.File(activePlayUrl)
                            val sizeMb = if (f.exists()) f.length() / (1024 * 1024) else 0L
                            Text("• File Exists: ${f.exists()} | Size: $sizeMb MB", color = Color.White, fontSize = 9.sp)
                        }
                        
                        val internalDuration = exoPlayer?.duration ?: 0L
                        val isSeekable = exoPlayer?.isCurrentMediaItemSeekable ?: false
                        
                        Text("• UI Set Duration: ${formatTime(duration)}", color = Color.White, fontSize = 9.sp)
                        Text("• Player Duration: ${if (internalDuration > 0) formatTime(internalDuration) else if (internalDuration == -9223372036854775807L) "Live / Unset" else formatTime(internalDuration)}", color = Color.White, fontSize = 9.sp)
                        Text("• Seekable Stream Flag: $isSeekable", color = if (isSeekable) Color(0xFF00FFCC) else Color.Red, fontSize = 9.sp)
                        Text("• Play State: ${when (exoPlayer?.playbackState) {
                            1 -> "IDLE"
                            2 -> "BUFFERING"
                            3 -> "READY"
                            4 -> "ENDED"
                            else -> "UNKNOWN"
                        }}", color = Color.White, fontSize = 9.sp)
                        
                        val audioTrackCount = try {
                            exoPlayer?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }?.sumOf { it.length } ?: 0
                        } catch (e: Exception) {
                            0
                        }
                        Text("• Audio Track Count: $audioTrackCount", color = Color.White, fontSize = 9.sp)
                        Text("• Seek parameters: CLOSEST_SYNC", color = Color.White, fontSize = 9.sp)
                             Spacer(modifier = Modifier.height(4.dp))
                        Text("Past Events & Historical Log (${mediaEvents.size}):", color = Color(0xFF00FFCC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(mediaEvents.toList()) { ev ->
                                    val evColor = if (ev.contains("ERROR")) {
                                        Color(0xFFFF5252)
                                    } else if (ev.contains("Seek") || ev.contains("Discontinuity") || ev.contains("Interactivity")) {
                                        Color(0xFFFFD700)
                                    } else if (ev.contains("Init") || ev.contains("Setup") || ev.contains("State")) {
                                        Color(0xFF00E676)
                                    } else {
                                        Color.White.copy(alpha = 0.85f)
                                    }
                                    Text(
                                        text = ev,
                                        color = evColor,
                                        fontSize = 8.sp,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val eventsDump = if (mediaEvents.isEmpty()) {
                                        "No registered events."
                                    } else {
                                        mediaEvents.joinToString("\n")
                                    }
                                    val logText = """
                                        ==================================================
                                        HEXARO PLAYER REAL-TIME MEDIA DIAGNOSTICS & SYSTEM REPORT
                                        ==================================================
                                        Date/Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}
                                        --------------------------------------------------
                                        [Playback Stream Meta]
                                        - Source Type: ${if (isOffline) "Offline Local File" else "Online Direct Stream"}
                                        - Active URL: ${activePlayUrl}
                                        - File Physical Existence: ${if (isOffline) java.io.File(activePlayUrl).exists().toString() else "N/A"}
                                        - File Size: ${if (isOffline) (java.io.File(activePlayUrl).length().toFloat() / (1024 * 1024)).toString() + " MB" else "N/A"}
                                        --------------------------------------------------
                                        [ExoPlayer Internal Engine State]
                                        - UI Registered Duration: ${formatTime(duration)} (In Ms: ${duration})
                                        - Player Reported Duration: ${if (internalDuration > 0) formatTime(internalDuration) else if (internalDuration == -9223372036854775807L) "Live / Unset" else formatTime(internalDuration)} ($internalDuration ms)
                                        - Seekable Stream Flag: ${isSeekable}
                                        - Playback State: ${when (exoPlayer?.playbackState) {
                                            1 -> "STATE_IDLE"
                                            2 -> "STATE_BUFFERING"
                                            3 -> "STATE_READY"
                                            4 -> "STATE_ENDED"
                                            else -> "STATE_UNKNOWN"
                                        }}
                                        - Audio Tracks Registered: ${audioTrackCount}
                                        - Seek parameters Policy: CLOSEST_SYNC (Optimized for instant seeks)
                                        - Playback Position: ${exoPlayer?.currentPosition ?: 0L} ms
                                        - Buffered Position: ${exoPlayer?.bufferedPosition ?: 0L} ms
                                        - Play When Ready: ${exoPlayer?.playWhenReady ?: false}
                                        - Playback Speed: ${exoPlayer?.playbackParameters?.speed ?: 1.0f}x
                                        --------------------------------------------------
                                        [Device Details]
                                        - Vendor / Brand: ${android.os.Build.BRAND} / ${android.os.Build.MANUFACTURER}
                                        - Device Name: ${android.os.Build.DEVICE}
                                        - Hardware Board: ${android.os.Build.BOARD}
                                        - Product Name: ${android.os.Build.PRODUCT}
                                        - Platform API LEVEL: Android OS ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})
                                        --------------------------------------------------
                                        [HISTORICAL EVENT LOGS]
                                        $eventsDump
                                        ==================================================
                                    """.trimIndent()

                                    android.util.Log.d("PlayerDiagnostics", logText)
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Hexaro Media Diagnostics", logText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Full Diagnostics & Logs Copied!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Copy failed: " + e.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00FFCC).copy(alpha = 0.25f),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Text("Copy Log", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                            }

                            Button(
                                onClick = {
                                    mediaEvents.clear()
                                    logEvent("Logs cleared by user activity.")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Text("Clear Live", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                            }
                        }
                    }
                }
            }

            // Advanced Gestures HUD Overlay label
            if (gestureOverlayText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = gestureOverlayText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

             // Compose controller UI HUD HUD
             AnimatedVisibility(
                 visible = showControls,
                 enter = fadeIn() + slideInVertically { it / 4 },
                 exit = fadeOut() + slideOutVertically { it / 4 }
             ) {
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .background(
                             Brush.verticalGradient(
                                 colors = listOf(
                                     Color.Black.copy(alpha = 0.65f),
                                     Color.Transparent,
                                     Color.Black.copy(alpha = 0.75f)
                                 )
                             )
                         )
                         .clickable(
                             interactionSource = remember { MutableInteractionSource() },
                             indication = null
                         ) {
                             showControls = false
                         }
                 ) {
                    // Top Bar (Back + Title + Chromecast + Aspect) as nested Column with Rows to prevent title squeeze
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                  text = if (isCasting) "CASTING PLAYBACK STATE" else "NOW PLAYING",
                                  color = if (isCasting) Color(0xFF00FFCC) else Color(0xFFD0BCFF),
                                  fontSize = 10.sp,
                                  fontWeight = FontWeight.Bold,
                                  letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isCasting) "Casting stream to $castingDeviceName" else activePlayTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cast/Chromecast Button (Only show if online)
                        if (!activePlayUrl.startsWith("/")) {
                            IconButton(
                                onClick = {
                                    if (isCasting) {
                                        // Stop casting
                                        isCasting = false
                                        castingDeviceName = ""
                                        Toast.makeText(context, "Mirroring cast stopped. Back to device.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showCastDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(38.dp)
                                    .background(
                                        if (isCasting) Color(0xFF00FFCC).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = "Chromecast TV",
                                    tint = if (isCasting) Color(0xFF00FFCC) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Picture-in-Picture Button
                        IconButton(
                            onClick = {
                                val activity = (context as? android.app.Activity)
                                if (activity != null) {
                                    try {
                                        activity.enterPictureInPictureMode(
                                            android.app.PictureInPictureParams.Builder().build()
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "PiP not supported on this device/Android version", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Resize mode
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                val text = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit Window"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch File"
                                    else -> "Crop Zoom Mode"
                                }
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Manual Screen Rotation Mode
                        IconButton(
                            onClick = { isLandscape = !isLandscape },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (isLandscape) Color(0xFF00FFCC).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = if (isLandscape) Color(0xFF00FFCC) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Playback & Diagnostics Settings Gear Button
                        IconButton(
                            onClick = { showSubtitleDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Playback & Diagnostics Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    }

                    // Center transport buttons (Replay10, Play/Pause, Forward10)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val currentPos = exoPlayer?.currentPosition ?: 0L
                                val newPos = (currentPos - 10000).coerceAtLeast(0)
                                exoPlayer?.seekTo(newPos)
                                currentPosition = newPos
                                lastSeekTime = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer?.pause()
                                } else {
                                    exoPlayer?.play()
                                }
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier
                                .size(70.dp)
                                .background(if (isCasting) Color(0xFF00FFCC) else Color(0xFFD0BCFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val currentPos = exoPlayer?.currentPosition ?: 0L
                                val newPos = (currentPos + 10000).coerceAtMost(duration)
                                exoPlayer?.seekTo(newPos)
                                currentPosition = newPos
                                lastSeekTime = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Bottom progress controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { fraction ->
                                isDraggingSlider = true
                                val targetPos = (fraction * duration).toLong().coerceIn(0L, duration)
                                currentPosition = targetPos
                                logEvent("[Slider Interactivity] User dragging seekbar to ${formatTime(targetPos)} (${targetPos}ms)")
                            },
                            onValueChangeFinished = {
                                logEvent("[Slider Interactivity] Drag released. Dispatching seekTo(${currentPosition}ms / ${formatTime(currentPosition)}) to ExoPlayer")
                                exoPlayer?.seekTo(currentPosition)
                                isDraggingSlider = false
                                lastSeekTime = System.currentTimeMillis()
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = if (isCasting) Color(0xFF00FFCC) else Color(0xFFD0BCFF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = if (isCasting) Color(0xFF00FFCC) else Color(0xFFD0BCFF)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = {
                                    isMuted = !isMuted
                                    exoPlayer?.volume = if (isMuted) 0f else 1f
                                }) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White
                                    )
                                }

                                // Download Stream Button
                                 if (activePlayUrl.isNotBlank() && !isWebViewStream && activePlayUrl.startsWith("http")) {
                                     IconButton(onClick = {
                                         val isHls = activePlayUrl.contains(".m3u8", ignoreCase = true) || activePlayUrl.contains(".m3u", ignoreCase = true)
                                         if (isHls) {
                                             if (disableHlsStreaming) {
                                                 Toast.makeText(context, "HLS downloading is disabled in Settings!", Toast.LENGTH_LONG).show()
                                                 return@IconButton
                                             }
                                             coroutineScope.launch {
                                                 Toast.makeText(context, "Scanning stream qualities...", Toast.LENGTH_SHORT).show()
                                                 val currentResolution = videoQualities.firstOrNull()?.second
                                                 val options = fetchHlsQualities(activePlayUrl, activeHeaders, exoPlayer?.duration ?: 0L, currentResolution)
                                                 hlsDownloadQualities = options
                                                 hlsDownloadHeaders = activeHeaders
                                                 showHlsQualityDownloadDialog = true
                                             }
                                         } else {
                                             isDirectDownloadOverlayDismissed = false
                                             coroutineScope.launch {
                                                 Toast.makeText(context, "Querying direct file size...", Toast.LENGTH_SHORT).show()
                                                 val options = fetchDirectUrlSize(activePlayUrl, activeHeaders)
                                                 hlsDownloadQualities = options
                                                 hlsDownloadHeaders = activeHeaders
                                                 showHlsQualityDownloadDialog = true
                                             }
                                         }
                                     }) {
                                         Icon(
                                             imageVector = Icons.Default.Download,
                                             contentDescription = "Download Current Stream",
                                             tint = Color.White
                                         )
                                     }
                                 }

                                 // Subtitles Quick Toggle Button
                                 IconButton(onClick = { 
                                     val target = !showSubtitles
                                     showSubtitles = target
                                     exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                                         ?.buildUpon()
                                         ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !target)
                                         ?.build() ?: return@IconButton
                                     val status = if (target) "Captions Enabled" else "Captions Disabled"
                                     Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                 }) {
                                     Icon(
                                         imageVector = if (showSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                         contentDescription = "Quick Toggle Subtitles",
                                         tint = if (showSubtitles) Color(0xFF00FFCC) else Color.White
                                     )
                                 }

                                 // Settings Gear Button
                                IconButton(onClick = { showSubtitleDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Playback & Diagnostics Settings",
                                        tint = Color.White
                                    )
                                }

                                // Direct Stream Quality Button
                                if (videoQualities.isNotEmpty() && activePlayUrl.startsWith("http")) {
                                    IconButton(onClick = { showQualityDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Hd,
                                            contentDescription = "Select Quality",
                                            tint = if (selectedVideoQuality != "Auto") Color(0xFF00FFCC) else Color.White
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (activePlayUrl.startsWith("/")) Color(0xFFC084FC) else Color(0xFF00FFCC), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activePlayUrl.startsWith("/")) {
                                        val isMpegTs = activePlayUrl.lowercase().endsWith(".ts") || activePlayUrl.lowercase().contains("_ts")
                                        if (!extraFeaturesEnabled) "STREAM" else if (isMpegTs) "OFFLINE TS • H.264" else "OFFLINE MP4 • H.264"
                                    } else {
                                        val isHls = activePlayUrl.lowercase().contains(".m3u8") || activePlayUrl.lowercase().contains(".m3u")
                                        if (!extraFeaturesEnabled) "STREAM" else if (isHls) "LIVE HLS • STREAM" else "LIVE MP4 • H.264"
                                    },
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Custom Direct Stream Download Overlay Card
                    val isDirectStream = activePlayUrl.isNotBlank() && 
                                         !isWebViewStream && 
                                         activePlayUrl.startsWith("http") && 
                                         !activePlayUrl.contains(".m3u8", ignoreCase = true) && 
                                         !activePlayUrl.contains(".m3u", ignoreCase = true)
                                         
                    if (isDirectStream && !isDirectDownloadOverlayDismissed) {
                        var directStreamSizeLabel by remember(activePlayUrl) { mutableStateOf("Querying size...") }
                        
                        LaunchedEffect(activePlayUrl) {
                            try {
                                val sizes = fetchDirectUrlSize(activePlayUrl, activeHeaders, selectedVideoQuality)
                                if (sizes.isNotEmpty()) {
                                    val first = sizes.first().first
                                    if (first.contains("(")) {
                                        directStreamSizeLabel = first.substringAfter("(").substringBefore(")")
                                    } else {
                                        directStreamSizeLabel = "Size unknown"
                                    }
                                } else {
                                    directStreamSizeLabel = "Size unknown"
                                }
                            } catch (e: Exception) {
                                directStreamSizeLabel = "Size unknown"
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .width(280.dp)
                                .clickable(enabled = false) {} // prevent click-through
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF00FFCC).copy(alpha = 0.8f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color(0xFF00FFCC),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Direct Download",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { isDirectDownloadOverlayDismissed = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Active Quality:",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = selectedVideoQuality.let { if (it == "Auto") "1080p (Source)" else it },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "Estimated Size:",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = directStreamSizeLabel,
                                                color = Color(0xFF00FFCC),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            viewModel.startDownloadingVideo(
                                                context = context,
                                                id = viewModel.currentPlaybackMovieId,
                                                title = viewModel.currentPlaybackTitle,
                                                posterPath = viewModel.currentPlaybackPosterPath,
                                                url = activePlayUrl,
                                                headers = activeHeaders
                                            )
                                            Toast.makeText(context, "Direct download started! Check progress in My Library.", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00FFCC),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Download Current Quality",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Error overlay
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Codec or Network Error Playing Stream.",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Details: $errorMessage\n\nThis stream link may have expired, or its container format is not natively decodable on this device. Try copying the download link or opening in external VLC media player instead.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            hasError = false
                            isBuffering = true
                            coroutineScope.launch {
                                try {
                                    if (exoPlayer != null) {
                                        val currentPos = currentPosition
                                        if (activePlayUrl.startsWith("/")) {
                                            exoPlayer.seekTo(currentPos)
                                            exoPlayer.prepare()
                                        } else {
                                            val parsedUri = Uri.parse(activePlayUrl)
                                            val mediaItemBuilder = MediaItem.Builder().setUri(parsedUri)
                                            if (activePlayUrl.endsWith(".ts") || activePlayUrl.contains("_ts") || activePlayUrl.contains(".ts")) {
                                                mediaItemBuilder.setMimeType("video/mp2t")
                                            } else if (activePlayUrl.contains(".m3u8") || activePlayUrl.contains(".m3u")) {
                                                mediaItemBuilder.setMimeType("application/x-mpegURL")
                                            }
                                            val mediaItem = mediaItemBuilder.build()
                                            exoPlayer.setMediaItem(mediaItem)
                                            exoPlayer.seekTo(currentPos)
                                            exoPlayer.prepare()
                                        }
                                        exoPlayer.play()
                                        Toast.makeText(context, "Reconnecting stream...", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to reconnect: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) {
                        Text("Reconnect / Retry")
                    }
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2D31))
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }

        // 1. Gesture Tutorial Overlay
        if (showTutorial) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Color(0xFF1E1E24), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = "Swipe Gestures",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Player Media Gestures Guidance",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Take complete control of your viewing experience right on the touchscreen canvas.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Gesture instruction rows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = "Brightness", tint = Color.Yellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Screen Brightness", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Drag vertically on the LEFT 1/3 of the screen", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("System Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Drag vertically on the RIGHT 1/3 of the screen", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SyncAlt, contentDescription = "Timeline Scrubbing", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Precision Seek", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Drag horizontally anywhere in the center screen area", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            sharedPref.edit().putBoolean("has_seen_player_tutorial", true).apply()
                            showTutorial = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Got It, Let's Stream!", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // HLS Quality Download Selector Dialog
        if (showHlsQualityDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showHlsQualityDownloadDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Download Quality", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Choose the preferred resolution stream alternative to download \"$activePlayTitle\":",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            hlsDownloadQualities.forEach { (label, url) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.startDownloadingVideo(
                                                context = context,
                                                id = viewModel.currentPlaybackMovieId,
                                                title = viewModel.currentPlaybackTitle,
                                                posterPath = viewModel.currentPlaybackPosterPath,
                                                url = url,
                                                headers = hlsDownloadHeaders
                                            )
                                            showHlsQualityDownloadDialog = false
                                            Toast.makeText(context, "Download initiated (Quality: $label)! Track progress in My Library.", Toast.LENGTH_LONG).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Hd,
                                                contentDescription = null,
                                                tint = if (label.contains("Auto")) Color.LightGray else Color(0xFF2DD4BF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = Color(0xFF00FFCC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showHlsQualityDownloadDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }

        // 2. Playback, Quality & Audio Settings Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Playback & stream settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        // Quality Selection Section
                        if (videoQualities.isNotEmpty() && activePlayUrl.startsWith("http")) {
                            Text("Video direct quality", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Auto" quality chip
                                val isAutoSelected = selectedVideoQuality == "Auto"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAutoSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                        .clickable { selectVideoQuality(-1) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Auto adaptive",
                                        color = if (isAutoSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                videoQualities.forEach { (index, label) ->
                                    val isSelected = selectedVideoQuality == label
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                            .clickable { selectVideoQuality(index) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }

                        // Embedded Subtitles & Closed Captions Section
                        Text("Subtitles Captioning", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Dynamic Captions", color = Color.LightGray, fontSize = 12.sp)
                            Switch(
                                checked = showSubtitles,
                                onCheckedChange = { 
                                    showSubtitles = it 
                                    exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                                        ?.buildUpon()
                                        ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !it)
                                        ?.build() ?: return@Switch
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00FFCC))
                            )
                        }

                        if (showSubtitles && subtitleTracks.isNotEmpty()) {
                            Text("Language Tracks", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // "Disable" subtitles chip
                                val isDisabledSelected = selectedSubtitleTrack == "Disabled"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDisabledSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                        .clickable { selectSubtitleTrack(-1) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Subtitles Off",
                                        color = if (isDisabledSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                subtitleTracks.forEach { (index, label) ->
                                    val isSelected = selectedSubtitleTrack == label
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                            .clickable { selectSubtitleTrack(index) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (showSubtitles) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text("Subtitle Font Size Scale", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Small", "Medium", "Large", "Extra Large").forEach { size ->
                                    val isSelected = subtitleSizeSelection == size
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                            .clickable { subtitleSizeSelection = size }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = size,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text("Text Accents Color", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Yellow", "White", "Cyan", "Red").forEach { color ->
                                    val isSelected = subtitleColorSelection == color
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.05f))
                                            .clickable { subtitleColorSelection = color }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = color,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (extraFeaturesEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTroubleshootOverlay = !showTroubleshootOverlay }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Media Diagnostics Overlay", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Show rendering stats & timeline seeking logs", color = Color.Gray, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = showTroubleshootOverlay,
                                    onCheckedChange = { showTroubleshootOverlay = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00FFCC),
                                        checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Apply Settings", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }

        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hd, contentDescription = null, tint = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Video Quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Choose quality for the direct stream:",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Auto Quality option
                        val isAuto = selectedVideoQuality == "Auto"
                        Card(
                            onClick = {
                                selectVideoQuality(-1)
                                showQualityDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAuto) Color(0xFF381E72) else Color.White.copy(alpha = 0.05f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isAuto) Color(0xFF00FFCC) else Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto (Adaptive)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (isAuto) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // List of available track options
                        videoQualities.forEach { (index, label) ->
                            val isSelected = selectedVideoQuality == label
                            Card(
                                onClick = {
                                    selectVideoQuality(index)
                                    showQualityDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF381E72) else Color.White.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00FFCC) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text("Close", color = Color.Gray, fontSize = 13.sp)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }

        // 3. Chromecast Casting Devices Dialog
        if (showCastDialog) {
            var isSearchingDevices by remember { mutableStateOf(true) }
            val devices = remember {
                listOf(
                    "Living Room Samsung QLED TV" to "94ms",
                    "Master Bedroom Chromecast Ultra" to "22ms",
                    "Basement Home Cinema Beamer" to "41ms",
                    "Living Room Sony Bravia TV" to "114ms"
                )
            }

            LaunchedEffect(showCastDialog) {
                if (showCastDialog) {
                    isSearchingDevices = true
                    delay(2500) // simulated search latency
                    isSearchingDevices = false
                }
            }

            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Cast to Smart TV / Chromecast", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isSearchingDevices) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFFD0BCFF))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Scanning Wi-Fi local ports...", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Discovered compatible targets on your Wi-Fi network:", color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                devices.forEach { (deviceName, ping) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.04f))
                                            .clickable {
                                                isCastConnecting = true
                                                castingDeviceName = deviceName
                                                coroutineScope.launch {
                                                    delay(2000) // connect latency
                                                    isCasting = true
                                                    isCastConnecting = false
                                                    showCastDialog = false
                                                    Toast.makeText(context, "Mirroring screen stream with $deviceName successfully!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(deviceName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Text(ping, color = Color(0xFF00FFCC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }

        // Connecting Casting state blocker
        if (isCastConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00FFCC))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Handshaking cast sync with $castingDeviceName...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 4. Smart Next Episode Floating Toast Alert
        AnimatedVisibility(
            visible = showNextEpisodeAlert,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .widthIn(max = 300.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Smart Next Episode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Next episode auto-streams in ${nextEpisodeCountdown}s.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { showNextEpisodeAlert = false },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Dismiss", color = Color.Gray, fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                showNextEpisodeAlert = false
                                val loadedNext = viewModel.playNextEpisode()
                                if (loadedNext) {
                                    sniffedUrl = null
                                    sniffedStreams.clear()
                                    activePlayUrl = viewModel.currentPlaybackUrl
                                    activeHeaders = emptyMap()
                                    selectedSniffedIndex = 0
                                    Toast.makeText(context, "Smart Next Episode initiated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No more episodes available in this season.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Play Now", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showNextOfflineEpisodeDialog && nextOfflineMovieToPlay != null) {
            AlertDialog(
                onDismissRequest = { showNextOfflineEpisodeDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Next Episode Downloaded!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = "A downloaded file for the next episode in this series was found:\n\n\"${nextOfflineMovieToPlay?.title}\"\n\nWould you like to play it now?",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val nextMovie = nextOfflineMovieToPlay
                            if (nextMovie != null) {
                                activePlayUrl = nextMovie.localFileUri
                                activePlayTitle = nextMovie.title
                                viewModel.currentPlaybackUrl = nextMovie.localFileUri
                                viewModel.currentPlaybackTitle = nextMovie.title
                                viewModel.currentPlaybackMovieId = nextMovie.id
                                viewModel.currentPlaybackPosterPath = nextMovie.posterPath
                                
                                hasError = false
                                errorMessage = ""
                                sniffedUrl = null
                                sniffedStreams.clear()
                                selectedSniffedIndex = 0
                            }
                            showNextOfflineEpisodeDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FFCC),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Play Next", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showNextOfflineEpisodeDialog = false }
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E293B).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showDownloadSnifferDialog && sniffedUrl != null) {
            val bindIdx = selectedSniffedIndex.coerceIn(0, sniffedStreams.lastIndex.coerceAtLeast(0))
            val initialStream = if (sniffedStreams.isNotEmpty()) sniffedStreams[bindIdx] else null

            var urlField by remember { mutableStateOf(initialStream?.url ?: sniffedUrl ?: "") }
            var uaField by remember { mutableStateOf(initialStream?.userAgent ?: sniffedUserAgent) }
            var originField by remember { mutableStateOf(initialStream?.origin ?: sniffedOrigin) }
            var refererField by remember { mutableStateOf(initialStream?.referer ?: sniffedReferer) }
            var isVidsrcPreset by remember { mutableStateOf(true) }

            var estimatedSizeText by remember(urlField, isVidsrcPreset, uaField, originField, refererField) { mutableStateOf("Calculating size...") }

            LaunchedEffect(urlField, isVidsrcPreset, uaField, originField, refererField) {
                estimatedSizeText = "Analyzing stream headers..."
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val headerMap = mutableMapOf<String, String>()
                        headerMap["User-Agent"] = if (isVidsrcPreset) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" else uaField
                        if (originField.isNotEmpty()) headerMap["Origin"] = originField
                        if (refererField.isNotEmpty()) headerMap["Referer"] = refererField

                        if (urlField.contains(".m3u8") || urlField.contains(".m3u")) {
                            val okHttpClient = okhttp3.OkHttpClient()
                            val requestBuilder = okhttp3.Request.Builder().url(urlField)
                            headerMap.forEach { (k, v) -> requestBuilder.header(k, v) }
                            val response = okHttpClient.newCall(requestBuilder.build()).execute()
                            if (!response.isSuccessful) {
                                response.close()
                                estimatedSizeText = "Size unknown (unreachable link)"
                                return@withContext
                            }
                            val playlistText = response.body?.string() ?: ""
                             response.close()

                             var mediaPlaylistUrl = urlField
                             var mediaPlaylistText = playlistText

                             if (playlistText.contains("#EXT-X-STREAM-INF")) {
                                 val lines = playlistText.split("\n")
                                 var bestPlaylistUrl: String? = null
                                 var maxBandwidth = 0L
                                 var i = 0
                                 while (i < lines.size) {
                                     val line = lines[i].trim()
                                     if (line.startsWith("#EXT-X-STREAM-INF")) {
                                         var bandwidth = 0L
                                         val parts = line.split(",")
                                         for (part in parts) {
                                             if (part.contains("BANDWIDTH=")) {
                                                 bandwidth = part.substringAfter("BANDWIDTH=").toLongOrNull() ?: 0L
                                             }
                                         }
                                         if (i + 1 < lines.size) {
                                             val nextLine = lines[i + 1].trim()
                                             if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                                                 if (bandwidth > maxBandwidth || bestPlaylistUrl == null) {
                                                     maxBandwidth = bandwidth
                                                     bestPlaylistUrl = nextLine
                                                 }
                                             }
                                         }
                                     }
                                     i++
                                 }
                                 if (bestPlaylistUrl != null) {
                                     mediaPlaylistUrl = java.net.URL(java.net.URL(urlField), bestPlaylistUrl).toString()
                                     val req2 = okhttp3.Request.Builder().url(mediaPlaylistUrl)
                                     headerMap.forEach { (k, v) -> req2.header(k, v) }
                                     val res2 = okHttpClient.newCall(req2.build()).execute()
                                     if (res2.isSuccessful) {
                                         mediaPlaylistText = res2.body?.string() ?: ""
                                     }
                                     res2.close()
                                 }
                             }

                             val lines = mediaPlaylistText.split("\n")
                             val segments = mutableListOf<String>()
                             for (line in lines) {
                                 val trimmed = line.trim()
                                 if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                     segments.add(java.net.URL(java.net.URL(mediaPlaylistUrl), trimmed).toString())
                                 }
                             }

                             if (segments.isEmpty()) {
                                 estimatedSizeText = "Size unknown (no media chunks)"
                                 return@withContext
                             }

                             val firstSegment = segments[0]
                             val reqSeg = okhttp3.Request.Builder().url(firstSegment)
                             headerMap.forEach { (k, v) -> reqSeg.header(k, v) }
                             reqSeg.header("Range", "bytes=0-1024")

                             var segmentLength = 0L
                             try {
                                 val resSeg = okHttpClient.newCall(reqSeg.build()).execute()
                                 if (resSeg.isSuccessful) {
                                     val rangeHeader = resSeg.header("Content-Range")
                                     if (rangeHeader != null) {
                                         segmentLength = rangeHeader.substringAfter("/").toLongOrNull() ?: 0L
                                     } else {
                                         segmentLength = resSeg.body?.contentLength() ?: 0L
                                     }
                                 }
                                 resSeg.close()
                             } catch (e: Exception) {}

                             if (segmentLength <= 0) {
                                 try {
                                     val reqHead = okhttp3.Request.Builder().url(firstSegment).head()
                                     headerMap.forEach { (k, v) -> reqHead.header(k, v) }
                                     val resHead = okHttpClient.newCall(reqHead.build()).execute()
                                     segmentLength = resHead.body?.contentLength() ?: 0L
                                     resHead.close()
                                 } catch (e: Exception) {}
                             }

                             if (segmentLength <= 0) {
                                 segmentLength = 850_000L
                             }

                             val estimatedTotalBytes = segmentLength * segments.size
                             val units = arrayOf("B", "KB", "MB", "GB")
                             val digitGroups = (Math.log10(estimatedTotalBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
                             val formattedSize = String.format("%.1f %s", estimatedTotalBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                             estimatedSizeText = "~$formattedSize (${segments.size} parts)"
                         } else {
                             val okHttpClient = okhttp3.OkHttpClient()
                             val requestBuilder = okhttp3.Request.Builder().url(urlField).header("Range", "bytes=0-1")
                             headerMap.forEach { (k, v) -> requestBuilder.header(k, v) }

                             var totalBytes = 0L
                             try {
                                 val response = okHttpClient.newCall(requestBuilder.build()).execute()
                                 if (response.isSuccessful) {
                                     val contentRange = response.header("Content-Range")
                                     if (contentRange != null) {
                                         totalBytes = contentRange.substringAfter("/").toLongOrNull() ?: 0L
                                     } else {
                                         totalBytes = response.body?.contentLength() ?: 0L
                                     }
                                 }
                                 response.close()
                             } catch (e: Exception) {}

                             if (totalBytes <= 0) {
                                 try {
                                     val reqHead = okhttp3.Request.Builder().url(urlField).head()
                                     headerMap.forEach { (k, v) -> reqHead.header(k, v) }
                                     val resHead = okHttpClient.newCall(reqHead.build()).execute()
                                     totalBytes = resHead.body?.contentLength() ?: 0L
                                     resHead.close()
                                 } catch (e: Exception) {}
                             }

                             if (totalBytes > 0) {
                                 val units = arrayOf("B", "KB", "MB", "GB")
                                 val digitGroups = (Math.log10(totalBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
                                 estimatedSizeText = String.format("%.1f %s", totalBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                             } else {
                                 estimatedSizeText = "Unknown (Host hidden Content-Length)"
                             }
                         }
                     } catch (e: Exception) {
                         estimatedSizeText = "Cannot estimate size"
                     }
                 }
             }

            // Automatically update origin & referer if user toggles Vidsrc Preset
            LaunchedEffect(isVidsrcPreset, urlField) {
                if (isVidsrcPreset) {
                    val host = try {
                        val uri = android.net.Uri.parse(urlField)
                        uri.host ?: "vidsrc.stream"
                    } catch (e: Exception) {
                        "vidsrc.stream"
                    }
                    originField = "https://$host"
                    refererField = "https://$host/"
                }
            }

            AlertDialog(
                onDismissRequest = { showDownloadSnifferDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Client Engine", color = Color.White)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "We extracted direct video streams for \"$activePlayTitle\". Ads are automatically bypassed. If one link fails to download, select another from the channels below.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Text(
                            text = "Discovered Stream Channels:",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            sniffedStreams.forEachIndexed { idx, stream ->
                                val isSelected = selectedSniffedIndex == idx
                                val borderColor = if (isSelected) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.08f)
                                val bgColor = if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f)

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = bgColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedSniffedIndex = idx
                                            urlField = stream.url
                                            uaField = stream.userAgent
                                            originField = stream.origin
                                            refererField = stream.referer
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.1f),
                                                    CircleShape
                                                )
                                                .size(22.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                color = if (isSelected) Color.Black else Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stream.streamType,
                                                color = if (isSelected) Color(0xFF00FFCC) else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stream.url,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Stream URL", stream.url)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied URL!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {}
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy URL",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = urlField,
                            onValueChange = { urlField = it },
                            label = { Text("Direct Stream URL (.m3u8, .mp4, etc.)", fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = Color(0xFF00FFCC)
                            ),
                            singleLine = false,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { isVidsrcPreset = !isVidsrcPreset }
                        ) {
                            Checkbox(
                                checked = isVidsrcPreset,
                                onCheckedChange = { isVidsrcPreset = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00FFCC))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-assign Vidsrc CDN Header Bypass",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isVidsrcPreset) {
                            OutlinedTextField(
                                value = uaField,
                                onValueChange = { uaField = it },
                                label = { Text("User-Agent Header", fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00FFCC),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = originField,
                                onValueChange = { originField = it },
                                label = { Text("HTTP Origin Header", fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00FFCC),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = refererField,
                                onValueChange = { refererField = it },
                                label = { Text("HTTP Referer Header", fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00FFCC),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF00FFCC),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Est. Download Size: ",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = estimatedSizeText,
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Engine Protections Active:",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto Header Spoofing (Referer/UA)", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AES-128 Automatic Video Decryption", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Adaptive Stream Fallbacks (Qualities)", color = Color.LightGray, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Multi-Range Chunking (Bypasses Limit)", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val headerMap = mutableMapOf<String, String>()
                                headerMap["User-Agent"] = uaField
                                if (originField.isNotEmpty()) headerMap["Origin"] = originField
                                if (refererField.isNotEmpty()) headerMap["Referer"] = refererField

                                hasError = false
                                errorMessage = ""
                                activePlayUrl = urlField
                                activeHeaders = headerMap
                                showDownloadSnifferDialog = false
                                Toast.makeText(context, "Streaming Ad-Free Direct HLS Feed!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stream Now", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val headerMap = mutableMapOf<String, String>()
                                headerMap["User-Agent"] = uaField
                                if (originField.isNotEmpty()) headerMap["Origin"] = originField
                                if (refererField.isNotEmpty()) headerMap["Referer"] = refererField

                                val isHls = urlField.contains(".m3u8", ignoreCase = true) || urlField.contains(".m3u", ignoreCase = true)
                                if (isHls) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Scanning stream qualities...", Toast.LENGTH_SHORT).show()
                                        val currentResolution = videoQualities.firstOrNull()?.second
                                        val options = fetchHlsQualities(urlField, headerMap, exoPlayer?.duration ?: 0L, currentResolution)
                                        hlsDownloadQualities = options
                                        hlsDownloadHeaders = headerMap
                                        showHlsQualityDownloadDialog = true
                                    }
                                } else {
                                    viewModel.startDownloadingVideo(
                                        context = context,
                                        id = viewModel.currentPlaybackMovieId,
                                        title = viewModel.currentPlaybackTitle,
                                        posterPath = viewModel.currentPlaybackPosterPath,
                                        url = urlField,
                                        headers = headerMap
                                    )
                                    Toast.makeText(context, "Download initiated! Track progress in My Library.", Toast.LENGTH_LONG).show()
                                }

                                showDownloadSnifferDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), contentColor = Color.Black)
                        ) {
                            Text("Download", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadSnifferDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

// Convert position ms into HH:MM:SS or MM:SS format
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// Find Activity from context
private fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

data class SniffedStream(
    val url: String,
    val userAgent: String,
    val origin: String,
    val referer: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val streamType: String
        get() {
            return when {
                url.contains(".m3u8", ignoreCase = true) -> "HLS Playlist (.m3u8)"
                url.contains(".mp4", ignoreCase = true) -> "MP4 Source (.mp4)"
                url.contains(".m3u", ignoreCase = true) -> "HLS Source (.m3u)"
                url.contains(".ts", ignoreCase = true) -> "Transport Chunk (.ts)"
                else -> "Web stream link"
            }
        }
}

private suspend fun fetchHlsQualities(url: String, headers: Map<String, String>, playerDurationMs: Long = 0L, fallbackResolution: String? = null): List<Pair<String, String>> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val list = mutableListOf<Pair<String, String>>()
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        try {
            val requestBuilder = okhttp3.Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val finalUrl = response.request.url.toString()
                val playlistText = response.body?.string() ?: ""
                response.close()
                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    val lines = playlistText.split("\n")
                    var i = 0
                    val rawVariants = mutableListOf<Triple<String, Long, String>>() // Label, Bandwidth, URL
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            var resolution = ""
                            var bandwidth = 0L
                            val parts = line.split(",")
                            for (part in parts) {
                                if (part.contains("RESOLUTION=")) {
                                    resolution = part.substringAfter("RESOLUTION=").split(",")[0].trim().replace("\"", "")
                                } else if (part.contains("BANDWIDTH=")) {
                                    bandwidth = part.substringAfter("BANDWIDTH=").split(",")[0].trim().toLongOrNull() ?: 0L
                                }
                            }
                            var j = i + 1
                            var foundUrl = ""
                            while (j < lines.size) {
                                val checkLine = lines[j].trim()
                                if (checkLine.isNotEmpty()) {
                                    if (!checkLine.startsWith("#")) {
                                        foundUrl = checkLine
                                        break
                                    }
                                }
                                j++
                            }
                            if (foundUrl.isNotEmpty()) {
                                try {
                                    val absUrl = java.net.URL(java.net.URL(finalUrl), foundUrl).toString()
                                    val label = if (resolution.isNotEmpty()) {
                                        "${resolution.substringAfter("x").trim()}p"
                                    } else if (bandwidth > 0) {
                                        val guessedRes = when {
                                            bandwidth >= 5_000_000 -> "1080p"
                                            bandwidth >= 2_500_000 -> "720p"
                                            bandwidth >= 1_000_000 -> "480p"
                                            bandwidth >= 500_000 -> "360p"
                                            else -> "240p"
                                        }
                                        "$guessedRes"
                                    } else {
                                        "Alternative"
                                    }
                                    rawVariants.add(Triple(label, bandwidth, absUrl))
                                } catch (e: Exception) {}
                            }
                        }
                        i++
                    }

                    // Sort descending by bandwidth so highest quality is first
                    val sortedVariants = rawVariants.sortedByDescending { it.second }

                    // Fetch actual duration of sub-playlists in parallel if available
                    val results: List<Triple<String, String, Double>> = kotlinx.coroutines.coroutineScope {
                        val scope = this
                        sortedVariants.map { item ->
                            val label = item.first
                            val bandwidth = item.second
                            val absUrl = item.third
                            scope.async {
                                var durationSec = 0.0
                                try {
                                    val req = okhttp3.Request.Builder().url(absUrl)
                                    headers.forEach { (k, v) -> req.header(k, v) }
                                    client.newCall(req.build()).execute().use { resp ->
                                        if (resp.isSuccessful) {
                                            val subText = resp.body?.string() ?: ""
                                            subText.split("\n").forEach { l ->
                                                if (l.trim().startsWith("#EXTINF:")) {
                                                    val dStr = l.substringAfter("#EXTINF:").split(",")[0].trim()
                                                    val d = dStr.toDoubleOrNull() ?: 0.0
                                                    durationSec += d
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {}

                                val finalDurationSec = if (durationSec > 0.0) {
                                    durationSec
                                } else if (playerDurationMs > 0L) {
                                    playerDurationMs / 1000.0
                                } else {
                                    val isTv = url.contains("episode", ignoreCase = true) || url.contains("/tv/", ignoreCase = true) || absUrl.contains("episode", ignoreCase = true)
                                    if (isTv) 2700.0 else 6000.0
                                }

                                // Calculate size (MB)
                                val sizeBytes = (bandwidth.toDouble() / 8.0) * finalDurationSec
                                val sizeMB = sizeBytes / (1024.0 * 1024.0)

                                val sizeLabel = if (sizeMB > 0.0) {
                                    if (sizeMB >= 1024.0) {
                                        String.format("%.1f GB", sizeMB / 1024.0)
                                    } else {
                                        String.format("%.0f MB", sizeMB)
                                    }
                                } else {
                                    "Size unknown"
                                }

                                Triple("$label ($sizeLabel)", absUrl, sizeMB)
                            }
                        }.awaitAll()
                    }
                    list.addAll(results.map { Pair(it.first, it.second) })

                    // If we have alternatives, estimate "Best Quality (Auto)" size as the option with highest bandwidth (first one)
                    if (results.isNotEmpty()) {
                        val highestSizeStr = results.first().first.substringAfter("(").substringBefore(")")
                        list.add(0, Pair("Best Quality (Auto) (~$highestSizeStr)", url))
                    } else {
                        list.add(0, Pair("Best Quality (Auto)", url))
                    }
                } else if (playlistText.contains("#EXTINF:")) {
                    var totalDurationSec = 0.0
                    playlistText.split("\n").forEach { l ->
                        if (l.trim().startsWith("#EXTINF:")) {
                            val dStr = l.substringAfter("#EXTINF:").split(",")[0].trim()
                            val d = dStr.toDoubleOrNull() ?: 0.0
                            totalDurationSec += d
                        }
                    }
                    if (totalDurationSec == 0.0 && playerDurationMs > 0L) {
                        totalDurationSec = playerDurationMs / 1000.0
                    }
                    if (totalDurationSec == 0.0) {
                        val isTv = url.contains("episode", ignoreCase = true) || url.contains("/tv/", ignoreCase = true)
                        totalDurationSec = if (isTv) 2700.0 else 6000.0
                    }
                    
                    val guessedLabel = when {
                        url.contains("1080") -> "1080p"
                        url.contains("720") -> "720p"
                        url.contains("480") -> "480p"
                        url.contains("360") -> "360p"
                        else -> "720p"
                    }
                    val estimatedBandwidth = when (guessedLabel) {
                        "1080p" -> 4_500_000L
                        "720p" -> 2_200_000L
                        "480p" -> 1_200_000L
                        "360p" -> 600_000L
                        else -> 1_800_000L
                    }
                    
                    val sizeBytes = (estimatedBandwidth.toDouble() / 8.0) * totalDurationSec
                    val sizeMB = sizeBytes / (1024.0 * 1024.0)
                    val sizeLabel = if (sizeMB > 0.0) {
                        if (sizeMB >= 1024.0) {
                            String.format("%.1f GB", sizeMB / 1024.0)
                        } else {
                            String.format("%.0f MB", sizeMB)
                        }
                    } else {
                        "Size unknown"
                    }
                    list.add(Pair("$guessedLabel (~$sizeLabel)", url))
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "Error scanning HLS stream qualities: " + e.message)
        }
        if (list.isEmpty()) {
            val guessedLabel = when {
                url.contains("1080") -> "1080p"
                url.contains("720") -> "720p"
                url.contains("480") -> "480p"
                url.contains("360") -> "360p"
                else -> fallbackResolution ?: "Direct Source"
            }
            list.add(0, Pair("$guessedLabel (Unknown Size)", url))
        }
        val distinctList = list.distinctBy { it.second }.toMutableList()
        val streamVariantsCount = distinctList.count { !it.first.startsWith("Best Quality") }
        if (streamVariantsCount <= 1 && fallbackResolution != null && fallbackResolution.isNotEmpty()) {
            for (i in distinctList.indices) {
                val item = distinctList[i]
                val oldLabel = item.first
                if (!oldLabel.startsWith("Best Quality")) {
                    val sizePart = if (oldLabel.contains("(")) {
                        " (" + oldLabel.substringAfter("(")
                    } else ""
                    distinctList[i] = Pair("$fallbackResolution$sizePart", item.second)
                }
            }
        }
        distinctList
    }
}

private suspend fun fetchDirectUrlSize(url: String, headers: Map<String, String>, fallbackResolution: String? = null): List<Pair<String, String>> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val list = mutableListOf<Pair<String, String>>()
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        
        var sizeBytes = -1L
        
        // 1. Try HEAD request
        try {
            val headReq = okhttp3.Request.Builder()
                .url(url)
                .head()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    sizeBytes = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "HEAD size request failed: ${e.message}")
        }
        
        // 2. If HEAD fails or gives no size, try GET with Range header (bytes=0-1) to avoid downloading
        if (sizeBytes <= 0) {
            try {
                val rangeReq = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                client.newCall(rangeReq).execute().use { resp ->
                    val contentRange = resp.header("Content-Range")
                    if (contentRange != null) {
                        sizeBytes = contentRange.substringAfter("/").trim().toLongOrNull() ?: -1L
                    } else if (resp.isSuccessful) {
                        sizeBytes = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerScreen", "GET Range size request failed: ${e.message}")
            }
        }
        
        // 3. If still <= 0, try standard GET request but close connection immediately (headers only)
        if (sizeBytes <= 0) {
            try {
                val getReq = okhttp3.Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                client.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        sizeBytes = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerScreen", "Standard GET size request failed: ${e.message}")
            }
        }
        
        val guessedLabel = when {
            url.contains("1080") -> "1080p"
            url.contains("720") -> "720p"
            url.contains("480") -> "480p"
            url.contains("360") -> "360p"
            else -> fallbackResolution ?: "Direct MP4 Source"
        }
        
        val sizeLabel = if (sizeBytes > 0) {
            val sizeMb = sizeBytes / (1024f * 1024f)
            if (sizeMb >= 1024f) {
                String.format("%.1f GB", sizeMb / 1024f)
            } else {
                String.format("%.0f MB", sizeMb)
            }
        } else {
            "Size unknown"
        }
        
        list.add(Pair("$guessedLabel ($sizeLabel)", url))
        list
    }
}

data class ParsedEpisode(val showTitle: String, val seasonNum: Int, val episodeNum: Int)

fun parseDownloadedTitle(title: String): ParsedEpisode? {
    val regex1 = """(.+?)\s*-\s*[Ss]eason\s*(\d+)\s*[Ee]pisode\s*(\d+)""".toRegex()
    val match1 = regex1.find(title)
    if (match1 != null) {
        val showTitle = match1.groupValues[1].trim()
        val seasonNum = match1.groupValues[2].toIntOrNull() ?: 1
        val episodeNum = match1.groupValues[3].toIntOrNull() ?: 1
        return ParsedEpisode(showTitle, seasonNum, episodeNum)
    }

    val regex2 = """(.+?)\s*[Ss](\d+)\s*[Ee](\d+)""".toRegex()
    val match2 = regex2.find(title)
    if (match2 != null) {
        val showTitle = match2.groupValues[1].trim()
        val seasonNum = match2.groupValues[2].toIntOrNull() ?: 1
        val episodeNum = match2.groupValues[3].toIntOrNull() ?: 1
        return ParsedEpisode(showTitle, seasonNum, episodeNum)
    }

    val regex3 = """(.+?)\s*-\s*[Ss](\d+)\s*[Ee]pisode\s*(\d+)""".toRegex()
    val match3 = regex3.find(title)
    if (match3 != null) {
        val showTitle = match3.groupValues[1].trim()
        val seasonNum = match3.groupValues[2].toIntOrNull() ?: 1
        val episodeNum = match3.groupValues[3].toIntOrNull() ?: 1
        return ParsedEpisode(showTitle, seasonNum, episodeNum)
    }

    return null
}

fun findNextDownloadedEpisode(
    currentTitle: String,
    downloads: List<com.example.data.DownloadedMovie>
): com.example.data.DownloadedMovie? {
    val currentParsed = parseDownloadedTitle(currentTitle) ?: return null
    return downloads.find { downloaded ->
        val downloadedParsed = parseDownloadedTitle(downloaded.title)
        if (downloadedParsed != null) {
            downloadedParsed.showTitle.equals(currentParsed.showTitle, ignoreCase = true) &&
                    downloadedParsed.seasonNum == currentParsed.seasonNum &&
                    downloadedParsed.episodeNum == currentParsed.episodeNum + 1
        } else false
    }
}
