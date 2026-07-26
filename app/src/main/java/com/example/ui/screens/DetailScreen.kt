package com.example.ui.screens

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.TmdbCastMember
import com.example.data.TmdbMovie
import com.example.ui.DetailUiState
import com.example.ui.MovieViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    movieId: Int,
    mediaType: String,
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onPlayInAppClick: (Int, String, String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val detailUiState by viewModel.detailState.collectAsState()
    val seasonDetailsState by viewModel.seasonDetails.collectAsState()

    val deepBackground = MaterialTheme.colorScheme.background
    val immersivePurple = MaterialTheme.colorScheme.primary
    val onPrimaryPurple = MaterialTheme.colorScheme.onPrimary
    val neutralDarkSurface = MaterialTheme.colorScheme.surface

    var isTvSeries by remember { mutableStateOf(mediaType == "tv") }
    var seasonNumber by remember { mutableStateOf(1) }
    var episodeNumber by remember { mutableStateOf(1) }

    val interactionState by viewModel.getInteractionFlow(movieId).collectAsState(initial = null)

    val detailEpisodeList = remember(seasonDetailsState) {
        seasonDetailsState?.getOrNull()?.episodes ?: emptyList() /* global_list */
    }

    LaunchedEffect(movieId, mediaType) {
        viewModel.loadMovieDetails(movieId, mediaType)
    }

    LaunchedEffect(movieId, seasonNumber, isTvSeries) {
        if (isTvSeries) {
            viewModel.loadTvSeasonDetails(movieId, seasonNumber)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = deepBackground,
        topBar = {
            TopAppBar(
                title = { Text("Movie Details", fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(neutralDarkSurface)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = deepBackground,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        when (val state = detailUiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = immersivePurple)
                }
            }
            is DetailUiState.Success -> {
                val movie = state.movie
                val watchlist by viewModel.watchlist.collectAsState()
                val isWatchlisted = remember(watchlist, movie.id) {
                    watchlist.any { it.id == movie.id }
                }

                val triggerAutoplayNext by viewModel.triggerAutoplayNextEpisode.collectAsState()
                LaunchedEffect(triggerAutoplayNext, interactionState, movie) {
                    if (triggerAutoplayNext && interactionState != null) {
                        viewModel.triggerAutoplayNextEpisode.value = false
                        val lastS = interactionState?.lastSeasonWatched ?: 1
                        val lastE = interactionState?.lastEpisodeWatched ?: 1
                        val isFinished = interactionState?.isEpisodeFinished == true
                        val maxEp = interactionState?.lastEpisodeMaxCount ?: 1
                        val lastServer = interactionState?.lastServerUsedIndex ?: 1

                        val continueSeason: Int
                        val continueEpisode: Int
                        if (isFinished) {
                            if (lastE < maxEp) {
                                continueSeason = lastS
                                continueEpisode = lastE + 1
                            } else {
                                continueSeason = lastS
                                continueEpisode = lastE
                            }
                        } else {
                            continueSeason = lastS
                            continueEpisode = lastE
                        }

                        android.widget.Toast.makeText(context, "Autoplaying Season $continueSeason Episode $continueEpisode...", android.widget.Toast.LENGTH_SHORT).show()

                        viewModel.isTvSeriesPlay = true
                        viewModel.currentTvSeriesId = movie.id
                        viewModel.currentSeasonNumber = continueSeason
                        viewModel.currentEpisodeNumber = continueEpisode
                        viewModel.selectedServerIndex = lastServer
                        viewModel.maxEpisodeNumber = maxEp

                        val url = when (lastServer) {
                            1 -> "https://vidsrc-embed.ru/embed/tv/${movie.id}/${continueSeason}-${continueEpisode}"
                            2 -> "https://multiembed.mov/?video_id=${movie.id}&tmdb=1&s=${continueSeason}&e=${continueEpisode}"
                            3 -> "https://www.2embed.cc/embedtv/${movie.id}?s=${continueSeason}&e=${continueEpisode}"
                            4 -> "https://hnembed.cc/tv/${movie.id}/${continueSeason}/${continueEpisode}"
                            5 -> "https://player.videasy.net/tv/${movie.id}/${continueSeason}/${continueEpisode}?color=ff0000"
                            6 -> "https://ezvidapi.com/tv/${movie.id}/${continueSeason}/${continueEpisode}?provider=vidsrc"
                            else -> "https://vidsrc-embed.ru/embed/tv/${movie.id}/${continueSeason}-${continueEpisode}"
                        }
                        onPlayInAppClick(movie.id, "${movie.displayTitle} - Season $continueSeason Episode $continueEpisode", movie.poster_path, url)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // 1. Movie Backdrop Image & Basic Header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(260.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(neutralDarkSurface)
                        ) {
                            AsyncImage(
                                model = movie.getBackdropUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Double gradient vignette mask
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Transparent,
                                                deepBackground
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // 2. Info Description Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = movie.displayTitle,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Watchlist bookmark button matching design secondary aesthetics
                                IconButton(
                                    onClick = { viewModel.toggleWatchlist(movie, isWatchlisted) },
                                    modifier = Modifier
                                        .background(neutralDarkSurface, CircleShape)
                                        .testTag("watchlist_button")
                                ) {
                                    Icon(
                                        imageVector = if (isWatchlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Watchlist",
                                        tint = if (isWatchlisted) immersivePurple else Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Stats & Info Panel matching HTML Design
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .border(width = (0.5).dp, color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Rating
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (movie.vote_average != null && movie.vote_average > 0.0) String.format("%.1f", movie.vote_average) else "N/A",
                                            color = immersivePurple,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = " / 10",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = "RATING",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                // Column 2: Duration
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (movie.runtime != null && movie.runtime > 0) "${movie.runtime} min" else "N/A",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "DURATION",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                // Column 3: Release
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = movie.displayDate.take(4).ifEmpty { "N/A" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "RELEASE",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Movie Genres chip layout
                            if (!movie.genres.isNullOrEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    movie.genres.take(3).forEach { genre ->
                                        Box(
                                            modifier = Modifier
                                                .background(neutralDarkSurface, RoundedCornerShape(8.dp))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = genre.name, color = immersivePurple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            // Personal Library interaction toggles
                            // duplicate removed
                            val isWatched = interactionState?.isWatched ?: false
                            val isCurrentlyWatching = interactionState?.isCurrentlyWatching ?: false
                            val isFavorite = interactionState?.isFavorite ?: false
                            val isWatchLater = interactionState?.isWatchLater ?: false
                            val isWatchlistedState = interactionState?.isWatchlist ?: isWatchlisted

                            var hasAutoSelected by remember(movie.id) { mutableStateOf(false) }
                            LaunchedEffect(interactionState, movie.id) {
                                if (!hasAutoSelected && interactionState != null) {
                                    val lastS = interactionState?.lastSeasonWatched
                                    val lastE = interactionState?.lastEpisodeWatched
                                    if (lastS != null) {
                                        seasonNumber = lastS
                                    }
                                    if (lastE != null) {
                                        episodeNumber = lastE
                                    }
                                    hasAutoSelected = true
                                }
                            }

                            Text(
                                text = "Personal Library",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InteractionChip(
                                    label = "Watchlist",
                                    selected = isWatchlistedState,
                                    activeIcon = Icons.Filled.Bookmark,
                                    inactiveIcon = Icons.Outlined.BookmarkBorder,
                                    activeColor = immersivePurple,
                                    onClick = {
                                        viewModel.toggleWatchlist(movie, isWatchlisted)
                                        viewModel.toggleInteractionField(
                                            id = movie.id,
                                            title = movie.displayTitle,
                                            posterPath = movie.poster_path,
                                            voteAverage = movie.vote_average,
                                            releaseDate = movie.displayDate,
                                            mediaType = if (movie.isTvShow) "tv" else "movie",
                                            field = "watchlist",
                                            value = !isWatchlistedState
                                        )
                                    }
                                )

                                InteractionChip(
                                    label = "Watched",
                                    selected = isWatched,
                                    activeIcon = Icons.Default.Check,
                                    inactiveIcon = Icons.Default.Check,
                                    activeColor = Color(0xFF2DD4BF),
                                    onClick = {
                                        viewModel.toggleInteractionField(
                                            id = movie.id,
                                            title = movie.displayTitle,
                                            posterPath = movie.poster_path,
                                            voteAverage = movie.vote_average,
                                            releaseDate = movie.displayDate,
                                            mediaType = if (movie.isTvShow) "tv" else "movie",
                                            field = "watched",
                                            value = !isWatched
                                        )
                                        if (!isWatched && isCurrentlyWatching) {
                                            viewModel.toggleInteractionField(
                                                id = movie.id,
                                                title = movie.displayTitle,
                                                posterPath = movie.poster_path,
                                                voteAverage = movie.vote_average,
                                                releaseDate = movie.displayDate,
                                                mediaType = if (movie.isTvShow) "tv" else "movie",
                                                field = "currentlyWatching",
                                                value = false
                                            )
                                        }
                                    }
                                )

                                InteractionChip(
                                    label = "Watching",
                                    selected = isCurrentlyWatching,
                                    activeIcon = Icons.Default.PlayArrow,
                                    inactiveIcon = Icons.Default.PlayArrow,
                                    activeColor = Color(0xFFF59E0B),
                                    onClick = {
                                        viewModel.toggleInteractionField(
                                            id = movie.id,
                                            title = movie.displayTitle,
                                            posterPath = movie.poster_path,
                                            voteAverage = movie.vote_average,
                                            releaseDate = movie.displayDate,
                                            mediaType = if (movie.isTvShow) "tv" else "movie",
                                            field = "currentlyWatching",
                                            value = !isCurrentlyWatching
                                        )
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InteractionChip(
                                    label = "Favorite",
                                    selected = isFavorite,
                                    activeIcon = Icons.Default.Favorite,
                                    inactiveIcon = Icons.Default.Favorite,
                                    activeColor = Color(0xFFF43F5E),
                                    onClick = {
                                        viewModel.toggleInteractionField(
                                            id = movie.id,
                                            title = movie.displayTitle,
                                            posterPath = movie.poster_path,
                                            voteAverage = movie.vote_average,
                                            releaseDate = movie.displayDate,
                                            mediaType = if (movie.isTvShow) "tv" else "movie",
                                            field = "favorite",
                                            value = !isFavorite
                                        )
                                    }
                                )

                                InteractionChip(
                                    label = "Watch Later",
                                    selected = isWatchLater,
                                    activeIcon = Icons.Default.Star,
                                    inactiveIcon = Icons.Default.Star,
                                    activeColor = Color(0xFF60A5FA),
                                    onClick = {
                                        viewModel.toggleInteractionField(
                                            id = movie.id,
                                            title = movie.displayTitle,
                                            posterPath = movie.poster_path,
                                            voteAverage = movie.vote_average,
                                            releaseDate = movie.displayDate,
                                            mediaType = if (movie.isTvShow) "tv" else "movie",
                                            field = "watchLater",
                                            value = !isWatchLater
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Movie Overview Plot synopsis
                            Text(text = "Synopsis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = movie.overview ?: "No movie description available.",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (isTvSeries && interactionState?.lastSeasonWatched != null && interactionState?.lastEpisodeWatched != null) {
                        val lastS = interactionState?.lastSeasonWatched ?: 1
                        val lastE = interactionState?.lastEpisodeWatched ?: 1
                        val isFinished = interactionState?.isEpisodeFinished == true
                        val maxEp = interactionState?.lastEpisodeMaxCount ?: 1
                        val lastServer = interactionState?.lastServerUsedIndex ?: 1

                        val continueSeason: Int
                        val continueEpisode: Int
                        val isNext: Boolean
                        if (isFinished) {
                            if (lastE < maxEp) {
                                continueSeason = lastS
                                continueEpisode = lastE + 1
                                isNext = true
                            } else {
                                continueSeason = lastS
                                continueEpisode = lastE
                                isNext = false
                            }
                        } else {
                            continueSeason = lastS
                            continueEpisode = lastE
                            isNext = false
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                onClick = {
                                    viewModel.isTvSeriesPlay = true
                                    viewModel.currentTvSeriesId = movie.id
                                    viewModel.currentSeasonNumber = continueSeason
                                    viewModel.currentEpisodeNumber = continueEpisode
                                    viewModel.selectedServerIndex = lastServer
                                    viewModel.maxEpisodeNumber = maxEp

                                    val url = when (lastServer) {
                                        1 -> "https://vidsrc-embed.ru/embed/tv/${movie.id}/${continueSeason}-${continueEpisode}"
                                        2 -> "https://multiembed.mov/?video_id=${movie.id}&tmdb=1&s=${continueSeason}&e=${continueEpisode}"
                                        3 -> "https://www.2embed.cc/embedtv/${movie.id}?s=${continueSeason}&e=${continueEpisode}"
                                        4 -> "https://hnembed.cc/tv/${movie.id}/${continueSeason}/${continueEpisode}"
                                        5 -> "https://player.videasy.net/tv/${movie.id}/${continueSeason}/${continueEpisode}?color=ff0000"
                                        6 -> "https://ezvidapi.com/tv/${movie.id}/${continueSeason}/${continueEpisode}?provider=vidsrc"
                                        else -> "https://vidsrc-embed.ru/embed/tv/${movie.id}/${continueSeason}-${continueEpisode}"
                                    }
                                    onPlayInAppClick(movie.id, "${movie.displayTitle} - Season $continueSeason Episode $continueEpisode", movie.poster_path, url)
                                },
                                colors = CardDefaults.cardColors(containerColor = immersivePurple.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .border(1.dp, immersivePurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isNext) "Continue with Next Episode" else "Continue Watching",
                                            color = Color(0xFF00FFCC),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Season $continueSeason - Episode $continueEpisode (Server $lastServer)",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        if (!isFinished) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "You left off here",
                                                color = Color.LightGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(immersivePurple),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Multi-Server Stream Embed Dashboard Section
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(24.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Watch from Sources",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            letterSpacing = (-0.3).sp
                                        )
                                        Text(
                                            text = "Choose a source to stream directly",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = null,
                                        tint = immersivePurple,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Interactive Season & Episode Picker (only visible if TV Series is active)
                                AnimatedVisibility(
                                    visible = isTvSeries,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    val seasonsList = remember(movie.seasons, movie.number_of_seasons) {
                                        if (!movie.seasons.isNullOrEmpty()) {
                                            movie.seasons.filter { it.season_number > 0 }.sortedBy { it.season_number }
                                        } else {
                                            val totalS = movie.number_of_seasons ?: 1
                                            (1..totalS).map { sNum ->
                                                com.example.data.TmdbSeasonDetail(
                                                    id = sNum,
                                                    name = "Season $sNum",
                                                    season_number = sNum,
                                                    episode_count = null,
                                                    poster_path = null,
                                                    overview = null,
                                                    vote_average = null
                                                )
                                            }
                                        }
                                    }

                                    // Sync seasonNumber State
                                     LaunchedEffect(seasonsList) {
                                         if (seasonsList.isNotEmpty() && seasonsList.none { it.season_number == seasonNumber }) {
                                             seasonNumber = seasonsList.first().season_number
                                         }
                                     }

                                     val episodeList = remember(seasonDetailsState) {
                                         seasonDetailsState?.getOrNull()?.episodes ?: emptyList()
                                     }
                                     val isLoadingEpisodes = seasonDetailsState == null

                                     // Sync episodeNumber State
                                     LaunchedEffect(episodeList) {
                                         if (episodeList.isNotEmpty() && episodeList.none { it.episode_number == episodeNumber }) {
                                             episodeNumber = episodeList.first().episode_number
                                         }
                                     }

                                     val selectedEpisode = remember(episodeList, episodeNumber) {
                                         episodeList.find { it.episode_number == episodeNumber }
                                     }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(20.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                                            .padding(16.dp)
                                    ) {
                                        // A beautifully styled Season select header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Season",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Season $seasonNumber",
                                                color = immersivePurple,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Horizontal scroll of Seasons
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(seasonsList) { s ->
                                                val isSelected = s.season_number == seasonNumber
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) immersivePurple else Color.White.copy(alpha = 0.05f))
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { seasonNumber = s.season_number }
                                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = s.name ?: "S${s.season_number}",
                                                        color = if (isSelected) onPrimaryPurple else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Beautifully styled Episode select header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Episode",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Episode $episodeNumber",
                                                color = immersivePurple,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (isLoadingEpisodes) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = immersivePurple, modifier = Modifier.size(24.dp))
                                            }
                                        } else if (episodeList.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No episodes found.", color = Color.Gray, fontSize = 12.sp)
                                            }
                                        } else {
                                            // Horizontal scroll of Episodes
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                            items(episodeList) { e ->
                                                 val isSelected = e.episode_number == episodeNumber
                                                 Column(
                                                     modifier = Modifier
                                                         .width(180.dp)
                                                         .clip(RoundedCornerShape(16.dp))
                                                         .background(if (isSelected) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                                                         .clickable { episodeNumber = e.episode_number }
                                                         .border(
                                                             width = if (isSelected) 1.5.dp else 1.dp,
                                                             color = if (isSelected) immersivePurple else Color.White.copy(alpha = 0.05f),
                                                             shape = RoundedCornerShape(16.dp)
                                                         )
                                                 ) {
                                                     Box(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .height(100.dp)
                                                             .background(Color.Black)
                                                     ) {
                                                         AsyncImage(
                                                             model = e.getStillUrl("w185").ifEmpty { movie.getBackdropUrl().ifEmpty { movie.getPosterUrl() } },
                                                             contentDescription = "Episode ${e.episode_number} Preview",
                                                             contentScale = ContentScale.Crop,
                                                             modifier = Modifier
                                                                 .fillMaxSize()
                                                                 .alpha(if (isSelected) 0.85f else 0.5f)
                                                         )
                                                         Box(
                                                             modifier = Modifier
                                                                 .fillMaxSize()
                                                                 .background(
                                                                     Brush.verticalGradient(
                                                                         colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                                                     )
                                                                 )
                                                         )
                                                         Box(
                                                             modifier = Modifier
                                                                 .align(Alignment.Center)
                                                                 .size(32.dp)
                                                                 .background(
                                                                     if (isSelected) immersivePurple else Color.Black.copy(alpha = 0.6f),
                                                                     CircleShape
                                                                 ),
                                                             contentAlignment = Alignment.Center
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.PlayArrow,
                                                                 contentDescription = "Play",
                                                                 tint = if (isSelected) onPrimaryPurple else Color.White,
                                                                 modifier = Modifier.size(18.dp)
                                                             )
                                                         }
                                                         Box(
                                                             modifier = Modifier
                                                                 .align(Alignment.TopStart)
                                                                 .padding(8.dp)
                                                                 .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                                                 .padding(horizontal = 6.dp, vertical = 2.dp)
                                                         ) {
                                                             Text(
                                                                 text = "S$seasonNumber:E${e.episode_number}",
                                                                 color = Color.White,
                                                                 fontSize = 10.sp,
                                                                 fontWeight = FontWeight.Bold
                                                             )
                                                         }
                                                         Box(
                                                             modifier = Modifier
                                                                 .align(Alignment.BottomEnd)
                                                                 .padding(8.dp)
                                                                 .background(immersivePurple.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                                                 .padding(horizontal = 6.dp, vertical = 2.dp)
                                                         ) {
                                                             Text(
                                                                 text = "HD 1080p",
                                                                 color = onPrimaryPurple,
                                                                 fontSize = 8.sp,
                                                                 fontWeight = FontWeight.Bold
                                                             )
                                                         }
                                                     }
                                                     Column(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .padding(10.dp)
                                                     ) {
                                                         Text(
                                                             text = e.name ?: "Episode ${e.episode_number}",
                                                             color = if (isSelected) immersivePurple else Color.White,
                                                             fontSize = 12.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             maxLines = 1
                                                         )
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = e.air_date ?: "Air Date N/A",
                                                             color = Color.Gray,
                                                             fontSize = 9.sp,
                                                             maxLines = 1
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                                   // DETAILED ACTIVE EPISODE DESCRIPTION PANEL
                                             selectedEpisode?.let { ep ->
                                                 Spacer(modifier = Modifier.height(18.dp))
                                                 Card(
                                                     shape = RoundedCornerShape(16.dp),
                                                     colors = CardDefaults.cardColors(
                                                         containerColor = Color.White.copy(alpha = 0.03f)
                                                     ),
                                                     border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(vertical = 4.dp)
                                                 ) {
                                                     Column(
                                                         modifier = Modifier.padding(14.dp)
                                                     ) {
                                                         Row(
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                             modifier = Modifier.fillMaxWidth()
                                                         ) {
                                                             // Miniature Still preview on the side
                                                             AsyncImage(
                                                                 model = ep.getStillUrl("w342").ifEmpty { movie.getBackdropUrl().ifEmpty { movie.getPosterUrl() } },
                                                                 contentDescription = null,
                                                                 contentScale = ContentScale.Crop,
                                                                 modifier = Modifier
                                                                     .size(width = 110.dp, height = 70.dp)
                                                                     .clip(RoundedCornerShape(10.dp))
                                                             )

                                                             Column(
                                                                 modifier = Modifier.weight(1f)
                                                             ) {
                                                                 Text(
                                                                     text = "S$seasonNumber : E${ep.episode_number}",
                                                                     color = immersivePurple,
                                                                     fontSize = 11.sp,
                                                                     fontWeight = FontWeight.Black
                                                                 )
                                                                 Text(
                                                                     text = ep.name ?: "Episode ${ep.episode_number}",
                                                                     color = Color.White,
                                                                     fontSize = 14.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis
                                                                 )
                                                                 
                                                                 Row(
                                                                     verticalAlignment = Alignment.CenterVertically,
                                                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                     modifier = Modifier.padding(top = 4.dp)
                                                                 ) {
                                                                     // Rating Badge
                                                                     val rating = ep.vote_average ?: 0.0
                                                                     if (rating > 0.0) {
                                                                         Row(
                                                                             verticalAlignment = Alignment.CenterVertically,
                                                                             horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                                             modifier = Modifier
                                                                                 .background(Color(0xFFE5A93B).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                                                 .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                         ) {
                                                                             Icon(
                                                                                 imageVector = Icons.Default.Star,
                                                                                 contentDescription = "Rating",
                                                                                 tint = Color(0xFFE5A93B),
                                                                                 modifier = Modifier.size(10.dp)
                                                                             )
                                                                             Text(
                                                                                 text = String.format("%.1f", rating),
                                                                                 color = Color(0xFFE5A93B),
                                                                                 fontSize = 9.sp,
                                                                                 fontWeight = FontWeight.Bold
                                                                             )
                                                                         }
                                                                     }

                                                                     // Release Air Date
                                                                     ep.air_date?.let { ad ->
                                                                         Text(
                                                                             text = "📅 $ad",
                                                                             color = Color.LightGray,
                                                                             fontSize = 9.sp
                                                                         )
                                                                     }
                                                                 }
                                                             }
                                                         }

                                                         Spacer(modifier = Modifier.height(12.dp))

                                                         // Episode Overview / Synopsis
                                                         val overview = ep.overview
                                                         Text(
                                                             text = if (overview.isNullOrEmpty()) "No synopsis is available for this episode." else overview,
                                                             color = Color.Gray,
                                                             fontSize = 12.sp,
                                                             lineHeight = 17.sp,
                                                             fontWeight = FontWeight.Normal
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                 }
                                 /* { e ->
                                                val isSelected = e == episodeNumber
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) immersivePurple else Color.White.copy(alpha = 0.05f))
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { episodeNumber = e }
                                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "E$e",
                                                        color = if (isSelected) onPrimaryPurple else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // List our 6 Stream Servers
                                */ val allServersList = listOf(
                                    Pair(Triple("Server 1 (Fast) — Vidsrc Embed", if (!isTvSeries) "https://vidsrc-embed.ru/embed/movie/${movie.id}" else "https://vidsrc-embed.ru/embed/tv/${movie.id}/${seasonNumber}-${episodeNumber}", "RECOMMENDED"), 1),
                                    Pair(Triple("Server 2 (Super) — Multiembed", if (!isTvSeries) "https://multiembed.mov/?video_id=${movie.id}&tmdb=1" else "https://multiembed.mov/?video_id=${movie.id}&tmdb=1&s=${seasonNumber}&e=${episodeNumber}", "FAST AD-BLOCK"), 2),
                                    Pair(Triple("Server 3 (2embed) — 2embed", if (!isTvSeries) "https://www.2embed.cc/embed/${movie.id}" else "https://www.2embed.cc/embedtv/${movie.id}?s=${seasonNumber}&e=${episodeNumber}", "STABLE HD"), 3),
                                    Pair(Triple("Server 4 (hnembed) — hnembed", if (!isTvSeries) "https://hnembed.cc/movie/${movie.id}" else "https://hnembed.cc/tv/${movie.id}/${seasonNumber}/${episodeNumber}", "MULTI SERVER"), 4),
                                    Pair(Triple("Server 5 (Videasy) — Videasy", if (!isTvSeries) "https://player.videasy.net/movie/${movie.id}?color=ff0000" else "https://player.videasy.net/tv/${movie.id}/${seasonNumber}/${episodeNumber}?color=ff0000", "ADVANCED PL"), 5),
                                    Pair(Triple("Server 6 (ezvidapi) — ezvidapi", if (!isTvSeries) "https://ezvidapi.com/movie/${movie.id}?provider=vidsrc" else "https://ezvidapi.com/tv/${movie.id}/${seasonNumber}/${episodeNumber}?provider=vidsrc", "PROXY STREAM"), 6)
                                )

                                val extraServersEnabled by viewModel.extraServersEnabled.collectAsState()
                                val serversList = if (extraServersEnabled) {
                                    allServersList
                                } else {
                                    listOf(allServersList[0], allServersList[4])
                                }

                                val isSmallScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
                                val serverTitleSize = if (isSmallScreen) 11.sp else 13.sp
                                val serverBadgeSize = if (isSmallScreen) 8.sp else 9.sp
                                val serverDescSize = if (isSmallScreen) 9.sp else 11.sp
                                val serverBtnSize = if (isSmallScreen) 11.sp else 12.sp
                                val serverBtnHeight = if (isSmallScreen) 34.dp else 40.dp
                                val serverPadding = if (isSmallScreen) 10.dp else 14.dp

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    serversList.forEachIndexed { loopIdx, pair ->
                                        val originalServerIndex = pair.second
                                        val (name, url, badge) = pair.first
                                        // Custom styled server container
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                                .padding(serverPadding)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(immersivePurple.copy(alpha = 0.15f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$originalServerIndex",
                                                            color = immersivePurple,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = serverTitleSize,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .background(immersivePurple.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = badge,
                                                        color = immersivePurple,
                                                        fontSize = serverBadgeSize,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = url,
                                                color = Color.Gray,
                                                fontSize = serverDescSize,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )

                                            Button(
                                                onClick = {
                                                    viewModel.isTvSeriesPlay = isTvSeries
                                                    viewModel.currentTvSeriesId = movie.id
                                                    viewModel.currentSeasonNumber = seasonNumber
                                                    viewModel.currentEpisodeNumber = episodeNumber
                                                    if (isTvSeries) {
                                                        viewModel.maxEpisodeNumber = detailEpisodeList.maxOfOrNull { it.episode_number } ?: 1
                                                    }
                                                    viewModel.selectedServerIndex = originalServerIndex

                                                    val displayTitleSuffix = if (isTvSeries) {
                                                        " - Season $seasonNumber Episode $episodeNumber"
                                                    } else {
                                                        ""
                                                    }
                                                    onPlayInAppClick(movie.id, "${movie.displayTitle}$displayTitleSuffix", movie.poster_path, url)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(serverBtnHeight),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = immersivePurple,
                                                    contentColor = onPrimaryPurple
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Stream in App", fontSize = serverBtnSize, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "DYNAMIC MAPPING STATUS",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Active Server Nodes • 6/6 Online",
                                            color = Color(0xFF00FFCC),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.height(16.dp)
                                    ) {
                                        Box(modifier = Modifier.width(3.dp).height(6.dp).background(Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(1.dp)))
                                        Box(modifier = Modifier.width(3.dp).height(10.dp).background(Color(0xFF00FFCC).copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                                        Box(modifier = Modifier.width(3.dp).height(15.dp).background(Color(0xFF00FFCC), RoundedCornerShape(1.dp)))
                                    }
                                }
                            }
                        }
                    }

                    // 4. Casting / Credits Row
                    if (state.credits.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Cast Credits",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(state.credits) { castMember ->
                                    CastItem(member = castMember)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = state.message, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun CastItem(member: TmdbCastMember) {
    Column(
        modifier = Modifier.width(75.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2D31))
        ) {
            if (member.profile_path != null) {
                AsyncImage(
                    model = member.getProfileUrl(),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = member.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = member.character,
            color = Color.Gray,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun InteractionChip(
    label: String,
    selected: Boolean,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) activeColor.copy(alpha = 0.15f) else Color(0xFF2A2D31),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) activeColor else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (selected) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = if (selected) activeColor else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (selected) Color.White else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

