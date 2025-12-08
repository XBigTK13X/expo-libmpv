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

      Prop("videoOutput") { view: LibmpvView, value: String ->
        view.videoOutput = value
        view.attemptCreation()
        view.log("setVideoOutput", value)
      }

    Prop("playUrl") { view: LibmpvView, value: String ->
        view.playUrl = value
        view.seekAppliedForCurrentFile = false
        view.attemptCreation()
        view.log("setPlayUrl", value)
    }

      Prop("decodingMode") { view: LibmpvView, value: String ->
        view.decodingMode = value
        view.attemptCreation()
        view.log("setDecodingMode", value)
      }

      Prop("acceleratedCodecs") { view: LibmpvView, value: String ->
        view.acceleratedCodecs = value
        view.attemptCreation()
        view.log("setAcceleratedCodecs", value)
      }

      Prop("surfaceWidth") { view: LibmpvView, value: Int ->
        view.surfaceWidth = value
        view.attemptCreation()
        view.log("setSurfaceWidth", "$value")
      }

      Prop("surfaceHeight") { view: LibmpvView, value: Int ->
        view.surfaceHeight = value
        view.attemptCreation()
        view.log("setSurfaceHeight", "$value")
      }

      Prop("selectedAudioTrack") { view: LibmpvView, index: Int ->
        view.selectAudioTrack(index)
        view.log("selectAudioTrack", "$index")
      }

      Prop("selectedSubtitleTrack") { view: LibmpvView, index: Int ->
        view.selectSubtitleTrack(index)
        view.log("selectSubtitleTrack", "$index")
      }

      Prop("seekToSeconds") { view: LibmpvView, seconds: Double ->
        view.setSeekToSeconds(seconds)
        view.log("seekToSeconds", "$seconds")
      }

      OnViewDestroys { view: LibmpvView ->
        view.cleanup()
      }
    }
  }
}
