package com.libmpv

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class LibmpvVideoModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LibmpvVideo")
    View(LibmpvVideoView::class) {
      Events("onLog", "onEvent")
      Prop("playUrl") { view: LibmpvVideoView, playUrl: String ->
        view.setPlayUrl(playUrl.toString())
      }
    }
  }
}
