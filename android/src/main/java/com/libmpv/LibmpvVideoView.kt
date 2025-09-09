package com.libmpv

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import expo.modules.kotlin.events.EventDispatcher
import expo.modules.kotlin.events.EventName
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.views.ViewManager
import expo.modules.kotlin.views.Prop

class LibmpvVideoView(context: Context) :
  ExpoView(context),
  SurfaceView(context),
  SurfaceHolder.Callback,
  MPVLib.LogObserver,
  MPVLib.EventObserver {

  companion object {
    const val HWDEC = "mediacodec-copy"
    const val HWCODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"
  }

  private var isSurfaceCreated: Boolean = false
  private val mpv: LibmpvWrapper = LibmpvWrapper(context)

  private var playUrl: String? = null
  private var surfaceWidth: Int? = null
  private var surfaceHeight: Int? = null
  private var audioIndex: Int? = null
  private var subtitleIndex: Int? = null
  private var useHardwareDecoder: Boolean? = null

  private val eventDispatcher = EventDispatcher()

  init {
    holder.addCallback(this)
  }

  // Props from JS
  @Prop fun setPlayUrl(url: String?) { playUrl = url }
  @Prop fun setSurfaceWidth(width: Int?) { surfaceWidth = width }
  @Prop fun setSurfaceHeight(height: Int?) { surfaceHeight = height }
  @Prop fun setAudioIndex(index: Int?) { audioIndex = index }
  @Prop fun setSubtitleIndex(index: Int?) { subtitleIndex = index }
  @Prop fun setUseHardwareDecoder(useHw: Boolean?) { useHardwareDecoder = useHw }

  fun cleanup() {
    holder.removeCallback(this)
    mpv.cleanup()
  }

  fun isSurfaceReady(): Boolean = isSurfaceCreated

  fun createNativePlayer() {
    mpv.create()
    prepareMpvSettings()
    log("LibmpvVideoView.createNativePlayer", "mpv settings prepared. Waiting on surface creation.")
  }

  private fun prepareMpvSettings() {
    mpv.addLogObserver(this)
    mpv.addEventObserver(this)
    mpv.setOptionString("force-window", "no")

    mpv.setOptionString("config", "yes")
    mpv.setOptionString("config-dir", mpv.getMpvDirectoryPath())
    mpv.setOptionString("sub-font-dir", mpv.getMpvDirectoryPath())

    mpv.setOptionString("keep-open", "always")
    mpv.setOptionString("save-position-on-quit", "no")
    mpv.setOptionString("ytdl", "no")
    mpv.setOptionString("msg-level", "all=no")

    mpv.setOptionString("profile", "fast")
    mpv.setOptionString("vo", "gpu-next")

    if (useHardwareDecoder == true) {
      mpv.setOptionString("hwdec", HWDEC)
      mpv.setOptionString("hwdec-codecs", HWCODECS)
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

  private fun log(method: String, argument: String) {
    eventDispatcher.dispatch(LibmpvEvents.Log, mapOf(
      "method" to method,
      "argument" to argument
    ))
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    val width = surfaceWidth ?: 0
    val height = surfaceHeight ?: 0
    holder.setFixedSize(width, height)
    mpv.setPropertyString("android-surface-size", "${width}x$height")
    mpv.attachSurface(this)
    prepareMpvPlayback()
    isSurfaceCreated = true
    log("LibmpvVideoView.surfaceCreated", "Surface created and MPV should be playing")
  }

  private fun prepareMpvPlayback() {
    mpv.init()
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
    mpv.play(playUrl, options)
  }

  fun setHardwareDecoder(useHardware: Boolean) {
    useHardwareDecoder = useHardware
    if (useHardwareDecoder == true) {
      mpv.setOptionString("hwdec", HWDEC)
      mpv.setOptionString("hwdec-codecs", HWCODECS)
    } else {
      mpv.setOptionString("hwdec", "no")
    }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    mpv.setPropertyString("vo", "null")
    mpv.setPropertyString("force-window", "no")
    mpv.detachSurface()
  }

  // MPVLib.LogObserver
  override fun logMessage(prefix: String, level: Int, text: String) {
    eventDispatcher.dispatch(LibmpvEvents.Log, mapOf(
      "prefix" to prefix,
      "level" to "$level",
      "text" to text
    ))
  }

  // MPVLib.EventObserver
  override fun eventProperty(property: String) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "property" to property,
      "kind" to "none"
    ))
  }

  override fun eventProperty(property: String, value: Long) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "property" to property,
      "kind" to "long",
      "value" to "$value"
    ))
  }

  override fun eventProperty(property: String, value: Double) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "property" to property,
      "kind" to "double",
      "value" to "$value"
    ))
  }

  override fun eventProperty(property: String, value: Boolean) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "property" to property,
      "kind" to "boolean",
      "value" to if (value) "true" else "false"
    ))
  }

  override fun eventProperty(property: String, value: String) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "property" to property,
      "kind" to "string",
      "value" to value
    ))
  }

  override fun event(eventId: Int) {
    eventDispatcher.dispatch(LibmpvEvents.Event, mapOf(
      "eventId" to "$eventId",
      "kind" to "eventId"
    ))
  }
}

// Define event names for Expo
object LibmpvEvents {
  val Log = EventName("onLibmpvLog")
  val Event = EventName("onLibmpvEvent")
}

// View Manager that registers this view for Expo
class LibmpvVideoViewManager : ViewManager<LibmpvVideoView>() {
  override fun getName() = "LibmpvVideoView"

  override fun createView(context: Context) = LibmpvVideoView(context)
}
