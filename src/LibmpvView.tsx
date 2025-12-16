import { requireNativeView } from 'expo';
import * as React from 'react';
import { LibmpvViewProps, LibmpvViewNativeMethods } from './LibmpvViewTypes';

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

const LibmpvViewNative: React.ComponentType<LibmpvViewProps> = requireNativeView('LibmpvView');

export const DEFAULT_DECODING_MODE: string = "mediacodec-copy"

export const DEFAULT_ACCELERATED_CODECS: string = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"

export const LibmpvView = React.forwardRef<LibmpvViewNativeMethods, LibmpvViewProps>((props: any, parentRef: any) => {
  React.useEffect(() => {
    return () => {
      if (parentRef?.current?.cleanup) {
        parentRef.current.cleanup();
      }
    };
  }, []);

  // Pass mpv events and logs back up to the parent
  const onLogEvent = (e: any) => {
    if (!props.onLibmpvEvent) {
      return
    }

    const src = e?.nativeEvent ?? e;
    const libmpvEvent = { ...src };

    if (libmpvEvent.eventId) {
      libmpvEvent.value = parseInt(libmpvEvent.eventId, 10);
      libmpvEvent.eventKind = EVENT_LOOKUP[libmpvEvent.eventId];
    } else if (libmpvEvent.kind === 'long' || libmpvEvent.kind === 'double') {
      libmpvEvent.value = Number(libmpvEvent.value);
    } else if (libmpvEvent.kind === 'boolean') {
      libmpvEvent.value = libmpvEvent.value === 'true';
    }

    return props.onLibmpvEvent(libmpvEvent);
  };

  const onLibmpvLog = (e: any) => {
    if (!props.onLibmpvLog) {
      return
    }
    const src = e?.nativeEvent ?? e
    return props.onLibmpvLog({ ...src });
  }

  // The order props are handled in the native code is non-deterministic
  // Each native prop setter checks to see if all required props are set
  // Only then will it try to create an instance of mpv

  return <LibmpvViewNative
    ref={parentRef}
    style={props.surfaceStyle ? props.surfaceStyle : styles.videoPlayer}
    videoOutput={props.videoOutput}
    playUrl={props.playUrl}
    isPlaying={props.isPlaying}
    decodingMode={props.decodingMode}
    acceleratedCodecs={props.acceleratedCodecs}
    videoSync={props.videoSync}
    surfaceWidth={props.surfaceWidth}
    surfaceHeight={props.surfaceHeight}
    seekToSeconds={props.seekToSeconds}
    selectedAudioTrack={props.selectedAudioTrack}
    selectedSubtitleTrack={props.selectedSubtitleTrack}
    onLibmpvEvent={onLogEvent}
    onLibmpvLog={onLibmpvLog}
  />
})

export default LibmpvView