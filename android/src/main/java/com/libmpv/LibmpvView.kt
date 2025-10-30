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
import kotlinx.coroutines.runBlocking

class LibmpvView(context: Context, appContext: AppContext) :
  ExpoView(context, appContext),
  SurfaceHolder.Callback,
  MPVLib.LogObserver,
  MPVLib.EventObserver {

  companion object {
    const val HARDWARE_OPTIONS = "mediacodec-copy"
    const val ACCELERATED_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"
  }

  private val onLibmpvLog by EventDispatcher()
  private val onLibmpvEvent by EventDispatcher()

  @Volatile private var isSurfaceCreated = false
  @Volatile private var cleaning = false
  private val mainHandler = Handler(Looper.getMainLooper())

  val mpv: LibmpvWrapper = LibmpvWrapper(context)
  private val surfaceView: SurfaceView = SurfaceView(context)

  // JavaScript props
  var playUrl: String? = null
  var surfaceWidth: Int? = null
  var surfaceHeight: Int? = null
  var audioIndex: Int? = null
  var subtitleIndex: Int? = null
  var useHardwareDecoder: Boolean? = null
  var seekToSeconds: Int? = null

  init {
    surfaceView.holder.addCallback(this)
    val layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )

    appContext.activityProvider?.currentActivity?.let { activity ->
      if (activity is androidx.lifecycle.LifecycleOwner) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) {
            log("LibmpvView", "Lifecycle onDestroy -> cleanup()")
            cleanup()
          }
        })
      }
    }

    addView(surfaceView, layoutParams)
  }

  private fun isAlive(): Boolean = mpv.isAlive()

  fun isSurfaceReady(): Boolean = isSurfaceCreated

  fun attemptCreation() {
    val allPropsReady = playUrl != null &&
                        surfaceWidth != null &&
                        surfaceHeight != null &&
                        audioIndex != null &&
                        subtitleIndex != null &&
                        useHardwareDecoder != null

    if (allPropsReady) {
      log("LibmpvView.attemptCreation", "Initializing MPV instance")
      createNativePlayer()
    } else {
      log("LibmpvView.attemptCreation", "attemptCreation wasn't ready")
    }
  }

  fun createNativePlayer() {
    mpv.create()
    prepareMpvSettings()
    log("LibmpvView.createNativePlayer", "mpv settings prepared. Waiting on surface creation.")
  }

  fun runCommand(orders: String) {
    if (!isAlive()) return
    mpv.command(orders.split("|").toTypedArray())
  }

  fun setOptionString(options: String) {
    if (!isAlive()) return
    val parts = options.split("|").toTypedArray()
    if (parts.size == 2) mpv.setOptionString(parts[0], parts[1])
  }

  fun getProperty(name: String): String? {
    if (!isAlive()) return null
    return runBlocking { mpv.getProperty(name) }
  }

  private fun prepareMpvSettings() {
    mpv.addLogObserver(this)
    mpv.addEventObserver(this)

    mpv.setOptionString("force-window", "no")
    mpv.setOptionString("config", "yes")

    mpv.getMpvDirectoryPath()?.let { dir ->
      mpv.setOptionString("config-dir", dir)
      mpv.setOptionString("sub-font-dir", dir)
    }

    mpv.setOptionString("keep-open", "always")
    mpv.setOptionString("save-position-on-quit", "no")
    mpv.setOptionString("ytdl", "no")
    mpv.setOptionString("msg-level", "all=no")
    mpv.setOptionString("profile", "fast")
    mpv.setOptionString("vo", "gpu-next")

    if (useHardwareDecoder == true) {
      mpv.setOptionString("hwdec", HARDWARE_OPTIONS)
      mpv.setOptionString("hwdec-codecs", ACCELERATED_CODECS)
    } else {
      mpv.setOptionString("hwdec", "no")
    }

    mpv.setOptionString("gpu-context", "android")
    mpv.setOptionString("opengl-es", "yes")
    mpv.setOptionString("video-sync", "audio")
    mpv.setOptionString("audio-samplerate", "48000")
    mpv.setOptionString("audio-format", "float")
    mpv.setOptionString("audio-buffer", "2.0")
    mpv.setOptionString("ao", "audiotrack")
    mpv.setOptionString("alang", "")
    mpv.setOptionString("sub-font-provider", "none")
    mpv.setOptionString("slang", "")
    mpv.setOptionString("sub-scale-with-window", "yes")
    mpv.setOptionString("sub-use-margins", "no")
    mpv.setOptionString("cache", "yes")
    mpv.setOptionString("cache-pause-initial", "yes")
    mpv.setOptionString("cache-secs", "5")
    mpv.setOptionString("demuxer-readahead-secs", "5")
    mpv.setOptionString("msg-level", "all=v")
  }

  fun log(method: String, argument: String) {
    onLibmpvLog(mapOf("method" to method, "argument" to argument))
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    mpv.attachSurface(surfaceView)
    prepareMpvPlayback()
    isSurfaceCreated = true
    log("LibmpvView.surfaceCreated", "Surface created and MPV attached")
  }

  private fun prepareMpvPlayback() {
    mpv.init()
    mpv.setOptionString("force-window", "yes")

    val aid = if (audioIndex == -1) "aid=no" else "aid=${(audioIndex ?: 0) + 1}"
    val sid = if (subtitleIndex == -1) "sid=no" else "sid=${(subtitleIndex ?: 0) + 1}"
    val opts = "vid=1,$aid,$sid"
    val url = playUrl ?: ""
    mpv.play(url, opts)
    if (seekToSeconds != null && seekToSeconds!! > 0) {
      mpv.seekToSeconds(seekToSeconds!!)
      log("LibmpvView.prepareMpvPlayback", "Applied initial seek to $seekToSeconds")
    }
  }

  fun setHardwareDecoder(useHardware: Boolean) {
    if (!isAlive()) return
    useHardwareDecoder = useHardware
    if (useHardware) {
      mpv.setOptionString("hwdec", HARDWARE_OPTIONS)
      mpv.setOptionString("hwdec-codecs", ACCELERATED_CODECS)
    } else {
      mpv.setOptionString("hwdec", "no")
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

  fun cleanup() {
    if (cleaning) return
    cleaning = true
    Thread {
      try {
        if (!isAlive()) return@Thread
        runCatching { mpv.setPropertyString("pause", "yes") }
        runCatching { mpv.setPropertyString("ao", "null") }

        mainHandler.post {
          try {
            surfaceView.holder.removeCallback(this@LibmpvView)
            mpv.cleanup()
          } catch (_: Exception) {}
          cleaning = false
        }
      } catch (_: Throwable) {}
    }.start()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    if (!isAlive()) return
    runCatching { mpv.setPropertyString("pause", "yes") }
    runCatching { mpv.setPropertyString("vo", "null") }
    runCatching { mpv.setPropertyString("force-window", "no") }
    runCatching { mpv.detachSurface() }
    isSurfaceCreated = false
  }

  override fun logMessage(prefix: String, level: Int, text: String) {
    onLibmpvLog(mapOf("prefix" to prefix, "level" to "$level", "text" to text))
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
    onLibmpvEvent(mapOf("property" to property, "kind" to "boolean", "value" to if (value) "true" else "false"))
  }

  override fun eventProperty(property: String, value: String) {
    onLibmpvEvent(mapOf("property" to property, "kind" to "string", "value" to value))
  }

  override fun event(eventId: Int) {
    onLibmpvEvent(mapOf("eventId" to "$eventId", "kind" to "eventId"))
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    if (!cleaning) {
      cleaning = true
      cleanup()
    }
  }
}
