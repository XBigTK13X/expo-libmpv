package com.libmpv

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.util.Log
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class LibmpvWrapper(private val applicationContext: Context) {
    companion object {
        private const val TAG = "react-native-libmpv"
        private var swallow = true
    }

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

    fun create(): Boolean {
        mpv.create(applicationContext)
        createMpvDirectory()
        created = true
        return true
    }

    fun isCreated(): Boolean = created
    fun isPlaying(): Boolean = isPlaying
    fun hasPlayedOnce(): Boolean = hasPlayedOnce
    fun getMpvDirectoryPath(): String? = mpvDirectory

    private fun createMpvDirectory() {
        if (isDestroyed) return
        val mpvDir = File(applicationContext.getExternalFilesDir("mpv"), "mpv")
        try {
            mpvDirectory = mpvDir.absolutePath
            if (!mpvDir.exists() && !mpvDir.mkdirs()) {
                Log.e(TAG, "exception", IllegalArgumentException("Unable to create $mpvDir"))
                return
            }

            // Copy font
            val mpvFontPath = "${mpvDir}/subfont.ttf"
            applicationContext.assets.open("subfont.ttf").use { subfontIn ->
                FileOutputStream(mpvFontPath).use { fontOut ->
                    subfontIn.copyTo(fontOut)
                }
            }

            // Copy conf
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

    private fun logException(exception: Exception) {
        if (isDestroyed) return
        try {
            val message: String = (exception.message as? String) ?: "Unable to read error message"
            logObserver?.logMessage("RNLE", 20, message)
        } catch (e: Exception) {
            if (!swallow) throw e
        }
    }

    fun addEventObserver(observer: MPVLib.EventObserver) {
        if (isDestroyed) return
        try {
            if (!created) return
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
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun addLogObserver(observer: MPVLib.LogObserver) {
        if (isDestroyed) return
        try {
            if (!created) return
            mpv.removeLogObservers()
            logObserver = observer
            mpv.addLogObserver(logObserver)
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun setOptionString(option: String, setting: String) {
        if (isDestroyed) return
        try {
            if (created) mpv.setOptionString(option, setting)
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun setPropertyString(property: String, setting: String) {
        if (isDestroyed) return
        try {
            if (created) mpv.setPropertyString(property, setting)
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun init() {
        if (isDestroyed) return
        try {
            if (created) mpv.init()
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun command(orders: Array<String>) {
        if (isDestroyed) return
        try {
            if (created) mpv.command(orders)
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        if (isDestroyed) return
        try {
            if (created) {
                this.surfaceView = surfaceView
                applySurfaceDimensions()
                mpv.attachSurface(surfaceView.holder.surface)
            }
        } catch (e: Exception) {
            logException(e)
            if (!swallow) throw e
        }
    }

    fun play(url: String, options: String? = null) {
        if (isDestroyed) return
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
        if (isDestroyed) return
        if (!hasPlayedOnce) return
        if (isPlaying) pause() else unpause()
    }

    fun pause() {
        if (isDestroyed) return
        if (!hasPlayedOnce) return
        if (isPlaying) {
            command(arrayOf("set", "pause", "yes"))
            isPlaying = false
        }
    }

    fun unpause() {
        if (isDestroyed) return
        if (!hasPlayedOnce) return
        if (!isPlaying) {
            command(arrayOf("set", "pause", "no"))
            isPlaying = true
        }
    }

    fun seekToSeconds(seconds: Int) {
        if (isDestroyed) return
        if (created) {
            command(arrayOf("seek", seconds.toString(), "absolute"))
        }
    }

    private fun applySurfaceDimensions() {
        if (isDestroyed) return
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

  fun detachSurface(){
    if(isDestroyed) return
    try{

    }catch(swallow:Exception){}
  }


fun cleanup() {
  if (!created || cleaning) return
  cleaning = true

  Thread {
    try {
      safe { command(arrayOf("stop")) }
      safe { setPropertyString("pause", "yes") }
      safe { setPropertyString("vo", "null") }
      safe { setPropertyString("ao", "null") }
      safe { detachSurface() }
      Handler(Looper.getMainLooper()).post {
        safe { mpv.removeObservers() }
        safe { mpv.removeLogObservers() }
        safe { mpv.destroy() }
        created = false
        cleaning = false
      }
    } catch (e: Exception) {
      logException(e)
      cleaning = false
    }
  }.start()
}

  private inline fun safe(block: () -> Unit) {
    try { if (created) block() } catch (e: Exception) { logException(e) }
  }

  fun destroy() = cleanup()
}
