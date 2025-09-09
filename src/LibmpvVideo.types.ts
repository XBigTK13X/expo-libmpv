export type LibmpvVideoViewProps = {
  style: object,
  playUrl: string,
  isPlaying: boolean,
  useHardwareDecoder: boolean,
  surfaceStyle: object,
  selectedAudioTrack: number,
  selectedSubtitleTrack: number,
  seekToSeconds: number,
  surfaceWidth: number,
  surfaceHeight: number,
  onLibmpvEvent: (libmpvEvent: object) => void,
  onLibmpvLog: (libmpvLog: object) => void,
};