package com.libmpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LibmpvWrapper(private val applicationContext: Context) {
    companion object {
        private const val TAG = "react-native-libmpv"
    }

    private enum class State { NEW, CREATED, INIT, RUNNING, CLEANUP, DESTROYED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mpv = MPVLib()

    @Volatile private var created = false
    @Volatile private var cleaning = false
    @Volatile private var isDestroyed = false
    @Volatile private var state = State.NEW

    private var isPlaying = false
    private var hasPlayedOnce = false
    private var mpvDirectory: String? = null
    private var eventObserver: MPVLib.EventObserver? = null
    private var logObserver: MPVLib.LogObserver? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceWidth: Int = -1
    private var surfaceHeight: Int = -1

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) { logException(e) }
    }

    private fun logException(e: Exception) {
        try {
            Log.e(TAG, "mpv exception", e)
            logObserver?.logMessage("RNLE", 20, e.message ?: "unknown native error")
        } catch (_: Throwable) {}
    }

    private fun ensureUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    fun create(): Boolean {
        ensureUi {
            safe {
                mpv.create(applicationContext)
                createMpvDirectory()
                created = true
                state = State.CREATED
            }
        }
        return true
    }

    fun init() {
        ensureUi {
            safe {
                if (state == State.CREATED) {
                    mpv.init()
                    state = State.RUNNING
                }
            }
        }
    }

    fun destroy() = cleanup()

    fun cleanup() {
        if (!created || cleaning || state >= State.CLEANUP) return
        cleaning = true
        state = State.CLEANUP
        ensureUi {
            safe { mpv.command(arrayOf("stop")) }
            safe { mpv.setPropertyString("pause", "yes") }
            safe { mpv.detachSurface() }
            safe { mpv.removeObservers() }
            safe { mpv.removeLogObservers() }
            safe { mpv.destroy() }
            created = false
            isDestroyed = true
            cleaning = false
            state = State.DESTROYED
        }
    }

    fun isCreated() = created
    fun isAlive() = !isDestroyed
    fun isPlaying() = isPlaying
    fun hasPlayedOnce() = hasPlayedOnce
    fun getMpvDirectoryPath() = mpvDirectory

    fun addEventObserver(observer: MPVLib.EventObserver) {
        ensureUi {
            safe {
                mpv.removeObservers()
                eventObserver = observer
                mpv.addObserver(observer)
                mpv.observeProperty("demuxer-cache-time", MPVLib.MPV_FORMAT_INT64)
                mpv.observeProperty("duration", MPVLib.MPV_FORMAT_INT64)
                mpv.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
                mpv.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
                mpv.observeProperty("seekable", MPVLib.MPV_FORMAT_FLAG)
                mpv.observeProperty("speed", MPVLib.MPV_FORMAT_DOUBLE)
                mpv.observeProperty("time-pos", MPVLib.MPV_FORMAT_INT64)
                mpv.observeProperty("track-list", MPVLib.MPV_FORMAT_STRING)
            }
        }
    }

    fun addLogObserver(observer: MPVLib.LogObserver) {
        ensureUi {
            safe {
                mpv.removeLogObservers()
                logObserver = observer
                mpv.addLogObserver(observer)
            }
        }
    }

    fun setOptionString(option: String, value: String) {
        ensureUi { safe { mpv.setOptionString(option, value) } }
    }

    fun setPropertyString(property: String, value: String) {
        ensureUi { safe { mpv.setPropertyString(property, value) } }
    }

    suspend fun getProperty(name: String): String? =
        suspendCancellableCoroutine { cont ->
            ensureUi {
                try { cont.resume(mpv.getPropertyString(name)) }
                catch (e: Exception) { logException(e); cont.resume(null) }
            }
        }

    fun command(orders: Array<String>) {
        ensureUi { safe { mpv.command(orders) } }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        applySurfaceDimensions()
        ensureUi {
            safe {
                if (state >= State.CREATED && state < State.CLEANUP) {
                    mpv.attachSurface(surfaceView.holder.surface)
                }
            }
        }
    }

    fun detachSurface() {
        ensureUi { safe { mpv.detachSurface() } }
    }

    fun setSurfaceWidth(width: Int) {
        surfaceWidth = width
        applySurfaceDimensions()
    }

    fun setSurfaceHeight(height: Int) {
        surfaceHeight = height
        applySurfaceDimensions()
    }

    private fun applySurfaceDimensions() {
        ensureUi {
            try {
                if (surfaceWidth > 0 && surfaceHeight > 0 && surfaceView != null) {
                    surfaceView?.holder?.setFixedSize(surfaceWidth, surfaceHeight)
                }
            } catch (e: Exception) { logException(e) }
        }
    }

    fun play(url: String, options: String? = null) {
        ensureUi {
            safe {
                if (!isPlaying) {
                    if (options == null)
                        mpv.command(arrayOf("loadfile", url))
                    else
                        mpv.command(arrayOf("loadfile", url, "replace", "0", options))
                    mpv.command(arrayOf("set", "pause", "no"))
                    hasPlayedOnce = true
                    isPlaying = true
                }
            }
        }
    }

    fun pause() {
        ensureUi { safe { mpv.command(arrayOf("set", "pause", "yes")); isPlaying = false } }
    }

    fun unpause() {
        ensureUi { safe { mpv.command(arrayOf("set", "pause", "no")); isPlaying = true } }
    }

    fun pauseOrUnpause() {
        ensureUi { if (isPlaying) pause() else unpause() }
    }

    fun seekToSeconds(seconds: Int) {
        ensureUi { safe { mpv.command(arrayOf("seek", seconds.toString(), "absolute")) } }
    }

    private fun createMpvDirectory() {
        val dir = File(applicationContext.getExternalFilesDir("mpv"), "mpv")
        try {
            mpvDirectory = dir.absolutePath
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Unable to create $dir")
                return
            }
            applicationContext.assets.open("subfont.ttf").use { inp ->
                FileOutputStream("${dir}/subfont.ttf").use { out -> inp.copyTo(out) }
            }
            applicationContext.assets.open("mpv.conf").use { inp ->
                FileOutputStream("${dir}/mpv.conf").use { out -> inp.copyTo(out) }
            }
        } catch (e: Exception) {
            logException(e)
        }
    }
}
