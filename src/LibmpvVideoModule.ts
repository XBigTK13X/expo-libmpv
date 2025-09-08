import { NativeModule, requireNativeModule } from 'expo';

import { LibmpvVideoModuleEvents } from './LibmpvVideo.types';

declare class LibmpvVideoModule extends NativeModule<LibmpvVideoModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<LibmpvVideoModule>('LibmpvVideo');
