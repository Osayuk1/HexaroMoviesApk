package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Entity
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlist_items")
data class WatchlistItem(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double?,
    val releaseDate: String?,
    val mediaType: String? = "movie",
    val addedAt: Long = System.currentTimeMillis()
) {
    fun getPosterUrl(): String {
        return if (posterPath.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
    }
}

@Entity(tableName = "downloaded_movies")
data class DownloadedMovie(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val localFileUri: String,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun getPosterUrl(): String {
        return if (posterPath.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
    }
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_items ORDER BY addedAt DESC")
    fun getWatchlistFlow(): Flow<List<WatchlistItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE id = :id LIMIT 1)")
    fun isWatchlistedFlow(id: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE id = :id LIMIT 1)")
    suspend fun isWatchlisted(id: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WatchlistItem)

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("DELETE FROM watchlist_items")
    suspend fun clearAll()
}

@Dao
interface DownloadedMovieDao {
    @Query("SELECT * FROM downloaded_movies ORDER BY downloadedAt DESC")
    fun getDownloadsFlow(): Flow<List<DownloadedMovie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(movie: DownloadedMovie)

    @Query("DELETE FROM downloaded_movies WHERE id = :id")
    suspend fun deleteDownloadById(id: Int)

    @Query("DELETE FROM downloaded_movies")
    suspend fun clearAll()
}

@Entity(tableName = "user_media_interactions")
data class UserMediaInteraction(
    @PrimaryKey val id: Int,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double?,
    val releaseDate: String?,
    val mediaType: String? = "movie",
    val isWatched: Boolean = false,
    val isCurrentlyWatching: Boolean = false,
    val isFavorite: Boolean = false,
    val isWatchlist: Boolean = false,
    val isWatchLater: Boolean = false,
    val rating: Float? = null,
    val progressPercent: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSeasonWatched: Int? = null,
    val lastEpisodeWatched: Int? = null,
    val lastServerUsedIndex: Int? = null,
    val isEpisodeFinished: Boolean = false,
    val lastEpisodeMaxCount: Int? = null
) {
    fun getPosterUrl(): String {
        return if (posterPath.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
    }
}

@Dao
interface UserMediaInteractionDao {
    @Query("SELECT * FROM user_media_interactions ORDER BY addedAt DESC")
    fun getAllInteractionsFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE isWatched = 1 ORDER BY addedAt DESC")
    fun getWatchedFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE isCurrentlyWatching = 1 ORDER BY addedAt DESC")
    fun getCurrentlyWatchingFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getFavoritesFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE isWatchlist = 1 ORDER BY addedAt DESC")
    fun getWatchlistFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE isWatchLater = 1 ORDER BY addedAt DESC")
    fun getWatchLaterFlow(): Flow<List<UserMediaInteraction>>

    @Query("SELECT * FROM user_media_interactions WHERE id = :id LIMIT 1")
    suspend fun getInteractionById(id: Int): UserMediaInteraction?

    @Query("SELECT * FROM user_media_interactions WHERE id = :id LIMIT 1")
    fun getInteractionFlowById(id: Int): Flow<UserMediaInteraction?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: UserMediaInteraction)

    @Query("DELETE FROM user_media_interactions WHERE id = :id")
    suspend fun deleteInteractionById(id: Int)

    @Query("DELETE FROM user_media_interactions")
    suspend fun clearAll()
}

@Entity(tableName = "search_history_items")
data class SearchHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history_items ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(item: SearchHistoryItem)

    @Query("DELETE FROM search_history_items WHERE `query` = :query")
    suspend fun deleteSearchHistoryByQuery(query: String)

    @Query("DELETE FROM search_history_items")
    suspend fun clearSearchHistory()
}

@Database(entities = [WatchlistItem::class, DownloadedMovie::class, UserMediaInteraction::class, SearchHistoryItem::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun downloadedMovieDao(): DownloadedMovieDao
    abstract fun userMediaInteractionDao(): UserMediaInteractionDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hexaro_movies_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
