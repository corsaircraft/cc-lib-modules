@file:Suppress("NOTHING_TO_INLINE")

package cc.modules

import minilog.Log
import org.bukkit.event.Listener
import java.util.logging.Level

object LogTag {
    // Used ONLY to debug code.
    val Debug   = Log.Tag("debug", 'D', Level.INFO)
    // Used ONLY to do manual correctness checks.
    val Info   = Log.Tag("info", 'I', Level.INFO)
    // Used for anything that may have security significance.
    val Audit   = Log.Tag("audit", 'A', Level.SEVERE)
    // An issue caused by the admin or configuration.
    val Error    = Log.Tag("error", 'E', Level.SEVERE)
    // An internal issue caused by a bug.
    val Bug     = Log.Tag("bug", 'B', Level.SEVERE)
}

inline fun Log.debug(noinline f: (Log.Context) -> Unit) = log(LogTag.Debug, f)
inline fun Log.info(noinline f: (Log.Context) -> Unit) = log(LogTag.Info, f)
inline fun Log.error(ulid: String, noinline f: (Log.Context) -> Unit) = log(ulid, LogTag.Error, f)
inline fun Log.bug(ulid: String, noinline f: (Log.Context) -> Unit) = log(ulid, LogTag.Bug, f)
inline fun Log.audit(ulid: String, noinline f: (Log.Context) -> Unit) = log(ulid, LogTag.Audit, f)

abstract class PluginModule : Listener {
    internal val deferredOnLoad: MutableList<() -> Unit> = mutableListOf()
    internal val deferredOnStart: MutableList<() -> Unit> = mutableListOf()
    internal val deferredOnStop: MutableList<() -> Unit> = mutableListOf()

    fun <R> evalOnStart(f: () -> R): Lazy<R> {
        val result: Lazy<R> = lazy<R> { f() }
        deferredOnStart.add { result.value }
        return result
    }
    fun <R> evalOnLoad(f: () -> R): Lazy<R> {
        val result: Lazy<R> = lazy<R> { f() }
        deferredOnLoad.add { result.value }
        return result
    }

    fun <R> Registry<R>.value(name: String): Lazy<R> {
        val registry = this
        val result: Lazy<R> = lazy<R> { registry.getOrThrow(name) }
        deferredOnStart.add { result.value }
        return result
    }
    fun <R, R1> Registry<R>.value(name: String, f: (R) -> R1): Lazy<R1> {
        val registry = this
        val result: Lazy<R1> = lazy<R1> { f(registry.getOrThrow(name)) }
        deferredOnStart.add { result.value }
        return result
    }

    fun runOnStart(f: () -> Unit) {
        deferredOnStart.add(f)
    }
    fun runOnLoad(f: () -> Unit) {
        deferredOnLoad.add(f)
    }
    fun runOnStop(f: () -> Unit) {
        deferredOnStop.add(f)
    }
    fun defer(f: () -> Unit) {
        deferredOnStop.add(f)
    }

    open fun onLoad() { }
    open fun onUnload() { }
    open fun start() { }
    open fun stop() { }
}
