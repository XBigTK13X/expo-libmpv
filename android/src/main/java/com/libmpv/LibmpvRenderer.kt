package com.libmpv

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import com.libmpv.LibmpvSession
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream

class LibmpvRenderer(
    private val session: LibmpvSession,
    private val surfaceView: SurfaceView,
    private val onLog: (Map<String, Any>) -> Unit,
    private val onEvent: (Map<String, Any>) -> Unit
) : MPVLib.EventObserver, MPVLib.LogObserver {

    private enum class State {
        NEW,
        CREATED,
        INITIALIZED,
        ACTIVE,
        SHUTTING_DOWN,
        DESTROYED
    }

    companion object {
        private const val TAG = "expo-libmpv"
        private const val LOG_LEVEL_WARN = 30
    }

    private val stateLock = Any()

    @Volatile private var state: State = State.NEW
    @Volatile private var destroyed = false
    @Volatile private var loadedUrl: String? = null

    private var mpvDirectory: String? = null

    private inline val mpvAlive: Boolean
        get() = when (state) {
            State.CREATED, State.INITIALIZED, State.ACTIVE -> true
            else -> false
        }

    private inline val shuttingDown: Boolean
        get() = state == State.SHUTTING_DOWN || state == State.DESTROYED

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runCommand(command: Array<String>) {
        if (!mpvAlive || shuttingDown) return
        try {
            MPVLib.command(command)
        } catch (e: Exception) {
            logException(e)
        }
    }

    fun setOptionString(option: String, value: String) {
        if (!mpvAlive || shuttingDown) return
        try {
            MPVLib.setOptionString(option, value)
        } catch (e: Exception) {
            logException(e)
        }
    }


    fun start() {
        synchronized(stateLock) {
            if (state != State.NEW && state != State.DESTROYED) return
            try {
                MPVLib.create(surfaceView.context.applicationContext)
                createMpvDirectory()
                state = State.CREATED
            } catch (e: Exception) {
                logException(e)
                return
            }
        }

        initMpv()
        attachSurfaceAndConfigure()
    }

    fun destroy() {
    val doTeardown: Boolean
    synchronized(stateLock) {
        doTeardown = when (state) {
            State.NEW, State.SHUTTING_DOWN, State.DESTROYED -> false
            else -> true
        }
        if (doTeardown) state = State.SHUTTING_DOWN
    }
    if (!doTeardown) return

    destroyed = true

    try {
        loadedUrl = null
        stopPlayback()
        detachSurfaceInternal()

        mainHandler.post {
            try {
                MPVLib.destroy()
            } finally {
                synchronized(stateLock) {
                    state = State.DESTROYED
                }
            }
        }
    } catch (e: Exception) {
        logException(e)
        synchronized(stateLock) {
            state = State.DESTROYED
        }
    }
}


    fun onSessionUpdated() {
        if (!mpvAlive || shuttingDown) return
        maybeStartPlayback()
        applyDeferredState()
        applyContinuousState()
    }

    private fun initMpv() {
        synchronized(stateLock) {
            if (shuttingDown || state != State.CREATED) return
            try {
                MPVLib.init()
                state = State.INITIALIZED
            } catch (e: Exception) {
                logException(e)
                return
            }
        }
        addObservers()
    }

    private fun attachSurfaceAndConfigure() {
        if (!mpvAlive || shuttingDown) return
        try {
            MPVLib.attachSurface(surfaceView.holder.surface)
            synchronized(stateLock) {
                if (state == State.CREATED || state == State.INITIALIZED) {
                    state = State.ACTIVE
                }
            }
            applyConfiguration()
            MPVLib.setPropertyString("pause", "yes")
            maybeStartPlayback()
        } catch (e: Exception) {
            logException(e)
        }
    }

    private fun addObservers() {
        if (!mpvAlive || shuttingDown) return
        try {
            MPVLib.removeObservers()
            MPVLib.addObserver(this)
            MPVLib.removeLogObservers()
            MPVLib.addLogObserver(this)

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
        }
    }

    private fun applyConfiguration() {
        if (!mpvAlive || shuttingDown) return
        try {
            MPVLib.setOptionString("force-window", "no")
            MPVLib.setOptionString("config", "yes")
            mpvDirectory?.let {
                MPVLib.setOptionString("config-dir", it)
                MPVLib.setOptionString("sub-font-dir", it)
            }

            MPVLib.setOptionString("keep-open", "always")
            MPVLib.setOptionString("save-position-on-quit", "no")
            MPVLib.setOptionString("ytdl", "no")
            MPVLib.setOptionString("msg-level", "all=no")

            session.videoOutput?.let { videoOutput ->
                MPVLib.setOptionString("vo", videoOutput)
            }

            val decodingMode = session.decodingMode
            val acceleratedCodecs = session.acceleratedCodecs
            if (!decodingMode.isNullOrBlank() && !acceleratedCodecs.isNullOrBlank()) {
                MPVLib.setOptionString("hwdec", decodingMode)
                if (decodingMode != "no"){
                    MPVLib.setOptionString("hwdec-codecs", acceleratedCodecs)
                }
            }

            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")

            val videoSync = session.videoSync
            if(!videoSync.isNullOrBlank()){
                if(videoSync == "display-resample"){
                    MPVLib.setOptionString("video-sync", "display-resample")
                    MPVLib.setOptionString("audio-pitch-correction","no")
                    MPVLib.setOptionString("autosync", "1")
                    MPVLib.setOptionString("correct-pts","no")
                }
                if(videoSync == "audio"){
                    MPVLib.setOptionString("video-sync", "audio")
                    MPVLib.setOptionString("audio-pitch-correction","yes")
                    MPVLib.setOptionString("autosync", "0")
                    MPVLib.setOptionString("correct-pts","yes")
                }
            }

            MPVLib.setOptionString("scale", "bilinear")
            MPVLib.setOptionString("dscale", "bilinear")
            MPVLib.setOptionString("tscale","off")
            MPVLib.setOptionString("interpolation","no")

            MPVLib.setOptionString("ao", "audiotrack")
            MPVLib.setOptionString("alang", "")

            MPVLib.setOptionString("sub-font-provider", "none")
            MPVLib.setOptionString("slang", "")
            MPVLib.setOptionString("sub-scale-with-window", "yes")
            MPVLib.setOptionString("sub-use-margins", "no")

            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-pause-initial", "yes")
            MPVLib.setOptionString("audio-buffer","2.0")

        } catch (e: Exception) {
            logException(e)
        }
    }

    private fun maybeStartPlayback() {
        val url = session.playUrl ?: return
          if (loadedUrl == url) return
        try {
              loadedUrl = url
            MPVLib.command(arrayOf("loadfile", url, "replace"))
            applyContinuousState()
        } catch (e: Exception) {
            logException(e)
        }
    }

    private fun applyContinuousState() {
        if (!mpvAlive || shuttingDown) return
        if (!session.hasFileLoaded) return

        MPVLib.command(
            arrayOf("set", "pause", if (session.isPlaying) "no" else "yes")
        )
    }

    private fun applyDeferredState() {
        if (!mpvAlive || shuttingDown) return

        session.seekToSeconds?.let { target ->
            if (session.needsApply(LibmpvSession.MpvIntent.SEEK)) {
                val timePos = MPVLib.getPropertyDouble("time-pos")
                val seekable = MPVLib.getPropertyBoolean("seekable") == true

                if (seekable && timePos != null && kotlin.math.abs(timePos - target) > 0.5) {
                    MPVLib.command(arrayOf("seek", target.toString(), "absolute"))
                    session.markApplied(LibmpvSession.MpvIntent.SEEK)
                }
            }
        }

        session.selectedAudioTrack?.let {
            if (session.needsApply(LibmpvSession.MpvIntent.AUDIO_TRACK)) {
                val aid = if (it == -1) "no" else (it + 1).toString()
                MPVLib.command(arrayOf("set", "aid", aid))
                session.markApplied(LibmpvSession.MpvIntent.AUDIO_TRACK)
            }
        }

        session.selectedSubtitleTrack?.let {
            if (session.needsApply(LibmpvSession.MpvIntent.SUBTITLE_TRACK)) {
                val sid = if (it == -1) "no" else (it + 1).toString()
                MPVLib.command(arrayOf("set", "sid", sid))
                session.markApplied(LibmpvSession.MpvIntent.SUBTITLE_TRACK)
            }
        }
    }

    private fun stopPlayback() {
        if (!mpvAlive) return
        try {
            MPVLib.command(arrayOf("stop"))
            MPVLib.setPropertyString("pause", "yes")
            MPVLib.setPropertyString("vo", "null")
            MPVLib.setPropertyString("ao", "null")
        } catch (e: Exception) {
            logException(e)
        }
    }

    private fun detachSurfaceInternal() {
        try {
            MPVLib.detachSurface()
        } catch (e: Exception) {
            logException(e)
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (shuttingDown) return
        if (level <= LOG_LEVEL_WARN) Log.w(TAG, "[$prefix] $text")
        onLog(mapOf("prefix" to prefix, "level" to level, "text" to text))
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED,
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                session.hasFileLoaded = true
                mainHandler.post {
                    applyDeferredState()
                    MPVLib.setPropertyString("pause", if (session.isPlaying) "no" else "yes")
                }
            }
        }
    }


    override fun eventProperty(property: String) =
        onEvent(mapOf("property" to property, "kind" to "none"))

    override fun eventProperty(property: String, value: Long) =
        onEvent(mapOf("property" to property, "kind" to "long", "value" to value))

    override fun eventProperty(property: String, value: Double) =
        onEvent(mapOf("property" to property, "kind" to "double", "value" to value))

    override fun eventProperty(property: String, value: Boolean) =
        onEvent(mapOf("property" to property, "kind" to "boolean", "value" to value))

    override fun eventProperty(property: String, value: String) =
        onEvent(mapOf("property" to property, "kind" to "string", "value" to value))

    private fun createMpvDirectory() {
        if (shuttingDown) return

        val ctx = surfaceView.context.applicationContext
        val dir = File(ctx.getExternalFilesDir("mpv"), "mpv")

        try {
            if (!dir.exists() && !dir.mkdirs()) return
            mpvDirectory = dir.absolutePath

            val subFont = File(dir, "subfont.ttf")
            if (!subFont.exists()) {
                ctx.assets.open("subfont.ttf").use { inS ->
                    FileOutputStream(subFont).use { outS ->
                        inS.copyTo(outS)
                    }
                }
            }

            val mpvConf = File(dir, "mpv.conf")
            if (!mpvConf.exists()) {
                ctx.assets.open("mpv.conf").use { inS ->
                    FileOutputStream(mpvConf).use { outS ->
                        inS.copyTo(outS)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "mpv directory init failed", e)
        }
    }

    private fun logException(e: Exception) {
        if (shuttingDown) return
        try {
            MPVLib.logMessage("RNLE", 20, e.message ?: "Unknown mpv error")
        } catch (_: Exception) {
            Log.e(TAG, "mpv error", e)
        }
    }
}
