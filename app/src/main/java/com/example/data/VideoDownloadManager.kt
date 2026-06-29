package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DownloadTask(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val url: String,
    val headers: Map<String, String>,
    val progress: Int = 0,
    val status: String = "Queued", // "Queued", "Downloading", "Success", "Failed", "Cancelled"
    val timestamp: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

object VideoDownloadManager {
    private const val TAG = "VideoDownloadManager"

    // List of all download tasks (Queued, Downloading, Success, Failed, etc)
    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    // Map of downloadId -> progress percentage (0 - 100) - kept for backward compatibility
    private val _downloadProgress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Int>> = _downloadProgress.asStateFlow()

    // Map of downloadId -> state: "Idle", "Downloading", "Success", "Failed: <message>" - kept for backward compatibility
    private val _downloadStates = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, String>> = _downloadStates.asStateFlow()

    // Map of active jobs to allow cancellation
    private val activeJobs = mutableMapOf<Int, Job>()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .build()
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val CHANNEL_ID = "video_download_channel"
    private const val CHANNEL_NAME = "Video Downloads"

    private fun createNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Shows progress and status of active app offline downloads"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showOrUpdateNotification(id: Int, title: String, status: String, progress: Int) {
        val context = appContext ?: return
        createNotificationChannel()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isOngoing = status == "Downloading" || status == "Queued"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            status == "Queued" -> {
                builder.setContentText("Queued in download list...")
                builder.setProgress(100, 0, true)
            }
            status == "Downloading" -> {
                builder.setContentText("Downloading... $progress%")
                builder.setProgress(100, progress, false)
            }
            status == "Success" -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText("Download completed successfully!")
                builder.setProgress(0, 0, false)
            }
            status == "Cancelled" -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText("Download cancelled.")
                builder.setProgress(0, 0, false)
            }
            status.startsWith("Failed") -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText(status)
                builder.setProgress(0, 0, false)
            }
            else -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.setContentText("Download failed.")
                builder.setProgress(0, 0, false)
            }
        }

        try {
            notificationManager.notify(id, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Notification post failed: ${e.message}")
        }
    }

    // Application context reference and completion callbacks
    private var appContext: android.content.Context? = null
    private val onCompleteCallbacks = java.util.concurrent.ConcurrentHashMap<Int, suspend (String) -> Unit>()

    fun startDownload(
        context: Context,
        id: Int,
        title: String,
        posterPath: String?,
        url: String,
        headers: Map<String, String>,
        onMovieComplete: suspend (localFileUriString: String) -> Unit
    ) {
        appContext = context.applicationContext
        onCompleteCallbacks[id] = onMovieComplete

        synchronized(this) {
            val alreadyQueuedOrRunning = _downloadTasks.value.any { 
                it.id == id && (it.status == "Queued" || it.status == "Downloading") 
            }
            if (alreadyQueuedOrRunning) {
                Log.d(TAG, "Task $id is already in queue or downloading. Skipping start request.")
                return
            }

            // Remove previous unsuccessful task record for this id if any, and add as Queued
            _downloadTasks.value = _downloadTasks.value.filterNot { it.id == id } + DownloadTask(
                id = id,
                title = title,
                posterPath = posterPath,
                url = url,
                headers = headers,
                status = "Queued"
            )

            _downloadStates.value = _downloadStates.value + (id to "Queued")
            _downloadProgress.value = _downloadProgress.value + (id to 0)
        }

        // Start Foreground Service to keep app and downloads alive
        VideoDownloadService.start(context)

        processQueue()
    }

    fun cancelDownload(id: Int) {
        synchronized(activeJobs) {
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
        }
        val task = _downloadTasks.value.find { it.id == id }
        if (task != null && (task.status == "Cancelled" || task.status.startsWith("Failed"))) {
            // If already failed or cancelled, remove the record completely from lists
            synchronized(this) {
                _downloadTasks.value = _downloadTasks.value.filterNot { it.id == id }
                _downloadStates.value = _downloadStates.value - id
                _downloadProgress.value = _downloadProgress.value - id
            }
        } else {
            updateTaskStatus(id, "Cancelled")
        }
        processQueue()
    }

    fun retryDownload(context: Context, id: Int) {
        val task = _downloadTasks.value.find { it.id == id } ?: return
        if (task.status == "Downloading" || task.status == "Queued") return

        synchronized(this) {
            // Move back to Queued state so processQueue picks it up
            _downloadTasks.value = _downloadTasks.value.map {
                if (it.id == id) {
                    it.copy(status = "Queued", progress = 0, downloadedBytes = 0L)
                } else {
                    it
                }
            }
            _downloadStates.value = _downloadStates.value + (id to "Queued")
            _downloadProgress.value = _downloadProgress.value + (id to 0)
        }

        // Reconstruct or preserve the callback to write to database on successful completion
        if (!onCompleteCallbacks.containsKey(id)) {
            onCompleteCallbacks[id] = { localPath ->
                try {
                    val db = AppDatabase.getDatabase(context)
                    val completedMovie = DownloadedMovie(
                        id = id,
                        title = task.title,
                        posterPath = task.posterPath,
                        localFileUri = localPath,
                        downloadedAt = System.currentTimeMillis()
                    )
                    db.downloadedMovieDao().insertDownload(completedMovie)
                    Log.d(TAG, "Reconstructed callback recorded success for: $id")
                } catch (dbEx: Exception) {
                    Log.e(TAG, "Reconstructed callback DB write failed: ${dbEx.message}", dbEx)
                }
            }
        }

        VideoDownloadService.start(context)
        processQueue()
    }

    private fun processQueue() {
        val context = appContext ?: return
        synchronized(this) {
            val downloadingTasksCount = _downloadTasks.value.count { it.status == "Downloading" }
            val queuedTasksCount = _downloadTasks.value.count { it.status == "Queued" }

            if (downloadingTasksCount >= 1) {
                // Keep peak bandwidth high by downloading 1 high-speed stream at a time
                return
            }

            val nextTask = _downloadTasks.value
                .filter { it.status == "Queued" }
                .minByOrNull { it.timestamp }

            if (nextTask == null) {
                if (downloadingTasksCount == 0 && queuedTasksCount == 0) {
                    // Stop backup Foreground Service as there are no active/queued tasks
                    VideoDownloadService.stop(context)
                }
                return
            }

            // Progress status update
            updateTaskStatus(nextTask.id, "Downloading", progress = 0)

            val job = downloadScope.launch {
                var finalLocalPath: String? = null
                try {
                    val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                    if (!moviesDir.exists()) {
                        moviesDir.mkdirs()
                    }

                    val sanitizedTitle = nextTask.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                    val isHls = nextTask.url.contains(".m3u8") || nextTask.url.contains(".m3u")
                    val extension = if (isHls) "ts" else "mp4"
                    val localFile = File(moviesDir, "${nextTask.id}_${sanitizedTitle}.$extension")
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                    localFile.createNewFile()
                    finalLocalPath = localFile.absolutePath

                    if (nextTask.url.contains(".m3u8") || nextTask.url.contains(".m3u")) {
                        downloadHls(nextTask.url, localFile, nextTask.headers, nextTask.id)
                    } else {
                        downloadDirect(nextTask.url, localFile, nextTask.headers, nextTask.id)
                    }

                    // Download completed successfully
                    updateTaskStatus(nextTask.id, "Success", progress = 100, localFilePath = finalLocalPath)

                    // Directly write completed download to database to prevent any losses if app context restarts
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val completedMovie = DownloadedMovie(
                            id = nextTask.id,
                            title = nextTask.title,
                            posterPath = nextTask.posterPath,
                            localFileUri = localFile.absolutePath,
                            downloadedAt = System.currentTimeMillis()
                        )
                        db.downloadedMovieDao().insertDownload(completedMovie)
                        Log.d(TAG, "Successfully recorded completed download directly to Room database: ${nextTask.id}")
                    } catch (dbEx: Exception) {
                        Log.e(TAG, "Direct DB write failed for completed download: ${dbEx.message}", dbEx)
                    }

                    // Execute saved complete callback
                    onCompleteCallbacks[nextTask.id]?.invoke(localFile.absolutePath)
                    onCompleteCallbacks.remove(nextTask.id)

                } catch (e: CancellationException) {
                    updateTaskStatus(nextTask.id, "Cancelled")
                    Log.d(TAG, "Download cancelled: ${nextTask.id}")
                } catch (e: Exception) {
                    val errorMsg = e.localizedMessage ?: "Unknown error"
                    updateTaskStatus(nextTask.id, "Failed: $errorMsg")
                    Log.e(TAG, "Download failed for ${nextTask.id}: ${e.message}", e)
                } finally {
                    synchronized(activeJobs) {
                        activeJobs.remove(nextTask.id)
                    }
                    // Trigger next item in queue
                    processQueue()
                }
            }

            synchronized(activeJobs) {
                activeJobs[nextTask.id] = job
            }
        }
    }

    private fun updateTaskStatus(id: Int, status: String, progress: Int? = null, localFilePath: String? = null) {
        synchronized(this) {
            var taskTitle = ""
            _downloadTasks.value = _downloadTasks.value.map { task ->
                if (task.id == id) {
                    taskTitle = task.title
                    task.copy(
                        status = status,
                        progress = progress ?: task.progress,
                        localFilePath = localFilePath ?: task.localFilePath
                    )
                } else {
                    task
                }
            }

            _downloadStates.value = _downloadStates.value + (id to status)
            val currentProgress = progress ?: _downloadProgress.value[id] ?: 0
            if (progress != null) {
                _downloadProgress.value = _downloadProgress.value + (id to progress)
            }

            if (taskTitle.isNotEmpty()) {
                showOrUpdateNotification(id, taskTitle, status, currentProgress)
            }
        }
    }

    private fun updateTaskProgress(id: Int, progress: Int) {
        synchronized(this) {
            var taskTitle = ""
            var taskStatus = ""
            _downloadTasks.value = _downloadTasks.value.map { task ->
                if (task.id == id) {
                    taskTitle = task.title
                    taskStatus = task.status
                    task.copy(progress = progress)
                } else {
                    task
                }
            }
            _downloadProgress.value = _downloadProgress.value + (id to progress)

            if (taskTitle.isNotEmpty()) {
                showOrUpdateNotification(id, taskTitle, taskStatus, progress)
            }
        }
    }

    private fun updateTaskBytes(id: Int, downloadedBytes: Long, totalBytes: Long) {
        synchronized(this) {
            _downloadTasks.value = _downloadTasks.value.map { task ->
                if (task.id == id) {
                    task.copy(downloadedBytes = downloadedBytes, totalBytes = totalBytes)
                } else {
                    task
                }
            }
        }
    }

    private fun sanitizeHeaders(url: String, headers: Map<String, String>): Map<String, String> {
        val result = headers.toMutableMap()
        // Inject Chrome standard User-Agent if empty, placeholder or okhttp
        val ua = result["User-Agent"]
        if (ua == null || ua.contains("okhttp", ignoreCase = true) || ua.isEmpty()) {
            result["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        // Ensure browser-like cookies or metadata can be received
        if (!result.containsKey("Accept")) {
            result["Accept"] = "*/*"
        }
        if (!result.containsKey("Accept-Language")) {
            result["Accept-Language"] = "en-US,en;q=0.9"
        }
        
        // Auto-detect stream host and apply verified Referer rules
        try {
            val uri = java.net.URI.create(url)
            val host = uri.host ?: ""
            if (host.contains("vidsrc") || host.contains("rcdn") || host.contains("vidplay")) {
                if (!result.containsKey("Referer")) result["Referer"] = "https://vidsrc.to/"
                if (!result.containsKey("Origin")) result["Origin"] = "https://vidsrc.to"
            } else if (host.contains("embed") || host.contains("su") || host.contains("multiembed")) {
                if (!result.containsKey("Referer")) result["Referer"] = "https://embed.su/"
                if (!result.containsKey("Origin")) result["Origin"] = "https://embed.su"
            } else {
                if (!result.containsKey("Referer")) {
                    result["Referer"] = "${uri.scheme}://${host}/"
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return result
    }

    private suspend fun downloadDirect(
        url: String,
        outputFile: File,
        headers: Map<String, String>,
        id: Int
    ) = withContext(Dispatchers.IO) {
        val sanitized = sanitizeHeaders(url, headers)
        
        // Let's first probe if Range is supported and get file size
        var totalBytes = -1L
        var rangeSupported = false
        try {
            val probeReq = Request.Builder()
                .url(url)
                .head()
                .apply { sanitized.forEach { (k, v) -> header(k, v) } }
                .build()
            okHttpClient.newCall(probeReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    totalBytes = resp.body?.contentLength() ?: -1L
                    val acceptRanges = resp.header("Accept-Ranges")
                    rangeSupported = (acceptRanges != null && acceptRanges.contains("bytes")) || resp.header("Content-Range") != null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Probe HEAD failed, trying GET with Range=0-1", e)
            try {
                val probeGet = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1")
                    .apply { sanitized.forEach { (k, v) -> header(k, v) } }
                    .build()
                okHttpClient.newCall(probeGet).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 206) {
                        rangeSupported = true
                        val contentRange = resp.header("Content-Range")
                        if (contentRange != null) {
                            totalBytes = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        if (rangeSupported && totalBytes > 0) {
            Log.d(TAG, "Server supports Range downloads! Using chunked range downloading for reliability. Total size: $totalBytes")
            val outputStream = FileOutputStream(outputFile, false)
            var currentByte = 0L
            val chunkSize = 8 * 1024 * 1024 // 8 MB chunks
            
            while (currentByte < totalBytes) {
                ensureActive()
                val endByte = (currentByte + chunkSize - 1).coerceAtMost(totalBytes - 1)
                var chunkSuccess = false
                var attempts = 0
                while (attempts < 3 && !chunkSuccess) {
                    try {
                        val req = Request.Builder()
                            .url(url)
                            .header("Range", "bytes=$currentByte-$endByte")
                            .apply { sanitized.forEach { (k, v) -> header(k, v) } }
                            .build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful || resp.code == 206) {
                                val bodyBytes = resp.body?.bytes()
                                if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                    outputStream.write(bodyBytes)
                                    currentByte += bodyBytes.size
                                    chunkSuccess = true
                                    val progress = ((currentByte * 100) / totalBytes).toInt()
                                    updateTaskProgress(id, progress)
                                    updateTaskBytes(id, currentByte, totalBytes)
                                } else {
                                    throw IOException("Empty chunk retrieved")
                                }
                            } else {
                                throw IOException("Chunk HTTP error ${resp.code}")
                            }
                        }
                    } catch (e: Exception) {
                        attempts++
                        Log.w(TAG, "Range chunk $currentByte-$endByte download failed (attempt $attempts): ${e.message}")
                        if (attempts >= 3) {
                            outputStream.close()
                            throw IOException("Failed to download chunk at byte $currentByte: ${e.localizedMessage}")
                        }
                        kotlinx.coroutines.delay(2000)
                    }
                }
            }
            outputStream.flush()
            outputStream.close()
        } else {
            Log.d(TAG, "Server does not support Accept-Ranges or content size is hidden. Using traditional fallback stream...")
            // Legacy single connection download with larger buffer to combat timeouts
            val req = Request.Builder().url(url).apply { sanitized.forEach { (k, v) -> header(k, v) } }.build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Direct server download failed: HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty body")
                val stream = body.byteStream()
                val out = FileOutputStream(outputFile)
                val buffer = ByteArray(128 * 1024)
                var read: Int
                var totalRead = 0L
                val len = body.contentLength()
                while (stream.read(buffer).also { read = it } != -1) {
                    ensureActive()
                    out.write(buffer, 0, read)
                    totalRead += read
                    if (len > 0) {
                        updateTaskProgress(id, ((totalRead * 100) / len).toInt())
                    }
                    updateTaskBytes(id, totalRead, if (len > 0) len else 0L)
                }
                out.flush()
                out.close()
            }
        }
    }

    private data class HlsSegment(
        val url: String,
        val index: Int,
        val sequenceNumber: Int,
        val keyBytes: ByteArray?,
        val ivBytes: ByteArray?
    )

    private suspend fun downloadHls(
        url: String,
        outputFile: File,
        headers: Map<String, String>,
        id: Int
    ) = withContext(Dispatchers.IO) {
        val sanitized = sanitizeHeaders(url, headers)
        
        // Fetch original master playlist text
        val playlistText = fetchPlaylistText(url, sanitized)
        
        // Extract all stream alternatives (ordered highest bandwidth first to lowest quality fallback)
        val variantUrls = parseVariantPlaylists(url, playlistText)
        
        var success = false
        var lastException: Exception? = null
        
        for (variantUrl in variantUrls) {
            try {
                ensureActive()
                Log.d(TAG, "Attempting HLS playlist download with variant: $variantUrl")
                
                val mediaPlaylistText = if (variantUrl != url) {
                    fetchPlaylistText(variantUrl, sanitized)
                } else {
                    playlistText
                }
                
                val segments = parseHlsPlaylist(variantUrl, mediaPlaylistText, sanitized, id)
                if (segments.isEmpty()) {
                    continue
                }
                
                // Track start and proceed
                downloadHlsSegments(segments, outputFile, sanitized, id)
                success = true
                Log.i(TAG, "Successfully downloaded HLS variant playlist to local file")
                break
            } catch (e: Exception) {
                Log.e(TAG, "HLS variant stream failed: ${e.message}. Attempting fallback to next quality...", e)
                lastException = e
            }
        }
        
        if (!success) {
            throw lastException ?: IOException("Failed to download HLS playlist: No variant playlists resolved to active segments")
        }
    }

    private fun parseVariantPlaylists(baseUrl: String, playlistText: String): List<String> {
        if (!playlistText.contains("#EXT-X-STREAM-INF")) {
            return listOf(baseUrl)
        }
        
        val lines = playlistText.split("\n")
        val variants = mutableListOf<Pair<Long, String>>()
        
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
                        variants.add(bandwidth to resolveAbsoluteUrl(baseUrl, nextLine))
                    }
                }
            }
            i++
        }
        
        // Sort bandwidth descending so highest quality is downloaded first
        val sorted = variants.sortedByDescending { it.first }.map { it.second }
        return sorted.ifEmpty { listOf(baseUrl) }
    }

    private suspend fun downloadHlsSegments(
        segments: List<HlsSegment>,
        outputFile: File,
        headers: Map<String, String>,
        id: Int
    ) = withContext(Dispatchers.IO) {
        val outputStream = FileOutputStream(outputFile)
        var downloadedCount = 0
        var totalDownloadedBytes = 0L
        
        // Concurrency settings: auto-adjust dynamically when rate limited
        var currentBatchSize = 4
        var activeDelayMs = 0L
        
        var i = 0
        while (i < segments.size) {
            ensureActive()
            
            val remaining = segments.size - i
            val currentBatch = segments.subList(i, i + remaining.coerceAtMost(currentBatchSize))
            
            val batchResults = withContext(Dispatchers.IO) {
                currentBatch.map { segment ->
                    async {
                        val rawData = downloadSegmentDataWithRetry(segment.url, headers, maxRetries = 3)
                        val decryptedData = if (rawData != null && segment.keyBytes != null && segment.ivBytes != null) {
                            try {
                                decryptAes128(rawData, segment.keyBytes, segment.ivBytes)
                            } catch (e: Exception) {
                                Log.e(TAG, "AES decryption failed on segment index ${segment.index}. Writing raw data.", e)
                                rawData
                            }
                        } else {
                            rawData
                        }
                        decryptedData
                    }
                }.awaitAll()
            }
            
            var segmentFailureDetected = false
            for (data in batchResults) {
                if (data != null && data.isNotEmpty()) {
                    outputStream.write(data)
                    totalDownloadedBytes += data.size
                } else {
                    segmentFailureDetected = true
                }
            }
            
            if (segmentFailureDetected) {
                // Adaptive congestion flow control: scale down parallelism, add backing off delay
                currentBatchSize = (currentBatchSize - 1).coerceAtLeast(1)
                activeDelayMs = (activeDelayMs + 250L).coerceAtMost(1500L)
                Log.d(TAG, "Congestion or chunk timeout. Throttling downloader: next batch size $currentBatchSize, delay ${activeDelayMs}ms")
            } else {
                // Slowly scale back up to full speed if segments download safely
                if (currentBatchSize < 6) {
                    currentBatchSize++
                }
                if (activeDelayMs > 0) {
                    activeDelayMs = (activeDelayMs - 100L).coerceAtLeast(0L)
                }
            }
            
            downloadedCount += currentBatch.size
            val progress = (downloadedCount * 100) / segments.size
            updateTaskProgress(id, progress)
            val estimatedTotalBytes = if (downloadedCount > 0) {
                (totalDownloadedBytes * segments.size) / downloadedCount
            } else {
                0L
            }
            updateTaskBytes(id, totalDownloadedBytes, estimatedTotalBytes)
            
            if (activeDelayMs > 0) {
                kotlinx.coroutines.delay(activeDelayMs)
            }
            i += currentBatch.size
        }
        
        outputStream.flush()
        outputStream.close()
    }

    private fun fetchPlaylistText(url: String, headers: Map<String, String>): String {
        var lastException: Exception? = null
        var attempt = 1
        while (attempt <= 3) {
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) ->
                    builder.header(k, v)
                }
                val response = okHttpClient.newCall(builder.build()).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw IOException("HTTP error $code")
                }
                val bodyText = response.body?.string() ?: throw IOException("Empty playlist body")
                response.close()
                return bodyText
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "fetchPlaylistText attempt $attempt failed: ${e.message}")
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            attempt++
        }
        throw lastException ?: IOException("Failed to fetch HLS playlist")
    }

    private fun parseHlsPlaylist(
        mediaPlaylistUrl: String,
        mediaPlaylistText: String,
        headers: Map<String, String>,
        id: Int
    ): List<HlsSegment> {
        val lines = mediaPlaylistText.split("\n")
        val segments = mutableListOf<HlsSegment>()
        
        var mediaSequence = 0
        var activeKeyBytes: ByteArray? = null
        var activeIvBytes: ByteArray? = null
        
        // Simple cache for keys so we don't fetch the same key repeatedly
        val keyCache = mutableMapOf<String, ByteArray>()
        
        // Parse media sequence number
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = trimmed.substringAfter("#EXT-X-MEDIA-SEQUENCE:").toIntOrNull() ?: 0
                break
            }
        }
        
        var totalDurationMs = 0L
        var currentSegmentDurationMs = 0L
        var segmentIndex = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            if (trimmed.startsWith("#EXTINF:")) {
                try {
                    val durationStr = trimmed.substringAfter("#EXTINF:").substringBefore(",").trim()
                    val durationFloat = durationStr.toFloatOrNull() ?: 0f
                    currentSegmentDurationMs = (durationFloat * 1000).toLong()
                } catch (e: Exception) {
                    currentSegmentDurationMs = 0L
                }
            } else if (trimmed.startsWith("#EXT-X-KEY:")) {
                if (trimmed.contains("METHOD=AES-128")) {
                    val uriRegex = """URI="([^"]+)"""".toRegex()
                    val match = uriRegex.find(trimmed)
                    val relativeKeyUrl = match?.groupValues?.get(1)
                    
                    if (relativeKeyUrl != null) {
                        val absoluteKeyUrl = resolveAbsoluteUrl(mediaPlaylistUrl, relativeKeyUrl)
                        activeKeyBytes = try {
                            keyCache.getOrPut(absoluteKeyUrl) {
                                fetchKeyBytes(absoluteKeyUrl, headers)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to download segment key from $absoluteKeyUrl", e)
                            null
                        }
                    }
                    
                    val ivRegex = """IV=0x([0-9a-fA-F]+)""".toRegex()
                    val ivMatch = ivRegex.find(trimmed)
                    val ivHex = ivMatch?.groupValues?.get(1)
                    if (ivHex != null) {
                        activeIvBytes = hexStringToByteArray(ivHex)
                    } else {
                        activeIvBytes = null // default to sequence number representation
                    }
                } else {
                    activeKeyBytes = null
                    activeIvBytes = null
                }
            } else if (!trimmed.startsWith("#")) {
                val segmentUrl = resolveAbsoluteUrl(mediaPlaylistUrl, trimmed)
                val seqNum = mediaSequence + segmentIndex
                
                val finalIv = if (activeKeyBytes != null) {
                    if (activeIvBytes != null) {
                        activeIvBytes
                    } else {
                        val ivBytes = ByteArray(16)
                        for (b in 0..7) {
                            ivBytes[15 - b] = (seqNum ushr (b * 8)).toByte()
                        }
                        ivBytes
                    }
                } else {
                    null
                }
                
                segments.add(
                    HlsSegment(
                        url = segmentUrl,
                        index = segmentIndex,
                        sequenceNumber = seqNum,
                        keyBytes = activeKeyBytes,
                        ivBytes = finalIv
                    )
                )
                segmentIndex++
                totalDurationMs += currentSegmentDurationMs
                currentSegmentDurationMs = 0L // Reset for next segment
            }
        }
        
        if (id > 0 && totalDurationMs > 0 && appContext != null) {
            try {
                val prefs = appContext!!.getSharedPreferences("download_durations", Context.MODE_PRIVATE)
                prefs.edit().putLong("duration_$id", totalDurationMs).apply()
                Log.d(TAG, "Saved parsed HLS download duration for task $id: $totalDurationMs ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving HLS download duration: ${e.message}")
            }
        }
        
        return segments
    }

    private fun fetchKeyBytes(keyUrl: String, headers: Map<String, String>): ByteArray {
        val builder = Request.Builder().url(keyUrl)
        headers.forEach { (k, v) -> builder.header(k, v) }
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch AES key: ${response.code}")
            return response.body?.bytes() ?: throw IOException("Empty HLS AES-128 key body")
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun downloadSegmentDataWithRetry(
        url: String,
        headers: Map<String, String>,
        maxRetries: Int
    ): ByteArray? {
        var attempts = 0
        while (attempts <= maxRetries) {
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) ->
                    builder.header(k, v)
                }
                okHttpClient.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chunk download retry ${attempts + 1} failed for $url: ${e.message}")
            }
            if (attempts < maxRetries) {
                try {
                    Thread.sleep(1500L) // Add 1.5 seconds delay between retries
                } catch (sleepEx: Exception) {}
            }
            attempts++
        }
        return null
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            URL(URL(baseUrl), relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
