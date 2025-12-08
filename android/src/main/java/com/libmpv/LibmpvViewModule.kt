package com.libmpv

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class LibmpvViewModule : Module() {

    override fun definition() = ModuleDefinition {
        Name("LibmpvView")

        View(LibmpvView::class) {

            Events(
                "onLibmpvLog",
                "onLibmpvEvent"
            )

            AsyncFunction("runCommand") { view: LibmpvView, orders: String ->
                view.runCommand(orders)
            }

            AsyncFunction("setOptionString") { view: LibmpvView, options: String ->
                view.setOptionString(options)
            }

            AsyncFunction("cleanup") { view: LibmpvView ->
                view.cleanup()
            }

            Prop("playUrl") { view: LibmpvView, value: String ->
                view.session.playUrl = value
                view.onSessionUpdatedFromProps()
            }

            Prop("seekToSeconds") { view: LibmpvView, seconds: Double ->
                view.session.seekToSeconds = seconds
                view.session.markDirty(LibmpvSession.MpvIntent.SEEK)
                view.onSessionUpdatedFromProps()
            }

            Prop("selectedAudioTrack") { view: LibmpvView, index: Int ->
                view.session.selectedAudioTrack = index
                view.session.markDirty(LibmpvSession.MpvIntent.AUDIO_TRACK)
                view.onSessionUpdatedFromProps()
            }

            Prop("selectedSubtitleTrack") { view: LibmpvView, index: Int ->
                view.session.selectedSubtitleTrack = index
                view.session.markDirty(LibmpvSession.MpvIntent.SUBTITLE_TRACK)
                view.onSessionUpdatedFromProps()
            }

            Prop("isPlaying") { view: LibmpvView, playing: Boolean ->
                view.session.isPlaying = playing
                view.onSessionUpdatedFromProps()
            }

            Prop("videoOutput") { view: LibmpvView, value: String ->
                view.session.videoOutput = value
                view.onSessionUpdatedFromProps()
            }

            Prop("decodingMode") { view: LibmpvView, value: String ->
                view.session.decodingMode = value
                view.onSessionUpdatedFromProps()
            }

            Prop("acceleratedCodecs") { view: LibmpvView, value: String ->
                view.session.acceleratedCodecs = value
                view.onSessionUpdatedFromProps()
            }

            Prop("surfaceWidth") { view: LibmpvView, value: Int ->
                view.session.surfaceWidth = value
            }

            Prop("surfaceHeight") { view: LibmpvView, value: Int ->
                view.session.surfaceHeight = value
            }


            OnViewDestroys { view: LibmpvView ->
                view.cleanup()
            }
        }
    }
}
