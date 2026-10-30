package net.nekomo.nex.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface FetchResult {
    val latencyMs: Long

    data class Ok(val code: Int, val body: ByteArray, val fromCache: Boolean, override val latencyMs: Long, val finalUrl: String) : FetchResult
    data class HttpError(val code: Int, val message: String, override val latencyMs: Long) : FetchResult
    data class NetworkError(val message: String, override val latencyMs: Long) : FetchResult
    data class TooLarge(val limit: Long, override val latencyMs: Long) : FetchResult
}

fun FetchResult.describe(): String = when (this) {
    is FetchResult.Ok -> "HTTP $code" + if (fromCache) " (cached)" else ""
    is FetchResult.HttpError -> "HTTP $code $message".trim()
    is FetchResult.NetworkError -> message
    is FetchResult.TooLarge -> "Response is larger than ${limit / 1024} KB"
}

/** Shared HTTP client. One instance per process; metadata responses are cached on disk to save data on slow links. */
object NexHttp {
    const val MAX_INDEX_BYTES = 16L * 1024 * 1024
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    @Volatile private var client: OkHttpClient? = null
    @Volatile var timeoutSeconds: Long = 30
        set(value) {
            field = value.coerceIn(5, 120)
            client = null
        }
    var userAgent: String = "NEX"

    private lateinit var cacheDir: File

    fun init(context: Context, userAgent: String) {
        cacheDir = File(context.cacheDir, "http")
        this.userAgent = userAgent
    }

    fun client(): OkHttpClient = client ?: synchronized(this) {
        client ?: OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds * 4, TimeUnit.SECONDS)
            .followRedirects(true)
            .apply { if (::cacheDir.isInitialized) cache(Cache(cacheDir, 20L * 1024 * 1024)) }
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build().also { client = it }
    }

    fun clearCache() {
        runCatching { client().cache?.evictAll() }
    }

    fun cacheSizeBytes(): Long = runCatching { client().cache?.size() ?: 0L }.getOrDefault(0L)

    suspend fun get(
        url: String,
        maxBytes: Long = MAX_INDEX_BYTES,
        headers: Map<String, String> = emptyMap(),
        preferCache: Boolean = false,
        method: String = "GET",
    ): FetchResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        try {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (preferCache) cacheControl(CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build())
                if (method == "HEAD") head()
            }.build()
            client().newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext FetchResult.HttpError(res.code, res.message, elapsed())
                val body = res.body ?: return@withContext FetchResult.Ok(res.code, ByteArray(0), false, elapsed(), res.request.url.toString())
                val declared = body.contentLength()
                if (declared > maxBytes) return@withContext FetchResult.TooLarge(maxBytes, elapsed())
                val bytes = body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return@withContext FetchResult.TooLarge(maxBytes, elapsed())
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                FetchResult.Ok(res.code, bytes, res.networkResponse == null && res.cacheResponse != null, elapsed(), res.request.url.toString())
            }
        } catch (e: IllegalArgumentException) {
            FetchResult.NetworkError("Invalid URL: ${e.message}", elapsed())
        } catch (e: IOException) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName, elapsed())
        }
    }

    /** Streams a download to [target], enforcing [maxBytes]. Returns null on success or an error message. */
    suspend fun download(url: String, target: File, maxBytes: Long = MAX_APK_BYTES, onProgress: (Long, Long) -> Unit = { _, _ -> }): String? =
        withContext(Dispatchers.IO) {
            try {
                client().newCall(Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()).execute().use { res ->
                    if (!res.isSuccessful) return@withContext "HTTP ${res.code} ${res.message}".trim()
                    val body = res.body ?: return@withContext "Empty response"
                    val len = body.contentLength()
                    if (len > maxBytes) return@withContext "File is too large"
                    target.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > maxBytes) return@withContext "File is too large"
                                out.write(buf, 0, n)
                                onProgress(total, len)
                            }
                        }
                    }
                    null
                }
            } catch (e: IOException) {
                target.delete()
                e.message ?: e.javaClass.simpleName
            } catch (e: IllegalArgumentException) {
                "Invalid URL"
            }
        }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= 23) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    fun isMetered(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
}
