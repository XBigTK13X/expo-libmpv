import { requireNativeModule } from 'expo-modules-core';

export const Libmpv = requireNativeModule<'getProperty' extends never ? any : {
    getProperty(viewTag: number, name: string): Promise<string | null>;
}>('Libmpv');

export default Libmpv