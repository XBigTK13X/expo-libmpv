package com.libmpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.jdtech.mpv.MPVLib
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher

class LibmpvView(context: Context, appContext: AppContext) :
    ExpoView(context, appContext),
    SurfaceHolder.Callback,
    MPVLib.LogObserver,
    MPVLib.EventObserver {

    var playIssued = false
    private var fileLoaded = false
    var canSeekNow = false
    var pendingSeekSeconds: Double? = null
    var seekAppliedForCurrentFile = false

    private val onLibmpvLog by EventDispatcher()
    private val onLibmpvEvent by EventDispatcher()

    private var surfaceAvailable = false
    private var isSurfaceCreated = false

    val mpv: LibmpvWrapper = LibmpvWrapper(context)
    private val surfaceView = SurfaceView(context)

    var playUrl: String? = null
    var surfaceWidth: Int? = null
    var surfaceHeight: Int? = null
    var audioIndex: Int? = null
    var subtitleIndex: Int? = null
    var videoOutput: String? = null
    var decodingMode: String? = null
    var acceleratedCodecs: String? = null

    init {
        surfaceView.holder.addCallback(this)

        addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        appContext.activityProvider?.currentActivity?.let { activity ->
            if (activity is LifecycleOwner) {
                activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        log("LibmpvView", "Lifecycle onDestroy -> cleanup()")
                        cleanup()
                    }
                })
            }
        }
    }

    fun log(method: String, argument: String) {
        onLibmpvLog(mapOf("method" to method, "argument" to argument))
    }

    fun isSurfaceReady(): Boolean = isSurfaceCreated

fun setSeekToSeconds(value: Double) {
    pendingSeekSeconds = value
    tryApplySeek()
}


    fun attemptCreation() {
        val ready =
            playUrl != null &&
            surfaceWidth != null &&
            surfaceHeight != null &&
            videoOutput != null &&
            decodingMode != null &&
            acceleratedCodecs != null

        if (!ready) {
            log("LibmpvView.attemptCreation", "attemptCreation wasn't ready")
            return
        }

        createNativePlayer()
        maybeStartPlayback()
    }

    fun createNativePlayer() {
        if (mpv.isCreated()) return

        playIssued = false
        fileLoaded = false
        canSeekNow = false

        mpv.createManagedInstance()
        prepareMpvSettings()
        mpv.initNativeBinding()

        log("LibmpvView.createNativePlayer", "mpv settings prepared + initialized.")
        tryAttachSurface()
    }

    fun seekToSeconds(seconds: Double) {
        pendingSeekSeconds = seconds
        maybeApplySeek()
    }

    private fun maybeApplySeek() {
        if (!canSeekNow) return
        if (!mpv.isCreated()) return

        pendingSeekSeconds?.let {
            mpv.command(arrayOf("seek", it.toString(), "absolute"))
            pendingSeekSeconds = null
        }
    }

private fun tryApplySeek() {
    val seconds = pendingSeekSeconds ?: return
    if (!mpv.isCreated()) return
    if (!canSeekNow) return
    if (seekAppliedForCurrentFile) return

    mpv.seekToSeconds(seconds)
    seekAppliedForCurrentFile = true
}



    fun selectAudioTrack(index: Int) {
        audioIndex = index
        maybeApplyAudioTrack()
    }

    private fun maybeApplyAudioTrack() {
        if (!canSeekNow || !mpv.isCreated()) return

        audioIndex?.let {
            val aid = if (it == -1) "no" else (it + 1).toString()
            mpv.command(arrayOf("set", "aid", aid))
        }
    }

    fun selectSubtitleTrack(index: Int) {
        subtitleIndex = index
        maybeApplySubtitleTrack()
    }

    private fun maybeApplySubtitleTrack() {
        if (!canSeekNow || !mpv.isCreated()) return

        subtitleIndex?.let {
            val sid = if (it == -1) "no" else (it + 1).toString()
            mpv.command(arrayOf("set", "sid", sid))
        }
    }

    private fun applyPendingPlaybackState() {
        maybeApplyAudioTrack()
        maybeApplySubtitleTrack()
        maybeApplySeek()
    }

    fun runCommand(orders: String) {
        mpv.command(orders.split("|").toTypedArray())
    }

    fun setOptionString(options: String) {
        val parts = options.split("|")
        mpv.setOptionString(parts[0], parts[1])
    }

    private fun prepareMpvSettings() {
        mpv.addLogObserver(this)
        mpv.addEventObserver(this)
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("keep-open", "always")

        videoOutput?.let { mpv.setOptionString("vo", it) }

        decodingMode?.let { mode ->
            acceleratedCodecs?.let { codecs ->
                mpv.setOptionString("hwdec", mode)
                if (mode != "no") {
                    mpv.setOptionString("hwdec-codecs", codecs)
                }
            }
        }
    }

    private fun maybeStartPlayback() {
        if (playIssued || !isSurfaceCreated || !mpv.isCreated()) return
        val url = playUrl ?: return

        mpv.setOptionString("force-window", "yes")
        mpv.play(url, "")

        playIssued = true
    }

    private fun tryAttachSurface() {
        if (!surfaceAvailable || !mpv.isCreated()) return
        mpv.attachSurface(surfaceView)
        maybeStartPlayback()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceAvailable = true
        isSurfaceCreated = true
        tryAttachSurface()
        log("LibmpvView.surfaceCreated", "Surface created")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceAvailable = false
        isSurfaceCreated = false
        try { mpv.detachSurface() } catch (_: Throwable) {}
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    fun cleanup() {
        Thread {
            Handler(Looper.getMainLooper()).post {
                try {
                    surfaceView.holder.removeCallback(this)
                    mpv.cleanup()
                } catch (_: Throwable) {}
            }
        }.start()
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        onLibmpvLog(mapOf("prefix" to prefix, "level" to "$level", "text" to text))
    }

    override fun event(eventId: Int) {
        when (eventId) {

            8, 21 -> { // FILE_LOADED or PLAYBACK_RESTART
                canSeekNow = true
                seekAppliedForCurrentFile = false

                Handler(Looper.getMainLooper()).post {
                    tryApplySeek()
                }
            }
        }

        onLibmpvEvent(
            mapOf(
                "eventId" to "$eventId",
                "kind" to "eventId"
            )
        )
    }



    override fun eventProperty(property: String) {
        onLibmpvEvent(mapOf("property" to property, "kind" to "none"))
    }

    override fun eventProperty(property: String, value: Long) {
        onLibmpvEvent(mapOf("property" to property, "kind" to "long", "value" to "$value"))
    }

    override fun eventProperty(property: String, value: Double) {
        onLibmpvEvent(mapOf("property" to property, "kind" to "double", "value" to "$value"))
    }

    override fun eventProperty(property: String, value: Boolean) {
        onLibmpvEvent(mapOf("property" to property, "kind" to "boolean", "value" to value))
    }

    override fun eventProperty(property: String, value: String) {
        onLibmpvEvent(mapOf("property" to property, "kind" to "string", "value" to value))
    }
}
