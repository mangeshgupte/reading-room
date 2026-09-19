package com.mangesh.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** The reader module on the Mac: http://starlight.local:8642/reader. One attempt, no retries. */
class Api(baseUrl: String, private val token: String) {

    sealed class Failure(val detail: String) {
        class Unreachable(detail: String) : Failure(detail)
        object ServerDown : Failure("starlight is up, the server is not running")
        object Unauthorized : Failure("token rejected — check Settings")
        class Http(val code: Int, val body: String) : Failure("server replied $code")
    }

    class ApiException(val failure: Failure) : Exception(failure.detail)

    private val base = baseUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val probeClient = client.newBuilder().readTimeout(5, TimeUnit.SECONDS).build()

    suspend fun queue(): List<Entry> {
        val o = JSONObject(call(get("/queue"), probeClient))
        val arr = o.getJSONArray("entries")
        return (0 until arr.length()).map { Entry.fromServer(arr.getJSONObject(it)) }
    }

    suspend fun report(id: Int): ReportPayload {
        val o = JSONObject(call(get("/report/$id")))
        val assets = o.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return ReportPayload(o.getInt("id"), o.str("raw_hash"), o.str("html"), assets)
    }

    suspend fun asset(path: String): ByteArray = callBytes(get("/asset?path=$path"))

    suspend fun actions(actions: List<Action>): List<ActionResult> {
        val body = JSONArray(actions.map { it.toJson() }).toString()
        val req = Request.Builder().url("$base/actions").header("Authorization", "Bearer $token")
            .header("Connection", "close")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val o = JSONObject(call(req))
        val arr = o.getJSONArray("results")
        return (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            ActionResult(r.str("uuid"), r.optBoolean("ok"), r.str(if (r.optBoolean("ok")) "message" else "error"))
        }
    }

    /** Connection: close on every request: the Mac's server is HTTP/1.0-style and a pooled
     *  socket it has already closed would fail the next request before it is even sent. */
    private fun get(path: String) =
        Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            .header("Connection", "close").get().build()

    private suspend fun call(req: Request, c: OkHttpClient = client): String = String(callBytes(req, c))

    private suspend fun callBytes(req: Request, c: OkHttpClient = client): ByteArray = withContext(Dispatchers.IO) {
        try {
            c.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                when {
                    resp.code == 401 -> throw ApiException(Failure.Unauthorized)
                    !resp.isSuccessful -> throw ApiException(Failure.Http(resp.code, String(bytes)))
                    else -> bytes
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ApiException(Failure.Unreachable("can't resolve the Mac's name — on home Wi-Fi? Or use its IP in Settings"))
        } catch (e: ConnectException) {
            throw ApiException(if (e.message?.contains("refused", true) == true) Failure.ServerDown
                               else Failure.Unreachable("Mac unreachable"))
        } catch (e: SocketTimeoutException) {
            throw ApiException(Failure.Unreachable("Mac unreachable"))
        } catch (e: IOException) {
            throw ApiException(Failure.Unreachable("Mac unreachable (${e.javaClass.simpleName})"))
        }
    }
}
