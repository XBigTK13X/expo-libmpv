
package com.libmpv

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.util.Log
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LibmpvWrapper(private val applicationContext: Context) {
    companion object {
        private const val TAG = "react-native-libmpv"
        private var swallow = true
    }

    private enum class State { NEW, CREATED, INIT, RUNNING, CLEANUP, DESTROYED }

    @Volatile private var created = false
    @Volatile private var destroying = false
    @Volatile private var isDestroyed = false
    @Volatile private var cleaning = false
    private var isPlaying = false
    private var hasPlayedOnce = false
    private var eventObserver: MPVLib.EventObserver? = null
    private var logObserver: MPVLib.LogObserver? = null
    private var mpvDirectory: String? = null
    private var surfaceWidth: Int = -1
    private var surfaceHeight: Int = -1
    private var surfaceView: SurfaceView? = null
    private val mpv: MPVLib = MPVLib()

    fun isAlive() = !isDestroyed

    @Volatile private var state: State = State.NEW
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mpvExec: ExecutorService = Executors.newSingleThreadExecutor()

    private inline fun safe(block: () -> Unit) {
        try { if (created && state < State.CLEANUP) block() } catch (e: Exception) { logException(e) }
    }

    private fun onMpvThread(block: () -> Unit) {
        if (state >= State.CLEANUP) return
        mpvExec.execute {
            if (state >= State.CLEANUP) return@execute
            try { block() } catch (e: Exception) { logException(e) }
        }
    }

    fun create(): Boolean {
        onMpvThread {
            mpv.create(applicationContext)
            createMpvDirectory()
            created = true
            state = State.CREATED
        }
        return true
    }

    fun init() {
        onMpvThread {
            if (state == State.CREATED) {
                mpv.init()
                state = State.RUNNING
            }
        }
    }

    fun isCreated(): Boolean = created
    fun isPlaying(): Boolean = isPlaying
    fun hasPlayedOnce(): Boolean = hasPlayedOnce
    fun getMpvDirectoryPath(): String? = mpvDirectory

    private fun createMpvDirectory() {
        val mpvDir = File(applicationContext.getExternalFilesDir("mpv"), "mpv")
        try {
            mpvDirectory = mpvDir.absolutePath
            if (!mpvDir.exists() && !mpvDir.mkdirs()) {
                Log.e(TAG, "exception", IllegalArgumentException("Unable to create $mpvDir"))
                return
            }
            applicationContext.assets.open("subfont.ttf").use { subfontIn ->
                FileOutputStream("${mpvDir}/subfont.ttf").use { fontOut -> subfontIn.copyTo(fontOut) }
            }
            applicationContext.assets.open("mpv.conf").use { mpvConfIn ->
                FileOutputStream("${mpvDir}/mpv.conf").use { confOut -> mpvConfIn.copyTo(confOut) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create the directory $mpvDir", e)
        }
    }

    private fun logException(exception: Exception) {
        if (isDestroyed || state >= State.CLEANUP) return
        try {
            val message: String = (exception.message as? String) ?: "Unable to read error message"
            logObserver?.logMessage("RNLE", 20, message)
        } catch (e: Exception) {
            if (!swallow) throw e
        }
    }

    fun addEventObserver(observer: MPVLib.EventObserver) {
        onMpvThread {
            mpv.removeObservers()
            eventObserver = observer
            mpv.addObserver(eventObserver)
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

    fun addLogObserver(observer: MPVLib.LogObserver) {
        onMpvThread {
            mpv.removeLogObservers()
            logObserver = observer
            mpv.addLogObserver(logObserver)
        }
    }

    fun setOptionString(option: String, setting: String) {
        onMpvThread { if (state == State.RUNNING) mpv.setOptionString(option, setting) }
    }

    fun setPropertyString(property: String, setting: String) {
        onMpvThread { if (state == State.RUNNING) mpv.setPropertyString(property, setting) }
    }

    suspend fun getProperty(name: String): String? =
        suspendCancellableCoroutine { cont ->
            if (!isAlive()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            mpvExec.execute {
                try {
                    if (isDestroyed) cont.resume(null)
                    else cont.resume(mpv.getPropertyString(name))
                } catch (e: Exception) {
                    logException(e)
                    cont.resume(null)
                }
            }
        }

    fun command(orders: Array<String>) {
        onMpvThread { if (state >= State.CREATED && state < State.CLEANUP) mpv.command(orders) }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        applySurfaceDimensions()
        onMpvThread {
            if (state >= State.CREATED && state < State.CLEANUP) {
                mpv.attachSurface(surfaceView.holder.surface)
            }
        }
    }

    fun detachSurface() {
        onMpvThread {
            if (state >= State.CREATED && state < State.CLEANUP) {
                mpv.detachSurface()
                surfaceView = null
            }
        }
    }

    fun play(url: String, options: String? = null) {
        if (state >= State.CLEANUP) return
        if (!isPlaying) {
            if (options == null) {
                command(arrayOf("loadfile", url))
            } else {
                command(arrayOf("loadfile", url, "replace", "0", options))
            }
            command(arrayOf("set", "pause", "no"))
            hasPlayedOnce = true
            isPlaying = true
        }
    }

    fun pauseOrUnpause() {
        if (state >= State.CLEANUP) return
        if (!hasPlayedOnce) return
        if (isPlaying) pause() else unpause()
    }

    fun pause() {
        if (state >= State.CLEANUP) return
        if (!hasPlayedOnce) return
        if (isPlaying) {
            command(arrayOf("set", "pause", "yes"))
            isPlaying = false
        }
    }

    fun unpause() {
        if (state >= State.CLEANUP) return
        if (!hasPlayedOnce) return
        if (!isPlaying) {
            command(arrayOf("set", "pause", "no"))
            isPlaying = true
        }
    }

    fun seekToSeconds(seconds: Int) {
        if (state >= State.CLEANUP) return
        if (created) command(arrayOf("seek", seconds.toString(), "absolute"))
    }

    private fun applySurfaceDimensions() {
        if (state >= State.CLEANUP) return
        if (surfaceHeight != -1 && surfaceWidth != -1 && surfaceView != null) {
            surfaceView?.holder?.setFixedSize(surfaceWidth, surfaceHeight)
        }
    }

    fun setSurfaceWidth(width: Int) {
        surfaceWidth = width
        applySurfaceDimensions()
    }

    fun setSurfaceHeight(height: Int) {
        surfaceHeight = height
        applySurfaceDimensions()
    }

    fun cleanup() {
        if (!created || cleaning || state >= State.CLEANUP) return
        cleaning = true
        state = State.CLEANUP

        onMpvThread {
            runCatching { mpv.command(arrayOf("stop")) }
            runCatching { mpv.setPropertyString("pause", "yes") }
            runCatching { mpv.detachSurface() }
            runCatching { mpv.removeObservers() }
            runCatching { mpv.removeLogObservers() }
            runCatching { mpv.destroy() }
            created = false
            cleaning = false
            isDestroyed = true
            destroying = false
            mainHandler.post {
                try { mpvExec.shutdown() } catch (_: Throwable) {}
            }
            state = State.DESTROYED
        }
    }

    fun destroy() = cleanup()
}
