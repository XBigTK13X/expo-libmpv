import { requireNativeView } from 'expo';
import * as React from 'react';

import { LibmpvVideoViewProps } from './LibmpvVideo.types';

const styles: any = {
  videoPlayer: {
    position: "absolute",
    left: 0,
    bottom: 0,
    right: 0,
    top: 0
  }
};

const EVENT_LOOKUP: any = {
  0: 'NONE',
  1: 'SHUTDOWN',
  2: 'LOG_MESSAGE',
  3: 'GET_PROPERTY_REPLY',
  4: 'SET_PROPERTY_REPLY',
  5: 'COMMAND_REPLY',
  6: 'START_FILE',
  7: 'END_FILE',
  8: 'FILE_LOADED',
  16: 'CLIENT_MESSAGE',
  17: 'VIDEO_RECONFIG',
  18: 'AUDIO_RECONFIG',
  20: 'SEEK',
  21: 'PLAYBACK_RESTART',
  22: 'PROPERTY_CHANGE',
  24: 'QUEUE_OVERFLOW',
  25: 'HOOK'
}

const LibmpvVideoView: React.ComponentType<LibmpvVideoViewProps> =
  requireNativeView('LibmpvVideo');

export const LibmpvVideo = React.forwardRef((props: LibmpvVideoViewProps, parentRef) => {
  const nativeRef = React.useRef<any>(null);

  // Pass mpv events and logs back up to the parent
  const onLogEvent = (libmpvEvent: any) => {
    if (props.onLibmpvEvent) {
      if (libmpvEvent.eventId) {
        libmpvEvent.value = parseInt(libmpvEvent.eventId, 10)
        libmpvEvent.eventKind = EVENT_LOOKUP[libmpvEvent.eventId]
      }
      else if (libmpvEvent.kind === 'long' || libmpvEvent.kind === 'double') {
        libmpvEvent.value = Number(libmpvEvent.value)
      }
      else if (libmpvEvent.kind === 'boolean') {
        libmpvEvent.value = libmpvEvent.value === 'true'
      }
      return props.onLibmpvEvent(libmpvEvent)
    }
  }
  const onLibmpvLog = (libmpvLog: any) => {
    if (props.onLibmpvLog) {
      return props.onLibmpvLog(libmpvLog);
    }
  }

  // Allow a parent to call native methods, such as tweaking subtitle properties
  const callNativeMethod = (target: string) => {
    return (pipeDelimitedArguments: string) => {
      if (nativeRef.current) {
        if (target === 'runCommand') {
          nativeRef.current.runCommand(pipeDelimitedArguments)
        }
        else if (target === 'setOptionString') {
          nativeRef.current.setOptionString(pipeDelimitedArguments)
        }
      }
    }
  }
  React.useImperativeHandle(parentRef, () => ({
    runMpvCommand: callNativeMethod("runCommand"),
    setOptionString: callNativeMethod("setOptionString")
  }));


  // The order props are handled in the native code is non-deterministic
  // Each native prop setter checks to see if all required props are set
  // Only then will it try to create an instance of mpv
  return <LibmpvVideoView
    ref={nativeRef}
    style={props.surfaceStyle ? props.surfaceStyle : styles.videoPlayer}
    playUrl={props.playUrl}
    isPlaying={props.isPlaying}
    useHardwareDecoder={props.useHardwareDecoder}
    surfaceWidth={props.surfaceWidth}
    surfaceHeight={props.surfaceHeight}
    selectedAudioTrack={props.selectedAudioTrack}
    selectedSubtitleTrack={props.selectedSubtitleTrack}
    seekToSeconds={props.seekToSeconds}
    onLogEvent={onLogEvent}
    onLibmpvLog={onLibmpvLog}
  />
})

export default LibmpvVideo
