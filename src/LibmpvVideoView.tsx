import { requireNativeView } from 'expo';
import * as React from 'react';

import { LibmpvVideoViewProps } from './LibmpvVideo.types';

const NativeView: React.ComponentType<LibmpvVideoViewProps> =
  requireNativeView('LibmpvVideo');

export default function LibmpvVideoView(props: LibmpvVideoViewProps) {
  return <NativeView {...props} />;
}
