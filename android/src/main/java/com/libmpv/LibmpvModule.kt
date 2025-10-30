package com.libmpv

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class LibmpvModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("Libmpv")

    AsyncFunction("getProperty") { viewTag: Int, name: String ->
      val view = appContext.findView<LibmpvView>(viewTag)
      view?.getProperty(name)
    }
  }
}
