package com.libmpv

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL

class LibmpvVideoModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LibmpvVideo")

    View(LibmpvVideoView::class) {
      Prop("playUrl") { view: LibmpvVideoView, playUrl: URL ->
        view.setPlayUrl(playUrl.toString())
      }
    }
  }
}
