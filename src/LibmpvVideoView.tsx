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

const NativeView: React.ComponentType<LibmpvVideoViewProps> =
  requireNativeView('LibmpvVideo');

export default function LibmpvVideoView(props: LibmpvVideoViewProps) {
  let x = EVENT_LOOKUP[0]
  let y = styles.videoPlayer
  console.log({ x, y })
  return <NativeView {...props} />;
}
