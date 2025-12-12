package com.libmpv

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.libmpv.LibmpvSession
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView
import expo.modules.kotlin.viewevent.EventDispatcher

class LibmpvView(
    context: Context,
    appContext: AppContext
) : ExpoView(context, appContext), SurfaceHolder.Callback {

    val session = LibmpvSession()
    private val onLibmpvLog by EventDispatcher()
    private val onLibmpvEvent by EventDispatcher()
    private val surfaceView = SurfaceView(context)
    private var surfaceReady = false
    private var attached = false
    private var renderer: LibmpvRenderer? = null

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
                        cleanup()
                    }
                })
            }
        }
    }

    private fun shouldHaveRenderer(): Boolean {
        return attached && surfaceReady
    }

private fun reconcileRenderer() {
    if (renderer == null && attached) {
        renderer = LibmpvRenderer(
            session = session,
            surfaceView = surfaceView,
            onLog = { payload -> onLibmpvLog(payload) },
            onEvent = { payload -> onLibmpvEvent(payload) }
        )
        renderer!!.start()
    }

    renderer?.let { r ->
        if (surfaceReady) {
            r.attachSurfaceIfNeeded()
        }
    }
}


    fun onSessionUpdatedFromProps() {
        reconcileRenderer()
        renderer?.onSessionUpdated()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        reconcileRenderer()
    }

    override fun onDetachedFromWindow() {
        attached = false
        reconcileRenderer()
        super.onDetachedFromWindow()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        renderer?.surfaceReady = true
        renderer?.attachSurfaceIfNeeded()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        renderer?.surfaceReady = false
        reconcileRenderer()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) = Unit

    fun runCommand(orders: String) {
        val parts = orders.split("|")
        renderer?.runCommand(parts.toTypedArray())
    }

    fun setOptionString(options: String) {
        val parts = options.split("|")
        if (parts.size == 2) {
            renderer?.setOptionString(parts[0], parts[1])
        }
    }

    fun cleanup() {
        renderer?.destroy()
        renderer = null
        surfaceView.holder.removeCallback(this)
    }
}
