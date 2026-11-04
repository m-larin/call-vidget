package com.callwidget

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Выполняет REST-запрос кнопки и превращает ответ в текст для заставки. */
object HttpRunner {

    sealed class Result {
        data class Ok(val code: Int) : Result()
        data class Error(val message: String) : Result()
    }

    private const val DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8"
    private const val MAX_ERROR_BODY = 500

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Разбирает заголовки «Имя: значение» (по одному на строку, пустые строки пропускаются).
     * Бросает IllegalArgumentException с номером строки, если строка некорректна.
     */
    fun parseHeaders(text: String): Headers {
        val builder = Headers.Builder()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val colon = line.indexOf(':')
            require(colon > 0) { "${index + 1}" }
            try {
                builder.add(line.substring(0, colon).trim(), line.substring(colon + 1).trim())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("${index + 1}", e)
            }
        }
        return builder.build()
    }

    fun isValidUrl(url: String): Boolean = url.toHttpUrlOrNull() != null

    /** Запускает запрос асинхронно; [onResult] вызывается в фоновом потоке OkHttp. */
    fun execute(context: Context, request: HttpRequest, onResult: (Result) -> Unit): Call? {
        val built = try {
            build(request)
        } catch (e: IllegalArgumentException) {
            onResult(Result.Error(context.getString(R.string.http_bad_request, e.message ?: "")))
            return null
        }
        DebugLog.log("http: ${request.method} ${built.url}")
        val call = client.newCall(built)
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    DebugLog.log("http: response ${it.code}")
                    if (it.code in 200..299) {
                        onResult(Result.Ok(it.code))
                    } else {
                        val body = runCatching { it.body?.string() }.getOrNull().orEmpty().trim().take(MAX_ERROR_BODY)
                        val status = "HTTP ${it.code} ${it.message}".trim()
                        onResult(Result.Error(if (body.isEmpty()) status else "$status\n\n$body"))
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                DebugLog.log("http: failure $e")
                if (call.isCanceled()) return
                onResult(Result.Error(describe(context, e)))
            }
        })
        return call
    }

    private fun build(request: HttpRequest): Request {
        val url = request.url.toHttpUrlOrNull() ?: throw IllegalArgumentException(request.url)
        val headers = parseHeaders(request.headers)
        val method = request.method.uppercase()
        val body = if (method in HttpRequest.NO_BODY) {
            null
        } else {
            val contentType = headers["Content-Type"] ?: DEFAULT_CONTENT_TYPE
            // DELETE без тела отправляем без тела, POST/PUT/PATCH требуют тело (пусть пустое)
            if (method == "DELETE" && request.body.isEmpty()) null
            else request.body.toRequestBody(contentType.toMediaTypeOrNull())
        }
        val builder = Request.Builder().url(url).headers(headers).method(method, body)
        if (request.basicAuth) {
            builder.header("Authorization", Credentials.basic(request.user, request.password, Charsets.UTF_8))
        }
        return builder.build()
    }

    private fun describe(context: Context, e: IOException): String = when (e) {
        is UnknownHostException -> context.getString(R.string.http_unknown_host, e.message ?: "")
        is SocketTimeoutException -> context.getString(R.string.http_timeout)
        is ConnectException -> context.getString(R.string.http_connect_failed, e.message ?: "")
        is SSLException -> context.getString(R.string.http_ssl_error, e.message ?: "")
        is SocketException -> if (e.message?.contains("EPERM") == true) context.getString(R.string.http_no_network_access)
            else context.getString(R.string.http_network_error, e.message ?: e.javaClass.simpleName)
        else -> context.getString(R.string.http_network_error, e.message ?: e.javaClass.simpleName)
    }
}
