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
        private const val TAG = "expo-libmpv"
        private var SWALLOW = true
    }

    @Volatile private var created = false
    @Volatile private var inited = false
    @Volatile private var destroyed = false
    @Volatile private var cleaning = false
    @Volatile private var isPlaying = false
    @Volatile private var hasPlayedOnce = false

    private var mpvDirectory: String? = null
    private var surfaceWidth: Int = -1
    private var surfaceHeight: Int = -1
    private var surfaceView: SurfaceView? = null

    fun isCreated(): Boolean = created
    fun isPlaying(): Boolean = isPlaying
    fun hasPlayedOnce(): Boolean = hasPlayedOnce
    fun getMpvDirectoryPath(): String? = mpvDirectory

    private fun logException(exception: Exception) {
        if (destroyed){ return }
        try {
            val message: String = (exception.message as? String) ?: "Unable to read error message"
            MPVLib.logMessage("RNLE", 20, message)
        } catch (e: Exception) {
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun createManagedInstance(): Boolean {
        if(created){ return false }
        try{
            MPVLib.create(applicationContext)
            createMpvDirectory()
            created = true
            return true
        } catch(e:Exception){
            return false
        }
    }

    fun initNativeBinding() {
        if (destroyed || !created || inited){ return }
        try {
            MPVLib.init()
            inited = true
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    private fun createMpvDirectory() {
        if (destroyed){ return }
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

    fun addEventObserver(observer: MPVLib.EventObserver) {
        if (destroyed || !created){ return }
        try {
            MPVLib.removeObservers()
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("seekable", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun addLogObserver(observer: MPVLib.LogObserver) {
        if (destroyed || !created){ return }
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

    fun setOptionString(option: String, setting: String) {
        if (destroyed || !created){ return }
        try {
            MPVLib.setOptionString(option, setting)
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun setPropertyString(property: String, setting: String) {
        if (destroyed || !created){ return }
        try {
            MPVLib.setPropertyString(property, setting)
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun command(orders: Array<String>) {
        if (destroyed || !created){ return }
        try {
            MPVLib.command(orders)
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        if (destroyed || !created){ return }
        try {
            this.surfaceView = surfaceView
            applySurfaceDimensions()
            MPVLib.attachSurface(surfaceView.holder.surface)
        } catch (e: Exception) {
            logException(e)
            if (!SWALLOW){
                throw e
            }
        }
    }

    fun play(url: String, options: String? = null) {
        if (destroyed) { return }
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
        if (destroyed || !hasPlayedOnce){ return }
        if (isPlaying){
            pause()
        } else {
            unpause()
        }
    }

    fun pause() {
        if (destroyed || !hasPlayedOnce) { return }
        if (isPlaying) {
            command(arrayOf("set", "pause", "yes"))
            isPlaying = false
        }
    }

    fun unpause() {
        if (destroyed || !hasPlayedOnce) { return }
        if (!isPlaying) {
            command(arrayOf("set", "pause", "no"))
            isPlaying = true
        }
    }

    fun seekToSeconds(seconds: Int) {
        if (destroyed || !created) { return }
        command(arrayOf("seek", seconds.toString(), "absolute"))
    }

    private fun applySurfaceDimensions() {
        if (destroyed) { return }
        if (surfaceHeight != -1 && surfaceWidth != -1 && surfaceView != null) {
            surfaceView?.holder?.setFixedSize(surfaceWidth, surfaceHeight)
        }
    }

    fun setSurfaceWidth(width: Int) {
        if (destroyed) { return }
        surfaceWidth = width
        applySurfaceDimensions()
    }

    fun setSurfaceHeight(height: Int) {
        if (destroyed) { return }
        surfaceHeight = height
        applySurfaceDimensions()
    }

    fun detachSurface(){
        if(destroyed) { return }
        try{
            MPVLib.detachSurface()
        }
        catch(e:Exception){
            logException(e)
            if(!SWALLOW){
                throw e
            }
        }
    }


    fun cleanup() {
        if (!created || cleaning)
        {
            return
        }

        cleaning = true

        try {
            command(arrayOf("stop"))
            setPropertyString("pause", "yes")
            setPropertyString("vo", "null")
            setPropertyString("ao", "null")
            detachSurface()
            Handler(Looper.getMainLooper()).post {
                MPVLib.removeObservers()
                MPVLib.removeLogObservers()
                MPVLib.destroy()
                created = false
                cleaning = false
            }
        } catch (e: Exception) {
            logException(e)
            cleaning = false
        }
    }

    fun destroy(){
        return cleanup()
    }
}
