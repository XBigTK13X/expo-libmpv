export type LibmpvVideoViewProps = {
  ref: any,
  style: any,
  playUrl: string,
  isPlaying: boolean,
  useHardwareDecoder: boolean,
  selectedAudioTrack: number,
  selectedSubtitleTrack: number,
  seekToSeconds: number,
  surfaceWidth: number,
  surfaceHeight: number,
  onLibmpvEvent: (libmpvEvent: any) => void,
  onLibmpvLog: (libmpvLog: any) => void,
};