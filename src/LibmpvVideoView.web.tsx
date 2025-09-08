import * as React from 'react';

import { LibmpvVideoViewProps } from './LibmpvVideo.types';

export default function LibmpvVideoView(props: LibmpvVideoViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
