package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.BuildConfig

// --- TMDB Model Classes ---
data class TmdbMovieResponse(
    val results: List<TmdbMovie>
)

data class TmdbMovie(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double?,
    val genre_ids: List<Int>? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre>? = null,
    var media_type: String? = null,
    val number_of_seasons: Int? = null,
    val number_of_episodes: Int? = null,
    val seasons: List<TmdbSeasonDetail>? = null,
    val adult: Boolean? = false
) {
    val displayTitle: String
        get() = title ?: name ?: "Untitled"

    val displayDate: String
        get() = release_date ?: first_air_date ?: ""

    val isTvShow: Boolean
        get() = media_type == "tv" || (!first_air_date.isNullOrEmpty() && release_date.isNullOrEmpty()) || title == null

    fun getPosterUrl(): String {
        return if (poster_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w500$poster_path"
    }
    fun getBackdropUrl(): String {
        return if (backdrop_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w780$backdrop_path"
    }
}

data class TmdbGenre(
    val id: Int,
    val name: String
)

data class TmdbGenreResponse(
    val genres: List<TmdbGenre>
)

data class TmdbSeasonDetail(
    val id: Int,
    val name: String?,
    val season_number: Int,
    val episode_count: Int?,
    val poster_path: String?,
    val overview: String?,
    val vote_average: Double?
) {
    fun getPosterUrl(): String {
        return if (poster_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w185$poster_path"
    }
}

data class TmdbSeasonResponse(
    val episodes: List<TmdbEpisode>
)

data class TmdbEpisode(
    val id: Int,
    val name: String?,
    val episode_number: Int,
    val still_path: String?,
    val overview: String?,
    val vote_average: Double? = null,
    val air_date: String? = null
) {
    fun getStillUrl(quality: String = "w185"): String {
        return if (still_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/$quality$still_path"
    }
}

data class TmdbCreditsResponse(
    val cast: List<TmdbCastMember>
)

data class TmdbCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profile_path: String?
) {
    fun getProfileUrl(): String {
        return if (profile_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w185$profile_path"
    }
}

// --- Retrofit Service Interfaces ---
interface TmdbService {
    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("trending/tv/day")
    suspend fun getTrendingTv(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null
    ): TmdbMovieResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int
    ): TmdbMovie

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int
    ): TmdbMovie

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Path("movie_id") movieId: Int
    ): TmdbCreditsResponse

    @GET("tv/{tv_id}/credits")
    suspend fun getTvCredits(
        @Path("tv_id") tvId: Int
    ): TmdbCreditsResponse

    @GET("genre/movie/list")
    suspend fun getMovieGenres(): TmdbGenreResponse

    @GET("genre/tv/list")
    suspend fun getTvGenres(): TmdbGenreResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") genreId: String?,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("with_origin_country") originCountry: String? = null
    ): TmdbMovieResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") genreId: String?,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean? = null,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("with_keywords") keywords: String? = null,
        @Query("with_origin_country") originCountry: String? = null
    ): TmdbMovieResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonResponse
}

// --- Retrofit & OkHttp Clients ---
object RetrofitClient {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Interceptor to inject TMDB Bearer Token and accept headers
    private val tmdbAuthInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = BuildConfig.TMDB_API_KEY
        
        val requestBuilder = original.newBuilder()
            .header("accept", "application/json")
        
        if (token.isNotEmpty() && token != "MY_GEMINI_API_KEY" && !token.startsWith("MY_")) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        chain.proceed(requestBuilder.build())
    }

    private val tmdbOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(tmdbAuthInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val tmdbService: TmdbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(tmdbOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbService::class.java)
    }
}
