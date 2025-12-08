package com.libmpv

import java.util.EnumSet

class LibmpvSession {
    var playUrl: String? = null
        set(value) {
            if (field != value) {
                field = value
                resetForNewMedia()
            }
        }
    var hasFileLoaded: Boolean = false

    var seekToSeconds: Double? = null
    var selectedAudioTrack: Int? = null
    var selectedSubtitleTrack: Int? = null

    var isPlaying: Boolean = true

    var videoOutput: String? = null
    var decodingMode: String? = null
    var acceleratedCodecs: String? = null

    var surfaceWidth: Int? = null
    var surfaceHeight: Int? = null

    enum class MpvIntent {
        SEEK,
        AUDIO_TRACK,
        SUBTITLE_TRACK
    }

    private val applied = EnumSet.noneOf(MpvIntent::class.java)

    fun needsApply(intent: MpvIntent): Boolean =
        !applied.contains(intent)

    fun markApplied(intent: MpvIntent) {
        applied.add(intent)
    }

    fun markDirty(intent: MpvIntent) {
        applied.remove(intent)
    }

    fun resetForNewMedia() {
        hasFileLoaded = false
        applied.clear()
    }
}
