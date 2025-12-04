export type LibmpvViewProps = {
  ref: any,
  style: any,
  videoOutput: string,
  playUrl: string,
  isPlaying: boolean,
  decodingMode: string,
  acceleratedCodecs: string,
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