package com.kuky.android.comvvmhelper.extension

import kotlinx.coroutines.*

/**
 * @author kuky.
 * @description 解决协程处理网络请求不能处理异常
 */

const val ERROR_CODE_NORM = 0xFF00
const val ERROR_CODE_INIT = 0xFF10
const val ERROR_CODE_MORE = 0xFF11

data class CoroutineCallback(
    var block: suspend () -> Unit = {},
    var onError: (Throwable) -> Unit = {}
)

fun CoroutineScope.safeLaunch(init: CoroutineCallback.() -> Unit): Job {
    val callback = CoroutineCallback().apply { this.init() }
    return launch(CoroutineExceptionHandler { _, throwable ->
        callback.onError(throwable)
    } + GlobalScope.coroutineContext) {
        callback.block()
    }
}

fun CoroutineScope.delayLaunch(timeMills: Long, init: CoroutineCallback.() -> Unit): Job {
    check(timeMills >= 0) { "timeMills must be positive" }
    val callback = CoroutineCallback().apply(init)
    return launch(CoroutineExceptionHandler { _, throwable ->
        callback.onError(throwable)
    } + GlobalScope.coroutineContext) {
        delay(timeMills)
        callback.block()
    }
}

@Suppress("FunctionName")
fun IOScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())