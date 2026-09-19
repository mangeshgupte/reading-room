package com.mangesh.reader.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mangesh.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pull-updates from the Mac (same scheme as the Home app): GET <server>/version.json,
 * compare versionCode, download the APK and hand it to the Android installer.
 */
class Updater(private val context: Context) {

    sealed class Result {
        data object UpToDate : Result()
        data class UpdateStarted(val versionName: String) : Result()
        data class Unreachable(val detail: String) : Result()
    }

    suspend fun checkAndInstall(serverUrl: String, token: String): Result {
        val info = try {
            JSONObject(fetch("$serverUrl/version.json", token).toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return Result.Unreachable("Update server not reachable (is the Mac on?)")
        }
        val remoteCode = info.getInt("versionCode")
        if (remoteCode <= BuildConfig.VERSION_CODE) return Result.UpToDate

        val apkName = info.getString("apk")
        val apkFile = try {
            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            File(dir, "update.apk").also { it.writeBytes(fetch("$serverUrl/$apkName", token, 60_000)) }
        } catch (e: Exception) {
            return Result.Unreachable("Download failed: ${e.message}")
        }
        promptInstall(apkFile)
        return Result.UpdateStarted(info.optString("versionName", remoteCode.toString()))
    }

    private suspend fun fetch(url: String, token: String, readTimeout: Int = 5000): ByteArray = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000
            conn.readTimeout = readTimeout
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun promptInstall(apk: File) {
        val uri = FileProvider.getUriForFile(context, "com.mangesh.reader.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
