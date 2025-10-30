export type LibmpvViewProps = {
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

export type LibmpvViewNativeMethods = {
  runCommand: (pipeDelimitedArguments: string) => void | Promise<void>;
  setOptionString: (pipeDelimitedArguments: string) => void | Promise<void>;
};