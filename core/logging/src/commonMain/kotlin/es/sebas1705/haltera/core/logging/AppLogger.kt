package es.sebas1705.haltera.core.logging

import co.touchlab.kermit.Logger

object AppLogger {
    fun d(tag: String, msg: String) = Logger.d(tag) { msg }
    fun i(tag: String, msg: String) = Logger.i(tag) { msg }
    fun w(tag: String, msg: String) = Logger.w(tag) { msg }
    fun e(tag: String, msg: String, t: Throwable? = null) =
        if (t != null) Logger.e(t, tag) { msg } else Logger.e(tag) { msg }
}
