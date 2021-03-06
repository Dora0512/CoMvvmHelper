package com.kk.android.comvvmhelper.helper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kk.android.comvvmhelper.utils.GsonParseUtils
import com.kk.android.comvvmhelper.utils.LogUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author kuky.
 * @description
 */
@ExperimentalCoroutinesApi
suspend fun http(init: OkRequestWrapper.() -> Unit) {
    val wrapper = OkRequestWrapper().apply(init)

    check(wrapper.baseUrl.matches(Regex("(http|https)?://(\\S)+"))) { "Illegal url" }

    HttpSingle.instance.executeForResult(wrapper)
}

inline fun <reified T> Response.checkResult(): T? {
    val response = this.body?.string() ?: ""
    return try {
        if (response.isBlank()) null
        else GsonParseUtils.instance.fromJson(response, T::class.java)
    } catch (e: Exception) {
        null
    }
}

inline fun <reified T> Response.checkList(): MutableList<T> {
    val response = this.body?.string() ?: ""
    return try {
        if (response.isBlank()) mutableListOf()
        else GsonParseUtils.instance.fromJson(response, object : TypeToken<MutableList<T>>() {}.type)
    } catch (e: Exception) {
        mutableListOf()
    }
}

fun Response.checkText(): String = this.body?.string() ?: ""

data class OkRequestWrapper(
    var flowDispatcher: CoroutineDispatcher? = null,
    var baseUrl: String = "",
    var method: String = "get",
    var requestBody: RequestBody? = null,
    var params: HashMap<String, Any> = hashMapOf(),
    var headers: HashMap<String, String> = hashMapOf(),
    var onSuccess: suspend (Response) -> Unit = {},
    var onFail: suspend (Throwable) -> Unit = {}
)

private class HttpSingle private constructor() {
    companion object {
        val instance by lazy { HttpSingle() }
    }

    private val mOkHttpClient by lazy { generateOkHttpClient() }

    @Suppress("BlockingMethodInNonBlockingContext")
    @ExperimentalCoroutinesApi
    internal suspend fun executeForResult(wrapper: OkRequestWrapper) {
        flow { emit(onExecute(wrapper)) }
            .flowOn(wrapper.flowDispatcher ?: GlobalScope.coroutineContext)
            .catch { wrapper.onFail(it) }
            .collect { wrapper.onSuccess(it) }
    }

    private fun onExecute(wrapper: OkRequestWrapper): Response {
        val requestBuilder = Request.Builder()
        if (!wrapper.headers.isNullOrEmpty()) {
            wrapper.headers.forEach { entry ->
                requestBuilder.addHeader(entry.key, entry.value)
            }
        }

        val request = when (wrapper.method.toLowerCase(Locale.getDefault())) {
            "post" -> requestBuilder.url(wrapper.baseUrl).post(
                if (wrapper.requestBody == null) generateRequestBody(wrapper.params)
                else wrapper.requestBody!!
            ).build()

            "put" -> requestBuilder.url(wrapper.baseUrl).put(
                if (wrapper.requestBody == null) generateRequestBody(wrapper.params)
                else wrapper.requestBody!!
            ).build()

            "delete" -> requestBuilder.url(wrapper.baseUrl).delete(
                if (wrapper.requestBody == null) generateRequestBody(wrapper.params)
                else wrapper.requestBody!!
            ).build()

            else -> requestBuilder.url(generateGetUrl(wrapper.params, wrapper.baseUrl))
                .get().build()
        }

        return mOkHttpClient.newCall(request).execute()
    }

    //region generate http params
    private fun generateGetUrl(params: HashMap<String, Any>, url: String) =
        if (url.contains(Regex("[?=]"))) url
        else {
            val urlSb = StringBuilder(url).append("?")
            if (params.isNotEmpty()) params.forEach { entry ->
                val value = entry.value
                urlSb.append(entry.key).append("=")
                    .append(if (value is String) value else Gson().toJson(value))
                    .append("&")
            }
            urlSb.substring(0, urlSb.length - 1).toString()
        }

    private fun generateRequestBody(params: HashMap<String, Any>) =
        FormBody.Builder().apply {
            if (params.isNotEmpty()) params.forEach { entry ->
                val value = entry.value
                add(entry.key, if (value is String) value else Gson().toJson(value))
            }
        }.build()

    private fun generateOkHttpClient() = OkHttpClient.Builder()
        .connectTimeout(5_000L, TimeUnit.MILLISECONDS)
        .readTimeout(20_000, TimeUnit.MILLISECONDS)
        .writeTimeout(30_000, TimeUnit.MILLISECONDS)
        .addInterceptor(HttpLoggingInterceptor(
            object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    if (message.startsWith("[") || message.startsWith("{"))
                        LogUtils.logJson(message)
                    else LogUtils.logInfo(message)
                }
            }
        ).apply { level = HttpLoggingInterceptor.Level.BODY }).build()
    //endregion
}