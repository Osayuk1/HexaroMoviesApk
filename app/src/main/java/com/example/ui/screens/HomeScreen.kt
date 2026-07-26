package com.example.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.data.TmdbMovie
import com.example.data.WatchlistItem
import com.example.ui.HomeUiState
import com.example.ui.MovieViewModel
import com.example.ui.SearchUiState
import com.example.ui.theme.*
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit,
    onPlayInAppClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeUiState by viewModel.homeState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchHistoryList by viewModel.searchHistory.collectAsState()
    val searchUiState by viewModel.searchState.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val downloadedMovies by viewModel.downloadedMovies.collectAsState()
    val watchedList by viewModel.watchedList.collectAsState()
    val currentlyWatchingList by viewModel.currentlyWatchingList.collectAsState()
    val favoritesList by viewModel.favoritesList.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val interactiveWatchlist by viewModel.interactiveWatchlist.collectAsState()

    val genresList by viewModel.genresList.collectAsState()
    val selectedGenreId by viewModel.selectedGenreId.collectAsState()
    val genreMediaState by viewModel.genreMediaState.collectAsState()

    val activeTab by viewModel.activeTab.collectAsState()
    val matureContentEnabled by viewModel.matureContentEnabled.collectAsState()

    val animeNavEnabled by viewModel.animeNavEnabled.collectAsState()
    val nollywoodNavEnabled by viewModel.nollywoodNavEnabled.collectAsState()
    val bollywoodNavEnabled by viewModel.bollywoodNavEnabled.collectAsState()
    val kdramaNavEnabled by viewModel.kdramaNavEnabled.collectAsState()

    val scope = rememberCoroutineScope()

    val deepBackground = MaterialTheme.colorScheme.background
    val immersivePurple = MaterialTheme.colorScheme.primary
    val onPrimaryPurple = MaterialTheme.colorScheme.onPrimary
    val neutralDarkSurface = MaterialTheme.colorScheme.surface

    var logoClickCount by remember { mutableStateOf(0) }

    // Handle back press neatly - go back to the home tab if in other tabs
    BackHandler(enabled = activeTab != "home") {
        viewModel.setActiveTab("home")
    }

    // In-app backendless check update modal
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateDownloadedSize by viewModel.updateDownloadedSize.collectAsState()
    val updateTotalSize by viewModel.updateTotalSize.collectAsState()
    val updateDownloadError by viewModel.updateDownloadError.collectAsState()
    val updateDownloadedFile by viewModel.updateDownloadedFile.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var hasInstallPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
        )
    }

    var showPermissionExplanationDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasInstallPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(updateCheckStatus) {
        when (val status = updateCheckStatus) {
            is com.example.ui.UpdateCheckStatus.AlreadyUpToDate -> {
                android.widget.Toast.makeText(context, "Hexaro Movies is fully up to date! (${status.currentVersion})", android.widget.Toast.LENGTH_LONG).show()
                viewModel.clearUpdateCheckStatus()
            }
            is com.example.ui.UpdateCheckStatus.NewUpdateAvailable -> {
                android.widget.Toast.makeText(context, "New update found: v${status.metadata.versionName}", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateCheckStatus()
            }
            is com.example.ui.UpdateCheckStatus.Error -> {
                android.widget.Toast.makeText(context, "Version check failed: ${status.errorMessage}", android.widget.Toast.LENGTH_LONG).show()
                viewModel.clearUpdateCheckStatus()
            }
            else -> {}
        }
    }

    if (updateAvailable != null) {
        val update = updateAvailable!!
        AlertDialog(
            onDismissRequest = {
                // Do not dismiss on click outside or back select to prevent accidental clicks
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDownloadingUpdate) Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDownloadingUpdate) "Downloading Update..." else "Update Available! (v${update.versionName})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloadingUpdate || updateDownloadedFile != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        val rotateAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale)
                                .padding(8.dp)
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0xFF0D9488).copy(alpha = 0.15f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                                )
                                drawArc(
                                    color = Color(0xFF0D9488),
                                    startAngle = rotateAngle,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 8.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                )
                            }
                            
                            Icon(
                                imageVector = if (updateDownloadedFile != null) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (updateDownloadedFile != null) Color(0xFF10B981) else Color(0xFF0D9488),
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (updateDownloadedFile != null) {
                            Text(
                                text = "Download Complete!",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ready to build and install update.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            val formattedDownloaded = String.format("%.2f", updateDownloadedSize / (1024f * 1024f))
                            val formattedTotal = if (updateTotalSize > 0) String.format("%.2f", updateTotalSize / (1024f * 1024f)) else "Unknown"
                            val progressPercent = (updateDownloadProgress * 100).toInt()

                            LinearProgressIndicator(
                                progress = updateDownloadProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF0D9488),
                                trackColor = Color(0xFF2A2D31)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$formattedDownloaded MB / $formattedTotal MB",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "$progressPercent%",
                                    color = Color(0xFF0D9488),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (updateDownloadError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Error: $updateDownloadError",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "A new version of Hexaro Movies is available to download.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        if (update.changelog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Changelog:",
                                color = Color(0xFF0D9488),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = update.changelog,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (updateDownloadedFile != null) {
                    Button(
                        onClick = {
                            if (hasInstallPermission) {
                                triggerApkInstall(context, updateDownloadedFile!!)
                            } else {
                                showPermissionExplanationDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Install Now", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else if (isDownloadingUpdate) {
                    TextButton(enabled = false, onClick = {}) {
                        Text("Downloading...", color = Color.Gray)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startDownloadUpdate(update.downloadUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Download & Install", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { viewModel.dismissUpdate() }) {
                        Text("Later", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E20),
            shape = RoundedCornerShape(16.dp)
        )
 
        LaunchedEffect(updateDownloadedFile, hasInstallPermission) {
            if (updateDownloadedFile != null && hasInstallPermission) {
                triggerApkInstall(context, updateDownloadedFile!!)
                showPermissionExplanationDialog = false
            }
        }

        // Periodic permission monitor running every 2 seconds when update is downloaded
        LaunchedEffect(updateDownloadedFile) {
            if (updateDownloadedFile != null) {
                while (true) {
                    val currentPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.packageManager.canRequestPackageInstalls()
                    } else {
                        true
                    }
                    if (currentPerm != hasInstallPermission) {
                        hasInstallPermission = currentPerm
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }
        }

        // Permission explanation popup
        if (showPermissionExplanationDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionExplanationDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Permission Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = "To apply the update and install Hexaro Movies v${update.versionName}, Android requires you to allow installing unknown apps from this source.\n\nClick below to open Settings and toggle \"Allow from this source\", then return to the app to proceed automatically.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Go to Settings", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionExplanationDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E20),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = deepBackground,
        bottomBar = {
            NavigationBar(
                containerColor = neutralDarkSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(width = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            ) {
                val navBarItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = onPrimaryPurple,
                    selectedTextColor = immersivePurple,
                    indicatorColor = immersivePurple,
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                )

                NavigationBarItem(
                    selected = activeTab == "home",
                    onClick = { viewModel.setActiveTab("home") },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
                NavigationBarItem(
                    selected = activeTab == "movies",
                    onClick = { viewModel.setActiveTab("movies") },
                    icon = { Icon(imageVector = Icons.Default.Movie, contentDescription = "Movies") },
                    label = { Text("Movies", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
                NavigationBarItem(
                    selected = activeTab == "tvshows",
                    onClick = { viewModel.setActiveTab("tvshows") },
                    icon = { Icon(imageVector = Icons.Default.LiveTv, contentDescription = "TV") },
                    label = { Text("TV", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
                if (animeNavEnabled) {
                    NavigationBarItem(
                        selected = activeTab == "anime",
                        onClick = { viewModel.setActiveTab("anime") },
                        icon = { Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Anime") },
                        label = { Text("Anime", fontSize = 10.sp, maxLines = 1) },
                        colors = navBarItemColors
                    )
                }
                if (nollywoodNavEnabled) {
                    NavigationBarItem(
                        selected = activeTab == "nollywood",
                        onClick = { viewModel.setActiveTab("nollywood") },
                        icon = { Icon(imageVector = Icons.Default.MovieFilter, contentDescription = "Nollywood") },
                        label = { Text("Nollywood", fontSize = 10.sp, maxLines = 1) },
                        colors = navBarItemColors
                    )
                }
                if (bollywoodNavEnabled) {
                    NavigationBarItem(
                        selected = activeTab == "bollywood",
                        onClick = { viewModel.setActiveTab("bollywood") },
                        icon = { Icon(imageVector = Icons.Default.MusicVideo, contentDescription = "Bollywood") },
                        label = { Text("Bollywood", fontSize = 10.sp, maxLines = 1) },
                        colors = navBarItemColors
                    )
                }
                if (kdramaNavEnabled) {
                    NavigationBarItem(
                        selected = activeTab == "kdrama",
                        onClick = { viewModel.setActiveTab("kdrama") },
                        icon = { Icon(imageVector = Icons.Default.LocalActivity, contentDescription = "K-Drama") },
                        label = { Text("K-Drama", fontSize = 10.sp, maxLines = 1) },
                        colors = navBarItemColors
                    )
                }
                NavigationBarItem(
                    selected = activeTab == "cartoons",
                    onClick = { viewModel.setActiveTab("cartoons") },
                    icon = { Icon(imageVector = Icons.Default.Face, contentDescription = "Cartoons") },
                    label = { Text("Cartoons", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
                NavigationBarItem(
                    selected = activeTab == "downloads",
                    onClick = { viewModel.setActiveTab("downloads") },
                    icon = { Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads") },
                    label = { Text("Downloads", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
                NavigationBarItem(
                    selected = activeTab == "settings",
                    onClick = { viewModel.setActiveTab("settings") },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp, maxLines = 1) },
                    colors = navBarItemColors
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            logoClickCount++
                            if (logoClickCount >= 10) {
                                viewModel.updateExtraFeaturesEnabled(true)
                                android.widget.Toast.makeText(context, "Extra features unlocked!", android.widget.Toast.LENGTH_LONG).show()
                                logoClickCount = 0
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(immersivePurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "H",
                                fontWeight = FontWeight.Bold,
                                color = onPrimaryPurple,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hexaro",
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setActiveTab("search") },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(neutralDarkSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Clickable profile icon top right
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (activeTab == "me") immersivePurple else Color(0xFF49454F))
                            .border(2.dp, immersivePurple, CircleShape)
                            .clickable { viewModel.setActiveTab("me") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile icon",
                            tint = if (activeTab == "me") onPrimaryPurple else Color.LightGray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = deepBackground,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        // Standalone Views to Eliminate Lag Completely
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "home" -> {
                    HomeTabContent(
                        viewModel = viewModel,
                        homeUiState = homeUiState,
                        genresList = genresList,
                        selectedGenreId = selectedGenreId,
                        genreMediaState = genreMediaState,
                        watchlist = watchlist,
                        immersivePurple = immersivePurple,
                        onPrimaryPurple = onPrimaryPurple,
                        neutralDarkSurface = neutralDarkSurface,
                        onMovieClick = onMovieClick
                    )
                }
                "movies" -> {
                    MoviesTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "tvshows" -> {
                    TvShowsTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "anime" -> {
                    AnimeTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "nollywood" -> {
                    NollywoodTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "bollywood" -> {
                    BollywoodTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "kdrama" -> {
                    KdramaTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "cartoons" -> {
                    CartoonsTabContent(
                        viewModel = viewModel,
                        onMovieClick = onMovieClick
                    )
                }
                "downloads" -> {
                    DedicatedGroupedDownloadsPage(
                        viewModel = viewModel,
                        downloadedMovies = downloadedMovies,
                        immersivePurple = immersivePurple,
                        onPrimaryPurple = onPrimaryPurple,
                        onMovieClick = onMovieClick,
                        onBack = { viewModel.setActiveTab("home") },
                        isTab = true
                    )
                }
                "search" -> {
                    SearchTabContent(
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        searchHistoryList = searchHistoryList,
                        searchUiState = searchUiState,
                        immersivePurple = immersivePurple,
                        neutralDarkSurface = neutralDarkSurface,
                        onMovieClick = onMovieClick
                    )
                }
                "me" -> {
                    MeTabContent(
                        viewModel = viewModel,
                        watchedList = watchedList,
                        favoritesList = favoritesList,
                        currentlyWatchingList = currentlyWatchingList,
                        watchLaterList = watchLaterList,
                        interactiveWatchlist = interactiveWatchlist,
                        downloadedMovies = downloadedMovies,
                        immersivePurple = immersivePurple,
                        onPrimaryPurple = onPrimaryPurple,
                        onMovieClick = onMovieClick
                    )
                }
                "settings" -> {
                    SettingsTabContent(
                        viewModel = viewModel,
                        immersivePurple = immersivePurple,
                        onPrimaryPurple = onPrimaryPurple,
                        neutralDarkSurface = neutralDarkSurface
                    )
                }
            }
        }
    }
}

// ==================== SUB-TAB CONTENTS ====================

@Composable
fun HomeTabContent(
    viewModel: MovieViewModel,
    homeUiState: HomeUiState,
    genresList: List<com.example.data.TmdbGenre>,
    selectedGenreId: Int?,
    genreMediaState: List<TmdbMovie>,
    watchlist: List<WatchlistItem>,
    immersivePurple: Color,
    onPrimaryPurple: Color,
    neutralDarkSurface: Color,
    onMovieClick: (Int, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        when (homeUiState) {
            is HomeUiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = immersivePurple)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Fetching Movies...", color = Color.LightGray)
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                if (homeUiState.trending.isNotEmpty()) {
                    val heroMovie = homeUiState.trending.first()
                    item {
                        FeaturedHeroBanner(
                            movie = heroMovie,
                            onClick = { onMovieClick(heroMovie.id, if (heroMovie.isTvShow) "tv" else "movie") },
                            neonTeal = immersivePurple,
                            neonViolet = Color.Transparent
                        )
                    }
                }

                if (genresList.isNotEmpty()) {
                    item {
                        MovieSectionHeader(title = "Browse Genres", icon = Icons.Default.Explore, tint = immersivePurple)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(genresList) { genre ->
                                val isSelected = genre.id == selectedGenreId
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) immersivePurple else neutralDarkSurface)
                                        .clickable { viewModel.selectGenre(genre.id) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = genre.name,
                                        color = if (isSelected) onPrimaryPurple else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // Render selected genre movies & shows
                        if (genreMediaState.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(genreMediaState) { media ->
                                    MovieCard(
                                        movie = media,
                                        onClick = { onMovieClick(media.id, if (media.isTvShow) "tv" else "movie") }
                                    )
                                }
                            }
                        }
                    }
                }

                if (watchlist.isNotEmpty()) {
                    item {
                        MovieSectionHeader(title = "Local Watchlist", icon = Icons.Default.Bookmark, tint = immersivePurple)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(watchlist) { watchItem ->
                                WatchlistItemCard(
                                    item = watchItem,
                                    onClick = { onMovieClick(watchItem.id, watchItem.mediaType ?: "movie") }
                                )
                            }
                        }
                    }
                }

                item {
                    MovieSectionHeader(title = "Trending Today", icon = Icons.Default.TrendingUp, tint = immersivePurple)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(homeUiState.trending.drop(1)) { movie ->
                            MovieCard(movie = movie, onClick = { onMovieClick(movie.id, if (movie.isTvShow) "tv" else "movie") })
                        }
                    }
                }

                if (homeUiState.trendingTv.isNotEmpty()) {
                    item {
                        MovieSectionHeader(title = "Trending TV Shows", icon = Icons.Default.LiveTv, tint = immersivePurple)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(homeUiState.trendingTv) { tv ->
                                MovieCard(movie = tv, onClick = { onMovieClick(tv.id, "tv") })
                            }
                        }
                    }
                }

                item {
                    MovieSectionHeader(title = "Popular Movies", icon = Icons.Default.ThumbUp, tint = immersivePurple)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(homeUiState.popular) { movie ->
                            MovieCard(movie = movie, onClick = { onMovieClick(movie.id, "movie") })
                        }
                    }
                }

                if (homeUiState.popularTv.isNotEmpty()) {
                    item {
                        MovieSectionHeader(title = "Popular TV Shows", icon = Icons.Default.ConnectedTv, tint = immersivePurple)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(homeUiState.popularTv) { tv ->
                                MovieCard(movie = tv, onClick = { onMovieClick(tv.id, "tv") })
                            }
                        }
                    }
                }

                item {
                    MovieSectionHeader(title = "Top Critically Rated Movies", icon = Icons.Default.Star, tint = immersivePurple)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(homeUiState.topRated) { movie ->
                            MovieCard(movie = movie, onClick = { onMovieClick(movie.id, "movie") })
                        }
                    }
                }

                if (homeUiState.topRatedTv.isNotEmpty()) {
                    item {
                        MovieSectionHeader(title = "Top Rated TV Series", icon = Icons.Default.Stars, tint = immersivePurple)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(homeUiState.topRatedTv) { tv ->
                                MovieCard(movie = tv, onClick = { onMovieClick(tv.id, "tv") })
                            }
                        }
                    }
                }
            }
            is HomeUiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = homeUiState.message,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadHomeData() },
                            colors = ButtonDefaults.buttonColors(containerColor = immersivePurple)
                        ) {
                            Text("Retry Connection")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviesTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val moviesCategory by viewModel.moviesCategory.collectAsState()
    val moviesList by viewModel.moviesList.collectAsState()
    val moviesLoading by viewModel.moviesLoading.collectAsState()

    val categories = listOf(
        "Popular", "Top Rated", "Trending", "Action", "Adventure", "Animation", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery",
        "Romance", "Science Fiction", "Thriller", "TV Movie", "War", "Western"
    )
    val gridState = rememberLazyGridState()

    // Infinite fetching trigger
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextMoviesPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Categories Horizontal bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = moviesCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setMoviesCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (moviesLoading && moviesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(moviesList) { movie ->
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, "movie") }
                    )
                }
                if (moviesLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvShowsTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val tvCategory by viewModel.tvCategory.collectAsState()
    val tvList by viewModel.tvList.collectAsState()
    val tvLoading by viewModel.tvLoading.collectAsState()

    val categories = listOf(
        "Popular", "Top Rated", "Trending", "Action", "Adventure", "Animation", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery",
        "Romance", "Science Fiction", "Thriller", "TV Movie", "War", "Western"
    )
    val gridState = rememberLazyGridState()

    // Infinite fetching trigger
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextTvPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Categories Horizontal bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = tvCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setTvCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (tvLoading && tvList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tvList) { movie ->
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, "tv") }
                    )
                }
                if (tvLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val animeCategory by viewModel.animeCategory.collectAsState()
    val animeList by viewModel.animeList.collectAsState()
    val animeLoading by viewModel.animeLoading.collectAsState()

    val categories = listOf(
        "Popular Anime", "Shonen", "Isekai", "Shojo", "Seinen", "Mecha", "Slice of Life",
        "Action & Adventure Anime", "Sci-Fi & Fantasy Anime", "Comedy Anime", "Drama Anime", "Mystery Anime", "Kids Anime"
    )
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextAnimePage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = animeCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setAnimeCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (animeLoading && animeList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(animeList) { anime ->
                    MovieVerticalGridCard(
                        movie = anime,
                        onClick = { onMovieClick(anime.id, "tv") }
                    )
                }
                if (animeLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatureTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val matureCategory by viewModel.matureCategory.collectAsState()
    val matureList by viewModel.matureList.collectAsState()
    val matureLoading by viewModel.matureLoading.collectAsState()

    val categories = listOf("Mature Movies", "Mature TV Shows", "Action/Thriller", "Horror/Dark")
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextMaturePage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = matureCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setMatureCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (matureLoading && matureList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(matureList) { matureItem ->
                    MovieVerticalGridCard(
                        movie = matureItem,
                        onClick = { onMovieClick(matureItem.id, if (matureCategory.contains("TV") || matureItem.isTvShow) "tv" else "movie") }
                    )
                }
                if (matureLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieVerticalGridCard(
    movie: TmdbMovie,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1B1F))
        ) {
            AsyncImage(
                model = movie.getPosterUrl(),
                contentDescription = movie.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Rating label overlay
            val rating = movie.vote_average ?: 0.0
            if (rating > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFE5A93B),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color(0xFFE5A93B),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.displayTitle,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = movie.displayDate.take(4),
            color = Color.Gray,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SearchTabContent(
    viewModel: MovieViewModel,
    searchQuery: String,
    searchHistoryList: List<com.example.data.SearchHistoryItem>,
    searchUiState: SearchUiState,
    immersivePurple: Color,
    neutralDarkSurface: Color,
    onMovieClick: (Int, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = neutralDarkSurface,
                    unfocusedContainerColor = neutralDarkSurface.copy(alpha = 0.8f),
                    focusedBorderColor = immersivePurple,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E2E6)
                ),
                placeholder = { Text("Search movies, TV shows and genres...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }

        if (searchQuery.trim().isEmpty()) {
            if (searchHistoryList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Searches", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { viewModel.clearAllSearchHistory() }) {
                            Text("Clear All", color = immersivePurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        searchHistoryList.forEach { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(neutralDarkSurface)
                                    .clickable {
                                        viewModel.onSearchQueryChanged(historyItem.query)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(historyItem.query, color = Color.LightGray, fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteSearchHistoryItem(historyItem.query)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Explore Curation Pools",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Type movie names, series, or genres to initiate real-time TMDB query pools.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Search Results for \"$searchQuery\"",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when (val searchState = searchUiState) {
                is SearchUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = immersivePurple)
                        }
                    }
                }
                is SearchUiState.Success -> {
                    if (searchState.results.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No results found matching search.", color = Color.LightGray, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(searchState.results.chunked(2)) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { movie ->
                                    MovieGridItem(
                                        movie = movie,
                                        onMovieClick = { id ->
                                            viewModel.recordSearchQuery(searchQuery)
                                            onMovieClick(id, if (movie.isTvShow) "tv" else "movie")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                is SearchUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1518)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Problem searching content. Please try again.",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MeTabContent(
    viewModel: MovieViewModel,
    watchedList: List<com.example.data.UserMediaInteraction>,
    favoritesList: List<com.example.data.UserMediaInteraction>,
    currentlyWatchingList: List<com.example.data.UserMediaInteraction>,
    watchLaterList: List<com.example.data.UserMediaInteraction>,
    interactiveWatchlist: List<com.example.data.UserMediaInteraction>,
    downloadedMovies: List<com.example.data.DownloadedMovie>,
    immersivePurple: Color,
    onPrimaryPurple: Color,
    onMovieClick: (Int, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingExportMovie by remember { mutableStateOf<com.example.data.DownloadedMovie?>(null) }

    val writePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingExportMovie?.let { completed ->
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val success = exportMovieToDownloadsFolder(context, completed)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (success) {
                            android.widget.Toast.makeText(context, "Movie successfully copied to Downloads/HexaroMovies folder!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to copy movie to Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            android.widget.Toast.makeText(context, "Storage permission is required to save movies to public Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
        }
        pendingExportMovie = null
    }

    var viewingGroupedDownloads by remember { mutableStateOf(false) }

    if (viewingGroupedDownloads) {
        DedicatedGroupedDownloadsPage(
            viewModel = viewModel,
            downloadedMovies = downloadedMovies,
            immersivePurple = immersivePurple,
            onPrimaryPurple = onPrimaryPurple,
            onMovieClick = onMovieClick,
            onBack = { viewingGroupedDownloads = false }
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        item {
            // User Profile Card & Stats Header
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(immersivePurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = onPrimaryPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "My Personal Studio",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "anon",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val watcherLevel = when {
                            watchedList.size >= 15 -> "Elite Cinephile"
                            watchedList.size >= 5 -> "Active Movie Critic"
                            else -> "Novice Enthusiast"
                        }
                        Box(
                            modifier = Modifier
                                .background(immersivePurple.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, immersivePurple, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = watcherLevel,
                                color = immersivePurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Stat grid items
        item {
            Text(
                text = "Personal Statistics",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Watched count card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
                    modifier = Modifier.weight(1f).border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Watched", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${watchedList.size}", color = Color(0xFF2DD4BF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("items completed", color = Color.Gray, fontSize = 9.sp)
                    }
                }

                // Favorites count card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
                    modifier = Modifier.weight(1f).border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Favorites", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${favoritesList.size}", color = Color(0xFFF43F5E), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("highly rated", color = Color.Gray, fontSize = 9.sp)
                    }
                }

                // Watching count card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
                    modifier = Modifier.weight(1f).border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("In Progress", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${currentlyWatchingList.size}", color = Color(0xFFF59E0B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("active session", color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }
        }

        // Screen Time / Binge Stats Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
                modifier = Modifier.fillMaxWidth().border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimated Viewing Duration", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalHours = (watchedList.size * 2) + (currentlyWatchingList.size * 1.5).toInt()
                        Text("$totalHours hours", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                }
            }
        }

        // 1. Currently Watching Row
        item {
            MovieSectionHeader(title = "Currently Watching", icon = Icons.Default.PlayArrow, tint = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(8.dp))
            if (currentlyWatchingList.isEmpty()) {
                EmptyTrackListPlaceholder("No items in your active watch sessions.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(currentlyWatchingList) { watchItem ->
                        UserInteractionCard(
                            item = watchItem,
                            onClick = { onMovieClick(watchItem.id, watchItem.mediaType ?: "movie") }
                        )
                    }
                }
            }
        }

        // 2. Favorites Row
        item {
            MovieSectionHeader(title = "My Favorites", icon = Icons.Default.Favorite, tint = Color(0xFFF43F5E))
            Spacer(modifier = Modifier.height(8.dp))
            if (favoritesList.isEmpty()) {
                EmptyTrackListPlaceholder("Mark items as Favorite on details tab.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favoritesList) { faveItem ->
                        UserInteractionCard(
                            item = faveItem,
                            onClick = { onMovieClick(faveItem.id, faveItem.mediaType ?: "movie") }
                        )
                    }
                }
            }
        }

        // 3. Watch Later Row
        item {
            MovieSectionHeader(title = "Watch Later", icon = Icons.Default.WatchLater, tint = Color(0xFF60A5FA))
            Spacer(modifier = Modifier.height(8.dp))
            if (watchLaterList.isEmpty()) {
                EmptyTrackListPlaceholder("Schedule movies or series for watch later.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(watchLaterList) { laterItem ->
                        UserInteractionCard(
                            item = laterItem,
                            onClick = { onMovieClick(laterItem.id, laterItem.mediaType ?: "movie") }
                        )
                    }
                }
            }
        }

        // 4. Watchlist Row
        item {
            MovieSectionHeader(title = "Custom Watchlist", icon = Icons.Default.Bookmark, tint = immersivePurple)
            Spacer(modifier = Modifier.height(8.dp))
            if (interactiveWatchlist.isEmpty()) {
                EmptyTrackListPlaceholder("Save cinematic entries to your watchlist.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(interactiveWatchlist) { watchItem ->
                        UserInteractionCard(
                            item = watchItem,
                            onClick = { onMovieClick(watchItem.id, watchItem.mediaType ?: "movie") }
                        )
                    }
                }
            }
        }

        // 5. Watched History Row
        item {
            MovieSectionHeader(title = "Watched History", icon = Icons.Default.CheckCircle, tint = Color(0xFF2DD4BF))
            Spacer(modifier = Modifier.height(8.dp))
            if (watchedList.isEmpty()) {
                EmptyTrackListPlaceholder("Mark items as Watched to build your visual profile.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(watchedList) { watchedItem ->
                        UserInteractionCard(
                            item = watchedItem,
                            onClick = { onMovieClick(watchedItem.id, watchedItem.mediaType ?: "movie") }
                        )
                    }
                }
            }
        }


    }
}
}

@Composable
fun SettingsTabContent(
    viewModel: MovieViewModel,
    immersivePurple: Color,
    onPrimaryPurple: Color,
    neutralDarkSurface: Color
) {
    val context = LocalContext.current
    val streamingQuality by viewModel.streamingQuality.collectAsState()
    val autoplayNext by viewModel.autoplayNext.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val matureEnabled by viewModel.matureContentEnabled.collectAsState()

    var importInputText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Video Playback Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Video & Playback Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Preferred Resolution",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Auto", "1080p", "720p", "480p").forEach { quality ->
                            val isSel = streamingQuality == quality
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) immersivePurple else Color.Black.copy(alpha = 0.3f))
                                    .clickable { viewModel.updateStreamingQuality(quality) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                  Text(
                                      text = quality,
                                      color = if (isSel) onPrimaryPurple else Color.White,
                                      fontSize = 11.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Autoplay Next Episode",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically load the next TV episode after one finishes.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                        Switch(
                            checked = autoplayNext,
                            onCheckedChange = { viewModel.updateAutoplayNext(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    val extraServersEnabled by viewModel.extraServersEnabled.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Extra Streaming Servers",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Show all 6 servers block. When disabled, only the reliable Vidsrc and Videasy servers are listed.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                        Switch(
                            checked = extraServersEnabled,
                            onCheckedChange = { viewModel.updateExtraServersEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    val disableHlsStreaming by viewModel.disableHlsStreaming.collectAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Disable HLS Streaming",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Completely disable playing or sniffing adaptive HLS (.m3u8) streams. Standard progressive (.mp4) videos will be preferred.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                        Switch(
                            checked = disableHlsStreaming,
                            onCheckedChange = { viewModel.updateDisableHlsStreaming(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    val extraFeaturesEnabled by viewModel.extraFeaturesEnabled.collectAsState()
                    if (extraFeaturesEnabled) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "Extra Features (Easter Egg)",
                                    color = Color(0xFF00FFCC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Disable the extra hidden developer options, special stream labels, and troubleshooting overlays.",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                            Switch(
                                checked = extraFeaturesEnabled,
                                onCheckedChange = { viewModel.updateExtraFeaturesEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = onPrimaryPurple,
                                    checkedTrackColor = immersivePurple,
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2c. Check for Updates Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        android.widget.Toast.makeText(context, "Checking for latest updates...", android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.checkForUpdates(isManual = true)
                    }
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = immersivePurple,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Check for Updates",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Verify if you are running the latest premium release version of Hexaro.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2b. Category Navigation Customization Card
        item {
            val animeNavEnabled by viewModel.animeNavEnabled.collectAsState()
            val nollywoodNavEnabled by viewModel.nollywoodNavEnabled.collectAsState()
            val bollywoodNavEnabled by viewModel.bollywoodNavEnabled.collectAsState()
            val kdramaNavEnabled by viewModel.kdramaNavEnabled.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Navigation Library Customization",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Toggle visibility of specialized content categories in the bottom navigation bar. Disabling a category removes its tab, but you can still search for titles within it.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Anime
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Anime Tab Screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = animeNavEnabled,
                            onCheckedChange = { viewModel.updateAnimeNavEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Nollywood
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Nollywood Tab Screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = nollywoodNavEnabled,
                            onCheckedChange = { viewModel.updateNollywoodNavEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Bollywood
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bollywood Tab Screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = bollywoodNavEnabled,
                            onCheckedChange = { viewModel.updateBollywoodNavEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // K-Drama
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "K-Drama Tab Screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = kdramaNavEnabled,
                            onCheckedChange = { viewModel.updateKdramaNavEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryPurple,
                                checkedTrackColor = immersivePurple,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // 3. Localization Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Language & Audio Layout",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Preferred Audio Language",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("English", "Spanish", "French", "Japanese").forEach { lang ->
                            val isSel = preferredLanguage == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) immersivePurple else Color.Black.copy(alpha = 0.3f))
                                    .clickable { viewModel.updatePreferredLanguage(lang) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang,
                                    color = if (isSel) onPrimaryPurple else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Local Cache & Reset Data Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Storage & Local Logs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Clear or reset your locally watchlisted movies and finished downloads to clean space.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.clearAllUserData()
                            android.widget.Toast.makeText(context, "Local watchlist and downloads cleared successfully!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f), contentColor = Color.Red),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Local Watchlist & Downloads", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Visual Themes & Accents
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Theme Selection",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Select active interface accents and dark canvas pairings:",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val themesList = listOf(
                        "Cosmic Purple" to Color(0xFFD0BCFF),
                        "Emerald Ocean" to Color(0xFF2DD4BF),
                        "Crimson Shadow" to Color(0xFFF43F5E),
                        "Sapphire Star" to Color(0xFF60A5FA),
                        "Sunset Gold" to Color(0xFFFBBF24),
                        "Dynamic Color" to Color(0xFF42A5F5)
                    )

                    themesList.forEach { (themeName, themeAccent) ->
                        val isSel = appTheme == themeName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) themeAccent.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isSel) 1.dp else 0.5.dp,
                                    color = if (isSel) themeAccent else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.updateAppTheme(themeName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(themeAccent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = themeName,
                                color = if (isSel) Color.White else Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSel) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = themeAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Backup & Restore
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Database Backup & Restore",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export your watchlisted titles and media logs as a portable JSON payload, or restore state from a clipboard import.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val payload = viewModel.exportLibraryToJson()
                                if (payload.isNotEmpty()) {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Hexaro Library Backup", payload)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Backup copied to clipboard! (Share/save safely)", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(context, "No backup data generated. Library is currently empty.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = {
                                showImportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = immersivePurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = onPrimaryPurple)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onPrimaryPurple)
                        }
                    }
                }
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Import Library Backup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        Column {
                            Text("Paste a database backup code block to sync states instantly.", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = importInputText,
                                onValueChange = { importInputText = it },
                                placeholder = { Text("Paste JSON payload here...", color = Color.Gray, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = Color.Black,
                                    unfocusedContainerColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                maxLines = 5
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (importInputText.trim().isNotEmpty()) {
                                    val success = viewModel.importLibraryFromJson(importInputText.trim())
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Database synced successfully!", android.widget.Toast.LENGTH_LONG).show()
                                        showImportDialog = false
                                        importInputText = ""
                                    } else {
                                        android.widget.Toast.makeText(context, "Invalid backup formatting code. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Restore", color = immersivePurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = neutralDarkSurface
                )
            }
        }

        // 7. Credits
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = neutralDarkSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = immersivePurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Credits & Attributions",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // osayuki Card - Prominent
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = "https://i.imgur.com/03Xuwcc.jpeg",
                                    contentDescription = "osayuki profile pic",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, immersivePurple, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "osayuki",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Lead Developer & Architect",
                                    color = immersivePurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Discord Badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFF5865F2).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://discord.com/users/334237583642738689"))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {}
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "Discord Link",
                                            tint = Color(0xFF8592FF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Discord",
                                            color = Color(0xFFD6DBFF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    // GitHub Badge
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Osayuk1"))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {}
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = "GitHub Link",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "GitHub",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // talentlessmolly Card - Smaller
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(immersivePurple.copy(alpha = 0.1f))
                                        .border(1.dp, immersivePurple.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "talentlessmolly profile pic",
                                        tint = immersivePurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "talentlessmolly",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Contributor",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // GitHub Badge for talentlessmolly
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/talentlessmoll"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "GitHub Link",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "GitHub",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .clickable {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.themoviedb.org"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = "https://i.imgur.com/XiP19Wp.png",
                                contentDescription = "TMDB Logo",
                                modifier = Modifier
                                    .height(48.dp)
                                    .fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                              text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                              color = Color.Gray,
                              fontSize = 10.sp,
                              textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== REUSABLE UTILITY COMPOSABLES ====================

@Composable
fun MovieSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            letterSpacing = (-0.4).sp
        )
    }
}

@Composable
fun FeaturedHeroBanner(
    movie: TmdbMovie,
    onClick: () -> Unit,
    neonTeal: Color,
    neonViolet: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable { onClick() }
            .border(
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.getBackdropUrl().ifEmpty { movie.getPosterUrl() },
                contentDescription = "Featured Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Red, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "EXCLUSIVE CONTENT",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.displayTitle,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: TmdbMovie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .height(170.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141218))
        ) {
            AsyncImage(
                model = movie.getPosterUrl(),
                contentDescription = movie.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val rating = movie.vote_average ?: 0.0
            if (rating > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE5A93B), modifier = Modifier.size(10.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color(0xFFE5A93B),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.displayTitle,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WatchlistItemCard(
    item: WatchlistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .height(170.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141218))
        ) {
            AsyncImage(
                model = if (!item.posterPath.isNullOrEmpty()) "https://image.tmdb.org/t/p/w342${item.posterPath}" else "",
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UserInteractionCard(
    item: com.example.data.UserMediaInteraction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .height(170.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141218))
        ) {
            AsyncImage(
                model = item.posterPath ?: "",
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (item.mediaType == "tv" && item.lastSeasonWatched != null && item.lastEpisodeWatched != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "S${item.lastSeasonWatched} E${item.lastEpisodeWatched}",
                        color = Color(0xFF00FFCC),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.mediaType == "tv" && item.lastSeasonWatched != null && item.lastEpisodeWatched != null) {
            Text(
                text = "Season ${item.lastSeasonWatched} Ep ${item.lastEpisodeWatched}",
                color = Color(0xFF00FFCC),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MovieGridItem(
    movie: TmdbMovie,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .clickable { onMovieClick(movie.id) }
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(0.67f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = movie.getPosterUrl(),
                    contentDescription = movie.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = movie.displayTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val year = if (!movie.displayDate.isNullOrEmpty() && movie.displayDate.length >= 4) {
                    movie.displayDate.substring(0, 4)
                } else {
                    null
                }
                val labelText = if (year != null) {
                    "${if (movie.isTvShow) "TV Series" else "Movie"} • $year"
                } else {
                    if (movie.isTvShow) "TV Series" else "Movie"
                }
                Text(
                    text = labelText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmptyTrackListPlaceholder(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CartoonsTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val cartoonsCategory by viewModel.cartoonsCategory.collectAsState()
    val cartoonsList by viewModel.cartoonsList.collectAsState()
    val cartoonsLoading by viewModel.cartoonsLoading.collectAsState()

    val categories = listOf(
        "Popular", "Top Rated", "Trending", "Action", "Adventure", "Animation", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery",
        "Romance", "Science Fiction", "Thriller", "TV Movie", "War", "Western"
    )
    val gridState = rememberLazyGridState()

    // Infinite fetching trigger
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextCartoonsPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Categories Horizontal bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = cartoonsCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setCartoonsCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (cartoonsLoading && cartoonsList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cartoonsList) { movie ->
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, "movie") }
                    )
                }
                if (cartoonsLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NollywoodTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val nollywoodCategory by viewModel.nollywoodCategory.collectAsState()
    val nollywoodList by viewModel.nollywoodList.collectAsState()
    val nollywoodLoading by viewModel.nollywoodLoading.collectAsState()

    val categories = listOf("Popular", "Movies", "TV Shows", "Drama", "Comedy")
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextNollywoodPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = nollywoodCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setNollywoodCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (nollywoodLoading && nollywoodList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(nollywoodList) { movie ->
                    val contentType = if (nollywoodCategory == "TV Shows") "tv" else "movie"
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, contentType) }
                    )
                }
                if (nollywoodLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BollywoodTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val bollywoodCategory by viewModel.bollywoodCategory.collectAsState()
    val bollywoodList by viewModel.bollywoodList.collectAsState()
    val bollywoodLoading by viewModel.bollywoodLoading.collectAsState()

    val categories = listOf("Popular", "Action", "Drama", "Comedy", "Romance", "TV Shows")
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextBollywoodPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = bollywoodCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setBollywoodCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (bollywoodLoading && bollywoodList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bollywoodList) { movie ->
                    val contentType = if (bollywoodCategory == "TV Shows") "tv" else "movie"
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, contentType) }
                    )
                }
                if (bollywoodLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KdramaTabContent(
    viewModel: MovieViewModel,
    onMovieClick: (Int, String) -> Unit
) {
    val kdramaCategory by viewModel.kdramaCategory.collectAsState()
    val kdramaList by viewModel.kdramaList.collectAsState()
    val kdramaLoading by viewModel.kdramaLoading.collectAsState()

    val categories = listOf("Popular", "Romance", "Drama", "Comedy", "Mystery", "Historical")
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextKdramaPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            items(categories) { cat ->
                val isSelected = kdramaCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.setKdramaCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (kdramaLoading && kdramaList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(kdramaList) { movie ->
                    MovieVerticalGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id, "tv") }
                    )
                }
                if (kdramaLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun exportMovieToDownloadsFolder(context: android.content.Context, completed: com.example.data.DownloadedMovie): Boolean {
    try {
        val srcFile = java.io.File(completed.localFileUri)
        if (!srcFile.exists() || !srcFile.isFile) {
            return false
        }
        val extension = srcFile.extension.lowercase().ifEmpty { "mp4" }
        val sanitizedTitle = completed.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val displayName = "${sanitizedTitle}_exported.$extension"

        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                val mimeType = if (extension == "ts") "video/mp2t" else "video/mp4"
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/HexaroMovies")
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.FileInputStream(srcFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return true
            }
        } else {
            val publicDownloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val hexaroDir = java.io.File(publicDownloadsDir, "HexaroMovies")
            if (!hexaroDir.exists()) {
                hexaroDir.mkdirs()
            }
            val destinationFile = java.io.File(hexaroDir, displayName)
            java.io.FileInputStream(srcFile).use { inputStream ->
                java.io.FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(destinationFile)
            context.sendBroadcast(mediaScanIntent)
            return true
        }
    } catch (e: Exception) {
        android.util.Log.e("ExportMovie", "Error exporting movie to public Downloads: " + e.message, e)
    }
    return false
}

private fun parseSeasonEpisode(title: String): Pair<Int, Int> {
    val lower = title.lowercase()
    
    // Pattern 1: s01e01, s1e1, s01 e01, s01e01-episode
    val sxxexxMatch = Regex("""s(\d+)\s*e(\d+)""").find(lower)
    if (sxxexxMatch != null) {
        val s = sxxexxMatch.groupValues[1].toIntOrNull() ?: 1
        val e = sxxexxMatch.groupValues[2].toIntOrNull() ?: 0
        return Pair(s, e)
    }
    
    // Pattern 2: season 01 episode 01, season 1, ep 2
    var season = 1
    var episode = 0
    
    val seasonMatch = Regex("""season\s*(\d+)""").find(lower)
    if (seasonMatch != null) {
        season = seasonMatch.groupValues[1].toIntOrNull() ?: 1
    } else {
        val sMatch = Regex("""\bss*(\d+)\b""").find(lower) ?: Regex("""\bs(\d+)""").find(lower)
        if (sMatch != null) {
            season = sMatch.groupValues[1].toIntOrNull() ?: 1
        }
    }
    
    val epMatch = Regex("""episode\s*(\d+)""").find(lower)
        ?: Regex("""ep\s*(\d+)""").find(lower)
        ?: Regex("""ep\.\s*(\d+)""").find(lower)
        ?: Regex("""\be(\d+)\b""").find(lower)
        ?: Regex("""\be\s*(\d+)""").find(lower)
    if (epMatch != null) {
        episode = epMatch.groupValues[1].toIntOrNull() ?: 0
    } else {
        val lastWord = lower.substringAfterLast(" ")
        if (lastWord.isNotEmpty()) {
            val num = lastWord.toIntOrNull()
            if (num != null) {
                episode = num
            }
        }
    }
    
    return Pair(season, episode)
}

private fun getCollectionGroupForMovie(title: String): Pair<String, String> {
    val lower = title.lowercase()
    val hasSeriesClue = lower.contains("season") || lower.contains("episode") || 
                        lower.contains("ep ") || lower.contains(" s") ||
                        Regex("""s\d+e\d+""").containsMatchIn(lower) || 
                        Regex("""\s[se]\d+""").containsMatchIn(lower)
    
    if (title.contains(" - ")) {
        val parts = title.split(" - ", limit = 2)
        if (parts.size == 2 && hasSeriesClue) {
            return Pair(parts[0].trim(), parts[1].trim())
        }
    }
    
    // Check if there is "Season X" in the name
    val matchSeason = Regex("""(?i)(.+?)\s+(Season\s+\d+.*|S\d+E\d+.*)""").find(title)
    if (matchSeason != null) {
        return Pair(matchSeason.groupValues[1].trim(), matchSeason.groupValues[2].trim())
    }
    
    return Pair("Standalone Movies", title)
}

@Composable
fun DedicatedGroupedDownloadsPage(
    viewModel: MovieViewModel,
    downloadedMovies: List<com.example.data.DownloadedMovie>,
    immersivePurple: Color,
    onPrimaryPurple: Color,
    onMovieClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportMovie by remember { mutableStateOf<com.example.data.DownloadedMovie?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val expandedSeries = remember { mutableStateMapOf<String, Boolean>() }

    val writePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingExportMovie?.let { completed ->
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val success = exportMovieToDownloadsFolder(context, completed)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (success) {
                            android.widget.Toast.makeText(context, "Movie successfully copied to Downloads/HexaroMovies folder!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to copy movie to Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            android.widget.Toast.makeText(context, "Storage permission is required to save movies to public Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
        }
        pendingExportMovie = null
    }

    // Filter downloaded items based on searchQuery
    val filteredMovies = remember(downloadedMovies, searchQuery) {
        if (searchQuery.isBlank()) downloadedMovies
        else downloadedMovies.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Group items and sort episodes within series folders properly.
    val groupedMap = remember(filteredMovies) {
        filteredMovies.groupBy { completed ->
            val (groupName, _) = getCollectionGroupForMovie(completed.title)
            groupName
        }.mapValues { (_, episodes) ->
            episodes.sortedWith(
                compareBy<com.example.data.DownloadedMovie> { completed ->
                    val (season, _) = parseSeasonEpisode(completed.title)
                    season
                }.thenBy { completed ->
                    val (_, episode) = parseSeasonEpisode(completed.title)
                    episode
                }.thenBy { completed ->
                    completed.title
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F10))
            .padding(16.dp)
    ) {
        // Upper row header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isTab) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Me tab",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = "Offline Downloads Library",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "${downloadedMovies.size} total files arranged by show",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar for downloaded files
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search offline files...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = immersivePurple,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color(0xFF1E1F21),
                unfocusedContainerColor = Color(0xFF1E1F21)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active download tasks progress tracking
        val queuedOrDownloadingTasks by viewModel.downloadTasks.collectAsState()
        val activeTasksToShow = queuedOrDownloadingTasks.filter {
            it.status == "Downloading" || it.status == "Queued" || it.status.startsWith("Failed") || it.status == "Cancelled"
        }
        if (activeTasksToShow.isNotEmpty()) {
            Text(
                text = "Active Downloads (${activeTasksToShow.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            activeTasksToShow.forEach { task ->
                val isFailed = task.status.startsWith("Failed")
                val isCancelled = task.status == "Cancelled"
                val cardBorderColor = when {
                    isFailed -> Color(0xFFEF4444).copy(alpha = 0.5f)
                    isCancelled -> Color.Gray.copy(alpha = 0.5f)
                    else -> Color(0xFF0D9488).copy(alpha = 0.3f)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(0.5.dp, cardBorderColor, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp, 54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.DarkGray)
                        ) {
                            if (!task.posterPath.isNullOrEmpty()) {
                                val fullPosterUrl = if (task.posterPath.startsWith("http")) task.posterPath else "https://image.tmdb.org/t/p/w185${task.posterPath}"
                                AsyncImage(
                                    model = fullPosterUrl,
                                    contentDescription = task.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            when {
                                task.status == "Downloading" -> {
                                    LinearProgressIndicator(
                                        progress = task.progress / 100f,
                                        color = Color(0xFF0D9488),
                                        trackColor = Color.DarkGray,
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val downloadedMb = task.downloadedBytes / (1024f * 1024f)
                                    val totalMb = task.totalBytes / (1024f * 1024f)
                                    val progressText = if (totalMb > 0f) {
                                        String.format("Downloading: %d%% (%.1f MB / %.1f MB)", task.progress, downloadedMb, totalMb)
                                    } else {
                                        String.format("Downloading: %d%% (%.1f MB)", task.progress, downloadedMb)
                                    }
                                    Text(progressText, color = Color(0xFF2DD4BF), fontSize = 11.sp)
                                }
                                task.status == "Queued" -> {
                                    Text("Queued...", color = Color.Gray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                                isFailed -> {
                                    Text(
                                        text = "Connection interrupted. Tap retry icon.",
                                        color = Color(0xFFF87171),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                isCancelled -> {
                                    Text("Download Cancelled", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isFailed || isCancelled) {
                                IconButton(onClick = { viewModel.retryVideoDownload(context, task.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry Download", tint = Color(0xFF2DD4BF))
                                }
                            }
                            IconButton(onClick = { viewModel.cancelVideoDownload(task.id) }) {
                                Icon(
                                    imageVector = if (isFailed || isCancelled) Icons.Default.Close else Icons.Default.Cancel,
                                    contentDescription = if (isFailed || isCancelled) "Dismiss" else "Cancel",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // List of Grouped Downloads
        if (groupedMap.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "No downloads",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No offline results found for \"$searchQuery\"" else "Your Offline Library is Empty",
                        color = Color.LightGray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // First list Standalone Movies for quick playback
                val standalone = groupedMap["Standalone Movies"]
                if (standalone != null && standalone.isNotEmpty()) {
                    item {
                        Text(
                            text = "Standalone Movies",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(standalone) { completed ->
                        OfflineVideoItemRow(
                            completed = completed,
                            context = context,
                            scope = scope,
                            viewModel = viewModel,
                            immersivePurple = immersivePurple,
                            onMovieClick = onMovieClick,
                            onExportClick = { movie ->
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val success = exportMovieToDownloadsFolder(context, movie)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Successfully copied to Downloads/HexaroMovies folder!", android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to copy to Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } else {
                                    val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val success = exportMovieToDownloadsFolder(context, movie)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                if (success) {
                                                    android.widget.Toast.makeText(context, "Successfully copied to Downloads/HexaroMovies folder!", android.widget.Toast.LENGTH_LONG).show()
                                                } else {
                                                    android.widget.Toast.makeText(context, "Failed to copy to Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else {
                                        pendingExportMovie = movie
                                        writePermissionLauncher.launch(permission)
                                    }
                                }
                            }
                        )
                    }
                }

                // Next, group TV Shows inside beautiful Expandable Series Folders
                val seriesGroups = groupedMap.filterKeys { it != "Standalone Movies" }
                if (seriesGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Series & Shows",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    seriesGroups.forEach { (seriesName, episodesList) ->
                        item {
                            val isExpanded = expandedSeries[seriesName] ?: false
                            val primaryPoster = episodesList.firstOrNull { !it.posterPath.isNullOrEmpty() }?.posterPath

                            Card(
                                onClick = { expandedSeries[seriesName] = !isExpanded },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) Color(0xFF1F1F22) else Color(0xFF141415)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isExpanded) immersivePurple.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp, 66.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.DarkGray)
                                        ) {
                                            if (!primaryPoster.isNullOrEmpty()) {
                                                val fullPosterUrl = if (primaryPoster.startsWith("http")) primaryPoster else "https://image.tmdb.org/t/p/w185$primaryPoster"
                                                AsyncImage(
                                                    model = fullPosterUrl,
                                                    contentDescription = seriesName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.LiveTv,
                                                    contentDescription = null,
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = seriesName,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${episodesList.size} episodes downloaded",
                                                color = Color(0xFF2DD4BF),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                            contentDescription = "Expand series episodes",
                                            tint = Color.White
                                        )
                                    }

                                    if (isExpanded) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0C0C0D))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            episodesList.forEach { completed ->
                                                val (_, episodeLabel) = getCollectionGroupForMovie(completed.title)
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151517)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = episodeLabel,
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            val fileSizeText = remember(completed.localFileUri) {
                                                                try {
                                                                    val file = java.io.File(completed.localFileUri)
                                                                    if (file.exists() && file.isFile) {
                                                                        val bytes = file.length()
                                                                        val units = arrayOf("B", "KB", "MB", "GB")
                                                                        if (bytes <= 0) "0 B"
                                                                        else {
                                                                            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
                                                                            String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                                                                        }
                                                                    } else {
                                                                        "Unavailable"
                                                                    }
                                                                } catch (e: Exception) {
                                                                    "Unknown size"
                                                                }
                                                            }
                                                            Text(
                                                                text = "Size: $fileSizeText",
                                                                color = Color.Gray,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    try {
                                                                        val file = java.io.File(completed.localFileUri)
                                                                        if (!file.exists()) {
                                                                            android.widget.Toast.makeText(context, "File does not exist locally!", android.widget.Toast.LENGTH_SHORT).show()
                                                                        } else {
                                                                            val authority = "${context.packageName}.fileprovider"
                                                                            val contentUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                                                setDataAndType(contentUri, "video/*")
                                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                                putExtra("title", completed.title)
                                                                                putExtra("displayName", completed.title)
                                                                            }
                                                                            val chooser = android.content.Intent.createChooser(intent, "Watch with External Player")
                                                                            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            context.startActivity(chooser)
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        android.widget.Toast.makeText(context, "Launcher error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.Launch, contentDescription = "Watch External", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                                            val success = exportMovieToDownloadsFolder(context, completed)
                                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                if (success) {
                                                                                    android.widget.Toast.makeText(context, "Saved to Downloads!", android.widget.Toast.LENGTH_SHORT).show()
                                                                                } else {
                                                                                    android.widget.Toast.makeText(context, "Failed to save.", android.widget.Toast.LENGTH_SHORT).show()
                                                                                }
                                                                            }
                                                                        }
                                                                    } else {
                                                                        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                                                val success = exportMovieToDownloadsFolder(context, completed)
                                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                    if (success) {
                                                                                        android.widget.Toast.makeText(context, "Saved to Downloads!", android.widget.Toast.LENGTH_SHORT).show()
                                                                                    } else {
                                                                                        android.widget.Toast.makeText(context, "Failed to save.", android.widget.Toast.LENGTH_SHORT).show()
                                                                                    }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            pendingExportMovie = completed
                                                                            writePermissionLauncher.launch(permission)
                                                                        }
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = "Save to Public Directory", tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                                                            }

                                                            IconButton(
                                                                onClick = {
                                                                    val file = java.io.File(completed.localFileUri)
                                                                    if (!file.exists()) {
                                                                        android.widget.Toast.makeText(context, "File does not exist!", android.widget.Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        viewModel.currentPlaybackUrl = completed.localFileUri
                                                                        viewModel.currentPlaybackTitle = completed.title
                                                                        viewModel.currentPlaybackMovieId = completed.id
                                                                        viewModel.currentPlaybackPosterPath = completed.posterPath
                                                                        onMovieClick(completed.id, "offline_play")
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Inline", tint = Color(0xFF00FFCC), modifier = Modifier.size(24.dp))
                                                            }

                                                            IconButton(onClick = { viewModel.removeDownload(completed.id) }) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete Local Episode", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineVideoItemRow(
    completed: com.example.data.DownloadedMovie,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: MovieViewModel,
    immersivePurple: Color,
    onMovieClick: (Int, String) -> Unit,
    onExportClick: (com.example.data.DownloadedMovie) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F21)),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp, 66.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray)
            ) {
                if (!completed.posterPath.isNullOrEmpty()) {
                    val fullPosterUrl = if (completed.posterPath?.startsWith("http") == true) completed.posterPath else "https://image.tmdb.org/t/p/w185${completed.posterPath}"
                    AsyncImage(model = fullPosterUrl, contentDescription = completed.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = Color.Gray, modifier = Modifier.align(Alignment.Center))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = completed.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val fileSizeText = remember(completed.localFileUri) {
                    try {
                        val file = java.io.File(completed.localFileUri)
                        if (file.exists() && file.isFile) {
                            val bytes = file.length()
                            val units = arrayOf("B", "KB", "MB", "GB")
                            if (bytes <= 0) "0 B"
                            else {
                                val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
                                String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                            }
                        } else {
                            "File unavailable"
                        }
                    } catch (e: Exception) {
                        "Size unknown"
                    }
                }
                Text(text = "Offline Playback Available • $fileSizeText", color = Color(0xFF2DD4BF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        try {
                            val file = java.io.File(completed.localFileUri)
                            if (!file.exists()) {
                                android.widget.Toast.makeText(context, "Downloaded file does not exist locally!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val authority = "${context.packageName}.fileprovider"
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(contentUri, "video/*")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    putExtra("title", completed.title)
                                    putExtra("displayName", completed.title)
                                }
                                val chooser = android.content.Intent.createChooser(intent, "Watch with External Player")
                                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Launch, contentDescription = "Watch External", tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { onExportClick(completed) }) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = "Export to Downloads", tint = Color(0xFFA78BFA), modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = {
                        val file = java.io.File(completed.localFileUri)
                        if (!file.exists()) {
                            android.widget.Toast.makeText(context, "Downloaded file does not exist locally!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.currentPlaybackUrl = completed.localFileUri
                            viewModel.currentPlaybackTitle = completed.title
                            viewModel.currentPlaybackMovieId = completed.id
                            viewModel.currentPlaybackPosterPath = completed.posterPath
                            onMovieClick(completed.id, "offline_play")
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Offline", tint = Color(0xFF00FFCC), modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { viewModel.removeDownload(completed.id) }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Download", tint = Color.Gray, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

fun triggerApkInstall(context: android.content.Context, apkFile: java.io.File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = androidx.core.content.FileProvider.getUriForFile(context, authority, apkFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Failed to start install: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

