package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

class MovieRepository(
    private val tmdbService: TmdbService,
    private val watchlistDao: WatchlistDao,
    private val downloadedMovieDao: DownloadedMovieDao,
    private val userMediaInteractionDao: UserMediaInteractionDao,
    private val searchHistoryDao: SearchHistoryDao
) {
    // --- Search History ---
    val searchHistoryFlow: Flow<List<SearchHistoryItem>> = searchHistoryDao.getSearchHistoryFlow()

    suspend fun addSearchHistory(query: String) {
        if (query.trim().isEmpty()) return
        searchHistoryDao.deleteSearchHistoryByQuery(query.trim()) // Remove duplicates to bubble to top
        searchHistoryDao.insertSearchHistory(SearchHistoryItem(query = query.trim()))
    }

    suspend fun deleteSearchHistory(query: String) {
        searchHistoryDao.deleteSearchHistoryByQuery(query.trim())
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearSearchHistory()
    }

    // --- Import / Export Support ---
    suspend fun getAllInteractionsList(): List<UserMediaInteraction> {
        // We'll collect the current state once or make a direct query. Exposing a flow is already done,
        // we can also add a Query or retrieve the list here. Let's add direct list return.
        return emptyList() // Or just get from flow dynamically or query. Let's add query later if needed, but we can do it via Dao or ViewModel collecting flow.
    }

    suspend fun insertAllInteractions(list: List<UserMediaInteraction>) {
        list.forEach {
            userMediaInteractionDao.insertInteraction(it)
        }
    }

    suspend fun insertAllWatchlistItems(list: List<WatchlistItem>) {
        list.forEach {
            watchlistDao.insertItem(it)
        }
    }
    // --- User Media Interactions (Watched, Favorite, Watch Later, currently Watching, etc) ---
    val watchedFlow: Flow<List<UserMediaInteraction>> = userMediaInteractionDao.getWatchedFlow()
    val currentlyWatchingFlow: Flow<List<UserMediaInteraction>> = userMediaInteractionDao.getCurrentlyWatchingFlow()
    val favoritesFlow: Flow<List<UserMediaInteraction>> = userMediaInteractionDao.getFavoritesFlow()
    val watchLaterFlow: Flow<List<UserMediaInteraction>> = userMediaInteractionDao.getWatchLaterFlow()
    val interactiveWatchlistFlow: Flow<List<UserMediaInteraction>> = userMediaInteractionDao.getWatchlistFlow()

    fun getInteractionFlow(id: Int): Flow<UserMediaInteraction?> {
        return userMediaInteractionDao.getInteractionFlowById(id)
    }

    suspend fun saveTvWatchProgress(
        id: Int,
        title: String,
        posterPath: String?,
        mediaType: String?,
        season: Int,
        episode: Int,
        serverIndex: Int,
        finished: Boolean,
        maxEpisode: Int
    ) {
        val existing = userMediaInteractionDao.getInteractionById(id)
        val updated = existing?.copy(
            isCurrentlyWatching = true,
            lastSeasonWatched = season,
            lastEpisodeWatched = episode,
            lastServerUsedIndex = serverIndex,
            isEpisodeFinished = finished,
            lastEpisodeMaxCount = maxEpisode,
            mediaType = mediaType ?: "tv",
            addedAt = System.currentTimeMillis()
        ) ?: UserMediaInteraction(
            id = id,
            title = title,
            posterPath = posterPath,
            voteAverage = null,
            releaseDate = null,
            mediaType = mediaType ?: "tv",
            isCurrentlyWatching = true,
            lastSeasonWatched = season,
            lastEpisodeWatched = episode,
            lastServerUsedIndex = serverIndex,
            isEpisodeFinished = finished,
            lastEpisodeMaxCount = maxEpisode,
            addedAt = System.currentTimeMillis()
        )
        userMediaInteractionDao.insertInteraction(updated)
    }

    suspend fun updateInteractionToggle(
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
        val existing = userMediaInteractionDao.getInteractionById(id)
        val updated = existing?.copy(
            isWatched = if (field == "watched") value else existing.isWatched,
            isCurrentlyWatching = if (field == "currentlyWatching") value else existing.isCurrentlyWatching,
            isFavorite = if (field == "favorite") value else existing.isFavorite,
            isWatchlist = if (field == "watchlist") value else existing.isWatchlist,
            isWatchLater = if (field == "watchLater") value else existing.isWatchLater,
            rating = if (field == "rating") rating else existing.rating,
            progressPercent = if (field == "progress") progressPercent else existing.progressPercent,
            addedAt = System.currentTimeMillis()
        ) ?: UserMediaInteraction(
            id = id,
            title = title,
            posterPath = posterPath,
            voteAverage = voteAverage,
            releaseDate = releaseDate,
            mediaType = mediaType ?: "movie",
            isWatched = if (field == "watched") value else false,
            isCurrentlyWatching = if (field == "currentlyWatching") value else false,
            isFavorite = if (field == "favorite") value else false,
            isWatchlist = if (field == "watchlist") value else false,
            isWatchLater = if (field == "watchLater") value else false,
            rating = if (field == "rating") rating else null,
            progressPercent = if (field == "progress") progressPercent else 0,
            addedAt = System.currentTimeMillis()
        )
        userMediaInteractionDao.insertInteraction(updated)
    }

    suspend fun clearAllInteractions() {
        userMediaInteractionDao.clearAll()
    }

    // --- Local Downloaded Methods ---
    val downloadsFlow: Flow<List<DownloadedMovie>> = downloadedMovieDao.getDownloadsFlow()

    suspend fun saveDownload(id: Int, title: String, posterPath: String?, fileUriString: String) {
        val download = DownloadedMovie(
            id = id,
            title = title,
            posterPath = posterPath,
            localFileUri = fileUriString
        )
        downloadedMovieDao.insertDownload(download)
    }

    suspend fun removeDownload(id: Int) {
        downloadedMovieDao.deleteDownloadById(id)
    }

    // --- Local Watchlist Methods ---
    val watchlistFlow: Flow<List<WatchlistItem>> = watchlistDao.getWatchlistFlow()

    fun isWatchlistedFlow(id: Int): Flow<Boolean> = watchlistDao.isWatchlistedFlow(id)

    suspend fun addToWatchlist(movie: TmdbMovie) {
        val item = WatchlistItem(
            id = movie.id,
            title = movie.displayTitle,
            posterPath = movie.poster_path,
            voteAverage = movie.vote_average,
            releaseDate = movie.displayDate,
            mediaType = if (movie.isTvShow) "tv" else "movie"
        )
        watchlistDao.insertItem(item)
    }

    suspend fun removeFromWatchlist(id: Int) {
        watchlistDao.deleteItemById(id)
    }

    suspend fun clearWatchlist() {
        watchlistDao.clearAll()
    }

    suspend fun clearAllDownloads() {
        downloadedMovieDao.clearAll()
    }

    // --- Network API Methods ---
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> withCache(key: String, block: suspend () -> Result<T>): Result<T> {
        val cached = memoryCache[key]
        if (cached != null) {
            return Result.success(cached as T)
        }
        return block().onSuccess { memoryCache[key] = it }
    }

    suspend fun getTrendingMovies(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("trending_movies_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getTrendingMovies(page, includeAdult).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun getTrendingTv(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("trending_tv_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getTrendingTv(page, includeAdult).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun getPopularMovies(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("popular_movies_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getPopularMovies(page, includeAdult).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun getPopularTv(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("popular_tv_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getPopularTv(page, includeAdult).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun getTopRatedMovies(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("top_rated_movies_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getTopRatedMovies(page, includeAdult).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun getTopRatedTv(page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("top_rated_tv_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.getTopRatedTv(page, includeAdult).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun searchMulti(query: String, page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("search_q${query}_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.searchMulti(query, page, includeAdult).results.filter { it.media_type == "movie" || it.media_type == "tv" }
        }
    }

    suspend fun getMovieDetails(movieId: Int): Result<TmdbMovie> = withCache("movie_details_${movieId}") {
        runCatching {
            tmdbService.getMovieDetails(movieId).apply { media_type = "movie" }
        }
    }

    suspend fun getTvDetails(tvId: Int): Result<TmdbMovie> = withCache("tv_details_${tvId}") {
        runCatching {
            tmdbService.getTvDetails(tvId).apply { media_type = "tv" }
        }
    }

    suspend fun getMovieCredits(movieId: Int): Result<List<TmdbCastMember>> = withCache("movie_credits_${movieId}") {
        runCatching {
            tmdbService.getMovieCredits(movieId).cast
        }
    }

    suspend fun getTvCredits(tvId: Int): Result<List<TmdbCastMember>> = withCache("tv_credits_${tvId}") {
        runCatching {
            tmdbService.getTvCredits(tvId).cast
        }
    }

    suspend fun getMovieGenres(): Result<List<TmdbGenre>> = withCache("movie_genres") {
        runCatching {
            tmdbService.getMovieGenres().genres
        }
    }

    suspend fun getTvGenres(): Result<List<TmdbGenre>> = withCache("tv_genres") {
        runCatching {
            tmdbService.getTvGenres().genres
        }
    }

    suspend fun discoverMoviesByGenre(genreId: Int, page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("discover_movies_g${genreId}_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.discoverMovies(genreId.toString(), page, includeAdult).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun discoverMoviesByGenreString(genres: String, page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("discover_movies_gstring${genres}_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.discoverMovies(genres, page, includeAdult).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun discoverTvByGenre(genreId: Int, originalLanguage: String? = null, page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("discover_tv_g${genreId}_l${originalLanguage}_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.discoverTv(genreId.toString(), originalLanguage, page, includeAdult).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun discoverTvByGenreString(genres: String, originalLanguage: String? = null, page: Int = 1, includeAdult: Boolean? = null): Result<List<TmdbMovie>> = withCache("discover_tv_gstring${genres}_l${originalLanguage}_p${page}_a${includeAdult}") {
        runCatching {
            tmdbService.discoverTv(genres, originalLanguage, page, includeAdult).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun discoverMoviesGeneral(genreId: String?, page: Int = 1, includeAdult: Boolean? = null, sortBy: String? = "popularity.desc", originalLanguage: String? = null, originCountry: String? = null): Result<List<TmdbMovie>> = withCache("discover_movies_general_g${genreId}_p${page}_a${includeAdult}_s${sortBy}_l${originalLanguage}_c${originCountry}") {
        runCatching {
            tmdbService.discoverMovies(genreId, page, includeAdult, sortBy, originalLanguage, originCountry).results.map { it.apply { media_type = "movie" } }
        }
    }

    suspend fun discoverTvGeneral(genreId: String?, originalLanguage: String? = null, page: Int = 1, includeAdult: Boolean? = null, sortBy: String? = "popularity.desc", keywords: String? = null, originCountry: String? = null): Result<List<TmdbMovie>> = withCache("discover_tv_general_g${genreId}_l${originalLanguage}_p${page}_a${includeAdult}_s${sortBy}_k${keywords}_c${originCountry}") {
        runCatching {
            tmdbService.discoverTv(genreId, originalLanguage, page, includeAdult, sortBy, keywords, originCountry).results.map { it.apply { media_type = "tv" } }
        }
    }

    suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<TmdbSeasonResponse> = withCache("tv_season_details_${tvId}_s${seasonNumber}") {
        runCatching {
            tmdbService.getTvSeasonDetails(tvId, seasonNumber)
        }
    }
}
