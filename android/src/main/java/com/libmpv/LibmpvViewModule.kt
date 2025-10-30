package com.libmpv

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class LibmpvViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LibmpvView")

    View(LibmpvView::class) {
        Events("onLibmpvLog", "onLibmpvEvent")

        AsyncFunction("runCommand") { view: LibmpvView, orders: String ->
            view.runCommand(orders)
        }

        AsyncFunction("setOptionString") { view: LibmpvView, options: String ->
            view.setOptionString(options)
        }

        AsyncFunction("cleanup") { view: LibmpvView ->
            view.cleanup()
        }

        Prop("playUrl") { view: LibmpvView, playUrl: String ->
            view.playUrl = playUrl
            if (view.isSurfaceReady()) {
                view.mpv.play(playUrl)
            } else {
                view.attemptCreation()
            }
            view.log("setPlayUrl", playUrl)
        }

        Prop("useHardwareDecoder") { view: LibmpvView, useHardwareDecoder: Boolean ->
            view.useHardwareDecoder = useHardwareDecoder
            if (view.isSurfaceReady()) {
                view.setHardwareDecoder(useHardwareDecoder)
            } else {
                view.attemptCreation()
            }
            view.log("setUseHardwareDecoder", "$useHardwareDecoder")
        }

        Prop("surfaceWidth") { view: LibmpvView, surfaceWidth: Int ->
            view.surfaceWidth = surfaceWidth
            if (view.isSurfaceReady()) {
                view.mpv.setSurfaceWidth(surfaceWidth)
            } else {
                view.attemptCreation()
            }
            view.log("setSurfaceWidth", "$surfaceWidth")
        }

        Prop("surfaceHeight") { view: LibmpvView, surfaceHeight: Int ->
            view.surfaceHeight = surfaceHeight
            if (view.isSurfaceReady()) {
                view.mpv.setSurfaceHeight(surfaceHeight)
            } else {
                view.attemptCreation()
            }
            view.log("setSurfaceHeight", "$surfaceHeight")
        }

        Prop("selectedAudioTrack") { view: LibmpvView, audioTrackIndex: Int ->
            view.audioIndex = audioTrackIndex
            if (view.isSurfaceReady()) {
                val mpvIndex = if (audioTrackIndex != -1) (audioTrackIndex + 1).toString() else "no"
                view.mpv.setOptionString("aid", mpvIndex)
            } else {
                view.attemptCreation()
            }
            view.log("selectAudioTrack", "$audioTrackIndex")
        }

        Prop("selectedSubtitleTrack") { view: LibmpvView, subtitleTrackIndex: Int ->
            view.subtitleIndex = subtitleTrackIndex
            if (view.isSurfaceReady()) {
                val mpvIndex = if (subtitleTrackIndex != -1) (subtitleTrackIndex + 1).toString() else "no"
                view.mpv.setOptionString("sid", mpvIndex)
            } else {
                view.attemptCreation()
            }
            view.log("selectSubtitleTrack", "$subtitleTrackIndex")
        }

        Prop("seekToSeconds") { view: LibmpvView, seconds: Int ->
            if (view.isSurfaceReady()) {
                view.mpv.seekToSeconds(seconds)
            } else {
                view.seekToSeconds = seconds
            }
            view.log("seekToSeconds", "$seconds")
        }

        Prop("isPlaying") { view: LibmpvView, isPlaying: Boolean ->
            if (view.isSurfaceReady() && view.mpv.hasPlayedOnce()) {
                when {
                    isPlaying && !view.mpv.isPlaying() -> {
                        view.mpv.unpause()
                    }
                    !isPlaying && view.mpv.isPlaying() -> {
                        view.mpv.pause()
                    }
                }
            } else {
                view.attemptCreation()
            }
        }

        OnViewDestroys { view: LibmpvView ->
            view.cleanup()
        }
    }
  }
}
