package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MovieRepository
import com.example.data.RetrofitClient
import com.example.data.TmdbCastMember
import com.example.data.TmdbMovie
import com.example.data.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.util.Log

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val trending: List<TmdbMovie>,
        val popular: List<TmdbMovie>,
        val topRated: List<TmdbMovie>,
        val trendingTv: List<TmdbMovie> = emptyList(),
        val popularTv: List<TmdbMovie> = emptyList(),
        val topRatedTv: List<TmdbMovie> = emptyList(),
        val genres: List<com.example.data.TmdbGenre> = emptyList()
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val results: List<TmdbMovie>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface DetailUiState {
    object Idle : DetailUiState
    object Loading : DetailUiState
    data class Success(
        val movie: TmdbMovie,
        val credits: List<TmdbCastMember>
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class MovieViewModel(
    application: Application,
    private val repository: MovieRepository
) : AndroidViewModel(application) {

    // --- Home Feeds ---
    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    // --- Search ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    // --- Detail ---
    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    // --- Genre caching & selection ---
    private val _selectedGenreId = MutableStateFlow<Int?>(null)
    val selectedGenreId: StateFlow<Int?> = _selectedGenreId.asStateFlow()

    private val _genreMediaState = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val genreMediaState: StateFlow<List<TmdbMovie>> = _genreMediaState.asStateFlow()

    private val _genresList = MutableStateFlow<List<com.example.data.TmdbGenre>>(emptyList())
    val genresList: StateFlow<List<com.example.data.TmdbGenre>> = _genresList.asStateFlow()

    private val _seasonDetails = MutableStateFlow<Result<com.example.data.TmdbSeasonResponse>?>(null)
    val seasonDetails: StateFlow<Result<com.example.data.TmdbSeasonResponse>?> = _seasonDetails.asStateFlow()

    fun loadTvSeasonDetails(tvId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            _seasonDetails.value = null
            _seasonDetails.value = repository.getTvSeasonDetails(tvId, seasonNumber)
        }
    }

    fun selectGenre(genreId: Int?) {
        _selectedGenreId.value = genreId
        if (genreId == null) {
            _genreMediaState.value = emptyList()
            return
        }
        viewModelScope.launch {
            val movieGenreRes = repository.discoverMoviesByGenre(genreId)
            val tvGenreRes = repository.discoverTvByGenre(genreId)
            val list = mutableListOf<TmdbMovie>()
            movieGenreRes.getOrNull()?.let { list.addAll(it) }
            tvGenreRes.getOrNull()?.let { list.addAll(it) }
            _genreMediaState.value = list.sortedByDescending { it.vote_average ?: 0.0 }
        }
    }

    // --- Player Playback State ---
    var currentPlaybackUrl: String = ""
    var currentPlaybackTitle: String = ""
    var currentPlaybackMovieId: Int = 0
    var currentPlaybackPosterPath: String? = null

    // TV Series Context for smart next episode autoplay
    var isTvSeriesPlay: Boolean = false
    var currentTvSeriesId: Int = 0
    var currentSeasonNumber: Int = 1
    var currentEpisodeNumber: Int = 1
    var maxEpisodeNumber: Int = 1
    var selectedServerIndex: Int = 1

    fun playNextEpisode(): Boolean {
        if (!isTvSeriesPlay) return false
        if (currentEpisodeNumber >= maxEpisodeNumber) {
            return false
        }
        currentEpisodeNumber += 1
        
        val nextUrl = when (selectedServerIndex) {
            1 -> "https://vidsrc-embed.ru/embed/tv/${currentTvSeriesId}/${currentSeasonNumber}-${currentEpisodeNumber}"
            2 -> "https://multiembed.mov/?video_id=${currentTvSeriesId}&tmdb=1&s=${currentSeasonNumber}&e=${currentEpisodeNumber}"
            3 -> "https://www.2embed.cc/embedtv/${currentTvSeriesId}?s=${currentSeasonNumber}&e=${currentEpisodeNumber}"
            4 -> "https://hnembed.cc/tv/${currentTvSeriesId}/${currentSeasonNumber}/${currentEpisodeNumber}"
            5 -> "https://player.videasy.net/tv/${currentTvSeriesId}/${currentSeasonNumber}/${currentEpisodeNumber}?color=ff0000"
            6 -> "https://ezvidapi.com/tv/${currentTvSeriesId}/${currentSeasonNumber}/${currentEpisodeNumber}?provider=vidsrc"
            else -> "https://vidsrc-embed.ru/embed/tv/${currentTvSeriesId}/${currentSeasonNumber}-${currentEpisodeNumber}"
        }
        
        val cleanTitle = currentPlaybackTitle.substringBefore(" - S").substringBefore(" - Season")
        currentPlaybackTitle = "$cleanTitle - Season $currentSeasonNumber Episode $currentEpisodeNumber"
        currentPlaybackUrl = nextUrl
        return true
    }

    private val _activeTab = MutableStateFlow("home")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    // --- Settings Preference Flow ---
    private val prefs = application.getSharedPreferences("movie_app_settings", android.content.Context.MODE_PRIVATE)

    private val _streamingQuality = MutableStateFlow(prefs.getString("streaming_quality", "Auto") ?: "Auto")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _autoplayNext = MutableStateFlow(prefs.getBoolean("autoplay_next", true))
    val autoplayNext: StateFlow<Boolean> = _autoplayNext.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(prefs.getString("pref_language", "English") ?: "English")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _matureContentEnabled = MutableStateFlow(prefs.getBoolean("mature_content_enabled", true))
    val matureContentEnabled: StateFlow<Boolean> = _matureContentEnabled.asStateFlow()

    private val _animeNavEnabled = MutableStateFlow(prefs.getBoolean("anime_nav_enabled", true))
    val animeNavEnabled: StateFlow<Boolean> = _animeNavEnabled.asStateFlow()

    // --- App Update states ---
    private val _updateAvailable = MutableStateFlow<UpdateMetadata?>(null)
    val updateAvailable: StateFlow<UpdateMetadata?> = _updateAvailable.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    val updateCheckStatus: StateFlow<UpdateCheckStatus> = _updateCheckStatus.asStateFlow()

    fun clearUpdateCheckStatus() {
        _updateCheckStatus.value = UpdateCheckStatus.Idle
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
        resetUpdateDownloadState()
    }

    // --- In-App Update states ---
    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0f)
    val updateDownloadProgress: StateFlow<Float> = _updateDownloadProgress.asStateFlow()

    private val _updateDownloadedSize = MutableStateFlow(0L)
    val updateDownloadedSize: StateFlow<Long> = _updateDownloadedSize.asStateFlow()

    private val _updateTotalSize = MutableStateFlow(0L)
    val updateTotalSize: StateFlow<Long> = _updateTotalSize.asStateFlow()

    private val _updateDownloadError = MutableStateFlow<String?>(null)
    val updateDownloadError: StateFlow<String?> = _updateDownloadError.asStateFlow()

    private val _updateDownloadedFile = MutableStateFlow<java.io.File?>(null)
    val updateDownloadedFile: StateFlow<java.io.File?> = _updateDownloadedFile.asStateFlow()

    fun resetUpdateDownloadState() {
        _isDownloadingUpdate.value = false
        _updateDownloadProgress.value = 0f
        _updateDownloadedSize.value = 0L
        _updateTotalSize.value = 0L
        _updateDownloadError.value = null
        _updateDownloadedFile.value = null
    }

    fun startDownloadUpdate(downloadUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingUpdate.value = true
            _updateDownloadProgress.value = 0f
            _updateDownloadedSize.value = 0L
            _updateTotalSize.value = 0L
            _updateDownloadError.value = null
            _updateDownloadedFile.value = null

            try {
                val app = getApplication<Application>()
                val updateDir = java.io.File(app.cacheDir, "updates")
                if (!updateDir.exists()) {
                    updateDir.mkdirs()
                }
                val apkFile = java.io.File(updateDir, "update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Unexpected HTTP code: $response")
                    }

                    val body = response.body ?: throw java.io.IOException("Response body is empty")
                    val contentLength = body.contentLength()
                    _updateTotalSize.value = if (contentLength > 0) contentLength else 0L

                    val inputStream = body.byteStream()
                    val outputStream = java.io.FileOutputStream(apkFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        _updateDownloadedSize.value = totalBytesRead
                        if (contentLength > 0) {
                            _updateDownloadProgress.value = totalBytesRead.toFloat() / contentLength.toFloat()
                        } else {
                            // If content length unknown, simulate incremental progress
                            _updateDownloadProgress.value = (totalBytesRead % 5_000_000).toFloat() / 5_000_000f
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    _updateDownloadedFile.value = apkFile
                    _updateDownloadProgress.value = 1.0f
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateDownloadError.value = e.localizedMessage ?: "Failed to download update."
                _isDownloadingUpdate.value = false
            }
        }
    }

    fun deleteUpdateApk() {
        try {
            val app = getApplication<Application>()
            val updateFile = java.io.File(java.io.File(app.cacheDir, "updates"), "update.apk")
            if (updateFile.exists()) {
                updateFile.delete()
                _updateDownloadedFile.value = null
                Log.d("MovieViewModel", "Successfully deleted update.apk to free up space")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _nollywoodNavEnabled = MutableStateFlow(prefs.getBoolean("nollywood_nav_enabled", false))
    val nollywoodNavEnabled: StateFlow<Boolean> = _nollywoodNavEnabled.asStateFlow()

    private val _bollywoodNavEnabled = MutableStateFlow(prefs.getBoolean("bollywood_nav_enabled", false))
    val bollywoodNavEnabled: StateFlow<Boolean> = _bollywoodNavEnabled.asStateFlow()

    private val _kdramaNavEnabled = MutableStateFlow(prefs.getBoolean("kdrama_nav_enabled", false))
    val kdramaNavEnabled: StateFlow<Boolean> = _kdramaNavEnabled.asStateFlow()

    private val _extraServersEnabled = MutableStateFlow(prefs.getBoolean("extra_servers_enabled", false))
    val extraServersEnabled: StateFlow<Boolean> = _extraServersEnabled.asStateFlow()

    fun updateExtraServersEnabled(enabled: Boolean) {
        _extraServersEnabled.value = enabled
        prefs.edit().putBoolean("extra_servers_enabled", enabled).apply()
    }

    private val _extraFeaturesEnabled = MutableStateFlow(prefs.getBoolean("extra_features_enabled", false))
    val extraFeaturesEnabled: StateFlow<Boolean> = _extraFeaturesEnabled.asStateFlow()

    private val _disableHlsStreaming = MutableStateFlow(prefs.getBoolean("disable_hls_streaming", false))
    val disableHlsStreaming: StateFlow<Boolean> = _disableHlsStreaming.asStateFlow()

    fun updateDisableHlsStreaming(disable: Boolean) {
        _disableHlsStreaming.value = disable
        prefs.edit().putBoolean("disable_hls_streaming", disable).apply()
    }

    val triggerAutoplayNextEpisode = MutableStateFlow(false)

    fun updateExtraFeaturesEnabled(enabled: Boolean) {
        _extraFeaturesEnabled.value = enabled
        prefs.edit().putBoolean("extra_features_enabled", enabled).apply()
    }

    fun updateStreamingQuality(quality: String) {
        _streamingQuality.value = quality
        prefs.edit().putString("streaming_quality", quality).apply()
    }

    fun updateAutoplayNext(autoplay: Boolean) {
        _autoplayNext.value = autoplay
        prefs.edit().putBoolean("autoplay_next", autoplay).apply()
    }

    fun updatePreferredLanguage(lang: String) {
        _preferredLanguage.value = lang
        prefs.edit().putString("pref_language", lang).apply()
    }

    fun updateMatureContentEnabled(enabled: Boolean) {
        _matureContentEnabled.value = enabled
        prefs.edit().putBoolean("mature_content_enabled", enabled).apply()
        loadHomeData()
        resetMoviesState()
        resetTvState()
        resetAnimeState()
        resetCartoonsState()
        resetNollywoodState()
        resetBollywoodState()
        resetKdramaState()
        if (enabled) {
            resetMatureState()
        }
        if (!enabled && _activeTab.value == "mature") {
            _activeTab.value = "home"
        }
    }

    fun updateAnimeNavEnabled(enabled: Boolean) {
        _animeNavEnabled.value = enabled
        prefs.edit().putBoolean("anime_nav_enabled", enabled).apply()
        if (!enabled && _activeTab.value == "anime") {
            _activeTab.value = "home"
        }
    }

    fun updateNollywoodNavEnabled(enabled: Boolean) {
        _nollywoodNavEnabled.value = enabled
        prefs.edit().putBoolean("nollywood_nav_enabled", enabled).apply()
        if (enabled) {
            loadNollywoodPage(isInitial = true)
        } else if (_activeTab.value == "nollywood") {
            _activeTab.value = "home"
        }
    }

    fun updateBollywoodNavEnabled(enabled: Boolean) {
        _bollywoodNavEnabled.value = enabled
        prefs.edit().putBoolean("bollywood_nav_enabled", enabled).apply()
        if (enabled) {
            loadBollywoodPage(isInitial = true)
        } else if (_activeTab.value == "bollywood") {
            _activeTab.value = "home"
        }
    }

    fun updateKdramaNavEnabled(enabled: Boolean) {
        _kdramaNavEnabled.value = enabled
        prefs.edit().putBoolean("kdrama_nav_enabled", enabled).apply()
        if (enabled) {
            loadKdramaPage(isInitial = true)
        } else if (_activeTab.value == "kdrama") {
            _activeTab.value = "home"
        }
    }

    // --- Theme Settings ---
    private val _appTheme = MutableStateFlow(prefs.getString("app_theme_selection", "Cosmic Purple") ?: "Cosmic Purple")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun updateAppTheme(theme: String) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme_selection", theme).apply()
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            repository.clearWatchlist()
            repository.clearAllDownloads()
            repository.clearAllInteractions()
        }
    }

    // --- Dynamic Tracking Flows for "Me" section ---
    val watchedList: StateFlow<List<com.example.data.UserMediaInteraction>> = repository.watchedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentlyWatchingList: StateFlow<List<com.example.data.UserMediaInteraction>> = repository.currentlyWatchingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritesList: StateFlow<List<com.example.data.UserMediaInteraction>> = repository.favoritesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val watchLaterList: StateFlow<List<com.example.data.UserMediaInteraction>> = repository.watchLaterFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val interactiveWatchlist: StateFlow<List<com.example.data.UserMediaInteraction>> = repository.interactiveWatchlistFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getInteractionFlow(id: Int): Flow<com.example.data.UserMediaInteraction?> {
        return repository.getInteractionFlow(id)
    }

    fun toggleInteractionField(
        id: Int,
        title: String,
        posterPath: String?,
        voteAverage: Double?,
        releaseDate: String?,
        mediaType: String?,
        field: String,
        value: Boolean,
        rating: Float? = null,
        progressPercent: Int = 0
    ) {
        viewModelScope.launch {
            repository.updateInteractionToggle(
                id = id,
                title = title,
                posterPath = posterPath,
                voteAverage = voteAverage,
                releaseDate = releaseDate,
                mediaType = mediaType,
                field = field,
                value = value,
                rating = rating,
                progressPercent = progressPercent
            )
        }
    }

    fun saveWatchHistory(
        id: Int,
        title: String,
        posterPath: String?,
        mediaType: String?,
        lastS: Int,
        lastE: Int,
        lastServer: Int,
        isFinished: Boolean,
        maxEp: Int
    ) {
        viewModelScope.launch {
            repository.saveTvWatchProgress(
                id = id,
                title = title,
                posterPath = posterPath,
                mediaType = mediaType,
                season = lastS,
                episode = lastE,
                serverIndex = lastServer,
                finished = isFinished,
                maxEpisode = maxEp
            )
        }
    }

    fun saveCurrentWatchProgress(finished: Boolean) {
        if (isTvSeriesPlay) {
            val titleClean = currentPlaybackTitle.substringBefore(" - S").substringBefore(" - Season")
            saveWatchHistory(
                id = currentTvSeriesId,
                title = titleClean,
                posterPath = currentPlaybackPosterPath,
                mediaType = "tv",
                lastS = currentSeasonNumber,
                lastE = currentEpisodeNumber,
                lastServer = selectedServerIndex,
                isFinished = finished,
                maxEp = maxEpisodeNumber
            )
        } else {
            viewModelScope.launch {
                repository.updateInteractionToggle(
                    id = currentTvSeriesId,
                    title = currentPlaybackTitle,
                    posterPath = currentPlaybackPosterPath,
                    voteAverage = null,
                    releaseDate = null,
                    mediaType = "movie",
                    field = "currentlyWatching",
                    value = !finished
                )
            }
        }
    }

    // --- Watchlist ---
    val watchlist: StateFlow<List<WatchlistItem>> = repository.watchlistFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Local Downloads ---
    val downloadedMovies: StateFlow<List<com.example.data.DownloadedMovie>> = repository.downloadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeDownloadProgress = com.example.data.VideoDownloadManager.downloadProgress
    val activeDownloadStates = com.example.data.VideoDownloadManager.downloadStates
    val downloadTasks = com.example.data.VideoDownloadManager.downloadTasks

    fun startDownloadingVideo(context: android.content.Context, id: Int, title: String, posterPath: String?, url: String, headers: Map<String, String>) {
        val finalId = if (isTvSeriesPlay) {
            (id.toString() + "_" + currentSeasonNumber + "_" + currentEpisodeNumber).hashCode() and 0x7FFFFFFF
        } else {
            id
        }
        com.example.data.VideoDownloadManager.startDownload(
            context = context,
            id = finalId,
            title = title,
            posterPath = posterPath,
            url = url,
            headers = headers
        ) { localFileUriString ->
            recordDownload(finalId, title, posterPath, localFileUriString)
        }
    }

    fun cancelVideoDownload(id: Int) {
        com.example.data.VideoDownloadManager.cancelDownload(id)
    }

    fun retryVideoDownload(context: android.content.Context, id: Int) {
        com.example.data.VideoDownloadManager.retryDownload(context, id)
    }

    fun recordDownload(id: Int, title: String, posterPath: String?, fileUriString: String) {
        viewModelScope.launch {
            repository.saveDownload(id, title, posterPath, fileUriString)
        }
    }

    fun removeDownload(id: Int) {
        viewModelScope.launch {
            try {
                val movie = downloadedMovies.value.find { it.id == id }
                if (movie != null && movie.localFileUri.isNotEmpty()) {
                    val file = java.io.File(movie.localFileUri)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // silent recovery
            }
            repository.removeDownload(id)
        }
    }

    // --- Friendly Error Helper ---
    fun getFriendlyErrorMessage(throwable: Throwable?): String {
        if (throwable == null) return "Failed to fetch content. Please check your network connection."
        val msg = throwable.message ?: ""
        return if (throwable is java.net.UnknownHostException || msg.contains("Unable to resolve host") || msg.contains("No address associated with hostname")) {
            "Failed to fetch content. Please check your internet connection."
        } else if (throwable is java.io.IOException) {
            "Connection error. Could not fetch movies."
        } else {
            "Failed to fetch movies or TV shows. Please try again."
        }
    }

    // --- Paginated Movies ---
    private val _moviesCategory = MutableStateFlow("Popular")
    val moviesCategory: StateFlow<String> = _moviesCategory.asStateFlow()

    private val _moviesList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val moviesList: StateFlow<List<TmdbMovie>> = _moviesList.asStateFlow()

    private val _moviesLoading = MutableStateFlow(false)
    val moviesLoading: StateFlow<Boolean> = _moviesLoading.asStateFlow()

    private var moviesCurrentPage = 1
    private var isMoviesLoadingNextPage = false
    private var isMoviesLastPage = false

    fun setMoviesCategory(category: String) {
        _moviesCategory.value = category
        moviesCurrentPage = 1
        _moviesList.value = emptyList()
        isMoviesLastPage = false
        loadMoviesPage(isInitial = true)
    }

    fun loadNextMoviesPage() {
        if (isMoviesLoadingNextPage || isMoviesLastPage) return
        loadMoviesPage(isInitial = false)
    }

    private fun loadMoviesPage(isInitial: Boolean) {
        if (isInitial) {
            _moviesLoading.value = true
        }
        isMoviesLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_moviesCategory.value) {
                "Popular" -> repository.getPopularMovies(moviesCurrentPage, adultValue)
                "Top Rated" -> repository.getTopRatedMovies(moviesCurrentPage, adultValue)
                "Trending" -> repository.getTrendingMovies(moviesCurrentPage, adultValue)
                "Action" -> repository.discoverMoviesByGenre(28, moviesCurrentPage, adultValue)
                "Adventure" -> repository.discoverMoviesByGenre(12, moviesCurrentPage, adultValue)
                "Animation" -> repository.discoverMoviesByGenre(16, moviesCurrentPage, adultValue)
                "Comedy" -> repository.discoverMoviesByGenre(35, moviesCurrentPage, adultValue)
                "Crime" -> repository.discoverMoviesByGenre(80, moviesCurrentPage, adultValue)
                "Documentary" -> repository.discoverMoviesByGenre(99, moviesCurrentPage, adultValue)
                "Drama" -> repository.discoverMoviesByGenre(18, moviesCurrentPage, adultValue)
                "Family" -> repository.discoverMoviesByGenre(10751, moviesCurrentPage, adultValue)
                "Fantasy" -> repository.discoverMoviesByGenre(14, moviesCurrentPage, adultValue)
                "History" -> repository.discoverMoviesByGenre(36, moviesCurrentPage, adultValue)
                "Horror" -> repository.discoverMoviesByGenre(27, moviesCurrentPage, adultValue)
                "Music" -> repository.discoverMoviesByGenre(10402, moviesCurrentPage, adultValue)
                "Mystery" -> repository.discoverMoviesByGenre(9648, moviesCurrentPage, adultValue)
                "Romance" -> repository.discoverMoviesByGenre(10749, moviesCurrentPage, adultValue)
                "Science Fiction" -> repository.discoverMoviesByGenre(878, moviesCurrentPage, adultValue)
                "Sci-Fi" -> repository.discoverMoviesByGenre(878, moviesCurrentPage, adultValue)
                "Thriller" -> repository.discoverMoviesByGenre(53, moviesCurrentPage, adultValue)
                "TV Movie" -> repository.discoverMoviesByGenre(10770, moviesCurrentPage, adultValue)
                "War" -> repository.discoverMoviesByGenre(10752, moviesCurrentPage, adultValue)
                "Western" -> repository.discoverMoviesByGenre(37, moviesCurrentPage, adultValue)
                else -> repository.getPopularMovies(moviesCurrentPage, adultValue)
            }
            if (isInitial) {
                _moviesLoading.value = false
            }
            isMoviesLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isMoviesLastPage = true
                    } else {
                        _moviesList.value = (_moviesList.value + list).distinctBy { it.id }
                        moviesCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetMoviesState() {
        setMoviesCategory(_moviesCategory.value)
    }

    // --- Paginated TV Shows ---
    private val _tvCategory = MutableStateFlow("Popular")
    val tvCategory: StateFlow<String> = _tvCategory.asStateFlow()

    private val _tvList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val tvList: StateFlow<List<TmdbMovie>> = _tvList.asStateFlow()

    private val _tvLoading = MutableStateFlow(false)
    val tvLoading: StateFlow<Boolean> = _tvLoading.asStateFlow()

    private var tvCurrentPage = 1
    private var isTvLoadingNextPage = false
    private var isTvLastPage = false

    fun setTvCategory(category: String) {
        _tvCategory.value = category
        tvCurrentPage = 1
        _tvList.value = emptyList()
        isTvLastPage = false
        loadTvPage(isInitial = true)
    }

    fun loadNextTvPage() {
        if (isTvLoadingNextPage || isTvLastPage) return
        loadTvPage(isInitial = false)
    }

    private fun loadTvPage(isInitial: Boolean) {
        if (isInitial) {
            _tvLoading.value = true
        }
        isTvLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_tvCategory.value) {
                "Popular" -> repository.getPopularTv(tvCurrentPage, adultValue)
                "Top Rated" -> repository.getTopRatedTv(tvCurrentPage, adultValue)
                "Trending" -> repository.getTrendingTv(tvCurrentPage, adultValue)
                "Action & Adventure" -> repository.discoverTvByGenre(10759, page = tvCurrentPage, includeAdult = adultValue)
                "Animation" -> repository.discoverTvByGenre(16, page = tvCurrentPage, includeAdult = adultValue)
                "Comedy" -> repository.discoverTvByGenre(35, page = tvCurrentPage, includeAdult = adultValue)
                "Crime" -> repository.discoverTvByGenre(80, page = tvCurrentPage, includeAdult = adultValue)
                "Documentary" -> repository.discoverTvByGenre(99, page = tvCurrentPage, includeAdult = adultValue)
                "Drama" -> repository.discoverTvByGenre(18, page = tvCurrentPage, includeAdult = adultValue)
                "Family" -> repository.discoverTvByGenre(10751, page = tvCurrentPage, includeAdult = adultValue)
                "Kids" -> repository.discoverTvByGenre(10762, page = tvCurrentPage, includeAdult = adultValue)
                "Mystery" -> repository.discoverTvByGenre(9648, page = tvCurrentPage, includeAdult = adultValue)
                "News" -> repository.discoverTvByGenre(10763, page = tvCurrentPage, includeAdult = adultValue)
                "Reality" -> repository.discoverTvByGenre(10764, page = tvCurrentPage, includeAdult = adultValue)
                "Sci-Fi & Fantasy" -> repository.discoverTvByGenre(10765, page = tvCurrentPage, includeAdult = adultValue)
                "Soap" -> repository.discoverTvByGenre(10766, page = tvCurrentPage, includeAdult = adultValue)
                "Talk" -> repository.discoverTvByGenre(10767, page = tvCurrentPage, includeAdult = adultValue)
                "War & Politics" -> repository.discoverTvByGenre(10768, page = tvCurrentPage, includeAdult = adultValue)
                "Western" -> repository.discoverTvByGenre(37, page = tvCurrentPage, includeAdult = adultValue)
                else -> repository.getPopularTv(tvCurrentPage, adultValue)
            }
            if (isInitial) {
                _tvLoading.value = false
            }
            isTvLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isTvLastPage = true
                    } else {
                        _tvList.value = (_tvList.value + list).distinctBy { it.id }
                        tvCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetTvState() {
        setTvCategory(_tvCategory.value)
    }

    // --- Paginated Anime ---
    private val _animeCategory = MutableStateFlow("Popular Anime")
    val animeCategory: StateFlow<String> = _animeCategory.asStateFlow()

    private val _animeList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val animeList: StateFlow<List<TmdbMovie>> = _animeList.asStateFlow()

    private val _animeLoading = MutableStateFlow(false)
    val animeLoading: StateFlow<Boolean> = _animeLoading.asStateFlow()

    private var animeCurrentPage = 1
    private var isAnimeLoadingNextPage = false
    private var isAnimeLastPage = false

    fun setAnimeCategory(category: String) {
        _animeCategory.value = category
        animeCurrentPage = 1
        _animeList.value = emptyList()
        isAnimeLastPage = false
        loadAnimePage(isInitial = true)
    }

    fun loadNextAnimePage() {
        if (isAnimeLoadingNextPage || isAnimeLastPage) return
        loadAnimePage(isInitial = false)
    }

    private fun loadAnimePage(isInitial: Boolean) {
        if (isInitial) {
            _animeLoading.value = true
        }
        isAnimeLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_animeCategory.value) {
                "Popular Anime" -> repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue)
                "Shonen" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "210024")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Shonen anime", animeCurrentPage, adultValue)
                }
                "Isekai" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "209971")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Isekai", animeCurrentPage, adultValue)
                }
                "Shojo" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "222216")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Shojo anime", animeCurrentPage, adultValue)
                }
                "Seinen" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "222217")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Seinen anime", animeCurrentPage, adultValue)
                }
                "Mecha" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "11422")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Mecha anime", animeCurrentPage, adultValue)
                }
                "Slice of Life" -> {
                    val r = repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue, keywords = "156050")
                    if (r.isSuccess && r.getOrNull()?.isNotEmpty() == true) r else repository.searchMulti("Slice of life anime", animeCurrentPage, adultValue)
                }
                "Action & Adventure Anime" -> repository.discoverTvGeneral("16,10759", "ja", animeCurrentPage, adultValue)
                "Sci-Fi & Fantasy Anime" -> repository.discoverTvGeneral("16,10765", "ja", animeCurrentPage, adultValue)
                "Comedy Anime" -> repository.discoverTvGeneral("16,35", "ja", animeCurrentPage, adultValue)
                "Drama Anime" -> repository.discoverTvGeneral("16,18", "ja", animeCurrentPage, adultValue)
                "Mystery Anime" -> repository.discoverTvGeneral("16,9648", "ja", animeCurrentPage, adultValue)
                "Kids Anime" -> repository.discoverTvGeneral("16,10762", "ja", animeCurrentPage, adultValue)
                else -> repository.discoverTvGeneral("16", "ja", animeCurrentPage, adultValue)
            }
            if (isInitial) {
                _animeLoading.value = false
            }
            isAnimeLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isAnimeLastPage = true
                    } else {
                        _animeList.value = (_animeList.value + list).distinctBy { it.id }
                        animeCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetAnimeState() {
        setAnimeCategory(_animeCategory.value)
    }

    // --- Paginated Nollywood ---
    private val _nollywoodCategory = MutableStateFlow("Popular")
    val nollywoodCategory: StateFlow<String> = _nollywoodCategory.asStateFlow()

    private val _nollywoodList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val nollywoodList: StateFlow<List<TmdbMovie>> = _nollywoodList.asStateFlow()

    private val _nollywoodLoading = MutableStateFlow(false)
    val nollywoodLoading: StateFlow<Boolean> = _nollywoodLoading.asStateFlow()

    private var nollywoodCurrentPage = 1
    private var isNollywoodLoadingNextPage = false
    private var isNollywoodLastPage = false

    fun setNollywoodCategory(category: String) {
        _nollywoodCategory.value = category
        nollywoodCurrentPage = 1
        _nollywoodList.value = emptyList()
        isNollywoodLastPage = false
        loadNollywoodPage(isInitial = true)
    }

    fun loadNextNollywoodPage() {
        if (isNollywoodLoadingNextPage || isNollywoodLastPage) return
        loadNollywoodPage(isInitial = false)
    }

    private fun loadNollywoodPage(isInitial: Boolean) {
        if (!_nollywoodNavEnabled.value) return
        if (isInitial) {
            _nollywoodLoading.value = true
        }
        isNollywoodLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_nollywoodCategory.value) {
                "Popular" -> repository.discoverMoviesGeneral(genreId = null, page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
                "Movies" -> repository.discoverMoviesGeneral(genreId = null, page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
                "TV Shows" -> repository.discoverTvGeneral(genreId = null, page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
                "Drama" -> repository.discoverMoviesGeneral(genreId = "18", page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
                "Comedy" -> repository.discoverMoviesGeneral(genreId = "35", page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
                else -> repository.discoverMoviesGeneral(genreId = null, page = nollywoodCurrentPage, includeAdult = adultValue, originCountry = "NG")
            }
            if (isInitial) {
                _nollywoodLoading.value = false
            }
            isNollywoodLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isNollywoodLastPage = true
                    } else {
                        _nollywoodList.value = (_nollywoodList.value + list).distinctBy { it.id }
                        nollywoodCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetNollywoodState() {
        setNollywoodCategory(_nollywoodCategory.value)
    }

    // --- Paginated Bollywood ---
    private val _bollywoodCategory = MutableStateFlow("Popular")
    val bollywoodCategory: StateFlow<String> = _bollywoodCategory.asStateFlow()

    private val _bollywoodList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val bollywoodList: StateFlow<List<TmdbMovie>> = _bollywoodList.asStateFlow()

    private val _bollywoodLoading = MutableStateFlow(false)
    val bollywoodLoading: StateFlow<Boolean> = _bollywoodLoading.asStateFlow()

    private var bollywoodCurrentPage = 1
    private var isBollywoodLoadingNextPage = false
    private var isBollywoodLastPage = false

    fun setBollywoodCategory(category: String) {
        _bollywoodCategory.value = category
        bollywoodCurrentPage = 1
        _bollywoodList.value = emptyList()
        isBollywoodLastPage = false
        loadBollywoodPage(isInitial = true)
    }

    fun loadNextBollywoodPage() {
        if (isBollywoodLoadingNextPage || isBollywoodLastPage) return
        loadBollywoodPage(isInitial = false)
    }

    private fun loadBollywoodPage(isInitial: Boolean) {
        if (!_bollywoodNavEnabled.value) return
        if (isInitial) {
            _bollywoodLoading.value = true
        }
        isBollywoodLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_bollywoodCategory.value) {
                "Popular" -> repository.discoverMoviesGeneral(genreId = null, page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                "Action" -> repository.discoverMoviesGeneral(genreId = "28", page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                "Drama" -> repository.discoverMoviesGeneral(genreId = "18", page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                "Comedy" -> repository.discoverMoviesGeneral(genreId = "35", page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                "Romance" -> repository.discoverMoviesGeneral(genreId = "10749", page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                "TV Shows" -> repository.discoverTvGeneral(genreId = null, page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
                else -> repository.discoverMoviesGeneral(genreId = null, page = bollywoodCurrentPage, includeAdult = adultValue, originCountry = "IN")
            }
            if (isInitial) {
                _bollywoodLoading.value = false
            }
            isBollywoodLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isBollywoodLastPage = true
                    } else {
                        _bollywoodList.value = (_bollywoodList.value + list).distinctBy { it.id }
                        bollywoodCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetBollywoodState() {
        setBollywoodCategory(_bollywoodCategory.value)
    }

    // --- Paginated K-Drama ---
    private val _kdramaCategory = MutableStateFlow("Popular")
    val kdramaCategory: StateFlow<String> = _kdramaCategory.asStateFlow()

    private val _kdramaList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val kdramaList: StateFlow<List<TmdbMovie>> = _kdramaList.asStateFlow()

    private val _kdramaLoading = MutableStateFlow(false)
    val kdramaLoading: StateFlow<Boolean> = _kdramaLoading.asStateFlow()

    private var kdramaCurrentPage = 1
    private var isKdramaLoadingNextPage = false
    private var isKdramaLastPage = false

    fun setKdramaCategory(category: String) {
        _kdramaCategory.value = category
        kdramaCurrentPage = 1
        _kdramaList.value = emptyList()
        isKdramaLastPage = false
        loadKdramaPage(isInitial = true)
    }

    fun loadNextKdramaPage() {
        if (isKdramaLoadingNextPage || isKdramaLastPage) return
        loadKdramaPage(isInitial = false)
    }

    private fun loadKdramaPage(isInitial: Boolean) {
        if (!_kdramaNavEnabled.value) return
        if (isInitial) {
            _kdramaLoading.value = true
        }
        isKdramaLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_kdramaCategory.value) {
                "Popular" -> repository.discoverTvGeneral(genreId = "18", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                "Romance" -> repository.discoverTvGeneral(genreId = "18", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                "Drama" -> repository.discoverTvGeneral(genreId = "18", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                "Comedy" -> repository.discoverTvGeneral(genreId = "35", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                "Mystery" -> repository.discoverTvGeneral(genreId = "9648", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                "Historical" -> repository.discoverTvGeneral(genreId = "18", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
                else -> repository.discoverTvGeneral(genreId = "18", originalLanguage = "ko", page = kdramaCurrentPage, includeAdult = adultValue, originCountry = "KR")
            }
            if (isInitial) {
                _kdramaLoading.value = false
            }
            isKdramaLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isKdramaLastPage = true
                    } else {
                        _kdramaList.value = (_kdramaList.value + list).distinctBy { it.id }
                        kdramaCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetKdramaState() {
        setKdramaCategory(_kdramaCategory.value)
    }

    // --- Paginated Cartoons ---
    private val _cartoonsCategory = MutableStateFlow("Popular")
    val cartoonsCategory: StateFlow<String> = _cartoonsCategory.asStateFlow()

    private val _cartoonsList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val cartoonsList: StateFlow<List<TmdbMovie>> = _cartoonsList.asStateFlow()

    private val _cartoonsLoading = MutableStateFlow(false)
    val cartoonsLoading: StateFlow<Boolean> = _cartoonsLoading.asStateFlow()

    private var cartoonsCurrentPage = 1
    private var isCartoonsLoadingNextPage = false
    private var isCartoonsLastPage = false

    fun setCartoonsCategory(category: String) {
        _cartoonsCategory.value = category
        cartoonsCurrentPage = 1
        _cartoonsList.value = emptyList()
        isCartoonsLastPage = false
        loadCartoonsPage(isInitial = true)
    }

    fun loadNextCartoonsPage() {
        if (isCartoonsLoadingNextPage || isCartoonsLastPage) return
        loadCartoonsPage(isInitial = false)
    }

    private fun loadCartoonsPage(isInitial: Boolean) {
        if (isInitial) {
            _cartoonsLoading.value = true
        }
        isCartoonsLoadingNextPage = true
        viewModelScope.launch {
            val adultValue = _matureContentEnabled.value
            val result = when (_cartoonsCategory.value) {
                "Popular" -> repository.discoverMoviesByGenre(16, cartoonsCurrentPage, adultValue)
                "Top Rated" -> repository.discoverMoviesByGenre(16, cartoonsCurrentPage, adultValue)
                "Trending" -> repository.discoverMoviesByGenre(16, cartoonsCurrentPage, adultValue)
                "Action" -> repository.discoverMoviesByGenreString("16,28", cartoonsCurrentPage, adultValue)
                "Adventure" -> repository.discoverMoviesByGenreString("16,12", cartoonsCurrentPage, adultValue)
                "Animation" -> repository.discoverMoviesByGenre(16, cartoonsCurrentPage, adultValue)
                "Comedy" -> repository.discoverMoviesByGenreString("16,35", cartoonsCurrentPage, adultValue)
                "Crime" -> repository.discoverMoviesByGenreString("16,80", cartoonsCurrentPage, adultValue)
                "Documentary" -> repository.discoverMoviesByGenreString("16,99", cartoonsCurrentPage, adultValue)
                "Drama" -> repository.discoverMoviesByGenreString("16,18", cartoonsCurrentPage, adultValue)
                "Family" -> repository.discoverMoviesByGenreString("16,10751", cartoonsCurrentPage, adultValue)
                "Fantasy" -> repository.discoverMoviesByGenreString("16,14", cartoonsCurrentPage, adultValue)
                "History" -> repository.discoverMoviesByGenreString("16,36", cartoonsCurrentPage, adultValue)
                "Horror" -> repository.discoverMoviesByGenreString("16,27", cartoonsCurrentPage, adultValue)
                "Music" -> repository.discoverMoviesByGenreString("16,10402", cartoonsCurrentPage, adultValue)
                "Mystery" -> repository.discoverMoviesByGenreString("16,9648", cartoonsCurrentPage, adultValue)
                "Romance" -> repository.discoverMoviesByGenreString("16,10749", cartoonsCurrentPage, adultValue)
                "Science Fiction" -> repository.discoverMoviesByGenreString("16,878", cartoonsCurrentPage, adultValue)
                "Thriller" -> repository.discoverMoviesByGenreString("16,53", cartoonsCurrentPage, adultValue)
                "TV Movie" -> repository.discoverMoviesByGenreString("16,10770", cartoonsCurrentPage, adultValue)
                "War" -> repository.discoverMoviesByGenreString("16,10752", cartoonsCurrentPage, adultValue)
                "Western" -> repository.discoverMoviesByGenreString("16,37", cartoonsCurrentPage, adultValue)
                else -> repository.discoverMoviesByGenre(16, cartoonsCurrentPage, adultValue)
            }
            if (isInitial) {
                _cartoonsLoading.value = false
            }
            isCartoonsLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isCartoonsLastPage = true
                    } else {
                        _cartoonsList.value = (_cartoonsList.value + list).distinctBy { it.id }
                        cartoonsCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetCartoonsState() {
        setCartoonsCategory(_cartoonsCategory.value)
    }

    // --- Paginated Mature Content ---
    private val _matureCategory = MutableStateFlow("Mature Movies")
    val matureCategory: StateFlow<String> = _matureCategory.asStateFlow()

    private val _matureList = MutableStateFlow<List<TmdbMovie>>(emptyList())
    val matureList: StateFlow<List<TmdbMovie>> = _matureList.asStateFlow()

    private val _matureLoading = MutableStateFlow(false)
    val matureLoading: StateFlow<Boolean> = _matureLoading.asStateFlow()

    private var matureCurrentPage = 1
    private var isMatureLoadingNextPage = false
    private var isMatureLastPage = false

    fun setMatureCategory(category: String) {
        _matureCategory.value = category
        matureCurrentPage = 1
        _matureList.value = emptyList()
        isMatureLastPage = false
        loadMaturePage(isInitial = true)
    }

    fun loadNextMaturePage() {
        if (isMatureLoadingNextPage || isMatureLastPage) return
        loadMaturePage(isInitial = false)
    }

    private fun loadMaturePage(isInitial: Boolean) {
        if (isInitial) {
            _matureLoading.value = true
        }
        isMatureLoadingNextPage = true
        viewModelScope.launch {
            val result = when (_matureCategory.value) {
                "Mature Movies" -> repository.discoverMoviesGeneral(genreId = null, page = matureCurrentPage, includeAdult = true)
                "Mature TV Shows" -> repository.discoverTvGeneral(genreId = null, page = matureCurrentPage, includeAdult = true)
                "Action/Thriller" -> repository.discoverMoviesGeneral(genreId = "28,53", page = matureCurrentPage, includeAdult = true)
                "Horror/Dark" -> repository.discoverMoviesGeneral(genreId = "27", page = matureCurrentPage, includeAdult = true)
                else -> repository.discoverMoviesGeneral(genreId = null, page = matureCurrentPage, includeAdult = true)
            }
            if (isInitial) {
                _matureLoading.value = false
            }
            isMatureLoadingNextPage = false
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        isMatureLastPage = true
                    } else {
                        _matureList.value = (_matureList.value + list).distinctBy { it.id }
                        matureCurrentPage++
                    }
                },
                onFailure = {
                    // silent fallback
                }
            )
        }
    }

    fun resetMatureState() {
        setMatureCategory(_matureCategory.value)
    }

    init {
        deleteUpdateApk()
        checkForUpdates()
        loadHomeData()
        loadMoviesPage(isInitial = true)
        loadTvPage(isInitial = true)
        loadAnimePage(isInitial = true)
        loadCartoonsPage(isInitial = true)
        if (_matureContentEnabled.value) {
            loadMaturePage(isInitial = true)
        }
        if (_nollywoodNavEnabled.value) {
            loadNollywoodPage(isInitial = true)
        }
        if (_bollywoodNavEnabled.value) {
            loadBollywoodPage(isInitial = true)
        }
        if (_kdramaNavEnabled.value) {
            loadKdramaPage(isInitial = true)
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        if (isManual) {
            _updateCheckStatus.value = UpdateCheckStatus.Checking
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = if (isManual) 1 else 3
            var success = false
            
            while (attempt < maxAttempts && !success) {
                try {
                    // Pre-delay on automatic background launch check to ensure network is active
                    if (!isManual) {
                        if (attempt == 0) {
                            kotlinx.coroutines.delay(4000L)
                        } else {
                            kotlinx.coroutines.delay(6000L * attempt)
                        }
                    }
                    
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder()
                        .url("https://raw.githubusercontent.com/Osayuk1/HexaroMoviesApk/main/version.json")
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val json = org.json.JSONObject(body)
                                val remoteVersionCode = json.optInt("versionCode", 0)
                                val remoteVersionName = json.optString("versionName", "")
                                var downloadUrl = json.optString("downloadUrl", "")
                                val changelog = json.optString("changelog", "")
                                
                                val localVersionCode = try {
                                    val app = getApplication<Application>()
                                    val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
                                    androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
                                } catch (e: Exception) {
                                    8 // fallback
                                }
                                
                                // Resolve direct download URL if needed
                                if (downloadUrl.isEmpty() || downloadUrl.endsWith("/releases/latest") || downloadUrl == "https://github.com/Osayuk1/HexaroMoviesApk/releases/latest") {
                                    try {
                                        val apiRequest = okhttp3.Request.Builder()
                                            .url("https://api.github.com/repos/Osayuk1/HexaroMoviesApk/releases/latest")
                                            .header("User-Agent", "HexaroMovies-App")
                                            .build()
                                        client.newCall(apiRequest).execute().use { apiResponse ->
                                            if (apiResponse.isSuccessful) {
                                                val apiBody = apiResponse.body?.string()
                                                if (!apiBody.isNullOrEmpty()) {
                                                    val apiJson = org.json.JSONObject(apiBody)
                                                    val tagName = apiJson.optString("tag_name", "")
                                                    if (tagName.isNotEmpty()) {
                                                        downloadUrl = "https://github.com/Osayuk1/HexaroMoviesApk/releases/download/$tagName/app-release.apk"
                                                    }
                                                }
                                            }
                                        }
                                    } catch (apiEx: Exception) {
                                        apiEx.printStackTrace()
                                    }
                                    
                                    // Fallback if GitHub API fails, rate limited or lacks tag_name
                                    if (downloadUrl.isEmpty() || downloadUrl.endsWith("/releases/latest") || downloadUrl == "https://github.com/Osayuk1/HexaroMoviesApk/releases/latest") {
                                        val tagSuffix = if (remoteVersionCode > 0) remoteVersionCode.toString() else remoteVersionName
                                        downloadUrl = "https://github.com/Osayuk1/HexaroMoviesApk/releases/download/v$tagSuffix/app-release.apk"
                                    }
                                }
                                
                                val metadata = UpdateMetadata(
                                    versionCode = remoteVersionCode,
                                    versionName = remoteVersionName,
                                    downloadUrl = downloadUrl,
                                    changelog = changelog
                                )
                                
                                if (remoteVersionCode > localVersionCode) {
                                    _updateAvailable.value = metadata
                                    if (isManual) {
                                        _updateCheckStatus.value = UpdateCheckStatus.NewUpdateAvailable(metadata)
                                    }
                                } else {
                                    if (isManual) {
                                        _updateCheckStatus.value = UpdateCheckStatus.AlreadyUpToDate("v$remoteVersionName")
                                    }
                                }
                                success = true
                            } else {
                                if (isManual) {
                                    _updateCheckStatus.value = UpdateCheckStatus.Error("Empty server version metadata")
                                }
                            }
                        } else {
                            if (isManual) {
                                _updateCheckStatus.value = UpdateCheckStatus.Error("Network failure (code ${response.code})")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    attempt++
                    if (isManual || attempt >= maxAttempts) {
                        if (isManual) {
                            _updateCheckStatus.value = UpdateCheckStatus.Error(e.message ?: "Connection timed out")
                        }
                    }
                }
            }
        }
    }

     fun loadHomeData() {
        viewModelScope.launch {
            _homeState.value = HomeUiState.Loading
            
            val adultValue = _matureContentEnabled.value
            val trendingRes = repository.getTrendingMovies(includeAdult = adultValue)
            val popularRes = repository.getPopularMovies(includeAdult = adultValue)
            val topRatedRes = repository.getTopRatedMovies(includeAdult = adultValue)

            val trendingTvRes = repository.getTrendingTv(includeAdult = adultValue)
            val popularTvRes = repository.getPopularTv(includeAdult = adultValue)
            val topRatedTvRes = repository.getTopRatedTv(includeAdult = adultValue)

            val movieGenresRes = repository.getMovieGenres()
            val tvGenresRes = repository.getTvGenres()
            val combinedGenres = mutableListOf<com.example.data.TmdbGenre>()
            movieGenresRes.getOrNull()?.let { combinedGenres.addAll(it) }
            tvGenresRes.getOrNull()?.let { tvGen ->
                tvGen.forEach { tg ->
                    if (combinedGenres.none { it.id == tg.id }) {
                        combinedGenres.add(tg)
                    }
                }
            }

            if (combinedGenres.isNotEmpty()) {
                _genresList.value = combinedGenres
            }

            if (trendingRes.isSuccess && popularRes.isSuccess && topRatedRes.isSuccess) {
                _homeState.value = HomeUiState.Success(
                    trending = trendingRes.getOrDefault(emptyList()),
                    popular = popularRes.getOrDefault(emptyList()),
                    topRated = topRatedRes.getOrDefault(emptyList()),
                    trendingTv = trendingTvRes.getOrDefault(emptyList()),
                    popularTv = popularTvRes.getOrDefault(emptyList()),
                    topRatedTv = topRatedTvRes.getOrDefault(emptyList()),
                    genres = combinedGenres
                )
                if (combinedGenres.isNotEmpty() && _selectedGenreId.value == null) {
                    selectGenre(combinedGenres.first().id)
                }
            } else {
                val primaryException = trendingRes.exceptionOrNull()
                    ?: popularRes.exceptionOrNull()
                    ?: topRatedRes.exceptionOrNull()
                    ?: trendingTvRes.exceptionOrNull()
                val errorMsg = getFriendlyErrorMessage(primaryException)
                _homeState.value = HomeUiState.Error(errorMsg)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            _searchState.value = SearchUiState.Idle
            return
        }

        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading

            // 1. Fetch remote search results from API using exact query first!
            repository.searchMulti(trimmedQuery).fold(
                onSuccess = { remoteResults ->
                    if (remoteResults.isNotEmpty()) {
                        // Success! Prioritize the exact search results entered by the user.
                        _searchState.value = SearchUiState.Success(remoteResults.distinctBy { it.id })
                    } else {
                        // 2. Exact search returned nothing. Search extra for corrections from candidates.
                        val candidates = getFuzzyCandidates()
                        val matchedCandidates = candidates.map { candidate ->
                            val score = calculateFuzzyMatchScore(trimmedQuery, candidate.displayTitle)
                            candidate to score
                        }.filter { it.second >= 0.75 } // high confidence typo correction threshold
                         .sortedByDescending { it.second }
                         .map { it.first }

                        if (matchedCandidates.isNotEmpty()) {
                            val correctedTitle = matchedCandidates.first().displayTitle
                            repository.searchMulti(correctedTitle).fold(
                                onSuccess = { correctedRemoteResults ->
                                    val fallbackResults = (matchedCandidates + correctedRemoteResults).distinctBy { it.id }
                                    _searchState.value = SearchUiState.Success(fallbackResults)
                                },
                                onFailure = {
                                    _searchState.value = SearchUiState.Success(matchedCandidates)
                                }
                            )
                        } else {
                            _searchState.value = SearchUiState.Success(emptyList())
                        }
                    }
                },
                onFailure = { error ->
                    // Fallback to local candidates if the remote server failed
                    val candidates = getFuzzyCandidates()
                    val matchedCandidates = candidates.map { candidate ->
                        val score = calculateFuzzyMatchScore(trimmedQuery, candidate.displayTitle)
                        candidate to score
                    }.filter { it.second >= 0.60 } // conservative fallback
                     .sortedByDescending { it.second }
                     .map { it.first }

                    if (matchedCandidates.isNotEmpty()) {
                        _searchState.value = SearchUiState.Success(matchedCandidates)
                    } else {
                        _searchState.value = SearchUiState.Error(error.message ?: "An unknown search error occurred.")
                    }
                }
            )
        }
    }

    fun loadMovieDetails(movieId: Int, mediaType: String) {
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading

            val detailsRes = if (mediaType == "tv") {
                repository.getTvDetails(movieId)
            } else {
                repository.getMovieDetails(movieId)
            }
            val creditsRes = if (mediaType == "tv") {
                repository.getTvCredits(movieId)
            } else {
                repository.getMovieCredits(movieId)
            }

            if (detailsRes.isSuccess) {
                val media = detailsRes.getOrThrow()
                media.media_type = mediaType
                _detailState.value = DetailUiState.Success(
                    movie = media,
                    credits = creditsRes.getOrDefault(emptyList())
                )
            } else {
                _detailState.value = DetailUiState.Error(
                    detailsRes.exceptionOrNull()?.message ?: "Failed to load media details."
                )
            }
        }
    }

    // --- Watchlist Modification ---
    fun toggleWatchlist(movie: TmdbMovie, isWatchlisted: Boolean) {
        viewModelScope.launch {
            if (isWatchlisted) {
                repository.removeFromWatchlist(movie.id)
            } else {
                repository.addToWatchlist(movie)
            }
        }
    }

    // --- Search History Control ---
    val searchHistory: StateFlow<List<com.example.data.SearchHistoryItem>> = repository.searchHistoryFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun recordSearchQuery(query: String) {
        viewModelScope.launch {
            repository.addSearchHistory(query)
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistory(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    // --- library Backup Serialization Structure ---
    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class LibraryBackup(
        val watchlist: List<com.example.data.WatchlistItem>,
        val interactions: List<com.example.data.UserMediaInteraction>
    )

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    fun exportLibraryToJson(): String {
        return try {
            val backup = LibraryBackup(
                watchlist = watchlist.value,
                interactions = watchedList.value + currentlyWatchingList.value + favoritesList.value + watchLaterList.value + interactiveWatchlist.value
            )
            val adapter = moshi.adapter(LibraryBackup::class.java)
            adapter.toJson(backup)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun importLibraryFromJson(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(LibraryBackup::class.java)
            val backup = adapter.fromJson(json) ?: return false
            viewModelScope.launch {
                // Remove existing to avoid constraints if needed, or simply let upsert work
                repository.insertAllWatchlistItems(backup.watchlist)
                repository.insertAllInteractions(backup.interactions)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- Fuzzy Matching / Typo-Tolerance Helpers ---

    private val curatedPopularMedia = listOf(
        TmdbMovie(
            id = 966,
            title = null,
            name = "SpongeBob SquarePants",
            overview = "Deep down in the Pacific Ocean in the subterranean city of Bikini Bottom lives a square yellow sea sponge named SpongeBob SquarePants.",
            poster_path = "/e7TTisZ9v9691X6K39Oii9i70Zk.jpg",
            backdrop_path = "/vS6NqHOn96P1Apt1pUWeY646P4V.jpg",
            first_air_date = "1999-05-01",
            vote_average = 7.6,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 1399,
            title = null,
            name = "Breaking Bad",
            overview = "When Walter White, a New Mexico chemistry teacher, is diagnosed with Stage III cancer and given a prognosis of only two years left to live, he becomes filled with a sense of fearlessness and an unrelenting desire to secure his family's financial future at any cost as he enters the dangerous world of drugs and crime.",
            poster_path = "/ztkUQp6v7zCCb37n6YvCg2Xv52d.jpg",
            backdrop_path = "/900tO0PrZ0Rt6gST68677NDm1gK.jpg",
            first_air_date = "2008-01-20",
            vote_average = 8.9,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 66732,
            title = null,
            name = "Stranger Things",
            overview = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
            poster_path = "/49W06mXmOfmHj9g9696Qv8tSNo.jpg",
            backdrop_path = "/56v2DnL56ZByZ0Y9uY6Wsn9Z8o8.jpg",
            first_air_date = "2016-07-15",
            vote_average = 8.6,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 13997,
            title = null,
            name = "Game of Thrones",
            overview = "Seven noble families fight for control of the mythical land of Westeros. Friction between the houses leads to full-scale war. All while a very ancient evil awakens in the farthest north.",
            poster_path = "/1xsE96v90g5T46B7XbU4N9v2b5.jpg",
            backdrop_path = "/37iW93O4N9C8N0A1hYv56gZ8YvL.jpg",
            first_air_date = "2011-04-17",
            vote_average = 8.4,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 60625,
            title = null,
            name = "Rick and Morty",
            overview = "An animated series on adult-swim about the infinite adventures of Rick, a genius alcoholic and careless scientist, with his grandson Morty, a 14-year-old anxious boy.",
            poster_path = "/gd5pbyG6XgS4e1d1I5S9O1c1O4m.jpg",
            backdrop_path = "/vSBM78x9m6P1b6S9sA9gYv8a1vC.jpg",
            first_air_date = "2013-12-02",
            vote_average = 8.7,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 201855,
            title = null,
            name = "Wednesday",
            overview = "Wednesday Addams, a student at Nevermore Academy, attempts to master her emerging psychic ability, thwart a monstrous killing spree, and solve the supernatural mystery that embroiled her parents 25 years ago.",
            poster_path = "/9PFznvZO47v96v96T7B0f6B7w7.jpg",
            backdrop_path = "/iH7zY5g6C8bO0b0A1hU5N6Yv78Z.jpg",
            first_air_date = "2022-11-23",
            vote_average = 8.5,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 76600,
            title = "Avatar: The Way of Water",
            name = null,
            overview = "Set more than a decade after the events of the first film, learn the story of the Sully family, the trouble that follows them, the lengths they go to keep each other safe, the battles they must fight to stay alive, and the tragedies they endure.",
            poster_path = "/t6zNZv4YhzX03gMa66b3fP7vWpI.jpg",
            backdrop_path = "/8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            release_date = "2022-12-14",
            vote_average = 7.7,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 19995,
            title = "Avatar",
            name = null,
            overview = "In the 22nd century, a paraplegic Marine is dispatched to the moon Pandora on a unique mission, but becomes torn between following orders and protecting an alien civilization.",
            poster_path = "/kye96v90g5T46B7XbU4N9v2b5.jpg",
            backdrop_path = "/8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            release_date = "2009-12-15",
            vote_average = 7.6,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 634649,
            title = "Spider-Man: No Way Home",
            name = null,
            overview = "Peter Parker is unmasked and no longer able to separate his normal life from the high-stakes of being a super-hero. When he asks for help from Doctor Strange, the stakes become even more dangerous, forcing him to discover what it truly means to be Spider-Man.",
            poster_path = "/uJv6v2d2Cmevuds78eY8v8B9.jpg",
            backdrop_path = "/14Iv6v8cOMUoN9m0o8X7eY4E.jpg",
            release_date = "2021-12-15",
            vote_average = 8.0,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 155,
            title = "The Dark Knight",
            name = null,
            overview = "Batman raises the stakes in his war on crime. With the help of Lt. Jim Gordon and District Attorney Harvey Dent, Batman sets out to dismantle the remaining criminal organizations that plague the streets. The partnership proves to be effective, but they soon find themselves prey to a reign of chaos unleashed by a rising criminal mastermind known to the terrified citizens of Gotham as the Joker.",
            poster_path = "/qJ2tWGB2g8gI3nZc7R8eY8u2u6K.jpg",
            backdrop_path = "/nMWp8cLvYvD3Bcl6C8Z8w7N7V9.jpg",
            release_date = "2008-07-16",
            vote_average = 8.5,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 157336,
            title = "Interstellar",
            name = null,
            overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to surpass the limitations on human space travel and conquer the vast distances involved in an interstellar voyage.",
            poster_path = "/gEU2Qv6vud7eYv2clv6M7gO6L.jpg",
            backdrop_path = "/nMWp8cLvYvD3Bcl6C8Z8w7N7V9.jpg",
            release_date = "2014-11-05",
            vote_average = 8.4,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 27205,
            title = "Inception",
            name = null,
            overview = "Cobb, a skilled thief who is absolute best in the dangerous art of extraction, steals valuable secrets from deep within the subconscious during the dream state, when the mind is at its most vulnerable. Cobb's rare ability has made him a coveted player in this treacherous new world of corporate espionage, but it has also made him an international fugitive.",
            poster_path = "/ljsO6v90g5T46B7XbU4N9v2b5.jpg",
            backdrop_path = "/f8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            release_date = "2010-07-15",
            vote_average = 8.4,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 808,
            title = "Shrek",
            name = null,
            overview = "It ain't easy bein' green -- especially if you're a likable ogre named Shrek. On a mission to retrieve a gorgeous princess from the clutches of a fire-breathing dragon, Shrek teams up with a wisecracking donkey.",
            poster_path = "/dy6JUor27vNSv6cx8clvSg0asvO.jpg",
            backdrop_path = "/3gU9Xb0Y3Ncb56clvY8W8vL82qP.jpg",
            release_date = "2001-05-18",
            vote_average = 7.7,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 603,
            title = "The Matrix",
            name = null,
            overview = "Set in the 22nd century, The Matrix tells the story of a computer hacker who joins a group of underground insurgents fighting the vast and powerful computers who now rule the world.",
            poster_path = "/f8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            backdrop_path = "/nMWp8cLvYvD3Bcl6C8Z8w7N7V9.jpg",
            release_date = "1999-03-30",
            vote_average = 8.2,
            media_type = "movie"
        ),
        TmdbMovie(
            id = 1668,
            title = null,
            name = "Friends",
            overview = "Six young people from New York find themselves navigating independent adult lives, love, and careers.",
            poster_path = "/f8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            backdrop_path = "/3gU9Xb0Y3Ncb56clvY8W8vL82qP.jpg",
            first_air_date = "1994-09-22",
            vote_average = 8.5,
            media_type = "tv"
        ),
        TmdbMovie(
            id = 2316,
            title = null,
            name = "The Office",
            overview = "A mockumentary on a group of typical office workers, where the workday consists of ego clashes, inappropriate behavior, and tedium.",
            poster_path = "/f8Yv56clvYvH5N6cZ9v8uP0b78YV.jpg",
            backdrop_path = "/3gU9Xb0Y3Ncb56clvY8W8vL82qP.jpg",
            first_air_date = "2005-03-24",
            vote_average = 8.6,
            media_type = "tv"
        )
    )

    private fun getFuzzyCandidates(): List<TmdbMovie> {
        val candidates = mutableListOf<TmdbMovie>()
        candidates.addAll(curatedPopularMedia)

        val home = _homeState.value
        if (home is HomeUiState.Success) {
            candidates.addAll(home.trending)
            candidates.addAll(home.popular)
            candidates.addAll(home.topRated)
            candidates.addAll(home.trendingTv)
            candidates.addAll(home.popularTv)
            candidates.addAll(home.topRatedTv)
        }

        watchlist.value.forEach { watch ->
            candidates.add(
                TmdbMovie(
                    id = watch.id,
                    title = if (watch.mediaType == "movie") watch.title else null,
                    name = if (watch.mediaType == "tv") watch.title else null,
                    overview = null,
                    poster_path = watch.posterPath,
                    backdrop_path = null,
                    release_date = if (watch.mediaType == "movie") watch.releaseDate else null,
                    first_air_date = if (watch.mediaType == "tv") watch.releaseDate else null,
                    vote_average = watch.voteAverage,
                    media_type = watch.mediaType
                )
            )
        }

        return candidates.distinctBy { it.id }
    }

    private fun getLevenshteinDistance(s: String, t: String): Int {
        if (s == t) return 0
        if (s.isEmpty()) return t.length
        if (t.isEmpty()) return s.length

        val d = Array(s.length + 1) { IntArray(t.length + 1) }

        for (i in 0..s.length) d[i][0] = i
        for (j in 0..t.length) d[0][j] = j

        for (i in 1..s.length) {
            for (j in 1..t.length) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                d[i][j] = minOf(
                    minOf(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )
            }
        }
        return d[s.length][t.length]
    }

    private fun getDiceCoefficient(s1: String, s2: String): Double {
        val nGrams1 = getCharacterBigrams(s1)
        val nGrams2 = getCharacterBigrams(s2)
        if (nGrams1.isEmpty() || nGrams2.isEmpty()) return 0.0

        var matches = 0
        val tempNGrams2 = nGrams2.toMutableList()
        for (gram in nGrams1) {
            if (tempNGrams2.remove(gram)) {
                matches++
            }
        }
        return (2.0 * matches) / (nGrams1.size + nGrams2.size)
    }

    private fun getCharacterBigrams(s: String): List<String> {
        val bigrams = mutableListOf<String>()
        val clean = s.filter { it.isLetterOrDigit() }
        if (clean.length < 2) return emptyList()
        for (i in 0 until clean.length - 1) {
            bigrams.add(clean.substring(i, i + 2))
        }
        return bigrams
    }

    private fun getJaroWinklerSimilarity(s1: String, s2: String): Double {
        val jaro = getJaroSimilarity(s1, s2)
        if (jaro < 0.6) return jaro

        var prefixLength = 0
        val maxPrefix = minOf(4, minOf(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) {
                prefixLength++
            } else {
                break
            }
        }
        return jaro + prefixLength * 0.1 * (1.0 - jaro)
    }

    private fun getJaroSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = maxOf(0, (maxOf(len1, len2) / 2) - 1)

        val matches1 = BooleanArray(len1)
        val matches2 = BooleanArray(len2)

        var commonMatches = 0
        for (i in 0 until len1) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(len2 - 1, i + matchDistance)
            for (j in start..end) {
                if (!matches2[j] && s1[i] == s2[j]) {
                    matches1[i] = true
                    matches2[j] = true
                    commonMatches++
                    break
                }
            }
        }

        if (commonMatches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (matches1[i]) {
                while (!matches2[k]) {
                    k++
                }
                if (s1[i] != s2[k]) {
                    transpositions++
                }
                k++
            }
        }

        val m = commonMatches.toDouble()
        val t = (transpositions / 2).toDouble()

        return (m / len1 + m / len2 + (m - t) / m) / 3.0
    }

    private fun calculateFuzzyMatchScore(query: String, title: String): Double {
        val qClean = query.lowercase().trim()
        val tClean = title.lowercase().trim()

        if (qClean.isEmpty() || tClean.isEmpty()) return 0.0

        // 1. Direct contains or exact equality
        if (tClean == qClean) return 1.0

        // Under 4 characters, we should match exactly or as starting word, not loose substrings
        if (qClean.length < 4) {
            val words = tClean.split("\\s+".toRegex())
            if (words.any { it.startsWith(qClean) }) {
                return 0.9
            }
            if (tClean.contains(qClean)) {
                return 0.8
            }
            return 0.0
        }

        if (tClean.contains(qClean)) {
            return 0.8 + (qClean.length.toDouble() / tClean.length.toDouble()) * 0.2
        }

        // 2. Token Sort Match
        val qWords = qClean.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val tWords = tClean.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val qSorted = qWords.sorted().joinToString(" ")
        val tSorted = tWords.sorted().joinToString(" ")
        if (qSorted == tSorted) return 0.95

        // 3. Sørensen-Dice Coefficient using character bigrams (Very strong for typo-toleration)
        val diceScore = getDiceCoefficient(qClean, tClean)

        // 4. Token-level Edit Distance (normalized per word)
        var wordMatchSum = 0.0
        for (qWord in qWords) {
            var bestWordSimilarity = 0.0
            for (tWord in tWords) {
                val dist = getLevenshteinDistance(qWord, tWord)
                val maxLen = maxOf(qWord.length, tWord.length)
                val wordSim = if (maxLen > 0) {
                    val sim = 1.0 - (dist.toDouble() / maxLen.toDouble())
                    // If word length is short and they don't match exactly, heavily penalize false positives
                    if (maxLen < 4 && dist > 0) sim * 0.5 else sim
                } else 0.0
                if (wordSim > bestWordSimilarity) {
                    bestWordSimilarity = wordSim
                }
            }
            wordMatchSum += bestWordSimilarity
        }
        val tokenMatchRatio = if (qWords.isNotEmpty()) wordMatchSum / qWords.size else 0.0

        // 5. Jaro-Winkler Similarity
        val jaroWinklerScore = getJaroWinklerSimilarity(qClean, tClean)

        // Return a weighted score prioritizing diceScore, token similarities, or Jaro-Winkler score
        val finalScore = maxOf(diceScore, tokenMatchRatio, jaroWinklerScore)
        return if (qClean.length < 5) {
            // For short queries, require strong word matches, not loose overall strings
            if (tokenMatchRatio >= 0.8 || tClean.contains(qClean)) finalScore else 0.0
        } else {
            finalScore
        }
    }

    // Factory Class
    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = AppDatabase.getDatabase(application)
                    val repository = MovieRepository(
                        tmdbService = RetrofitClient.tmdbService,
                        watchlistDao = database.watchlistDao(),
                        downloadedMovieDao = database.downloadedMovieDao(),
                        userMediaInteractionDao = database.userMediaInteractionDao(),
                        searchHistoryDao = database.searchHistoryDao()
                    )
                    @Suppress("UNCHECKED_CAST")
                    return MovieViewModel(application, repository) as T
                }
            }
    }
}

data class UpdateMetadata(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

sealed interface UpdateCheckStatus {
    object Idle : UpdateCheckStatus
    object Checking : UpdateCheckStatus
    data class NewUpdateAvailable(val metadata: UpdateMetadata) : UpdateCheckStatus
    data class AlreadyUpToDate(val currentVersion: String) : UpdateCheckStatus
    data class Error(val errorMessage: String) : UpdateCheckStatus
}
