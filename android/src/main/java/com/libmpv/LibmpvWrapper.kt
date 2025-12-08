package com.libmpv

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.util.Log
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream

class LibmpvWrapper(private val applicationContext: Context) {
    companion object {
        private const val TAG = "expo-libmpv"
        private var SWALLOW = true
    }

    private enum class State {
        NEW,
        CREATED,
        INITIALIZED,
        ACTIVE,
        SHUTTING_DOWN,
        DESTROYED
    }

    @Volatile private var state = State.NEW
    @Volatile private var isPlaying = false
    @Volatile private var hasPlayedOnce = false
    @Volatile private var pendingPlayUrl: String? = null
    @Volatile private var pendingPlayOptions: String? = null
    @Volatile private var playIssued = false


    private val stateLock = Any()

    private var mpvDirectory: String? = null
    private var surfaceWidth: Int = -1
    private var surfaceHeight: Int = -1
    private var surfaceView: SurfaceView? = null

    private inline val isAlive: Boolean
        get() = when (state) {
            State.CREATED, State.INITIALIZED, State.ACTIVE -> true
            else -> false
        }

    private inline val isShuttingDown: Boolean
        get() = state == State.SHUTTING_DOWN || state == State.DESTROYED

    fun isCreated(): Boolean = isAlive

    fun isPlaying(): Boolean = isPlaying

    fun hasPlayedOnce(): Boolean = hasPlayedOnce

    fun getMpvDirectoryPath(): String? = mpvDirectory

    private fun logException(exception: Exception) {
        if (isShuttingDown) return
        try {
            val message: String = (exception.message as? String) ?: "Unable to read error message"
            MPVLib.logMessage("RNLE", 20, message)
        } catch (e: Exception) {
            if (!SWALLOW) {
                throw e
            }
        }
    }

    private inline fun ifAlive(block: () -> Unit) {
        if (isShuttingDown || !isAlive) return
        block()
    }

    fun createManagedInstance(): Boolean {
        synchronized(stateLock) {
            if (state != State.NEW && state != State.DESTROYED) {
                return false
            }
            return try {
                MPVLib.create(applicationContext)
                createMpvDirectory()
                state = State.CREATED
                true
            } catch (e: Exception) {
                logException(e)
                false
            }
        }
    }

    fun initNativeBinding() {
        synchronized(stateLock) {
            if (isShuttingDown || state != State.CREATED) {
                return
            }
            try {
                MPVLib.init()
                state = State.INITIALIZED
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    private fun createMpvDirectory() {
        if (isShuttingDown) return
        val mpvDir = File(applicationContext.getExternalFilesDir("mpv"), "mpv")
        try {
            mpvDirectory = mpvDir.absolutePath
            if (!mpvDir.exists() && !mpvDir.mkdirs()) {
                Log.e(TAG, "exception", IllegalArgumentException("Unable to create $mpvDir"))
                return
            }

            val mpvFontPath = "${mpvDir}/subfont.ttf"
            applicationContext.assets.open("subfont.ttf").use { subfontIn ->
                FileOutputStream(mpvFontPath).use { fontOut ->
                    subfontIn.copyTo(fontOut)
                }
            }

            val mpvConfPath = "${mpvDir}/mpv.conf"
            applicationContext.assets.open("mpv.conf").use { mpvConfIn ->
                FileOutputStream(mpvConfPath).use { confOut ->
                    mpvConfIn.copyTo(confOut)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create the directory $mpvDir", e)
        }
    }

    fun addEventObserver(observer: MPVLib.EventObserver) {
        ifAlive {
            try {
                MPVLib.removeObservers()
                MPVLib.addObserver(observer)

                MPVLib.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("seekable", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    fun addLogObserver(observer: MPVLib.LogObserver) {
        ifAlive {
            try {
                MPVLib.removeLogObservers()
                MPVLib.addLogObserver(observer)
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    fun setOptionString(option: String, setting: String) {
        ifAlive {
            try {
                MPVLib.setOptionString(option, setting)
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    fun setPropertyString(property: String, setting: String) {
        ifAlive {
            try {
                MPVLib.setPropertyString(property, setting)
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    fun command(orders: Array<String>) {
        ifAlive {
            try {
                MPVLib.command(orders)
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        ifAlive {
            try {
                this.surfaceView = surfaceView
                applySurfaceDimensions()
                MPVLib.attachSurface(surfaceView.holder.surface)
                synchronized(stateLock) {
                    if (state == State.INITIALIZED || state == State.CREATED) {
                        state = State.ACTIVE
                    }
                }
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    private fun maybeStartPlayback() {
        if (!isAlive || surfaceView == null || playIssued) return

        val url = pendingPlayUrl ?: return
        val options = pendingPlayOptions

        playIssued = true
        pendingPlayUrl = null
        pendingPlayOptions = null

        command(arrayOf("loadfile", url, "replace"))
        command(arrayOf("set", "pause", "no"))
        hasPlayedOnce = true
        isPlaying = true
    }


    fun prepareForNewPlayback() {
        hasPlayedOnce = false
        isPlaying = false
        playIssued = false
    }

    fun play(url: String, options: String? = null) {
        prepareForNewPlayback()
        pendingPlayUrl = url
        pendingPlayOptions = options
        maybeStartPlayback()
    }



    fun pauseOrUnpause() {
        if (!hasPlayedOnce || isShuttingDown) return
        if (isPlaying) {
            pause()
        } else {
            unpause()
        }
    }

    fun pause() {
        if (!hasPlayedOnce || isShuttingDown) return
        if (isPlaying) {
            command(arrayOf("set", "pause", "yes"))
            isPlaying = false
        }
    }

    fun unpause() {
        if (!hasPlayedOnce || isShuttingDown) return
        if (!isPlaying) {
            command(arrayOf("set", "pause", "no"))
            isPlaying = true
        }
    }

    fun seekToSeconds(seconds: Double) {
        command(arrayOf("seek", seconds.toString(), "absolute"))
    }

    private fun applySurfaceDimensions() {
        if (isShuttingDown) return
        if (surfaceHeight != -1 && surfaceWidth != -1 && surfaceView != null) {
            surfaceView?.holder?.setFixedSize(surfaceWidth, surfaceHeight)
        }
    }

    fun setSurfaceWidth(width: Int) {
        if (isShuttingDown) return
        surfaceWidth = width
        applySurfaceDimensions()
    }

    fun setSurfaceHeight(height: Int) {
        if (isShuttingDown) return
        surfaceHeight = height
        applySurfaceDimensions()
    }

    fun detachSurface() {
        ifAlive {
            try {
                MPVLib.detachSurface()
            } catch (e: Exception) {
                logException(e)
                if (!SWALLOW) {
                    throw e
                }
            }
        }
    }

    private fun detachSurfaceInternal() {
        try {
            MPVLib.detachSurface()
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW) {
                throw e
            }
        } finally {
            surfaceView = null
        }
    }

    private fun tryStopPlayback() {
        try {
            command(arrayOf("stop"))
            setPropertyString("pause", "yes")
            setPropertyString("vo", "null")
            setPropertyString("ao", "null")
        } catch (e: Exception) {
            logException(e)
        } finally {
            isPlaying = false
        }
    }

    fun cleanup() {
        val doTeardown: Boolean
        synchronized(stateLock) {
            doTeardown = when (state) {
                State.NEW, State.SHUTTING_DOWN, State.DESTROYED -> false
                else -> true
            }
            if (doTeardown) {
                state = State.SHUTTING_DOWN
            }
        }
        if (!doTeardown) {
            return
        }

        try {
            if (hasPlayedOnce) {
                tryStopPlayback()
            }
            detachSurfaceInternal()

            Handler(Looper.getMainLooper()).post {
                try {
                    MPVLib.beginShutdown()
                    MPVLib.destroy()
                } catch (e: Exception) {
                    logException(e)
                    if (!SWALLOW) {
                        throw e
                    }
                } finally {
                    synchronized(stateLock) {
                        state = State.DESTROYED
                    }
                }
            }
        } catch (e: Exception) {
            logException(e)
            synchronized(stateLock) {
                if (state == State.SHUTTING_DOWN) {
                    state = State.DESTROYED
                }
            }
        }
    }

    fun destroy() {
        cleanup()
    }
}
