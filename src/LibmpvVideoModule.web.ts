import { registerWebModule, NativeModule } from 'expo';

import { LibmpvVideoModuleEvents } from './LibmpvVideo.types';

class LibmpvVideoModule extends NativeModule<LibmpvVideoModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(LibmpvVideoModule, 'LibmpvVideoModule');
