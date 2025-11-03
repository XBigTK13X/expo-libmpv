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
  ExpoView(context,appContext),
  SurfaceHolder.Callback,
  MPVLib.LogObserver,
  MPVLib.EventObserver {

  companion object {
    const val HARDWARE_OPTIONS = "mediacodec-copy"
    const val ACCELERATED_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"
  }

  private val onLibmpvLog by EventDispatcher()
  private val onLibmpvEvent by EventDispatcher()

  private var isSurfaceCreated: Boolean = false
  val mpv: LibmpvWrapper = LibmpvWrapper(context)
  private val surfaceView: SurfaceView = SurfaceView(context)

  // JavaScript props
  var playUrl: String? = null
  var surfaceWidth: Int? = null
  var surfaceHeight: Int? = null
  var audioIndex: Int? = null
  var subtitleIndex: Int? = null
  var useHardwareDecoder: Boolean? = null

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

  fun isSurfaceReady(): Boolean {
    return isSurfaceCreated
  }

  fun attemptCreation(){
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
    mpv.createManagedInstance()
    prepareMpvSettings()
    log("LibmpvView.createNativePlayer", "mpv settings prepared. Waiting on surface creation.")
  }

  fun runCommand(orders: String){
    mpv.command(orders.split("|").toTypedArray())
  }

  fun setOptionString(options: String){
    val parts = options.split("|").toTypedArray()
    mpv.setOptionString(parts[0],parts[1])
  }

  private fun prepareMpvSettings() {
    mpv.addLogObserver(this)
    mpv.addEventObserver(this)
    mpv.setOptionString("force-window", "no")

    mpv.setOptionString("config", "yes")
    val mpvDir = mpv.getMpvDirectoryPath()
    mpvDir?.let{
      mpv.setOptionString("config-dir", mpvDir)
      mpv.setOptionString("sub-font-dir", mpvDir)
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
  }

  fun log(method: String, argument: String) {
    onLibmpvLog(mapOf(
      "method" to method,
      "argument" to argument
    ))
  }

  private fun prepareMpvPlayback() {
    mpv.initNativeBinding()
    mpv.setOptionString("force-window", "yes")
    var options = "vid=1"
    options += if (audioIndex == -1) {
      ",aid=no"
    } else {
      ",aid=${(audioIndex ?: 0) + 1}"
    }
    options += if (subtitleIndex == -1) {
      ",sid=no"
    } else {
      ",sid=${(subtitleIndex ?: 0) + 1}"
    }
    val url: String = (playUrl as? String) ?: ""
    mpv.play(url, options)
  }

  fun setHardwareDecoder(useHardware: Boolean) {
    useHardwareDecoder = useHardware
    if (useHardwareDecoder == true) {
      mpv.setOptionString("hwdec", HARDWARE_OPTIONS)
      mpv.setOptionString("hwdec-codecs", ACCELERATED_CODECS)
    } else {
      mpv.setOptionString("hwdec", "no")
    }
  }

  // SurfaceHolder.Callback
  override fun surfaceCreated(holder: SurfaceHolder) {
    val width = surfaceWidth ?: 0
    val height = surfaceHeight ?: 0

    // In the new Fabric version, this is stretching the content
    //holder.setFixedSize(width, height)
    //mpv.setPropertyString("android-surface-size", "${width}x${height}")

    mpv.attachSurface(surfaceView)
    prepareMpvPlayback()
    isSurfaceCreated = true
    log("LibmpvView.surfaceCreated", "Surface created and MPV should be playing")
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    try{
      mpv.setSurfaceHeight(height)
      mpv.setSurfaceWidth(width)
    }
    catch(e:Exception){}
  }

  fun cleanup() {
    // Launch on background executor so UI returns immediately
    Thread {
      try {
        try { mpv.setPropertyString("pause", "yes") } catch (_: Throwable) {}
        try { mpv.setPropertyString("ao", "null") } catch (_: Throwable) {}
        Handler(Looper.getMainLooper()).post {
          try {
            surfaceView.holder.removeCallback(this@LibmpvView)
            mpv.cleanup()
          } catch (e: Exception) {
          }
        }
      } catch (t: Throwable) {}
    }.start()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    try {
      mpv.setPropertyString("pause", "yes")
      mpv.setPropertyString("vo", "null")
      mpv.setPropertyString("force-window", "no")
    } catch (t: Throwable) {}
    try {
        mpv.detachSurface()
    } catch (t: Throwable) {}
  }


  // MPVLib.LogObserver
  override fun logMessage(prefix: String, level: Int, text: String) {
    onLibmpvLog(mapOf(
      "prefix" to prefix,
      "level" to "$level",
      "text" to text
    ))
  }

  // MPVLib.EventObserver
  override fun eventProperty(property: String) {
    onLibmpvEvent(mapOf(
      "property" to property,
      "kind" to "none"
    ))
  }

  override fun eventProperty(property: String, value: Long) {
    onLibmpvEvent(mapOf(
      "property" to property,
      "kind" to "long",
      "value" to "$value"
    ))
  }

  override fun eventProperty(property: String, value: Double) {
    onLibmpvEvent(mapOf(
      "property" to property,
      "kind" to "double",
      "value" to "$value"
    ))
  }

  override fun eventProperty(property: String, value: Boolean) {
    onLibmpvEvent(mapOf(
      "property" to property,
      "kind" to "boolean",
      "value" to if (value) "true" else "false"
    ))
  }

  override fun eventProperty(property: String, value: String) {
    onLibmpvEvent(mapOf(
      "property" to property,
      "kind" to "string",
      "value" to value
    ))
  }

  override fun event(eventId: Int) {
    onLibmpvEvent(mapOf(
      "eventId" to "$eventId",
      "kind" to "eventId"
    ))
  }

  override fun onDetachedFromWindow() {
    try{
    super.onDetachedFromWindow()
    cleanup()
    }
    catch(e:Exception){}
  }
}
